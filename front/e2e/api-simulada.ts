import { Page, Route } from '@playwright/test';

/** Usuário Gestor fictício devolvido por /api/auth/me. */
export const GESTOR = {
  login: 'gestor.demo',
  nome: 'Gestor Demo',
  perfil: 'OPERADOR_SIGILO',
  perfilDescricao: 'Gestor',
  cpfCnpj: null,
  permissoes: {
    verSigilosos: true,
    verAuditoriaAcessos: false,
    acessarConfiguracoes: false,
    restritoAoInteressado: false,
    verResponsavelTarefa: true,
  },
};

/** Operador fictício: só os processos em que é o interessado, sem sigilosos (RN07). */
export const OPERADOR = {
  ...GESTOR,
  login: 'operador.demo',
  nome: 'Operador Demo',
  perfil: 'OPERADOR',
  perfilDescricao: 'Operador',
  cpfCnpj: '<CPF_DE_TESTE>',
  permissoes: { ...GESTOR.permissoes, verSigilosos: false, restritoAoInteressado: true, verResponsavelTarefa: false },
};

/** Administrador fictício: acesso irrestrito. */
export const ADMIN = {
  ...GESTOR,
  login: 'admin.demo',
  nome: 'Admin Demo',
  perfil: 'ADMIN',
  perfilDescricao: 'Administrador',
  permissoes: { ...GESTOR.permissoes, verAuditoriaAcessos: true, acessarConfiguracoes: true },
};

export type UsuarioSimulado = typeof GESTOR;

const POR_LOGIN: Record<string, UsuarioSimulado> = { [OPERADOR.login]: OPERADOR, [GESTOR.login]: GESTOR, [ADMIN.login]: ADMIN };

export interface OpcoesDaSimulacao {
  /** Usuário de /api/auth/me (padrão: Gestor). */
  usuario?: UsuarioSimulado;
  /** false começa sem token, para passar pelo login (padrão: true). */
  comSessao?: boolean;
}

export type EstadoDaBusca = 'ok' | '400' | '403' | '404' | '503' | 'rede' | 'lento';

export interface ApiSimulada {
  estadoDaBusca: EstadoDaBusca;
  /** Caminhos de /api chamados, na ordem. */
  chamadas: string[];
}

/** Bem mais que duas linhas em qualquer largura: o alerta tem de cortar em duas, sem crescer. */
const MENSAGEM_LONGA = Array.from({ length: 40 }, (_, i) => `palavra${i}`).join(' ');

function problema(status: number, errorCode: string): object {
  return { status, errorCode, title: errorCode, detail: 'detalhe técnico que nunca deve aparecer', correlationId: 'c-1' };
}

/** Entra com a sessão já aberta e responde /api/** sem back-end. */
export async function simularApi(page: Page, opcoes: OpcoesDaSimulacao = {}): Promise<ApiSimulada> {
  const api: ApiSimulada = { estadoDaBusca: 'ok', chamadas: [] };
  let usuario = opcoes.usuario ?? GESTOR;
  if (opcoes.comSessao !== false) {
    await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  }

  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    api.chamadas.push(url.pathname);
    if (url.pathname === '/api/auth/login') {
      const { usuario: login } = route.request().postDataJSON() as { usuario: string };
      usuario = POR_LOGIN[login] ?? GESTOR;
      return route.fulfill({ json: { token: 'token-de-teste', tipo: 'Bearer', expiraEm: '2099-01-01T00:00:00-04:00', usuario } });
    }
    if (url.pathname === '/api/auth/logout') {
      return route.fulfill({ status: 204 });
    }
    if (url.pathname === '/api/auth/me') {
      return route.fulfill({ json: usuario });
    }
    if (url.pathname === '/api/busca') {
      switch (api.estadoDaBusca) {
        case 'rede':
          return route.abort('failed');
        case '400':
          return route.fulfill({
            status: 400,
            json: { ...problema(400, 'PARAMETRO_INVALIDO'), campos: [{ campo: 'numero', mensagem: MENSAGEM_LONGA }] },
          });
        case '403':
          return route.fulfill({ status: 403, json: problema(403, 'NAO_INTERESSADO') });
        case '404':
          return route.fulfill({ status: 404, json: problema(404, 'NAO_ENCONTRADO') });
        case '503':
          return route.fulfill({ status: 503, json: problema(503, 'SIGA_INDISPONIVEL') });
        case 'lento':
          await new Promise((resolver) => setTimeout(resolver, 1500));
          break;
        default:
          break;
      }
      return route.fulfill({
        json: { conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 },
      });
    }
    return route.fulfill({ status: 404, json: problema(404, 'NAO_ENCONTRADO') });
  });
  return api;
}
