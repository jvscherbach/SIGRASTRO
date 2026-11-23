import { Page, Route } from '@playwright/test';

import { GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from '../api-simulada';
import { SimulacaoCompleta, simularTudo } from '../simulacao-completa';
import { gerarPdfDemonstracao } from './pdf-demonstracao';

/**
 * V1, bloco 2: conjunto de dados fictícios da demonstração em vídeo. Reaproveita `simularTudo` (login, sessão, dossiês
 * gerados, auditoria e configurações) e sobrepõe só o que a demonstração precisa: os quatro processos, o painel e o
 * dossiê. Nada daqui é usado pelos e2e existentes e nada fala com back-end, SIGA ou sistema externo.
 *
 * Processos (formato do SIGA, `N/AAAA`, faixa alta e fictícia):
 *   9101/2026  concluído (verificação da revisão como "Concluído")
 *   9102/2026  análise em andamento com 3 analistas (2 concluíram, 1 pendente)
 *   9103/2026  taxa vencida
 *   9104/2026  interessado é o Operador da demonstração
 */

/** Relógio fixo da gravação (fuso America/Cuiaba). */
export const RELOGIO_DA_DEMONSTRACAO = '2026-11-23T10:00:00-04:00';

/** CNPJ fictício documentado no repositório (12.345.678/0001-90) e outro, evidentemente fictício (dígitos finais inválidos). */
export const CNPJ_DA_ASSOCIACAO = '12345678000190';
export const CNPJ_DO_OPERADOR = '98765432000100';

/** Operador da demonstração: interessado do processo 9104/2026 (RN07). */
export const OPERADOR_DA_DEMONSTRACAO: UsuarioSimulado = { ...OPERADOR, nome: 'Interessado Exemplo', login: 'interessado.demo', cpfCnpj: CNPJ_DO_OPERADOR };
export const GESTOR_DA_DEMONSTRACAO: UsuarioSimulado = { ...GESTOR, nome: 'Gestor Demo' };

/** Nomes que podem aparecer em qualquer dado da demonstração (a varredura confere). */
export const NOMES_FICTICIOS = [
  'Gestor Demo',
  'Admin Demo',
  'Interessado Exemplo',
  'Associação Residencial Vale Verde',
  'Fazenda Modelo',
  'Cooperativa Exemplo',
  'Analista Exemplo 01',
  'Analista Exemplo 02',
  'Analista Exemplo 03',
  'Analista Exemplo 04',
  'Analista Exemplo 05',
  'Analista Exemplo 06',
  'Analista Exemplo 07',
  'Conferente Exemplo',
  'Revisor Exemplo',
];

const IDS = { concluido: 9101, variosAnalistas: 9102, taxaVencida: 9103, operador: 9104 } as const;
type Caso = keyof typeof IDS;

const INTERESSADO_GESTOR: Record<Caso, { nome: string; cpfCnpj: string }> = {
  concluido: { nome: 'Fazenda Modelo', cpfCnpj: CNPJ_DA_ASSOCIACAO },
  variosAnalistas: { nome: 'Associação Residencial Vale Verde', cpfCnpj: CNPJ_DA_ASSOCIACAO },
  taxaVencida: { nome: 'Cooperativa Exemplo', cpfCnpj: CNPJ_DA_ASSOCIACAO },
  operador: { nome: 'Interessado Exemplo', cpfCnpj: CNPJ_DO_OPERADOR },
};

const dia = (n: number, hora = 9): string => `2026-10-${String(n).padStart(2, '0')}T${String(hora).padStart(2, '0')}:00:00`;

type Tipo = 'TAREFA_HUMANA' | 'ESPERA' | 'AUTOMATICA' | 'EMISSAO';
const etapa = (id: string, rotulo: string, tipo: Tipo = 'TAREFA_HUMANA') => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const CONFERENCIA = etapa('conferencia_dos_documentos', 'Conferência dos documentos');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const REVISAO = etapa('revisao_do_parecer_tecnico', 'Revisão do parecer técnico');
const VERIFICACAO = etapa('verificacao_da_revisao_do_processo', 'Verificação da revisão do processo');

const marca = (tipo: string, rotulo: string, data: string | null, valor: string | null = null) => ({ tipo, rotulo, data, valor, dataAConfirmar: false });

function pessoa(papel: string, nome: string | null, estado: 'CONCLUIU' | 'PENDENTE', ocultar: boolean) {
  const mostrado = ocultar ? null : nome;
  return { tipo: mostrado ? 'SERVIDOR' : 'NAO_IDENTIFICADO', papel, nome: mostrado, inicio: dia(12), fim: estado === 'PENDENTE' ? null : dia(14), estado };
}

function passagem(e: ReturnType<typeof etapa>, entrada: number, saida: number | null, sobre: object = {}) {
  return {
    etapa: { ...e },
    ciclo: 1,
    ramo: 'PRINCIPAL',
    entrada: dia(entrada),
    saida: saida === null ? null : dia(saida),
    duracaoSegundos: saida === null ? null : (saida - entrada) * 86400,
    atual: saida === null,
    emParalelo: false,
    processoConcluido: false,
    motivoDaVolta: null,
    responsaveis: [],
    participantes: [],
    atos: [],
    marcasFinanceiras: [],
    idTecnico: 'taskFicticiaDaDemonstracao',
    repeticoes: 1,
    ...sobre,
  };
}

/** Os três analistas do 9102: dois concluíram e um segue pendente. `ocultar` esconde os nomes (Operador, RN12). */
const analistas = (ocultar: boolean) => [
  pessoa('Analista', 'Analista Exemplo 01', 'CONCLUIU', ocultar),
  pessoa('Analista', 'Analista Exemplo 02', 'CONCLUIU', ocultar),
  pessoa('Analista', 'Analista Exemplo 03', 'PENDENTE', ocultar),
];

function numeros(caso: Caso) {
  const id = IDS[caso];
  return { id, numeroProcesso: `${id}/2026`, numeroRequerimento: `${id - 100}/2026` };
}

function modeloBase(caso: Caso) {
  const { id, numeroProcesso, numeroRequerimento } = numeros(caso);
  return { idRequerimento: id, numeroRequerimento, numeroProcesso, modelo: 'Outorga de recursos hídricos' };
}

function requerimento(caso: Caso) {
  const { id, numeroProcesso, numeroRequerimento } = numeros(caso);
  const concluido = caso === 'concluido';
  return {
    ...REQUERIMENTO,
    id,
    identificacao: { rotulo: 'Processo', numero: numeroProcesso, protocolado: true },
    numeroRequerimento,
    numeroProcesso,
    situacao: concluido ? { codigo: 'CONCLUIDO', descricao: 'Concluído' } : caso === 'taxaVencida' ? { codigo: 'AGUARDANDO_PAGAMENTO_DE_TAXAS', descricao: 'Aguardando pagamento de taxas' } : { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    abertura: dia(1, 10),
    atualizacao: dia(concluido ? 20 : 15, 9),
    interessado: { ...INTERESSADO_GESTOR[caso] },
  };
}

function tramitacao(caso: Caso, ocultar: boolean) {
  const base = {
    ...modeloBase(caso),
    situacaoLicenciamento: requerimento(caso).situacao,
    estado: 'ATIVO',
    aproximado: false,
    truncado: false,
    complementacoes: [],
    taxa: { principal: 'TAXAVEL', correcao: null },
    final: null,
    avisos: [],
    planoB: null,
  };
  const cadastro = passagem(CADASTRO, 1, 2, { responsaveis: [pessoa('Interessado (cadastrante)', null, 'CONCLUIU', true)] });
  const conferencia = passagem(CONFERENCIA, 2, 3, { responsaveis: [pessoa('Conferente', 'Conferente Exemplo', 'CONCLUIU', ocultar)] });
  const taxaPaga = [
    passagem(TAXA, 3, 3, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-10-03'), marca('NUMERO_DAR', 'DAR', null, `FICT-${IDS[caso]}`)] }),
    passagem(PAGAMENTO, 3, 6, { marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-10-13'), marca('PAGA_EM', 'Paga em', '2026-10-06')] }),
  ];
  if (caso === 'concluido') {
    return {
      ...base,
      estado: 'CONCLUIDO',
      passagens: [
        cadastro,
        conferencia,
        ...taxaPaga,
        passagem(ANALISE, 6, 12, { participantes: analistas(ocultar).map((p) => ({ ...p, estado: 'CONCLUIU', fim: dia(11) })), duracaoSegundos: 6 * 86400 }),
        passagem(REVISAO, 12, 18, { responsaveis: [pessoa('Revisor', 'Revisor Exemplo', 'CONCLUIU', ocultar)] }),
        passagem(VERIFICACAO, 18, null, { processoConcluido: true }),
      ],
      final: { rotulo: 'Concluído', tipo: 'CONCLUIDO_NA_VERIFICACAO', encerradoEm: dia(18), idTecnico: 'Event_Ficticio' },
    };
  }
  if (caso === 'variosAnalistas') {
    return { ...base, passagens: [cadastro, conferencia, ...taxaPaga, passagem(ANALISE, 6, null, { participantes: analistas(ocultar) })] };
  }
  if (caso === 'taxaVencida') {
    return {
      ...base,
      passagens: [
        cadastro,
        conferencia,
        passagem(TAXA, 3, 3, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-10-03'), marca('NUMERO_DAR', 'DAR', null, `FICT-${IDS[caso]}`)] }),
        passagem(PAGAMENTO, 3, null, { marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-11-05'), marca('TAXA_VENCIDA', 'Taxa vencida', '2026-11-05')] }),
      ],
    };
  }
  return {
    ...base,
    passagens: [
      cadastro,
      conferencia,
      ...taxaPaga,
      passagem(ANALISE, 6, null, { participantes: [pessoa('Analista', 'Analista Exemplo 04', 'PENDENTE', ocultar)] }),
    ],
  };
}

function posicao(caso: Caso, ocultar: boolean) {
  const base = {
    ...modeloBase(caso),
    situacaoLicenciamento: requerimento(caso).situacao,
    estado: 'ATIVO',
    emParalelo: [],
    complementacao: null,
    multiInstancia: null,
    avisos: [] as object[],
    final: null,
  };
  const servidor = (papel: string, nome: string) => ({ tipo: 'SERVIDOR', papel, area: null, nome: ocultar ? null : nome, texto: ocultar ? papel : nome });
  if (caso === 'concluido') {
    return {
      ...base,
      estado: 'CONCLUIDO',
      etapaAtual: null,
      avisos: [{ codigo: 'CONCLUIDO_ANTES_DA_VERIFICACAO', texto: 'Processo concluído; a verificação da revisão é só arquivamento.', gravidade: 'INFO' }],
      final: { rotulo: 'Concluído', encerradoEm: dia(18) },
    };
  }
  if (caso === 'variosAnalistas') {
    return {
      ...base,
      etapaAtual: {
        ...ANALISE,
        desde: dia(6),
        desdeUltimaEntrada: false,
        responsavel: { tipo: 'VARIOS', papel: 'Analista', area: null, nome: null, texto: 'Analista' },
        idTecnico: 'taskFicticiaDaDemonstracao',
      },
      multiInstancia: {
        concluidos: 2,
        ativas: 1,
        total: 3,
        participantes: [
          { nome: ocultar ? null : 'Analista Exemplo 01', papel: 'Analista', concluiu: true },
          { nome: ocultar ? null : 'Analista Exemplo 02', papel: 'Analista', concluiu: true },
          { nome: ocultar ? null : 'Analista Exemplo 03', papel: 'Analista', concluiu: false },
        ],
        maisParticipantes: 0,
      },
    };
  }
  if (caso === 'taxaVencida') {
    return {
      ...base,
      etapaAtual: {
        ...PAGAMENTO,
        desde: dia(3),
        desdeUltimaEntrada: false,
        responsavel: { tipo: 'INTERESSADO', papel: 'Interessado', area: null, nome: null, texto: 'Interessado' },
        idTecnico: 'taskFicticiaDaDemonstracao',
      },
      avisos: [
        {
          codigo: 'TAXA_VENCIDA',
          texto: 'Taxa vencida em 05/11/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.',
          gravidade: 'ATENCAO',
        },
      ],
    };
  }
  return {
    ...base,
    etapaAtual: {
      ...ANALISE,
      desde: dia(6),
      desdeUltimaEntrada: false,
      responsavel: servidor('Analista', 'Analista Exemplo 04'),
      idTecnico: 'taskFicticiaDaDemonstracao',
    },
  };
}

function financeiro(caso: Caso) {
  const vencida = caso === 'taxaVencida';
  return {
    ...modeloBase(caso),
    estado: 'ATIVO',
    resumo: vencida ? 'VENCIDO' : 'QUITADO',
    mostraValores: true,
    lancamentos: [
      {
        ordem: 1,
        rotulo: '1ª taxa',
        situacao: vencida ? 'AGUARDANDO_PAGAMENTO' : 'PAGA',
        vencida,
        vencidaPorData: vencida,
        inconsistente: false,
        emitidaEm: '2026-10-03',
        venceEm: vencida ? '2026-11-05' : '2026-10-13',
        pagaEm: vencida ? null : '2026-10-06',
        numeroDar: `FICT-${IDS[caso]}`,
        valor: 1200,
        desconto: 0,
        upf: 30,
      },
    ],
    isencoes: [],
    avisos: vencida
      ? [{ codigo: 'TAXA_VENCIDA', texto: 'Taxa vencida em 05/11/2026. O SIGA ainda não marca a taxa como vencida.', gravidade: 'ATENCAO' }]
      : [],
  };
}

function documentos(caso: Caso) {
  const id = IDS[caso];
  const doc = (n: string, titulo: string, data: string) => ({
    origem: 'DOCUMENTO_PROCESSO',
    id: `${id}${n}`,
    idRequerimento: id,
    titulo,
    nomeArquivo: `${titulo.toLowerCase().replace(/\s+/g, '-')}.pdf`,
    origemDescricao: 'Documento do processo',
    data,
    situacao: 'VIGENTE',
    sigiloso: false,
    acessivel: true,
    extensao: 'pdf',
    tipoArquivo: 'PDF',
    selecionavelPorPadrao: true,
    bloqueio: null,
  });
  return {
    itens: [doc('1', 'Requerimento assinado', dia(1, 10)), doc('2', 'Planilha de acompanhamento', dia(5, 14)), doc('3', 'Parecer técnico', dia(12, 16))],
    avisos: [],
    geometrias: [],
    totais: { total: 3, vigentes: 3, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 },
  };
}

/** Painel do Gestor: 12 meses (protocolados acima dos concluídos), fila por etapa e carga de 7 servidores. */
export const PAINEL_DA_DEMONSTRACAO = {
  estado: 'PRONTO',
  ano: 2026,
  geradoEm: '2026-11-23T09:30:00-04:00',
  falhouEm: null,
  proximaAtualizacaoEm: '2026-11-23T10:30:00-04:00',
  truncado: false,
  serie: [
    { mes: 1, protocolados: 120, concluidos: 80 },
    { mes: 2, protocolados: 100, concluidos: 70 },
    { mes: 3, protocolados: 140, concluidos: 90 },
    { mes: 4, protocolados: 130, concluidos: 100 },
    { mes: 5, protocolados: 150, concluidos: 110 },
    { mes: 6, protocolados: 110, concluidos: 90 },
    { mes: 7, protocolados: 160, concluidos: 120 },
    { mes: 8, protocolados: 170, concluidos: 130 },
    { mes: 9, protocolados: 140, concluidos: 110 },
    { mes: 10, protocolados: 180, concluidos: 120 },
    { mes: 11, protocolados: 90, concluidos: 60 },
    { mes: 12, protocolados: 0, concluidos: 0 },
  ],
  totalProtocolados: 1490,
  totalConcluidos: 1080,
  semDataDeProtocolo: 0,
  filaPorEtapa: [
    { rotulo: 'Hídrico · Cadastro do requerimento', total: 40 },
    { rotulo: 'Hídrico · Conferência dos documentos', total: 30 },
    { rotulo: 'Hídrico · Aguardando pagamento', total: 50 },
    { rotulo: 'Hídrico · Análise do processo', total: 120 },
    { rotulo: 'Hídrico · Revisão do parecer técnico', total: 30 },
    { rotulo: 'Hídrico · Emissão do ato', total: 20 },
  ],
  totalNaFila: 290,
  foraDoCatalogo: 0,
  semInstancia: 0,
  etapaNaoMapeada: 0,
  cargaPorResponsavel: [
    { rotulo: 'Analista Exemplo 01', total: 30, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 02', total: 25, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 03', total: 20, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 04', total: 20, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 05', total: 15, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 06', total: 10, tipo: 'SERVIDOR' },
    { rotulo: 'Analista Exemplo 07', total: 5, tipo: 'SERVIDOR' },
  ],
  nomesOcultos: false,
  avisos: [],
};

/** Tudo o que o conjunto da demonstração devolve por perfil (usado pelo gravador e pela varredura). */
export function conjuntoDaDemonstracao(ocultarNomes: boolean): object {
  const casos = Object.keys(IDS) as Caso[];
  return {
    painel: PAINEL_DA_DEMONSTRACAO,
    processos: casos.map((c) => ({
      requerimento: requerimento(c),
      tramitacao: tramitacao(c, ocultarNomes),
      posicao: posicao(c, ocultarNomes),
      financeiro: financeiro(c),
      documentos: documentos(c),
    })),
  };
}

function resultadoDaBusca(caso: Caso) {
  const r = requerimento(caso);
  return {
    id: r.id,
    identificacao: r.identificacao,
    numeroRequerimento: r.numeroRequerimento,
    numeroProcesso: r.numeroProcesso,
    interessado: r.interessado,
    tipoProcesso: r.tipoProcesso,
    situacao: r.situacao,
    abertura: r.abertura,
  };
}

const pagina = (conteudo: object[]) => ({ conteudo, pagina: 0, tamanho: 20, totalElementos: conteudo.length, totalPaginas: conteudo.length ? 1 : 0 });

const casoPorId = (id: string): Caso | undefined => (Object.keys(IDS) as Caso[]).find((c) => String(IDS[c]) === id);

export interface OpcoesDaDemonstracao {
  /** Gestor (padrão) ou Operador da demonstração. */
  usuario?: UsuarioSimulado;
}

/**
 * Simula a API inteira da demonstração. O Operador só enxerga o 9104 (RN07) e nunca recebe o nome do responsável.
 * Registra as rotas por cima de `simularTudo`: o que não for da demonstração cai nela.
 */
export async function simularDemonstracao(page: Page, opcoes: OpcoesDaDemonstracao = {}): Promise<SimulacaoCompleta> {
  const usuario = opcoes.usuario ?? GESTOR_DA_DEMONSTRACAO;
  const sim = await simularTudo(page, { usuario });
  const operador = usuario.permissoes.restritoAoInteressado;
  const pdf = gerarPdfDemonstracao();
  const permitido = (c: Caso) => !operador || c === 'operador';
  let consultasDoPedido = 0;

  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();

    if (caminho === '/api/painel' && metodo === 'GET') return route.fulfill({ json: PAINEL_DA_DEMONSTRACAO });
    if (caminho === '/api/painel/atualizacao' && metodo === 'POST') return route.fulfill({ status: 202, json: {} });

    if (caminho === '/api/busca' && metodo === 'GET') {
      const numero = (url.searchParams.get('numero') ?? '').trim();
      const achados = (Object.keys(IDS) as Caso[]).filter((c) => permitido(c) && (numero === '' || [numeros(c).numeroProcesso, numeros(c).numeroRequerimento].includes(numero)));
      return route.fulfill({ json: pagina(achados.map(resultadoDaBusca)) });
    }

    const rota = /^\/api\/requerimentos\/(\d+)(\/[a-z]+)?$/.exec(caminho);
    if (rota && metodo === 'GET') {
      const caso = casoPorId(rota[1]);
      if (!caso) return route.fallback();
      if (!permitido(caso)) return route.fulfill({ status: 403, json: { status: 403, errorCode: 'NAO_INTERESSADO' } });
      switch (rota[2]) {
        case undefined:
          return route.fulfill({ json: requerimento(caso) });
        case '/tramitacao':
          return route.fulfill({ json: tramitacao(caso, operador) });
        case '/posicao':
          return route.fulfill({ json: posicao(caso, operador) });
        case '/financeiro':
          return route.fulfill({ json: financeiro(caso) });
        case '/documentos':
          return route.fulfill({ json: documentos(caso) });
        default:
          return route.fallback();
      }
    }

    if (caminho === '/api/dossies' && metodo === 'POST') {
      consultasDoPedido = 0;
      return route.fulfill({ status: 202, json: { id: 'pedido-demo' } });
    }
    if (caminho === '/api/dossies/pedido-demo' && metodo === 'GET') {
      consultasDoPedido += 1;
      const pronto = consultasDoPedido >= 2;
      const itens = [
        { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento assinado' },
        { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Planilha de acompanhamento' },
        { origem: 'DOCUMENTO_PROCESSO', id: '3', titulo: 'Parecer técnico' },
      ];
      return route.fulfill({
        json: {
          id: 'pedido-demo',
          status: pronto ? 'CONCLUIDO' : 'OBTENDO',
          total: 3,
          processados: pronto ? 3 : 1,
          erro: null,
          falhas: [],
          paginas: pronto ? 4 : null,
          expiraEm: '2099-01-01T00:00:00',
          itens: itens.map((i, n) => ({ ...i, situacao: pronto || n === 0 ? 'OK' : 'OBTENDO' })),
        },
      });
    }
    if (caminho === '/api/dossies/pedido-demo/arquivo' && metodo === 'GET') {
      const anexo = url.searchParams.get('disposicao') === 'attachment';
      return route.fulfill({
        status: 200,
        body: pdf,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `${anexo ? 'attachment' : 'inline'}; filename*=UTF-8''dossie-processo-9101-2026.pdf` },
      });
    }
    if (/^\/api\/documentos\/[^/]+\/[^/]+\/conteudo$/.test(caminho) && metodo === 'GET') {
      return route.fulfill({ status: 200, body: pdf, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="documento-exemplo.pdf"' } });
    }
    return route.fallback();
  });
  return sim;
}
