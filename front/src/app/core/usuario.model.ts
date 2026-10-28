export type Perfil = 'OPERADOR' | 'OPERADOR_SIGILO' | 'ADMIN';

export interface Permissoes {
  verSigilosos: boolean;
  verAuditoriaAcessos: boolean;
  acessarConfiguracoes: boolean;
  restritoAoInteressado: boolean;
  verResponsavelTarefa: boolean;
}

/** Permissão de /auth/me que libera uma tela e o item de menu dela (RN08). */
export type PermissaoDeTela = keyof Pick<Permissoes, 'verAuditoriaAcessos' | 'acessarConfiguracoes'>;

export interface Usuario {
  login: string;
  nome: string;
  perfil: Perfil;
  perfilDescricao: string;
  cpfCnpj: string | null;
  permissoes: Permissoes;
}

export interface LoginResposta {
  token: string;
  tipo: string;
  expiraEm: string;
  usuario: Usuario;
}

export interface CampoInvalido {
  campo: string;
  mensagem: string;
}

/** Corpo de erro da API (ProblemDetail com os campos extras do projeto). */
export interface ProblemDetail {
  status: number;
  title?: string;
  detail?: string;
  errorCode?: string;
  timestamp?: string;
  path?: string;
  correlationId?: string;
  campos?: CampoInvalido[];
}
