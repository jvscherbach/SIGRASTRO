import { PageResponse } from '../../shared/api-tipos';

export type GrupoDeAuditoria = 'documentos' | 'acessos';
export type PeriodoDeAuditoria = 'hoje' | '7d' | '30d' | 'todo';

export const PERIODOS: { codigo: PeriodoDeAuditoria; rotulo: string }[] = [
  { codigo: 'hoje', rotulo: 'Hoje' },
  { codigo: '7d', rotulo: 'Últimos 7 dias' },
  { codigo: '30d', rotulo: 'Últimos 30 dias' },
  { codigo: 'todo', rotulo: 'Todo o período' },
];

/** Tipos de evento por grupo, com o texto de docs/11 (o servidor devolve o mesmo `rotulo` em cada evento). */
export const TIPOS_DO_GRUPO: Record<GrupoDeAuditoria, { codigo: string; rotulo: string }[]> = {
  documentos: [
    { codigo: 'CONSULTA', rotulo: 'Consulta' },
    { codigo: 'VISUALIZACAO', rotulo: 'Visualização' },
    { codigo: 'DOSSIE', rotulo: 'Dossiê gerado' },
    { codigo: 'FALHA', rotulo: 'Falha na geração' },
    { codigo: 'DOWNLOAD', rotulo: 'Download' },
    { codigo: 'GEOMETRIAS', rotulo: 'Geometrias baixadas' },
    { codigo: 'TRAMITACAO', rotulo: 'Posição na tramitação' },
  ],
  acessos: [
    { codigo: 'LOGIN', rotulo: 'Acesso' },
    { codigo: 'PERFIL', rotulo: 'Troca de perfil' },
    { codigo: 'USUARIO', rotulo: 'Usuário alterado' },
    { codigo: 'CONFIG', rotulo: 'Configuração alterada' },
    { codigo: 'CONEXAO', rotulo: 'Teste de conexão' },
  ],
};

export const ROTULO_DO_PERFIL: Record<string, string | undefined> = {
  OPERADOR: 'Operador',
  OPERADOR_SIGILO: 'Gestor',
  ADMIN: 'Administrador',
};

export interface FiltroDeAuditoria {
  periodo: PeriodoDeAuditoria;
  texto: string;
  tipo: string;
  usuario: string;
  somenteSigilosos: boolean;
  pagina: number;
}

export interface UsuarioDoEvento {
  login: string;
  nome: string;
  perfil: string;
}

export interface EventoDeAuditoria {
  id: number;
  dataHora: string;
  tipo: string;
  rotulo: string;
  usuario: UsuarioDoEvento;
  idRequerimento: number | null;
  numeroProcesso: string | null;
  numeroRequerimento: string | null;
  sigiloso: boolean;
  arquivo: string | null;
  idPedido: string | null;
  detalhe: string | null;
}

export interface DocumentoDoEvento {
  origem: string;
  id: string;
  titulo: string;
  nomeDoArquivo: string | null;
  sigiloso: boolean;
  ordem: number | null;
}

export interface AlteracaoDoEvento {
  item: string;
  valorAnterior: string | null;
  valorNovo: string | null;
}

export interface EventoDetalhado extends EventoDeAuditoria {
  origem: string | null;
  correlationId: string | null;
  documentos: DocumentoDoEvento[];
  alteracoes: AlteracaoDoEvento[];
}

/** Só os indicadores do grupo vêm preenchidos. */
export interface IndicadoresDoPeriodo {
  dossiesGerados?: number;
  eventosComSigilosos?: number;
  entradas?: number;
  alteracoes?: number;
  usuarios: number;
}

export type EventosResposta = PageResponse<EventoDeAuditoria> & { indicadores: IndicadoresDoPeriodo };

/** CSV recebido: o Blob, o nome do Content-Disposition e se o servidor cortou nas linhas máximas. */
export interface CsvRecebido {
  blob: Blob;
  nome: string | null;
  truncado: boolean;
}
