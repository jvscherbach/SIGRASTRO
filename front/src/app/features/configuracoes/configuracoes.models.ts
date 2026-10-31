/** Usuário da aba "Usuários e perfis" (GET /configuracoes/usuarios). O CPF/CNPJ só chega mascarado. */
export interface UsuarioConfigurado {
  id: string;
  nome: string;
  login: string;
  perfil: string;
  perfilRotulo: string;
  ativo: boolean;
  ultimoAcesso: string | null;
  cpfCnpj: string | null;
}

export interface NovoUsuario {
  nome: string;
  login: string;
  cpfCnpj: string;
  perfil: string;
}

export interface AlteracaoDeUsuario {
  perfil?: string;
  ativo?: boolean;
}

export const PERFIS_ASSINAVEIS: { codigo: string; rotulo: string }[] = [
  { codigo: 'OPERADOR', rotulo: 'Operador' },
  { codigo: 'OPERADOR_SIGILO', rotulo: 'Gestor' },
  { codigo: 'ADMIN', rotulo: 'Administrador' },
];

export const LOGIN_VALIDO = /^[a-z0-9._-]{3,60}$/;
