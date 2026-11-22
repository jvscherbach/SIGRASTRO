/**
 * Posição atual do requerimento no fluxo (S7; `GET /api/requerimentos/{id}/posicao`, docs/07 "Tramitação").
 * Os campos só do Administrador (`idTecnico`, `processoFilho`, `final.idTecnico`, `idsNaoMapeados`) ficam de fora de
 * propósito: a tela nunca mostra id técnico.
 */
export type EstadoDaPosicao = 'ATIVO' | 'ENCERRADO' | 'CONCLUIDO' | 'SEM_INSTANCIA' | 'NAO_SUPORTADO' | 'INDISPONIVEL';

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

/** Cor do ponto de estado; o texto acessível vem junto em {@link ResumoDaPosicao.estado}. */
export type PontoDeEstado = 'ativo' | 'atencao' | 'encerrado' | 'neutro' | 'erro';

/** Posição numa linha (modo resumido do processo e coluna "Etapa atual" da busca). */
export interface ResumoDaPosicao {
  texto: string;
  comQuem: string | null;
  tipo: string | null;
  paralelos: number;
  ponto: PontoDeEstado;
  /** Texto do ponto de estado para o leitor de tela ("Em andamento", "Encerrado"...). */
  estado: string;
}

export const RESUMO_DE_ERRO: ResumoDaPosicao = {
  texto: 'Não foi possível consultar',
  comQuem: null,
  tipo: null,
  paralelos: 0,
  ponto: 'erro',
  estado: 'Indisponível',
};

export function resumirPosicao(p: PosicaoDoRequerimento): ResumoDaPosicao {
  const base = { comQuem: null, tipo: null, paralelos: 0 };
  switch (p.estado) {
    case 'ATIVO': {
      const etapa = p.etapaAtual;
      const atencao = p.avisos.some((a) => a.gravidade === 'ATENCAO');
      return {
        texto: etapa?.rotulo ?? 'Etapa não identificada',
        comQuem: etapa?.responsavel?.texto ?? null,
        tipo: etapa?.tipo ? ROTULO_DO_TIPO[etapa.tipo] : null,
        paralelos: p.emParalelo.length,
        ponto: atencao ? 'atencao' : 'ativo',
        estado: atencao ? 'Em andamento, com aviso' : 'Em andamento',
      };
    }
    case 'ENCERRADO':
      return { ...base, texto: p.final?.rotulo ?? 'Encerrado', ponto: 'encerrado', estado: 'Encerrado' };
    case 'CONCLUIDO':
      // P38 (D83): verificação da revisão = arquivamento do processo concluído, não "em andamento com alguém".
      return { ...base, texto: p.final?.rotulo ?? 'Concluído', ponto: 'encerrado', estado: 'Concluído' };
    case 'SEM_INSTANCIA':
      return { ...base, texto: 'Histórico detalhado indisponível', ponto: 'neutro', estado: 'Sem histórico detalhado' };
    case 'NAO_SUPORTADO':
      return { ...base, texto: 'Fluxo ainda não suportado', ponto: 'neutro', estado: 'Não suportado' };
    default:
      return { ...base, texto: 'Andamento indisponível agora', ponto: 'erro', estado: 'Indisponível' };
  }
}

/** Rótulo do selo de tipo (texto, não só cor). */
export const ROTULO_DO_TIPO: Record<TipoDeEtapa, string> = {
  TAREFA_HUMANA: 'Tarefa',
  ESPERA: 'Espera',
  AUTOMATICA: 'Automática',
  EMISSAO: 'Emissão',
};
