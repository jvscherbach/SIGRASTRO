import { EstadoDaPosicao, TipoDeEtapa } from './posicao.models';

/**
 * Tramitação completa do requerimento (S8; `GET /api/requerimentos/{id}/tramitacao`, docs/07 "Tramitação completa").
 * Os campos só do Administrador (`idTecnico`, `repeticoes`, `final.idTecnico`, `idsNaoMapeados`, `diagnosticos`) ficam de
 * fora de propósito: a tela nunca mostra id técnico.
 */
export type EstadoDaTramitacao = EstadoDaPosicao;

export interface EtapaDaPassagem {
  /** Nulo quando a etapa não está no catálogo ("Etapa não mapeada"). */
  id: string | null;
  rotulo: string;
  tipo: TipoDeEtapa | null;
}

export type EstadoDoParticipante = 'CONCLUIU' | 'PENDENTE' | 'ENCERRADA_SEM_ACAO';

export interface ResponsavelDaPassagem {
  tipo: string;
  papel: string;
  /** Nulo para o Operador (RN12), com o parâmetro de nome desligado e quando não identificado. */
  nome: string | null;
  inicio: string | null;
  /** Nulo = pendente. */
  fim: string | null;
  estado: EstadoDoParticipante;
}

export interface AtoDaPassagem {
  tipo: string;
  rotulo: string;
  inicio: string | null;
  fim: string | null;
  estado: 'EMITIDO' | 'NAO_CONCLUIDO' | 'EM_ANDAMENTO' | 'INDETERMINADO';
  ocorreu: boolean;
}

export type TipoDeMarcaFinanceira =
  | 'EMITIDA_EM'
  | 'NUMERO_DAR'
  | 'VENCE_EM'
  | 'PAGA_EM'
  | 'TAXA_VENCIDA'
  | 'ISENTO_DE_TAXA'
  | 'DA_CORRECAO';

/**
 * Marca financeira ao lado de uma passagem (S9, bloco 5; `passagens[].marcasFinanceiras`, docs/07): só fatos lidos do
 * lançamento de taxa. Vazia sem caminho exato (plano B, Camunda fora) e sem lançamento casado: a tela nunca inventa marca.
 */
export interface MarcaFinanceira {
  tipo: TipoDeMarcaFinanceira;
  /** Texto da API ("Emitida em", "Vence em", "Isento de Taxa"...). */
  rotulo: string;
  /** Data pura (aaaa-mm-dd); vazia quando a marca não é de data. */
  data: string | null;
  /** Só no DAR: o número. */
  valor: string | null;
  /** Só no "Isento de Taxa": a data é provisória. */
  dataAConfirmar: boolean;
}

export interface PassagemDaTramitacao {
  etapa: EtapaDaPassagem;
  /** 1 na primeira passagem pela etapa; 2, 3... nas voltas. */
  ciclo: number;
  ramo: 'PRINCIPAL' | 'CORRECAO';
  entrada: string;
  /** Nulo na passagem atual. */
  saida: string | null;
  duracaoSegundos: number | null;
  atual: boolean;
  emParalelo: boolean;
  processoConcluido: boolean;
  motivoDaVolta: { codigo: string; texto: string } | null;
  responsaveis: ResponsavelDaPassagem[];
  participantes: ResponsavelDaPassagem[];
  atos: AtoDaPassagem[];
  /** S9: ausente (respostas antigas) equivale a vazia. */
  marcasFinanceiras?: MarcaFinanceira[];
}

export interface AvisoDaTramitacao {
  codigo: string;
  texto: string;
  gravidade: 'INFO' | 'ATENCAO';
}

export interface FinalDaTramitacao {
  rotulo: string;
  /** `FINAL` = evento de fim (bola preta); `CONCLUIDO_NA_VERIFICACAO` = processo concluído, sem bola preta. */
  tipo: 'FINAL' | 'CONCLUIDO_NA_VERIFICACAO';
  encerradoEm: string | null;
}

export interface HistoricoAproximado {
  selo: string;
  semDetalhamento: boolean;
  etapas: { etapa: EtapaDaPassagem; entrada: string; saida: string | null; eventos: number }[];
  marcos: { tipo: string; rotulo: string; data: string }[];
  situacao: string | null;
  eventosIgnorados: number;
  eventosTruncados: boolean;
}

