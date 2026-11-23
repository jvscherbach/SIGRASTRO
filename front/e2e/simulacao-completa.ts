import { Page, Route } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

const POR_LOGIN: Record<string, UsuarioSimulado> = { [OPERADOR.login]: OPERADOR, [GESTOR.login]: GESTOR, [ADMIN.login]: ADMIN };

/**
 * R2, bloco 15: API simulada que responde com sucesso (2xx) a tudo o que as telas principais pedem ao abrir (login,
 * consulta, detalhe, dossiês gerados, auditoria e configurações). Serve ao e2e de layout e tema, que exige zero erro
 * de console, e ao script de capturas. Só dados fictícios; nada fala com back-end ou sistema externo.
 */
export interface SimulacaoCompleta {
  /** Caminhos de /api sem resposta simulada (devolvem 404); o e2e exige lista vazia. */
  naoSimuladas: string[];
  /** Requisições a qualquer endereço fora do próprio front (ex.: fontes de terceiros). */
  externas: string[];
  /** Mensagens de erro do console e exceções não tratadas. */
  errosDoConsole: string[];
}

export interface OpcoesDaSimulacaoCompleta {
  usuario?: UsuarioSimulado;
  /** false começa sem token, para abrir o login (padrão: true). */
  comSessao?: boolean;
}

const pagina = (conteudo: object[]) => ({ conteudo, pagina: 0, tamanho: 20, totalElementos: conteudo.length, totalPaginas: conteudo.length ? 1 : 0 });

const TOTAIS = { total: 1, vigentes: 1, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 };

const DOCUMENTOS = {
  itens: [
    {
      origem: 'DOCUMENTO_PROCESSO',
      id: '1',
      idRequerimento: 101,
      titulo: 'Requerimento assinado',
      nomeArquivo: 'requerimento-assinado-com-nome-de-arquivo-bem-comprido.pdf',
      origemDescricao: 'Documento do processo',
      data: '2025-04-01T10:00:00',
      situacao: 'VIGENTE',
      sigiloso: false,
      acessivel: true,
      extensao: 'pdf',
      tipoArquivo: 'PDF',
      selecionavelPorPadrao: true,
      bloqueio: null,
    },
  ],
  avisos: [],
  geometrias: [],
  totais: TOTAIS,
};

const ETAPA = { id: 'analise_do_processo', rotulo: 'Análise do processo', tipo: 'TAREFA_HUMANA' };

const TRAMITACAO = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  modelo: 'Outorga de recursos hídricos',
  estado: 'ATIVO',
  aproximado: false,
  truncado: false,
  passagens: [
    {
      etapa: { id: 'cadastro_do_requerimento', rotulo: 'Cadastro do requerimento', tipo: 'TAREFA_HUMANA' },
      ciclo: 1,
      ramo: 'PRINCIPAL',
      entrada: '2025-03-31T10:12:00',
      saida: '2025-04-02T09:00:00',
      duracaoSegundos: 168480,
      atual: false,
      emParalelo: false,
      processoConcluido: false,
      motivoDaVolta: null,
      responsaveis: [],
      participantes: [],
      atos: [],
      marcasFinanceiras: [],
      idTecnico: 'taskFicticiaCadastro',
      repeticoes: 1,
    },
    {
      etapa: { ...ETAPA },
      ciclo: 1,
      ramo: 'PRINCIPAL',
      entrada: '2025-04-02T09:00:00',
      saida: null,
      duracaoSegundos: null,
      atual: true,
      emParalelo: false,
      processoConcluido: false,
      motivoDaVolta: null,
      responsaveis: [],
      participantes: [],
      atos: [],
      marcasFinanceiras: [],
      idTecnico: 'taskFicticiaAnalise',
      repeticoes: 1,
    },
  ],
  complementacoes: [],
  taxa: null,
  final: null,
  avisos: [],
  planoB: null,
};

const POSICAO = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  modelo: 'Outorga de recursos hídricos',
  estado: 'ATIVO',
  etapaAtual: {
    ...ETAPA,
    desde: '2025-04-02T09:00:00',
    desdeUltimaEntrada: false,
    responsavel: { tipo: 'AREA', papel: 'Equipe', area: 'Análise técnica', nome: null, texto: 'Análise técnica' },
    idTecnico: 'taskFicticiaAnalise',
  },
  emParalelo: [],
  complementacao: null,
  multiInstancia: null,
  avisos: [],
  final: null,
};

