import { Page, Route, expect, test } from '@playwright/test';

import { ANALISTA, simularApi } from './api-simulada';

/** S11, bloco 7: Minha caixa com a API simulada. Dados fictícios (cenário CX): só nome do interessado, nunca CPF/CNPJ. */
const LINHAS = [
  { id: 15014, numero: '1014/2027', interessado: 'Interessado Exemplo LTDA', tipo: 'Outorga', posse: 'COM_ANALISTA', etapaId: 'analise_do_processo', etapa: 'Análise do processo', diasNaEtapa: 400, novo: false },
  { id: 15001, numero: '1001/2028', interessado: 'Interessado Exemplo 01', tipo: 'Outorga', posse: 'COM_ANALISTA', etapaId: 'analise_do_processo', etapa: 'Análise do processo', diasNaEtapa: 20, novo: false },
  { id: 15005, numero: '1005/2028', interessado: 'Interessado Exemplo 03', tipo: 'Outorga', posse: 'COM_ANALISTA', etapaId: 'analise_do_processo', etapa: 'Análise do processo', diasNaEtapa: 10, novo: true },
  { id: 15006, numero: '1006/2028', interessado: 'Interessado Exemplo 01', tipo: 'Outorga', posse: 'COM_ANALISTA', etapaId: 'revisao_do_parecer_tecnico', etapa: 'Revisão do parecer técnico', diasNaEtapa: 4, novo: true },
  { id: 15004, numero: '1004/2028', interessado: 'Cooperativa Exemplo 04', tipo: 'Outorga', posse: 'COM_INTERESSADO', etapaId: 'correcao_do_requerimento', etapa: 'Correção do requerimento', diasNaEtapa: 12, novo: false },
];

const RESUMO = {
  total: 5,
  comInteressado: 1,
  porEtapa: [
    { id: 'analise_do_processo', rotulo: 'Análise do processo', quantidade: 3 },
    { id: 'revisao_do_parecer_tecnico', rotulo: 'Revisão do parecer técnico', quantidade: 1 },
  ],
};

const AVISOS = {
  semVinculo: false,
  mensagem: null,
  quantidade: 2,
  avisos: [
    { id: 15005, numero: '1005/2028', interessado: 'Interessado Exemplo 03', tipo: 'Outorga', etapaId: 'analise_do_processo', etapa: 'Análise do processo', origemCodigo: 'VOLTOU_DA_CORRECAO', origem: 'Voltou da correção' },
    { id: 15006, numero: '1006/2028', interessado: 'Interessado Exemplo 01', tipo: 'Outorga', etapaId: 'revisao_do_parecer_tecnico', etapa: 'Revisão do parecer técnico', origemCodigo: 'VOLTOU_DA_EMISSAO', origem: 'Voltou da emissão do ato' },
  ],
};

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

interface Estado {
  visto: boolean;
  vazia: boolean;
  semVinculo: boolean;
  pedidos: string[];
  vistos: number[][];
}

/** Servidor simulado: filtra, ordena e guarda o visto como o back-end faz. */
async function simularCaixa(page: Page, parcial: Partial<Estado> = {}): Promise<Estado> {
  const estado: Estado = { visto: false, vazia: false, semVinculo: false, pedidos: [], vistos: [], ...parcial };
  await page.route('**/api/caixa**', async (route: Route) => {
    const url = new URL(route.request().url());
    estado.pedidos.push(`${route.request().method()} ${url.pathname}${url.search}`);
    if (url.pathname === '/api/caixa/avisos/visto') {
      estado.visto = true;
      estado.vistos.push((route.request().postDataJSON() as { ids: number[] }).ids);
      return route.fulfill({ status: 204 });
    }
    if (url.pathname === '/api/caixa/avisos') {
      return route.fulfill({ json: AVISOS });
    }
    if (url.pathname === '/api/caixa/atualizar') {
      return route.fulfill({ status: 202 });
    }
    const base = { estado: 'PRONTO', geradoEm: '2028-06-15T12:30:00-04:00', falhouEm: null, incompleta: false };
    if (estado.semVinculo) {
      return route.fulfill({
        json: { ...base, semVinculo: true, mensagem: 'Seu usuário não está vinculado a um servidor. Peça ao Administrador.', novos: 0, resumo: { total: 0, comInteressado: 0, porEtapa: [] }, linhas: [] },
      });
    }
    if (estado.vazia) {
      return route.fulfill({ json: { ...base, semVinculo: false, mensagem: null, novos: 0, resumo: { total: 0, comInteressado: 0, porEtapa: [] }, linhas: [] } });
    }
    const etapa = url.searchParams.get('etapa');
    const busca = normalizar(url.searchParams.get('busca') ?? '');
    let linhas = LINHAS.filter((l) => !etapa || l.etapaId === etapa).filter(
      (l) => !busca || normalizar(`${l.interessado} ${l.numero}`).includes(busca),
    );
    linhas = linhas.map((l) => ({ ...l, novo: l.novo && !estado.visto }));
    linhas.sort((a, b) => (url.searchParams.get('ordem') === 'MAIS_RECENTE' ? a.diasNaEtapa - b.diasNaEtapa : b.diasNaEtapa - a.diasNaEtapa));
    return route.fulfill({
      json: { ...base, semVinculo: false, mensagem: null, novos: estado.visto ? 0 : 2, resumo: RESUMO, linhas },
    });
  });
  return estado;
}

async function abrir(page: Page, parcial: Partial<Estado> = {}): Promise<Estado> {
  await simularApi(page, { usuario: ANALISTA });
  const estado = await simularCaixa(page, parcial);
  await page.goto('/caixa');
  await expect(page.getByRole('heading', { name: 'Minha caixa' })).toBeVisible();
  return estado;
}

