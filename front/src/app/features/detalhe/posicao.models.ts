/**
 * Posição atual do requerimento no fluxo (S7; `GET /api/requerimentos/{id}/posicao`, docs/07 "Tramitação").
 * Os campos só do Administrador (`idTecnico`, `processoFilho`, `final.idTecnico`, `idsNaoMapeados`) ficam de fora de
 * propósito: a tela nunca mostra id técnico.
 */
export type EstadoDaPosicao = 'ATIVO' | 'ENCERRADO' | 'SEM_INSTANCIA' | 'NAO_SUPORTADO' | 'INDISPONIVEL';

export type TipoDeEtapa = 'TAREFA_HUMANA' | 'ESPERA' | 'AUTOMATICA' | 'EMISSAO';

export interface ResponsavelDaEtapa {
  tipo: string;
  papel: string;
  area: string | null;
  /** Nulo para o Operador (RN12) e com o parâmetro de nome desligado. */
  nome: string | null;
  /** Nome, senão área, senão papel: o que a tela mostra. */
  texto: string;
}

export interface EtapaDaPosicao {
  /** Nulo quando a etapa não está no catálogo ("Etapa não mapeada"). */
  id: string | null;
  rotulo: string;
  tipo: TipoDeEtapa | null;
  /** Nulo quando não se sabe (ex.: em atendimento de suporte). */
  desde: string | null;
  desdeUltimaEntrada: boolean;
  responsavel: ResponsavelDaEtapa | null;
}

export interface ParticipanteDaEtapa {
  nome: string | null;
  papel: string;
  concluiu: boolean;
}

export interface MultiInstancia {
  /** Nulos quando o ciclo não é conhecido: a tela mostra só as ativas, sem "x de y". */
  concluidos: number | null;
  ativas: number;
  total: number | null;
  participantes: ParticipanteDaEtapa[];
  maisParticipantes: number;
}

export interface AvisoDaPosicao {
  codigo: string;
  texto: string;
  gravidade: 'INFO' | 'ATENCAO';
}

export interface PosicaoDoRequerimento {
  idRequerimento: number;
  numeroRequerimento: string;
  numeroProcesso: string | null;
  situacaoLicenciamento: { codigo: string; descricao: string } | null;
  modelo: string | null;
  estado: EstadoDaPosicao;
  etapaAtual: EtapaDaPosicao | null;
  emParalelo: EtapaDaPosicao[];
  complementacao: { quantidade: number; desde: string | null } | null;
  multiInstancia: MultiInstancia | null;
  avisos: AvisoDaPosicao[];
  final: { rotulo: string; encerradoEm: string | null } | null;
}

/** Rótulo do selo de tipo (texto, não só cor). */
export const ROTULO_DO_TIPO: Record<TipoDeEtapa, string> = {
  TAREFA_HUMANA: 'Tarefa',
  ESPERA: 'Espera',
  AUTOMATICA: 'Automática',
  EMISSAO: 'Emissão',
};