export interface TramitacaoDoRequerimento {
  idRequerimento: number;
  numeroRequerimento: string;
  numeroProcesso: string | null;
  situacaoLicenciamento: { codigo: string; descricao: string } | null;
  modelo: string | null;
  estado: EstadoDaTramitacao;
  /** Plano B: dado aproximado, nunca exato. */
  aproximado: boolean;
  truncado: boolean;
  passagens: PassagemDaTramitacao[];
  complementacoes: { entrada: string; saida: string | null }[];
  final: FinalDaTramitacao | null;
  avisos: AvisoDaTramitacao[];
  planoB: HistoricoAproximado | null;
}

/** "2ª passagem: devolvido na validação do parecer"; sem motivo determinável, só "2ª passagem"; 1ª passagem, vazio. */
export function textoDaVolta(p: PassagemDaTramitacao): string {
  if (p.ciclo <= 1) return '';
  const base = `${p.ciclo}ª passagem`;
  return p.motivoDaVolta ? `${base}: ${p.motivoDaVolta.texto}` : base;
}

/** Etapa que o sistema cumpre sozinho (emissão da taxa, isenção, termo, conclusão): some do filtro "ocultar automáticas". */
export function ehAutomatica(p: PassagemDaTramitacao): boolean {
  return p.etapa.tipo === 'AUTOMATICA';
}

/** As marcas financeiras da passagem (vazia quando a API não mandou). */
export function marcasDe(p: PassagemDaTramitacao): MarcaFinanceira[] {
  return p.marcasFinanceiras ?? [];
}

/**
 * Passagens que a lista mostra. Com "ocultar etapas automáticas" ligado, saem as automáticas, **menos** a etapa atual
 * (nunca esconder onde o requerimento está) e **menos** as que trazem marca financeira (a "Emissão da taxa" e a "Isenção
 * confirmada" são automáticas, mas é nelas que a data, o DAR e o "Isento de Taxa" aparecem; S9, bloco 9). A lista completa
 * continua a um clique.
 */
export function passagensVisiveis(passagens: PassagemDaTramitacao[], ocultarAutomaticas: boolean): PassagemDaTramitacao[] {
  return ocultarAutomaticas ? passagens.filter((p) => !ehAutomatica(p) || p.atual || marcasDe(p).length > 0) : passagens;
}

/**
 * Texto de uma marca ("Emitida em 04/03/2026", "DAR FICT-0001", "Taxa vencida em 19/03/2026", "Isento de Taxa em
 * 04/03/2026"). A data entra como data pura (nunca desloca o dia). O "Isento de Taxa" sem data diz "data não registrada";
 * a nota "data a confirmar" é de `marcaPedeConfirmacao`.
 */
export function textoDaMarca(m: MarcaFinanceira, formatarData: (iso: string | null) => string): string {
  switch (m.tipo) {
    case 'NUMERO_DAR':
      return m.valor ? `DAR ${m.valor}` : 'DAR';
    case 'DA_CORRECAO':
      return m.rotulo || 'da correção';
    case 'ISENTO_DE_TAXA':
      return m.data ? `${m.rotulo} em ${formatarData(m.data)}` : `${m.rotulo} — data não registrada`;
    default:
      return m.data ? `${m.rotulo} ${m.tipo === 'TAXA_VENCIDA' ? 'em ' : ''}${formatarData(m.data)}` : m.rotulo;
  }
}

/** O "Isento de Taxa" com data que não é a do lançamento: a tela acrescenta "data a confirmar". */
export function marcaPedeConfirmacao(m: MarcaFinanceira): boolean {
  return m.tipo === 'ISENTO_DE_TAXA' && m.dataAConfirmar && m.data !== null;
}

export const ROTULO_DO_ESTADO_DO_PARTICIPANTE: Record<EstadoDoParticipante, string> = {
  CONCLUIU: 'Concluiu',
  PENDENTE: 'Pendente',
  ENCERRADA_SEM_ACAO: 'Encerrada sem ação',
};

export const ROTULO_DO_ESTADO_DO_ATO: Record<AtoDaPassagem['estado'], string> = {
  EMITIDO: 'Emitido',
  NAO_CONCLUIDO: 'Não concluído',
  EM_ANDAMENTO: 'Em andamento',
  INDETERMINADO: 'Situação não confirmada',
};
