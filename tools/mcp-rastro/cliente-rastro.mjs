// Cliente HTTP do servidor MCP para o Rastro local (S6, bloco 7). Só local, só leitura:
//  - recusa URL cujo host não seja localhost, 127.0.0.1 ou host.docker.internal (sem exceção por variável);
//  - o único pedido que não é GET é o login (POST /api/auth/login), feito pelo próprio cliente; as consultas usam só GET;
//  - token só em memória, renovado ao vencer ou ao receber 401;
//  - tempo limite, tamanho máximo de resposta e redirecionamento recusado (um 3xx poderia levar a outro host);
//  - erro sem URL, sem parâmetro, sem token e sem corpo bruto.
// Usuário e senha vêm do ambiente do processo (RASTRO_MCP_USUARIO, RASTRO_MCP_SENHA), nunca de arquivo.

export const HOSTS_PERMITIDOS = new Set(['localhost', '127.0.0.1', 'host.docker.internal']);
export const URL_PADRAO = 'http://localhost:8080';
export const TEMPO_LIMITE_PADRAO_MS = 10_000;
export const TAMANHO_MAXIMO_PADRAO = 1024 * 1024;
const RENOVAR_ANTES_MS = 60_000;

/** Erro com mensagem segura para mostrar (nunca URL, token, corpo). */
export class ErroDoRastro extends Error {
  constructor(mensagem, { status = null, codigo = null } = {}) {
    super(mensagem);
    this.name = 'ErroDoRastro';
    this.status = status;
    this.codigo = codigo;
  }
}

/** Valida e normaliza a base; recusa host remoto, protocolo estranho, usuário/senha na URL e caminho com consulta. */
export function validarUrl(texto) {
  let url;
  try {
    url = new URL(texto || URL_PADRAO);
  } catch {
    throw new ErroDoRastro('RASTRO_MCP_URL inválida.');
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new ErroDoRastro('RASTRO_MCP_URL precisa ser http ou https.');
  }
  if (!HOSTS_PERMITIDOS.has(url.hostname.toLowerCase())) {
    throw new ErroDoRastro(
      'Recusado: o servidor MCP só fala com o Rastro local (localhost, 127.0.0.1 ou host.docker.internal).',
    );
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new ErroDoRastro('RASTRO_MCP_URL não pode ter usuário, senha, consulta nem fragmento.');
  }
  return url.origin + url.pathname.replace(/\/+$/, '');
}

export class ClienteRastro {
  /**
   * @param {object} opcoes
   * @param {string} [opcoes.url]
   * @param {string} [opcoes.usuario]
   * @param {string} [opcoes.senha]
   * @param {number} [opcoes.tempoLimiteMs]
   * @param {number} [opcoes.tamanhoMaximo]
   * @param {typeof fetch} [opcoes.fetch]
   * @param {() => number} [opcoes.agora]
   */
  constructor({ url, usuario, senha, tempoLimiteMs, tamanhoMaximo, fetch: buscar, agora } = {}) {
    this.base = validarUrl(url);
    this.usuario = usuario ?? '';
    this.senha = senha ?? '';
    this.tempoLimiteMs = tempoLimiteMs ?? TEMPO_LIMITE_PADRAO_MS;
    this.tamanhoMaximo = tamanhoMaximo ?? TAMANHO_MAXIMO_PADRAO;
    this.buscar = buscar ?? globalThis.fetch;
    this.agora = agora ?? (() => Date.now());
    this.token = null;
    this.expiraEm = 0;
  }

