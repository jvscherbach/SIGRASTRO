import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * S5, bloco 1: a geração do dossiê continua em segundo plano ao fechar a gaveta. API simulada, sem back-end: o
 * andamento só avança com as consultas, e o POST pode ser atrasado para fechar a gaveta durante o envio.
 */
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

const DOCUMENTOS = {
  itens: [doc('1', 'Requerimento'), doc('2', 'Oficio')],
  totais: { total: 2, vigentes: 2, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0 },
};

const ITENS = [
  { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento' },
  { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio' },
];

interface Controle {
  postsConcluidos: number;
  deletes: number;
  consultas: number;
  /** Consulta (1-based) em que o pedido deixa de estar em andamento. */
  terminaNaConsulta: number;
  statusFinal: 'CONCLUIDO' | 'PENDENTE_DECISAO';
  atrasoDoPostMs: number;
}

async function simular(page: Page, sobre: Partial<Controle> = {}): Promise<Controle> {
  const c: Controle = { postsConcluidos: 0, deletes: 0, consultas: 0, terminaNaConsulta: 3, statusFinal: 'CONCLUIDO', atrasoDoPostMs: 0, ...sobre };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/auth/logout') return route.fulfill({ status: 204 });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho === '/api/dossies' && metodo === 'POST') {
      await new Promise((resolver) => setTimeout(resolver, c.atrasoDoPostMs));
      c.postsConcluidos += 1;
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'DELETE') {
      c.deletes += 1;
      return route.fulfill({ status: 204 });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      c.consultas += 1;
      const fim = c.consultas >= c.terminaNaConsulta;
      const falha = fim && c.statusFinal === 'PENDENTE_DECISAO';
      return route.fulfill({
        json: {
          id: 'pedido-1',
          status: fim ? c.statusFinal : 'OBTENDO',
          total: 2,
          processados: fim ? 2 : 1,
          erro: null,
          falhas: falha ? [{ origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio', motivo: 'tempo limite excedido' }] : [],
          paginas: fim && !falha ? 4 : null,
          expiraEm: '2099-01-01T00:00:00',
          itens: ITENS.map((i, n) => ({ ...i, situacao: n === 1 && falha ? 'FALHA' : fim || n === 0 ? 'OK' : 'OBTENDO' })),
        },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

const alturaDoQuadro = (page: Page) => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);

test('clicar fora da gaveta durante a geração minimiza: o indicador avança, avisa ao concluir e reabre', async ({ page }) => {
  const c = await simular(page);
  await page.goto('/consulta/requerimentos/101');
  await expect(page.locator('tbody tr')).toHaveCount(2);
  const alturaSem = await alturaDoQuadro(page);

  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog', { name: /Gerando dossiê/ })).toBeVisible();
  await page.locator('.scrim').click({ position: { x: 5, y: 5 } });
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // indicador na barra do topo, e o quadro do topo não muda de altura
  const indicador = page.locator('.topbar .ind-dossie');
  await expect(indicador).toContainText('Gerando dossiê');
  expect(await alturaDoQuadro(page)).toBe(alturaSem);

  // navega por outra tela: o indicador acompanha
  await page.locator('.sidebar').getByRole('link', { name: /Consulta/ }).first().click();
  await expect(page).toHaveURL(/\/consulta$/);
  await expect(indicador).toBeVisible();

  // conclui com a gaveta fechada: aviso com "Abrir" e indicador "Dossiê pronto"
  await expect(page.locator('.toast', { hasText: 'Dossiê pronto para baixar' })).toBeVisible({ timeout: 15_000 });
  await expect(indicador).toContainText('Dossiê pronto');
  expect(c.deletes).toBe(0);

  await page.locator('.toast').getByRole('button', { name: 'Abrir' }).click();
  await expect(page.getByRole('dialog', { name: /Dossiê pronto/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Baixar PDF' })).toBeVisible();
});

test('Esc minimiza e o indicador reabre a gaveta no mesmo pedido', async ({ page }) => {
  const c = await simular(page, { terminaNaConsulta: 99 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();

  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.locator('.topbar .ind-abrir').click();

  await expect(page.getByRole('dialog', { name: /Gerando dossiê/ })).toBeVisible();
  expect(c.postsConcluidos).toBe(1);
  expect(c.deletes).toBe(0);
});

test('fechar durante o envio do POST: o pedido é criado e acompanhado', async ({ page }) => {
  const c = await simular(page, { atrasoDoPostMs: 1200 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByText('Enviando pedido…')).toBeVisible();

  await page.locator('.scrim').click({ position: { x: 5, y: 5 } });

  await expect(page.locator('.topbar .ind-dossie')).toBeVisible();
  await expect.poll(() => c.postsConcluidos).toBe(1);
  await expect.poll(() => c.consultas, { timeout: 10_000 }).toBeGreaterThan(0);
  expect(c.deletes).toBe(0);
});

test('parada à espera de decisão com a gaveta fechada: aviso e indicador em destaque', async ({ page }) => {
  await simular(page, { statusFinal: 'PENDENTE_DECISAO', terminaNaConsulta: 2 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');

  await expect(page.locator('.toast', { hasText: 'precisa da sua decisão' })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.topbar .ind-dossie[data-tipo="decisao"]')).toBeVisible();
  await page.locator('.toast').getByRole('button', { name: 'Abrir' }).click();
  await expect(page.getByRole('dialog', { name: /Documentos com problema/ })).toBeVisible();
});

test('"Cancelar geração" continua cancelando (DELETE)', async ({ page }) => {
  const c = await simular(page, { terminaNaConsulta: 99 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('button', { name: 'Cancelar geração' })).toBeEnabled();

  await page.getByRole('button', { name: 'Cancelar geração' }).click();

  await expect.poll(() => c.deletes).toBe(1);
});

test('segundo "Gerar dossiê" com pedido ativo avisa e oferece abrir a gaveta atual', async ({ page }) => {
  const c = await simular(page, { terminaNaConsulta: 99 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('button', { name: 'Gerar dossiê' }).click();

  await expect(page.locator('.toast', { hasText: 'Já existe uma geração em andamento' })).toBeVisible();
  await page.locator('.toast').getByRole('button', { name: 'Abrir' }).click();
  await expect(page.getByRole('dialog', { name: /Gerando dossiê/ })).toBeVisible();
  expect(c.postsConcluidos).toBe(1);
});

test('sair da aplicação encerra o acompanhamento: o indicador some e nenhuma consulta nova sai', async ({ page }) => {
  const c = await simular(page, { terminaNaConsulta: 99 });
  await page.goto('/consulta/requerimentos/101');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('.topbar .ind-dossie')).toBeVisible();

  await page.locator('.user-btn').click();
  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page).toHaveURL(/\/login/);
  const consultas = c.consultas;
  await page.waitForTimeout(3000);

  expect(c.consultas).toBe(consultas);
});

test.describe('celular', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('com o indicador na barra não há rolagem horizontal', async ({ page }) => {
    await simular(page, { terminaNaConsulta: 99 });
    await page.goto('/consulta/requerimentos/101');
    await page.getByRole('button', { name: 'Gerar dossiê' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('.topbar .ind-dossie')).toBeVisible();

    const rolagem = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(rolagem).toBeLessThanOrEqual(0);
    const caixa = await page.locator('.topbar .ind-dossie').boundingBox();
    expect(caixa!.x + caixa!.width).toBeLessThanOrEqual(390);
  });
});