const FINANCEIRO = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  estado: 'ATIVO',
  resumo: 'QUITADO',
  mostraValores: true,
  lancamentos: [
    {
      ordem: 1,
      rotulo: '1ª taxa',
      situacao: 'PAGA',
      vencida: false,
      vencidaPorData: false,
      inconsistente: false,
      emitidaEm: '2025-04-02',
      venceEm: '2025-04-12',
      pagaEm: '2025-04-05',
      numeroDar: 'FICT-0001',
      valor: 980,
      desconto: 0,
      upf: 28,
    },
  ],
  isencoes: [],
  avisos: [],
};

const DOSSIES = [
  {
    id: 'p-pronto',
    idRequerimento: 101,
    identificacao: { rotulo: 'Processo', numero: '2825/2025' },
    situacao: 'CONCLUIDO',
    criadoEm: '2099-01-01T10:00:00',
    expiraEm: '2099-01-01T10:31:00',
    documentos: 3,
    falhas: 0,
    listados: 3,
    tamanhoBytes: 2 * 1024 * 1024,
    entregaPorTicket: false,
    temArquivo: true,
    arquivoGerado: true,
    geradoPor: 'Gestor Demo',
  },
];

const EVENTO_DE_DOCUMENTO = {
  id: 1,
  dataHora: '2026-10-21T10:30:00-04:00',
  tipo: 'VISUALIZACAO',
  rotulo: 'Visualização',
  usuario: { login: 'gestor.demo', nome: 'Gestor Demo', perfil: 'OPERADOR_SIGILO' },
  idRequerimento: 101,
  numeroProcesso: '2825/2025',
  numeroRequerimento: '3331/2025',
  sigiloso: false,
  arquivo: 'requerimento-assinado.pdf',
  idPedido: null,
  detalhe: 'Documento visualizado',
};

const EVENTO_DE_ACESSO = { ...EVENTO_DE_DOCUMENTO, tipo: 'LOGIN', rotulo: 'Acesso', idRequerimento: null, numeroProcesso: null, numeroRequerimento: null, arquivo: null, detalhe: null };

const USUARIOS = [
  { id: 'admin.demo', nome: 'Admin Demo', login: 'admin.demo', perfil: 'ADMIN', perfilRotulo: 'Administrador', ativo: true, ultimoAcesso: null, cpfCnpj: null },
  { id: 'gestor.demo', nome: 'Gestor Demo', login: 'gestor.demo', perfil: 'OPERADOR_SIGILO', perfilRotulo: 'Gestor', ativo: true, ultimoAcesso: null, cpfCnpj: null },
];

const PARAMETROS = [
  {
    nome: 'dossie.ttlMinutos',
    rotulo: 'Expiração do dossiê (min)',
    grupo: 'DOSSIE',
    tipo: 'INTEIRO',
    valor: '30',
    padrao: '30',
    minimo: 5,
    maximo: 240,
    opcoes: null,
    classeDeAplicacao: 'PROXIMA_GERACAO',
    sobreposto: false,
    pendenteDeReinicio: false,
  },
];

const CONEXOES = {
  conexoes: [{ nome: 'siga', rotulo: 'Banco do SIGA', estado: 'NAO_CONFIGURADA', horasAteExpirar: null, tokenVencido: false }],
  tempoLimiteDoTesteSegundos: 15,
};

