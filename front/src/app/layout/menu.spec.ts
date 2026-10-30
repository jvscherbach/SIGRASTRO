import { Permissoes } from '../core/usuario.model';
import { menuDasPermissoes } from './menu';

/** Permissões como o back-end devolve em /auth/me para cada perfil (docs/12). */
const PERMISSOES: Record<'OPERADOR' | 'GESTOR' | 'ADMIN', Permissoes> = {
  OPERADOR: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: true, verResponsavelTarefa: false },
  GESTOR: { verSigilosos: true, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: false, verResponsavelTarefa: true },
  ADMIN: { verSigilosos: true, verAuditoriaAcessos: true, acessarConfiguracoes: true, restritoAoInteressado: false, verResponsavelTarefa: true },
};

function rotas(permissoes: Permissoes | undefined): string[] {
  return menuDasPermissoes(permissoes)
    .flatMap((secao) => secao.itens)
    .flatMap((item) => (item.filhos ? item.filhos : [item]))
    .map((item) => item.rota ?? '');
}

describe('menuDasPermissoes (RN08)', () => {
  it('Operador não recebe Acessos nem Configurações', () => {
    expect(rotas(PERMISSOES.OPERADOR)).toEqual(['/consulta', '/dossies', '/auditoria/documentos']);
  });

  it('Gestor não recebe Acessos nem Configurações', () => {
    expect(rotas(PERMISSOES.GESTOR)).toEqual(['/consulta', '/dossies', '/auditoria/documentos']);
  });

  it('Administrador vê tudo', () => {
    expect(rotas(PERMISSOES.ADMIN)).toEqual([
      '/consulta',
      '/dossies',
      '/auditoria/documentos',
      '/auditoria/acessos',
      '/configuracoes',
    ]);
  });

  it('cada item restrito segue a sua permissão, não o nome do perfil', () => {
    expect(rotas({ ...PERMISSOES.GESTOR, acessarConfiguracoes: true })).toContain('/configuracoes');
    expect(rotas({ ...PERMISSOES.GESTOR, acessarConfiguracoes: true })).not.toContain('/auditoria/acessos');
  });

  it('sem permissões, nada restrito aparece', () => {
    expect(rotas(undefined)).toEqual(['/consulta', '/dossies', '/auditoria/documentos']);
  });

  it('item restrito não leva cadeado: simplesmente não existe no menu', () => {
    const rotulos = menuDasPermissoes(PERMISSOES.OPERADOR)
      .flatMap((secao) => secao.itens)
      .flatMap((item) => [item.rotulo, ...(item.filhos ?? []).map((filho) => filho.rotulo)]);
    expect(rotulos).not.toContain('Acessos');
    expect(rotulos).not.toContain('Configurações');
  });
});