test('caixa vazia diz que não há requerimentos', async ({ page }) => {
  await abrir(page, { vazia: true });
  await expect(page.getByText('Nenhum requerimento na sua caixa.')).toBeVisible();
  await expect(page.locator('.caixa-aviso')).toHaveCount(0);
});

test('com itens: resumo, lista com interessado, dias e a ordem padrão (mais parado primeiro)', async ({ page }) => {
  await abrir(page);
  await expect(page.locator('[data-resumo="total"] b')).toHaveText('5');
  await expect(page.locator('.caixa-lista li')).toHaveCount(5);
  await expect(page.locator('.caixa-lista li').first()).toContainText('Interessado Exemplo LTDA');
  await expect(page.locator('.caixa-lista li').first()).toContainText('400 dias na etapa');
  await expect(page.getByText('Atualizado às 12:30')).toBeVisible();
  await expect(page.locator('.caixa-novo')).toHaveCount(2);
});

test('aviso de recebimento: a caixinha abre a lista de mudanças, grava o visto e some', async ({ page }) => {
  const estado = await abrir(page);
  const caixinha = page.locator('.caixa-aviso');
  await expect(caixinha).toContainText('2 requerimentos chegaram para você');

  await caixinha.click();
  const dialogo = page.getByRole('dialog', { name: 'O que chegou para você' });
  await expect(dialogo).toBeVisible();
  await expect(dialogo).toContainText('Voltou da correção');
  await expect(dialogo).toContainText('Voltou da emissão do ato');
  await expect(dialogo).toContainText('Interessado Exemplo 03');
  await expect.poll(() => estado.vistos).toEqual([[15005, 15006]]);

  await dialogo.getByRole('button', { name: 'Fechar' }).last().click();
  await expect(dialogo).toHaveCount(0);
  await expect(page.locator('.caixa-aviso')).toHaveCount(0);
  await expect(page.locator('.caixa-novo')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Requerimentos na sua posse' })).toBeFocused();

  await page.reload();
  await expect(page.locator('.caixa-lista li')).toHaveCount(5);
  await expect(page.locator('.caixa-aviso')).toHaveCount(0);
});

test('a lista de mudanças fecha com Esc', async ({ page }) => {
  await abrir(page);
  await page.locator('.caixa-aviso').click();
  const dialogo = page.getByRole('dialog', { name: 'O que chegou para você' });
  await expect(dialogo).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialogo).toHaveCount(0);
});

test('na lista de mudanças, o item leva ao requerimento', async ({ page }) => {
  await abrir(page);
  await page.locator('.caixa-aviso').click();
  await page.getByRole('dialog').getByRole('link', { name: /1005\/2028/ }).click();
  await expect(page).toHaveURL(/\/consulta\/requerimentos\/15005$/);
});

test('busca por interessado ou número', async ({ page }) => {
  await abrir(page);
  await page.getByLabel('Buscar por nome do interessado ou número').fill('exemplo 01');
  await expect(page.locator('.caixa-lista li')).toHaveCount(2);
  await page.getByLabel('Buscar por nome do interessado ou número').fill('1004');
  await expect(page.locator('.caixa-lista li')).toHaveCount(1);
  await page.getByLabel('Buscar por nome do interessado ou número').fill('nada disso');
  await expect(page.getByText('Nenhum requerimento corresponde aos filtros.')).toBeVisible();
});

test('ordenação: mais recente primeiro', async ({ page }) => {
  await abrir(page);
  await page.getByLabel('Ordenação').selectOption('MAIS_RECENTE');
  await expect(page.locator('.caixa-lista li').first()).toContainText('1006/2028');
});

test('por etapa: aba da revisão e aba do que está com o interessado', async ({ page }) => {
  await abrir(page);
  await page.getByRole('button', { name: 'Revisão do parecer técnico (1)' }).click();
  await expect(page.locator('.caixa-lista li')).toHaveCount(1);
  await expect(page.locator('.caixa-lista li')).toContainText('Revisão do parecer técnico');

  await page.getByRole('button', { name: 'Com o interessado (1)' }).click();
  await expect(page.locator('.caixa-lista li')).toHaveCount(1);
  await expect(page.locator('.caixa-lista li')).toContainText('Com o interessado');

  await page.getByRole('button', { name: 'Geral' }).click();
  await expect(page.locator('.caixa-lista li')).toHaveCount(5);
});

test('sem CPF vinculado: mensagem e nenhuma lista', async ({ page }) => {
  await abrir(page, { semVinculo: true });
  await expect(page.getByText('Seu usuário não está vinculado a um servidor. Peça ao Administrador.')).toBeVisible();
  await expect(page.locator('.caixa-lista')).toHaveCount(0);
});

test('o botão Atualizar pede a atualização', async ({ page }) => {
  const estado = await abrir(page);
  await page.getByRole('button', { name: 'Atualizar' }).click();
  await expect(page.getByText('Atualização pedida.')).toBeVisible();
  expect(estado.pedidos).toContain('POST /api/caixa/atualizar');
});

for (const largura of [360, 390, 1280]) {
  test(`sem rolagem horizontal em ${largura} px, com a caixinha e a lista`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 800 });
    await abrir(page);
    await expect(page.locator('.caixa-aviso')).toBeVisible();
    const sobra = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(sobra).toBeLessThanOrEqual(0);
    await page.locator('.caixa-aviso').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    const sobraComDialogo = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(sobraComDialogo).toBeLessThanOrEqual(0);
  });
}

test('o DOM não traz CPF ou CNPJ', async ({ page }) => {
  await abrir(page);
  await page.locator('.caixa-aviso').click();
  const texto = await page.locator('body').innerText();
  expect(texto).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}|\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}/);
});
