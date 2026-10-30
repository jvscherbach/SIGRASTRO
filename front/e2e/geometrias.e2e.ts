import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

/**
 * S4, bloco 4: seção Geometrias no detalhe, com "Baixar geometrias" (ZIP por Blob, token no cabeçalho), erros por
 * errorCode e o aviso de sigilosas omitidas para o Operador. API simulada; arquivos e dados fictícios.
 */
const ZIP_MINIMO = Buffer.from('504b0506000000000000000000000000000000000000', 'hex');

function doc(id: string, titulo: string) {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id,
    idRequerimento: 101,
    titulo,
    nomeArquivo: `${titulo.toLowerCase()}.pdf`,
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

function geo(id: string, titulo: string, arquivo: string, sobre: object = {}) {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id,
    idRequerimento: 101,
    titulo,
    nomeArquivo: arquivo,
    origemDescricao: 'Documento do processo',
    data: '2025-04-02T09:00:00',
    situacao: 'VIGENTE',
    sigiloso: false,
    extensao: arquivo.split('.').pop(),
    tipoArquivo: 'GEOMETRIA',
    tamanho: null,
    ...sobre,
  };
}

const ITENS = [doc('1', 'Requerimento'), doc('2', 'Oficio')];
const TOTAIS = { total: 2, vigentes: 2, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0 };

type Download = 'ok' | 'incompleto' | 'grande';

interface Simulacao {
  download: Download;
  pedidosDeZip: { authorization: string | undefined; url: string }[];
  corpoDoDossie: unknown;
}

async function simular(page: Page, geometrias: object[], opcoes: { omitidas?: number; usuario?: UsuarioSimulado } = {}): Promise<Simulacao> {
  const sim: Simulacao = { download: 'ok', pedidosDeZip: [], corpoDoDossie: null };
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: opcoes.usuario ?? GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') {
      return route.fulfill({
        json: { itens: ITENS, totais: { ...TOTAIS, geometrias: geometrias.length, geometriasOmitidas: opcoes.omitidas ?? 0 }, geometrias },
      });
    }
    if (caminho === '/api/requerimentos/101/geometrias/arquivo') {
      sim.pedidosDeZip.push({ authorization: route.request().headers()['authorization'], url: route.request().url() });
      if (sim.download === 'incompleto') {
        return route.fulfill({
          status: 502,
          contentType: 'application/problem+json',
          json: {
            status: 502,
            errorCode: 'GEOMETRIAS_INCOMPLETAS',
            detail: 'detalhe técnico que nunca deve aparecer',
            arquivos: [{ nome: 'reserva.shp', motivo: 'arquivo não encontrado no repositório' }],
          },
        });
      }
      if (sim.download === 'grande') {
        return route.fulfill({ status: 413, contentType: 'application/problem+json', json: { status: 413, errorCode: 'GEOMETRIAS_GRANDES_DEMAIS' } });
      }
      return route.fulfill({
        status: 200,
        body: ZIP_MINIMO,
        headers: {
          'Content-Type': 'application/zip',
          'Content-Disposition': `attachment; filename*=UTF-8''geometrias-processo-2825-2025.zip`,
          'Cache-Control': 'no-store',
        },
      });
    }
    if (caminho === '/api/dossies' && metodo === 'POST') {
      sim.corpoDoDossie = route.request().postDataJSON();
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

const secao = (page: Page) => page.locator('app-geometrias-secao');
const tabelaDeDocumentos = (page: Page) => page.locator('section[aria-label="Documentos"] tbody tr');
const alturaDoQuadro = (page: Page) => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);

test.use({ viewport: { width: 1280, height: 900 } });

test('sem geometrias, a seção não aparece', async ({ page }) => {
  await simular(page, []);
  await page.goto('/consulta/requerimentos/101');
  await expect(tabelaDeDocumentos(page)).toHaveCount(2);
  await expect(secao(page)).toHaveCount(0);
});

