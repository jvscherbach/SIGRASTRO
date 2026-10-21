import { Perfil } from '../core/usuario.model';

export interface ItemMenu {
  rotulo: string;
  icone: string;
  rota?: string;
  /** Perfis que veem o item (RN08). Sem a lista, todos veem. */
  perfis?: Perfil[];
  filhos?: ItemMenu[];
}

export interface SecaoMenu {
  rotulo: string;
  itens: ItemMenu[];
}

export const APENAS_ADMIN: Perfil[] = ['ADMIN'];

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
          { rotulo: 'Acessos', icone: 'user', rota: '/auditoria/acessos', perfis: APENAS_ADMIN },
        ],
      },
      { rotulo: 'Configurações', icone: 'settings', rota: '/configuracoes', perfis: APENAS_ADMIN },
    ],
  },
];

/** Menu do perfil: o que ele não usa não é renderizado (RN08), e grupos vazios somem. */
export function menuDoPerfil(perfil: Perfil | undefined): SecaoMenu[] {
  const visivel = (item: ItemMenu) => !item.perfis || (perfil !== undefined && item.perfis.includes(perfil));
  return MENU.map((secao) => ({
    ...secao,
    itens: secao.itens
      .filter(visivel)
      .map((item) => (item.filhos ? { ...item, filhos: item.filhos.filter(visivel) } : item))
      .filter((item) => !item.filhos || item.filhos.length > 0),
  })).filter((secao) => secao.itens.length > 0);
}
