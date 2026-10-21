import { Identificacao, Interessado, Situacao } from '../../shared/api-tipos';

export interface FinalidadeAtividade {
  finalidade: string | null;
  atividade: string | null;
}

export interface TipoObjetivo {
  tipo: string | null;
  tipoDescricao: string | null;
  objetivo: string | null;
}

export interface AtividadeLicenciamento {
  grupo: string | null;
  atividade: string | null;
}

/** Listas independentes, como na tela do SIGA: objetivos e pares finalidade/atividade não se pareiam. */
export interface Objeto {
  objetivos: string[];
  finalidadesAtividades: FinalidadeAtividade[];
  tiposObjetivo: TipoObjetivo[];
  atividadeLicenciamento: AtividadeLicenciamento | null;
}

export interface CriterioPrioridade {
  codigo: number;
  descricao: string;
}

export interface Prioridade {
  codigo: 'NORMAL' | 'PRIORITARIO';
  descricao: string;
  criterios: CriterioPrioridade[];
}

export type EstadoDaTarefa = 'ATIVA' | 'SEM_TAREFA' | 'INDISPONIVEL';

export interface Responsavel {
  situacao: 'ATRIBUIDO' | 'NAO_ATRIBUIDA' | 'NAO_IDENTIFICADO';
  nome?: string | null;
}

export interface TarefaAtual {
  estado: EstadoDaTarefa;
  nome: string | null;
  desde: string | null;
  /** Só vem para quem o perfil permite (Gestor e Administrador); ausente para o Operador. */
  responsavel?: Responsavel;
  fluxo: string | null;
}

export interface Requerimento {
  id: number;
  identificacao: Identificacao;
  numeroRequerimento: string;
  numeroProcesso: string | null;
  tipoRequerimento: string | null;
  prioridade: Prioridade;
  situacao: Situacao;
  abertura: string | null;
  atualizacao: string | null;
  interessado: Interessado | null;
  tipoProcesso: string | null;
  objeto: Objeto;
  tarefaAtual: TarefaAtual;
}
