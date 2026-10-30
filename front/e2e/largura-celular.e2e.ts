import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * Sem rolagem horizontal da página no celular. A tabela larga rola dentro da própria moldura (.table-wrap); nada pode
 * escapar dela e alargar a página (antes, o rótulo oculto "Ações", .sr-only, escapava e gerava 30 px de rolagem).
 */
function doc(id: string, titulo: string) {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id,
    idRequerimento: 101,
    titulo,
    nomeArquivo: `${titulo.toLowerCase().replace(/\s+/g, '-')}-com-nome-de-arquivo-bem-comprido.pdf`,
    origemDescricao: 'Documento do processo',
    data: `2025-04-0${id}T10:00:00`,
    situacao: 'VIGENTE',
    sigiloso: false,
    acessivel: true,
    extensao: 'pdf',
    tipoArquivo: 'PDF',
    selecionavelPorPadrao: true,
    bloqueio: null,
  };
}

const TOTAIS = { vigentes: 0, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 };

const CENARIOS = [
  { nome: 'sem documentos', lista: { itens: [], avisos: [], geometrias: [], totais: { ...TOTAIS, total: 0 } } },
  {
    nome: 'com documentos, aviso do SCORP e geometrias',
    lista: {
      itens: [doc('1', 'Requerimento assinado'), doc('2', 'Oficio de resposta')],
      avisos: [{ codigo: 'SCORP_INDISPONIVEL', mensagem: 'x' }],
      geometrias: [
        {
          origem: 'DOCUMENTO_PROCESSO',
          id: '900',
          idRequerimento: 101,
          titulo: 'Polígono da área',
          nomeArquivo: 'poligono-da-area-de-preservacao-permanente.kml',
          origemDescricao: 'Documento do processo',
          data: '2025-04-02T09:00:00',
          situacao: 'VIGENTE',
          sigiloso: false,
          extensao: 'kml',
          tipoArquivo: 'GEOMETRIA',
          tamanho: null,
        },
      ],
      totais: { ...TOTAIS, total: 2, vigentes: 2, geometrias: 1 },
    },
  },
];

async function simular(page: Page, lista: object): Promise<void> {
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: lista });
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

test.use({ viewport: { width: 390, height: 844 } });

for (const cenario of CENARIOS) {
  test(`detalhe no celular (${cenario.nome}): a página não rola na horizontal`, async ({ page }) => {
    await simular(page, cenario.lista);
    await page.goto('/consulta/requerimentos/101');
    await page.locator('section[aria-label="Documentos"] .table-wrap').waitFor();

    const medida = await page.evaluate(() => ({
      pagina: document.documentElement.scrollWidth,
      tela: document.documentElement.clientWidth,
    }));
    expect(medida.pagina).toBe(medida.tela);
  });
}

test('no celular, a tabela larga rola dentro da própria moldura', async ({ page }) => {
  await simular(page, CENARIOS[1].lista);
  await page.goto('/consulta/requerimentos/101');
  const moldura = page.locator('section[aria-label="Documentos"] .table-wrap');
  await moldura.waitFor();

  const { conteudo, visivel } = await moldura.evaluate((e) => ({ conteudo: e.scrollWidth, visivel: e.clientWidth }));
  expect(conteudo).toBeGreaterThan(visivel);
});
