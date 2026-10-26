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

export type EstadoDaBusca = 'ok' | '400' | '403' | '404' | '503' | 'rede' | 'lento';

export interface ApiSimulada {
  estadoDaBusca: EstadoDaBusca;
}

/** Bem mais que duas linhas em qualquer largura: o alerta tem de cortar em duas, sem crescer. */
const MENSAGEM_LONGA = Array.from({ length: 40 }, (_, i) => `palavra${i}`).join(' ');

function problema(status: number, errorCode: string): object {
  return { status, errorCode, title: errorCode, detail: 'detalhe técnico que nunca deve aparecer', correlationId: 'c-1' };
}

/** Entra com a sessão já aberta e responde /api/** sem back-end. */
export async function simularApi(page: Page): Promise<ApiSimulada> {
  const api: ApiSimulada = { estadoDaBusca: 'ok' };
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));

  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/me') {
      return route.fulfill({ json: GESTOR });
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