  /** Abre a sessão. Único pedido que não é GET. */
  async entrar() {
    if (!this.usuario || !this.senha) {
      throw new ErroDoRastro('Defina RASTRO_MCP_USUARIO e RASTRO_MCP_SENHA (usuário de demonstração) no ambiente do servidor MCP.');
    }
    const resposta = await this.#pedir('POST', '/api/auth/login', {
      body: JSON.stringify({ usuario: this.usuario, senha: this.senha }),
      headers: { 'Content-Type': 'application/json' },
    });
    const corpo = await this.#json(resposta);
    if (!corpo || typeof corpo.token !== 'string' || !corpo.token) {
      throw new ErroDoRastro('O Rastro não devolveu uma sessão.');
    }
    this.token = corpo.token;
    const expira = Date.parse(corpo.expiraEm ?? '');
    this.expiraEm = Number.isFinite(expira) ? expira : this.agora() + 30 * 60_000;
  }

  /** GET autenticado; renova a sessão perto de vencer e uma vez depois de um 401. */
  async get(caminho, parametros = {}) {
    if (!this.token || this.agora() >= this.expiraEm - RENOVAR_ANTES_MS) {
      await this.entrar();
    }
    const consulta = new URLSearchParams();
    for (const [chave, valor] of Object.entries(parametros)) {
      if (valor !== undefined && valor !== null && valor !== '') consulta.set(chave, String(valor));
    }
    const sufixo = consulta.size ? `?${consulta}` : '';
    let resposta = await this.#pedir('GET', caminho + sufixo, { autenticado: true, aceitarErro: true });
    if (resposta.status === 401) {
      this.token = null;
      await this.entrar();
      resposta = await this.#pedir('GET', caminho + sufixo, { autenticado: true, aceitarErro: true });
    }
    if (!resposta.ok) {
      throw await this.#erroHttp(resposta);
    }
    return this.#json(resposta);
  }

  async #pedir(metodo, caminho, { body, headers = {}, autenticado = false, aceitarErro = false } = {}) {
    const cabecalhos = { Accept: 'application/json', ...headers };
    if (autenticado && this.token) {
      cabecalhos.Authorization = `Bearer ${this.token}`;
    }
    let resposta;
    try {
      resposta = await this.buscar(this.base + caminho, {
        method: metodo,
        headers: cabecalhos,
        body,
        redirect: 'manual',
        signal: AbortSignal.timeout(this.tempoLimiteMs),
      });
    } catch (erro) {
      if (erro && (erro.name === 'TimeoutError' || erro.name === 'AbortError')) {
        throw new ErroDoRastro(`Tempo limite (${Math.round(this.tempoLimiteMs / 1000)} s) excedido sem resposta do Rastro.`);
      }
      throw new ErroDoRastro('Rastro local inacessível. Suba o ambiente de demonstração (docs/claude-code/mcp.md).');
    }
    if (resposta.status >= 300 && resposta.status < 400) {
      throw new ErroDoRastro(`O Rastro respondeu com redirecionamento (HTTP ${resposta.status}); recusado.`, {
        status: resposta.status,
      });
    }
    if (!resposta.ok && !aceitarErro) {
      throw await this.#erroHttp(resposta);
    }
    return resposta;
  }

  /** Lê o corpo com teto de tamanho, sem guardar além do limite. */
  async #texto(resposta) {
    const declarado = Number(resposta.headers.get('content-length'));
    if (Number.isFinite(declarado) && declarado > this.tamanhoMaximo) {
      throw new ErroDoRastro('Resposta do Rastro grande demais; recusada.');
    }
    if (!resposta.body) {
      return '';
    }
    const leitor = resposta.body.getReader();
    const partes = [];
    let total = 0;
    for (;;) {
      const { done, value } = await leitor.read();
      if (done) break;
      total += value.byteLength;
      if (total > this.tamanhoMaximo) {
        await leitor.cancel();
        throw new ErroDoRastro('Resposta do Rastro grande demais; recusada.');
      }
      partes.push(value);
    }
    return Buffer.concat(partes.map((p) => Buffer.from(p))).toString('utf8');
  }

  async #json(resposta) {
    const texto = await this.#texto(resposta);
    if (!texto) return null;
    try {
      return JSON.parse(texto);
    } catch {
      throw new ErroDoRastro('Resposta do Rastro não é JSON.');
    }
  }

  /** Só o status e o errorCode do ProblemDetail: nunca o corpo nem a URL. */
  async #erroHttp(resposta) {
    let codigo = null;
    try {
      const corpo = JSON.parse(await this.#texto(resposta));
      if (corpo && typeof corpo.errorCode === 'string' && /^[A-Z_]{3,60}$/.test(corpo.errorCode)) {
        codigo = corpo.errorCode;
      }
    } catch {
      // corpo ilegível: fica só o status
    }
    const explicacao = {
      401: 'credencial de demonstração recusada',
      403: 'sem permissão',
      404: 'não encontrado',
      503: 'serviço indisponível (no modo de demonstração o SIGA não está ligado)',
    }[resposta.status];
    const partes = [`HTTP ${resposta.status}`];
    if (codigo) partes.push(codigo);
    return new ErroDoRastro(
      `O Rastro respondeu ${partes.join(' ')}${explicacao ? ` (${explicacao})` : ''}.`,
      { status: resposta.status, codigo },
    );
  }
}
