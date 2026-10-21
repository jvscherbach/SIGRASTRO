import { Perfil } from '../core/usuario.model';
import { menuDoPerfil } from './menu';

function rotas(perfil: Perfil | undefined): string[] {
  return menuDoPerfil(perfil)
    .flatMap((secao) => secao.itens)
    .flatMap((item) => (item.filhos ? item.filhos : [item]))
    .map((item) => item.rota ?? '');
}

describe('menuDoPerfil (RN08)', () => {
  it('Operador e Gestor não recebem Acessos nem Configurações', () => {
    for (const perfil of ['OPERADOR', 'OPERADOR_SIGILO'] as Perfil[]) {
      expect(rotas(perfil)).toEqual(['/consulta', '/dossies', '/auditoria/documentos']);
    }
  });

  it('Administrador vê tudo', () => {
    expect(rotas('ADMIN')).toEqual([
      '/consulta',
      '/dossies',
      '/auditoria/documentos',
      '/auditoria/acessos',
      '/configuracoes',
    ]);
  });

  it('sem perfil, nada restrito aparece', () => {
    expect(rotas(undefined)).not.toContain('/configuracoes');
  });
});
