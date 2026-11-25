import { PermissaoDeTela, Permissoes } from '../core/usuario.model';

export interface ItemMenu {
  rotulo: string;
  icone: string;
  rota: string;
  /** Prefixo de URL em que o item aparece como ativo (ex.: as abas de Auditoria). Sem ele, vale a própria rota. */
  prefixoAtivo?: string;
  /** Permissão exigida para ver o item (RN08). Sem ela, todos veem. */
  permissao?: PermissaoDeTela;
}

export interface SecaoMenu {
  rotulo: string;
  itens: ItemMenu[];
}

export const MENU: SecaoMenu[] = [
  {
    rotulo: 'Área de trabalho',
    itens: [
      { rotulo: 'Consulta', icone: 'search', rota: '/consulta' },
      { rotulo: 'Dossiês gerados', icone: 'layers', rota: '/dossies' },
    ],
  },
  {
    rotulo: 'Gestão',
    itens: [
      { rotulo: 'Painel', icone: 'bars', rota: '/painel', permissao: 'verPainel' },
      // Item único: "Documentos" e "Acessos" viram abas dentro da tela (R2, bloco 12).
      { rotulo: 'Auditoria', icone: 'shield', rota: '/auditoria/documentos', prefixoAtivo: '/auditoria' },
      { rotulo: 'Configurações', icone: 'settings', rota: '/configuracoes', permissao: 'acessarConfiguracoes' },
    ],
  },
];

/** Menu das permissões do usuário: o que ele não usa não é renderizado (RN08), e seções vazias somem. */
export function menuDasPermissoes(permissoes: Permissoes | undefined): SecaoMenu[] {
  const visivel = (item: ItemMenu) => !item.permissao || permissoes?.[item.permissao] === true;
  return MENU.map((secao) => ({ ...secao, itens: secao.itens.filter(visivel) })).filter((secao) => secao.itens.length > 0);
}

/** O item está ativo quando a URL atual é a rota (ou o prefixo) dele, sem contar parâmetros e âncora. */
export function itemAtivo(item: ItemMenu, url: string): boolean {
  const caminho = url.split(/[?#]/)[0];
  const base = item.prefixoAtivo ?? item.rota;
  return caminho === base || caminho.startsWith(base + '/');
}
