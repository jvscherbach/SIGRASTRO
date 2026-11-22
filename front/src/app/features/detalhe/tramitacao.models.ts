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

/**
 * Passagens que a lista mostra. Com "ocultar etapas automáticas" ligado, saem as automáticas, **menos** a etapa atual
 * (nunca esconder onde o requerimento está). A lista completa continua a um clique.
 */
export function passagensVisiveis(passagens: PassagemDaTramitacao[], ocultarAutomaticas: boolean): PassagemDaTramitacao[] {
  return ocultarAutomaticas ? passagens.filter((p) => !ehAutomatica(p) || p.atual) : passagens;
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