test('lista as geometrias à parte e baixa o ZIP por Blob com o token no cabeçalho', async ({ page }) => {
  const sim = await simular(page, [geo('900', 'Polígono da área', 'area.kml'), geo('901', 'Reserva legal', 'reserva.shp')]);
  await page.goto('/consulta/requerimentos/101');

  await expect(secao(page).locator('tbody tr')).toHaveCount(2);
  await expect(secao(page)).toContainText('area.kml');
  await expect(tabelaDeDocumentos(page)).toHaveCount(2);
  await expect(page.locator('section[aria-label="Documentos"]')).not.toContainText('Polígono da área');
  const alturaAntes = await alturaDoQuadro(page);

  const download = page.waitForEvent('download');
  await secao(page).getByRole('button', { name: 'Baixar geometrias' }).click();
  expect((await download).suggestedFilename()).toBe('geometrias-processo-2825-2025.zip');
  await expect(secao(page)).toContainText('Download iniciado: geometrias-processo-2825-2025.zip');

  expect(sim.pedidosDeZip).toHaveLength(1);
  expect(sim.pedidosDeZip[0].authorization).toBe('Bearer token-de-teste');
  expect(sim.pedidosDeZip[0].url).not.toContain('token-de-teste');
  expect(await alturaDoQuadro(page)).toBe(alturaAntes);
});

test('GEOMETRIAS_INCOMPLETAS lista os arquivos com problema e "Tentar novamente" baixa', async ({ page }) => {
  const sim = await simular(page, [geo('900', 'Polígono da área', 'area.kml')]);
  sim.download = 'incompleto';
  await page.goto('/consulta/requerimentos/101');
  const alturaAntes = await alturaDoQuadro(page);

  await secao(page).getByRole('button', { name: 'Baixar geometrias' }).click();
  const erro = secao(page).locator('.geometrias-erro');
  await expect(erro).toContainText('Nenhum arquivo foi baixado');
  await expect(erro).toContainText('reserva.shp: arquivo não encontrado no repositório');
  await expect(erro).not.toContainText('detalhe técnico');
  await expect(page.locator('.toast')).toHaveCount(0);

  sim.download = 'ok';
  const download = page.waitForEvent('download');
  await secao(page).getByRole('button', { name: 'Tentar novamente' }).click();
  await download;
  await expect(erro).toHaveCount(0);
  expect(sim.pedidosDeZip).toHaveLength(2);
  expect(await alturaDoQuadro(page)).toBe(alturaAntes);
});

test('413 mostra texto próprio', async ({ page }) => {
  const sim = await simular(page, [geo('900', 'Polígono da área', 'area.kml')]);
  sim.download = 'grande';
  await page.goto('/consulta/requerimentos/101');
  await secao(page).getByRole('button', { name: 'Baixar geometrias' }).click();
  await expect(secao(page).locator('.geometrias-erro')).toContainText('passam do limite de tamanho ou de quantidade');
});

test('Operador: sigilosas omitidas aparecem só como contagem', async ({ page }) => {
  await simular(page, [geo('900', 'Polígono da área', 'area.kml')], { omitidas: 2, usuario: OPERADOR });
  await page.goto('/consulta/requerimentos/101');

  await expect(secao(page).locator('.geometrias-omitidas')).toHaveText(/2 geometria\(s\) sigilosa\(s\) não são exibidas para o seu perfil\./);
  await expect(secao(page).locator('tbody tr')).toHaveCount(1);
  // o aviso das geometrias não vira documento indisponível
  await expect(page.locator('section[aria-label="Documentos"] .dash-sec-head p')).toContainText('0 indisponível(is)');
});

test('geometrias ficam fora da seleção e do POST /dossies', async ({ page }) => {
  const sim = await simular(page, [geo('900', 'Polígono da área', 'area.kml')]);
  await page.goto('/consulta/requerimentos/101');

  await expect(page.locator('.selbar b')).toHaveText('2');
  await expect(secao(page).locator('input[type=checkbox]')).toHaveCount(0);
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect.poll(() => sim.corpoDoDossie).not.toBeNull();
  expect(sim.corpoDoDossie).toEqual({
    idRequerimento: 101,
    documentos: [
      { origem: 'DOCUMENTO_PROCESSO', id: '1' },
      { origem: 'DOCUMENTO_PROCESSO', id: '2' },
    ],
  });
});
