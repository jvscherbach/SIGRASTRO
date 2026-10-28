import { PermissaoDeTela, Permissoes } from '../core/usuario.model';

export interface ItemMenu {
  rotulo: string;
  icone: string;
  rota?: string;
  /** Permissão exigida para ver o item (RN08). Sem ela, todos veem. */
  permissao?: PermissaoDeTela;
  filhos?: ItemMenu[];
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
      {
        rotulo: 'Auditoria',
        icone: 'shield',
        filhos: [
          { rotulo: 'Documentos', icone: 'file', rota: '/auditoria/documentos' },
          { rotulo: 'Acessos', icone: 'user', rota: '/auditoria/acessos', permissao: 'verAuditoriaAcessos' },
        ],
      },
      { rotulo: 'Configurações', icone: 'settings', rota: '/configuracoes', permissao: 'acessarConfiguracoes' },
    ],
  },
];

/** Menu das permissões do usuário: o que ele não usa não é renderizado (RN08), e grupos vazios somem. */
export function menuDasPermissoes(permissoes: Permissoes | undefined): SecaoMenu[] {
  const visivel = (item: ItemMenu) => !item.permissao || permissoes?.[item.permissao] === true;
  return MENU.map((secao) => ({
    ...secao,
    itens: secao.itens
      .filter(visivel)
      .map((item) => (item.filhos ? { ...item, filhos: item.filhos.filter(visivel) } : item))
      .filter((item) => !item.filhos || item.filhos.length > 0),
  })).filter((secao) => secao.itens.length > 0);
}
