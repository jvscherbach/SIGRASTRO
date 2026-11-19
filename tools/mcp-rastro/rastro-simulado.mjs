// Rastro SIMULADO (S6, bloco 7): servidor HTTP local, só em 127.0.0.1, com dados fictícios evidentes, para os testes
// do servidor MCP e para a demonstração sem SIGA. Responde só às rotas que o servidor MCP usa e registra cada pedido
// (método e caminho) para os testes conferirem que nenhuma ferramenta usa método diferente de GET.
//
// `vazar: true` injeta de propósito, na resposta, coisas que nunca podem sair do MCP (CPF/CNPJ com padrão de documento,
// e-mail, JWT montado na hora, Bearer, cookie, chave de arquivo), para provar o mascaramento. Nada disso é real.

import http from 'node:http';
import { pathToFileURL } from 'node:url';

const REQUERIMENTO = {
  id: 101,
  identificacao: { rotulo: 'Processo', numero: '2825/2025' },
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  tipoProcesso: 'Uso de Recursos Hídricos',
  situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  abertura: '2025-03-31T10:12:00',
};

const DOCUMENTOS = [
  { titulo: 'Requerimento assinado', nomeArquivo: 'requerimento.pdf', tipoArquivo: 'PDF', origemDescricao: 'Documento do processo', data: '2025-04-01T10:00:00', situacao: 'VIGENTE', sigiloso: false },
  { titulo: 'Planilha de vazões', nomeArquivo: 'vazoes.xlsx', tipoArquivo: 'PLANILHA', origemDescricao: 'Documento do processo', data: '2025-04-02T09:30:00', situacao: 'VIGENTE', sigiloso: false },
  { titulo: 'Laudo técnico', nomeArquivo: 'laudo-antigo.pdf', tipoArquivo: 'PDF', origemDescricao: 'Documento do requerimento', data: '2025-03-20T08:00:00', situacao: 'SUBSTITUIDO', sigiloso: false },
  { titulo: 'Documento sigiloso', nomeArquivo: null, tipoArquivo: null, origemDescricao: 'Documento do processo', data: '2025-04-03T15:00:00', situacao: 'INDISPONIVEL', sigiloso: true },
];

/** Monta um JWT de mentira na hora (nunca literal no repositório). */
function jwtFicticio() {
  const b = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  return `${b({ alg: 'none' })}.${b({ sub: 'ficticio', exp: 1 })}.${'s'.repeat(12)}`;
}

/** Documento com o padrão de CPF/CNPJ, montado na hora; inválido de propósito, só para testar a máscara. */
const cpfFormato = () => ['000', '000', '000'].join('.') + '-01';
const cnpjCru = () => '0'.repeat(13) + '1';

function vazamentos() {
  return {
    cpfCnpjDoInteressado: cpfFormato(),
    email: ['contato', 'exemplo.invalid'].join('@'),
    observacao: `interessado ${cnpjCru()} token=${jwtFicticio()} Bearer abcdefghijklmnop12345 Cookie: sessao=xyz`,
    chave: 'CHAVE-INTERNA-0001',
  };
}

/**
 * @param {object} [opcoes]
 * @param {string} [opcoes.modo] 'demonstracao' (padrão) ou outro
 * @param {boolean} [opcoes.vazar] injeta dados que o MCP precisa mascarar
 * @param {number} [opcoes.atrasoMs] atraso das consultas (testes de tempo limite)
 * @param {number} [opcoes.porta] 0 = qualquer porta livre
 * @param {number} [opcoes.validadeMs] validade da sessão devolvida no login
 */
export function iniciarRastroSimulado({ modo = 'demonstracao', vazar = false, atrasoMs = 0, porta = 0, validadeMs = 8 * 3600_000, tamanhoExtra = 0 } = {}) {
  const pedidos = [];
  let sessoes = 0;
  const tokens = new Set();

  const servidor = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    pedidos.push({ metodo: req.method, caminho: url.pathname, consulta: url.search });
    const responder = (status, corpo) => {
      const texto = JSON.stringify(corpo);
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(texto);
    };
    if (req.method === 'POST' && url.pathname === '/api/auth/login') {
      let corpo = '';
      req.on('data', (p) => (corpo += p));
      req.on('end', () => {
        let dados = {};
        try { dados = JSON.parse(corpo); } catch { /* corpo inválido */ }
        if (!dados.usuario || !dados.senha) return responder(401, { status: 401, errorCode: 'NAO_AUTENTICADO' });
        sessoes++;
        const token = `sessao-simulada-${sessoes}`;
        tokens.add(token);
        return responder(200, { token, tipo: 'Bearer', expiraEm: new Date(Date.now() + validadeMs).toISOString() });
      });
      return;
    }
    const token = (req.headers.authorization ?? '').replace(/^Bearer /, '');
    if (!tokens.has(token)) return responder(401, { status: 401, errorCode: 'NAO_AUTENTICADO', detail: 'sessão inválida' });
    const enviar = (status, corpo) => setTimeout(() => responder(status, corpo), atrasoMs);
    if (req.method !== 'GET') return enviar(405, { status: 405, errorCode: 'PARAMETRO_INVALIDO' });
    if (url.pathname === '/api/estado') return enviar(200, { status: 'UP', modo, sistemasExternos: false });
    if (url.pathname === '/api/busca') {
      const numero = url.searchParams.get('numero');
      const tipo = url.searchParams.get('tipo');
      const achou = (tipo === 'PROCESSO' && numero === REQUERIMENTO.numeroProcesso)
        || (tipo === 'REQUERIMENTO' && numero === REQUERIMENTO.numeroRequerimento);
      if (!achou) return enviar(404, { status: 404, errorCode: 'NAO_ENCONTRADO', detail: `Nada para ${numero}` });
      const item = vazar ? { ...REQUERIMENTO, ...vazamentos() } : REQUERIMENTO;
      return enviar(200, { conteudo: [item], pagina: 0, tamanho: 20, totalElementos: 1, totalPaginas: 1, extra: 'x'.repeat(tamanhoExtra) });
    }
    if (url.pathname === '/api/requerimentos/101/documentos') {
      const itens = DOCUMENTOS.map((d) => (vazar ? { ...d, ...vazamentos(), titulo: `${d.titulo} ${cpfFormato()}` } : d));
      return enviar(200, { itens, totais: {}, geometrias: [], avisos: [] });
    }
    return enviar(404, { status: 404, errorCode: 'NAO_ENCONTRADO' });
  });

  return new Promise((resolver) => {
    servidor.listen(porta, '127.0.0.1', () => {
      const { port } = servidor.address();
      resolver({
        url: `http://127.0.0.1:${port}`,
        pedidos,
        sessoes: () => sessoes,
        invalidarSessoes: () => tokens.clear(),
        fechar: () => new Promise((r) => servidor.close(() => r())),
      });
    });
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const porta = Number(process.env.PORTA) || 8099;
  iniciarRastroSimulado({ porta, vazar: process.env.VAZAR === '1' }).then(({ url }) => {
    process.stderr.write(`Rastro simulado (dados fictícios, modo demonstracao) em ${url}\n`);
    process.stderr.write('Aponte RASTRO_MCP_URL para este endereço; qualquer usuário e senha não vazios entram.\n');
  });
}