function resposta(caminho: string, metodo: string): object | null {
  if (metodo !== 'GET') return null;
  if (caminho === '/api/busca') return pagina([]);
  if (caminho === '/api/requerimentos/101') return REQUERIMENTO;
  if (caminho === '/api/requerimentos/101/documentos') return DOCUMENTOS;
  if (caminho === '/api/requerimentos/101/tramitacao') return TRAMITACAO;
  if (caminho === '/api/requerimentos/101/posicao') return POSICAO;
  if (caminho === '/api/requerimentos/101/financeiro') return FINANCEIRO;
  if (caminho === '/api/tramitacao/parametros') return { posicoesSimultaneas: 4 };
  if (caminho === '/api/dossies') return pagina(DOSSIES);
  if (caminho === '/api/auditoria/documentos') {
    return { ...pagina([EVENTO_DE_DOCUMENTO]), indicadores: { dossiesGerados: 1, eventosComSigilosos: 0, usuarios: 1 } };
  }
  if (caminho === '/api/auditoria/acessos') return { ...pagina([EVENTO_DE_ACESSO]), indicadores: { entradas: 1, alteracoes: 0, usuarios: 1 } };
  if (caminho === '/api/configuracoes/usuarios') return pagina(USUARIOS);
  if (caminho === '/api/configuracoes/parametros') return { parametros: PARAMETROS };
  if (caminho === '/api/configuracoes/conexoes') return CONEXOES;
  return null;
}

/** Simula a API inteira das telas principais e passa a registrar console, requisições externas e caminhos sem simulação. */
export async function simularTudo(page: Page, opcoes: OpcoesDaSimulacaoCompleta = {}): Promise<SimulacaoCompleta> {
  const sim: SimulacaoCompleta = { naoSimuladas: [], externas: [], errosDoConsole: [] };
  let usuario = opcoes.usuario ?? GESTOR;
  if (opcoes.comSessao !== false) {
    await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  }
  page.on('console', (mensagem) => {
    if (mensagem.type() === 'error') sim.errosDoConsole.push(mensagem.text());
  });
  page.on('pageerror', (erro) => sim.errosDoConsole.push(erro.message));
  page.on('request', (requisicao) => {
    const url = new URL(requisicao.url());
    if (url.protocol.startsWith('http') && url.hostname !== 'localhost') sim.externas.push(url.origin);
  });

  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/auth/login') {
      const { usuario: login } = route.request().postDataJSON() as { usuario: string };
      usuario = POR_LOGIN[login] ?? usuario;
      return route.fulfill({ json: { token: 'token-de-teste', tipo: 'Bearer', expiraEm: '2099-01-01T00:00:00-04:00', usuario } });
    }
    if (caminho === '/api/auth/logout') return route.fulfill({ status: 204 });
    const json = resposta(caminho, metodo);
    if (json) return route.fulfill({ json });
    sim.naoSimuladas.push(`${metodo} ${caminho}`);
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

/** Uma tela principal: rota, quem a abre e o elemento que indica que ela terminou de carregar. */
export interface TelaPrincipal {
  nome: string;
  rota: string;
  /** null abre sem sessão (login). */
  usuario: UsuarioSimulado | null;
  pronta: string;
}

export const TELAS_PRINCIPAIS: TelaPrincipal[] = [
  { nome: 'login', rota: '/login', usuario: null, pronta: '.login-card' },
  { nome: 'consulta', rota: '/consulta', usuario: GESTOR, pronta: '#consulta-numero' },
  { nome: 'detalhe', rota: '/consulta/requerimentos/101', usuario: GESTOR, pronta: 'section[aria-label="Documentos"] .table-wrap tbody tr' },
  { nome: 'dossies', rota: '/dossies', usuario: GESTOR, pronta: 'main table tbody tr' },
  { nome: 'auditoria-documentos', rota: '/auditoria/documentos', usuario: ADMIN, pronta: 'main table tbody tr' },
  { nome: 'auditoria-acessos', rota: '/auditoria/acessos', usuario: ADMIN, pronta: 'main table tbody tr' },
  { nome: 'configuracoes', rota: '/configuracoes', usuario: ADMIN, pronta: 'tr[data-login="admin.demo"]' },
];

/** Abre a tela com a simulação completa e espera ela terminar de carregar (sem pedido de rede pendente). */
export async function abrirTela(page: Page, tela: TelaPrincipal): Promise<SimulacaoCompleta> {
  const sim = await simularTudo(page, tela.usuario ? { usuario: tela.usuario } : { comSessao: false });
  await page.goto(tela.rota);
  await page.locator(tela.pronta).first().waitFor();
  await page.waitForLoadState('networkidle');
  return sim;
}
