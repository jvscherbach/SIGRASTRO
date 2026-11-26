import { Usuario } from './usuario.model';

/**
 * Tela em que o usuário cai depois do login e quando uma rota o recusa: o Analista vai para a caixa (S11); os demais
 * perfis, para a consulta.
 */
export function destinoInicial(usuario: Pick<Usuario, 'permissoes'> | null | undefined): string {
  return usuario?.permissoes?.verCaixa === true ? '/caixa' : '/consulta';
}
