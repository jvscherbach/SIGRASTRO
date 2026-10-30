import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

/**
 * S4, bloco 6: documentos pessoais do SCORP (origem "Pessoal") na lista, no filtro, no modal e no POST /dossies, e a
 * faixa de aviso quando o SCORP falha. API simulada; dados fictícios.
 */
function doc(id: string, titulo: string, sobre: object = {}) {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id,
    idRequerimento: 101,
    titulo,
    nomeArquivo: `${titulo.toLowerCase().replace(/\s+/g, '-')}.pdf`,
    origemDescricao: 'Documento do processo',
    data: `2025-04-0${id}T10:00:00`,
    situacao: 'VIGENTE',
    sigiloso: false,
    acessivel: true,
    extensao: 'pdf',
    tipoArquivo: 'PDF',
    selecionavelPorPadrao: true,
    bloqueio: null,
    ...sobre,
  };
}

const SIGA = [doc('1', 'Requerimento'), doc('2', 'Oficio')];
const PESSOAIS = [
  doc('a1b2c3d4e5f60718', 'Procuracao', { origem: 'SCORP', origemDescricao: 'Pessoal · Representante legal', data: '2025-04-03T10:00:00' }),
  doc('0f0e0d0c0b0a0908', 'Contrato social', { origem: 'SCORP', origemDescricao: 'Pessoal · Interessado', data: '2025-04-04T10:00:00' }),
];
const SIGILOSO_PESSOAL = doc('99aa88bb77cc66dd', 'Documento sigiloso', {
  origem: 'SCORP',
  origemDescricao: 'Pessoal · Responsável técnico',
  nomeArquivo: null,
  extensao: null,
  tipoArquivo: null,
  sigiloso: true,
  acessivel: false,
  selecionavelPorPadrao: false,
  bloqueio: 'SIGILOSO',
  data: '2025-04-05T10:00:00',
});

type Scorp = 'ok' | 'fora' | 'parcial';

interface Simulacao {
  scorp: Scorp;
  listagens: number;
  corpoDoDossie: unknown;
}

function lista(scorp: Scorp, extras: object[]) {
  const itens = scorp === 'fora' ? SIGA : scorp === 'parcial' ? [...SIGA, PESSOAIS[0]] : [...SIGA, ...PESSOAIS, ...extras];
  const avisos =
    scorp === 'ok'
      ? []
      : [{ codigo: scorp === 'fora' ? 'SCORP_INDISPONIVEL' : 'SCORP_PARCIAL', mensagem: 'mensagem da API que a tela substitui' }];
  const sigilosos = itens.filter((d) => (d as { sigiloso: boolean }).sigiloso).length;
  return {
    itens,
    avisos,
    geometrias: [],
    totais: { total: itens.length, vigentes: itens.length, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 },
  };
}

async function simular(page: Page, scorp: Scorp, opcoes: { usuario?: UsuarioSimulado; extras?: object[] } = {}): Promise<Simulacao> {
  const sim: Simulacao = { scorp, listagens: 0, corpoDoDossie: null };
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: opcoes.usuario ?? GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') {
      sim.listagens += 1;
      return route.fulfill({ json: lista(sim.scorp, opcoes.extras ?? []) });
    }
    if (caminho === '/api/dossies' && metodo === 'POST') {
      sim.corpoDoDossie = route.request().postDataJSON();
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

const linhas = (page: Page) => page.locator('section[aria-label="Documentos"] tbody tr');
const faixa = (page: Page) => page.locator('.aviso-scorp');
const alturaDoQuadro = (page: Page) => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);

test.use({ viewport: { width: 1280, height: 900 } });

test('origem Pessoal com o papel na coluna, no filtro e no modal', async ({ page }) => {
  await simular(page, 'ok');
  await page.goto('/consulta/requerimentos/101');

  await expect(linhas(page)).toHaveCount(4);
  await expect(linhas(page).filter({ hasText: 'Procuracao' })).toContainText('Pessoal · Representante legal');
  await expect(faixa(page)).toHaveCount(0);

  await page.getByLabel('Origem').selectOption({ label: 'Pessoal' });
  await expect(linhas(page)).toHaveCount(2);

  await linhas(page).filter({ hasText: 'Procuracao' }).getByRole('button', { name: /Visualizar/ }).click();
  await expect(page.getByRole('dialog')).toContainText('Pessoal · Representante legal');
});

test('documento Pessoal entra na seleção total e no POST /dossies', async ({ page }) => {
  const sim = await simular(page, 'ok');
  await page.goto('/consulta/requerimentos/101');

  await expect(page.locator('.selbar b')).toHaveText('4');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect.poll(() => sim.corpoDoDossie).not.toBeNull();
  expect((sim.corpoDoDossie as { documentos: object[] }).documentos).toContainEqual({ origem: 'SCORP', id: 'a1b2c3d4e5f60718' });
});

test('SCORP fora do ar: faixa, seleção e geração liberadas; "Consultar novamente" refaz só a lista', async ({ page }) => {
  const sim = await simular(page, 'fora');
  await page.goto('/consulta/requerimentos/101');
  const alturaAntes = await alturaDoQuadro(page);

  await expect(faixa(page)).toContainText('Os documentos pessoais (SCORP) não puderam ser consultados agora');
  await expect(faixa(page)).not.toContainText('mensagem da API');
  await expect(linhas(page)).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Gerar dossiê' })).toBeEnabled();
  await expect(page.locator('.toast')).toHaveCount(0);

  sim.scorp = 'ok';
  await faixa(page).getByRole('button', { name: 'Consultar novamente' }).click();

  await expect(faixa(page)).toHaveCount(0);
  await expect(linhas(page)).toHaveCount(4);
  await expect(page.locator('.selbar b')).toHaveText('4');
  expect(sim.listagens).toBe(2);
  expect(await alturaDoQuadro(page)).toBe(alturaAntes);
});

test('SCORP parcial: texto próprio', async ({ page }) => {
  await simular(page, 'parcial');
  await page.goto('/consulta/requerimentos/101');
  await expect(faixa(page)).toContainText('Parte dos documentos pessoais (SCORP) não pôde ser consultada agora');
  await expect(linhas(page)).toHaveCount(3);
});

test('Operador: pessoal sigiloso vem mascarado e fora da seleção', async ({ page }) => {
  const sim = await simular(page, 'ok', { usuario: OPERADOR, extras: [SIGILOSO_PESSOAL] });
  await page.goto('/consulta/requerimentos/101');

  const linha = linhas(page).filter({ hasText: 'Pessoal · Responsável técnico' });
  await expect(linha).toContainText('Documento sigiloso');
  await expect(linha.locator('input[type=checkbox]')).toBeDisabled();
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect.poll(() => sim.corpoDoDossie).not.toBeNull();
  expect(JSON.stringify(sim.corpoDoDossie)).not.toContain('99aa88bb77cc66dd');
});

test('faixa cabe na largura do celular, com o botão visível', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await simular(page, 'fora');
  await page.goto('/consulta/requerimentos/101');

  const botao = faixa(page).getByRole('button', { name: 'Consultar novamente' });
  await expect(botao).toBeVisible();
  const largura = await page.evaluate(() => document.documentElement.clientWidth);
  for (const caixa of [await faixa(page).boundingBox(), await botao.boundingBox()]) {
    expect(caixa!.x + caixa!.width).toBeLessThanOrEqual(largura);
  }
});
