import { Permissoes } from '../core/usuario.model';
import { MENU, itemAtivo, menuDasPermissoes } from './menu';

/** Permissões como o back-end devolve em /auth/me para cada perfil (docs/12). */
const PERMISSOES: Record<'OPERADOR' | 'GESTOR' | 'ANALISTA' | 'ADMIN', Permissoes> = {
  ANALISTA: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: false, verResponsavelTarefa: false, verPainel: false, verCaixa: true },
  OPERADOR: { verSigilosos: false, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: true, verResponsavelTarefa: false, verPainel: false, verCaixa: false },
  GESTOR: { verSigilosos: true, verAuditoriaAcessos: false, acessarConfiguracoes: false, restritoAoInteressado: false, verResponsavelTarefa: true, verPainel: true, verCaixa: false },
  ADMIN: { verSigilosos: true, verAuditoriaAcessos: true, acessarConfiguracoes: true, restritoAoInteressado: false, verResponsavelTarefa: true, verPainel: true, verCaixa: false },
};

function rotas(permissoes: Permissoes | undefined): string[] {
  return menuDasPermissoes(permissoes)
    .flatMap((secao) => secao.itens)
    .map((item) => item.rota);
}

describe('menuDasPermissoes (RN08)', () => {
  it('Operador não recebe Configurações', () => {
    expect(rotas(PERMISSOES.OPERADOR)).toEqual(['/consulta', '/dossies', '/auditoria/documentos', '/conta']);
  });

  it('Gestor recebe o Painel e não recebe Configurações', () => {
    expect(rotas(PERMISSOES.GESTOR)).toEqual(['/consulta', '/dossies', '/painel', '/auditoria/documentos', '/conta']);
  });

  it('Analista recebe "Minha caixa" (só ele) e não recebe Painel nem Configurações', () => {
    expect(rotas(PERMISSOES.ANALISTA)).toEqual(['/caixa', '/consulta', '/dossies', '/conta']);
    for (const outro of [PERMISSOES.OPERADOR, PERMISSOES.GESTOR, PERMISSOES.ADMIN]) {
      expect(rotas(outro)).not.toContain('/caixa');
    }
  });

  it('Administrador vê tudo, com Auditoria como item único', () => {
    expect(rotas(PERMISSOES.ADMIN)).toEqual(['/consulta', '/dossies', '/painel', '/auditoria/documentos', '/configuracoes', '/conta']);
  });

  it('Auditoria é um item só, para todos os perfis', () => {
    for (const permissoes of [PERMISSOES.OPERADOR, PERMISSOES.GESTOR, PERMISSOES.ADMIN]) {
      const rotulos = menuDasPermissoes(permissoes)
        .flatMap((secao) => secao.itens)
        .map((item) => item.rotulo);
      expect(rotulos.filter((rotulo) => rotulo === 'Auditoria')).toHaveLength(1);
      expect(rotulos).not.toContain('Documentos');
      expect(rotulos).not.toContain('Acessos');
    }
  });

  it('cada item restrito segue a sua permissão, não o nome do perfil', () => {
    expect(rotas({ ...PERMISSOES.GESTOR, acessarConfiguracoes: true })).toContain('/configuracoes');
    expect(rotas({ ...PERMISSOES.ADMIN, acessarConfiguracoes: false })).not.toContain('/configuracoes');
    expect(rotas({ ...PERMISSOES.OPERADOR, verPainel: true, verCaixa: false })).toContain('/painel');
    expect(rotas({ ...PERMISSOES.GESTOR, verPainel: false, verCaixa: false })).not.toContain('/painel');
  });

  it('sem permissões, nada restrito aparece', () => {
    expect(rotas(undefined)).toEqual(['/consulta', '/dossies', '/auditoria/documentos', '/conta']);
  });

  it('item restrito não leva cadeado: simplesmente não existe no menu', () => {
    const rotulos = menuDasPermissoes(PERMISSOES.OPERADOR)
      .flatMap((secao) => secao.itens)
      .map((item) => item.rotulo);
    expect(rotulos).not.toContain('Configurações');
  });
});

describe('itemAtivo', () => {
  const auditoria = MENU.flatMap((s) => s.itens).find((i) => i.rotulo === 'Auditoria')!;
  const consulta = MENU.flatMap((s) => s.itens).find((i) => i.rotulo === 'Consulta')!;

  it('Auditoria fica ativa nas duas abas', () => {
    expect(itemAtivo(auditoria, '/auditoria/documentos')).toBe(true);
    expect(itemAtivo(auditoria, '/auditoria/acessos?periodo=7d')).toBe(true);
    expect(itemAtivo(auditoria, '/consulta')).toBe(false);
  });

  it('o item vale para a rota e para as filhas, sem confundir prefixo parecido', () => {
    expect(itemAtivo(consulta, '/consulta')).toBe(true);
    expect(itemAtivo(consulta, '/consulta/123')).toBe(true);
    expect(itemAtivo(consulta, '/consultas')).toBe(false);
  });
});
