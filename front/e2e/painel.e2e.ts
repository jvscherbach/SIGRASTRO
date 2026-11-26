import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, simularApi } from './api-simulada';

/** Instantâneo fictício (PN01 a PN15): só rótulos e totais, nenhum CPF nem id técnico. */
const PAINEL = {
  estado: 'PRONTO',
  ano: 2026,
  geradoEm: '2026-12-31T11:30:00-04:00',
  falhouEm: null,
  proximaAtualizacaoEm: '2026-12-31T12:00:00-04:00',
  truncado: false,
  serie: [
    { mes: 1, protocolados: 10, concluidos: 4 },
    { mes: 2, protocolados: 6, concluidos: 5 },
    { mes: 3, protocolados: 3, concluidos: 0 },
  ],
  totalProtocolados: 19,
  totalConcluidos: 9,
  semDataDeProtocolo: 1,
  filaPorEtapa: [
    { rotulo: 'Hídrico · Cadastro do requerimento', total: 5 },
    { rotulo: 'Hídrico · Análise técnica', total: 3 },
    { rotulo: 'LAC · Cadastro do requerimento', total: 0 },
  ],
  totalNaFila: 7,
  foraDoCatalogo: 2,
  semInstancia: 1,
  etapaNaoMapeada: 1,
  cargaPorResponsavel: [
    { rotulo: 'Servidor Exemplo 01', total: 4, tipo: 'SERVIDOR' },
    { rotulo: 'Área técnica', total: 3, tipo: 'AREA' },
    { rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' },
  ],
  nomesOcultos: false,
  avisos: [],
};

async function responderPainel(page: Page, corpo: object): Promise<void> {
  await page.route('**/api/painel', (route: Route) => route.fulfill({ json: corpo }));
}

test('Gestor abre o painel pelo menu e vê os três gráficos', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  await page.goto('/consulta');
  await page.locator('.top .nav a', { hasText: 'Painel' }).click();

  await expect(page).toHaveURL(/\/painel$/);
  await expect(page).toHaveTitle('Painel · SIGRASTRO');
  await expect(page.getByText('Ano de 2026 (requerimentos protocolados no ano)')).toBeVisible();
  await expect(page.locator('[role="img"]')).toHaveCount(3);
  await expect(page.getByText('Hídrico · Análise técnica')).toBeVisible();
  await expect(page.getByText('Concluídos (mês aproximado)').first()).toBeVisible();
});

test('Administrador também abre o painel', async ({ page }) => {
  await simularApi(page, { usuario: ADMIN });
  await responderPainel(page, PAINEL);
  await page.goto('/painel');
  await expect(page.locator('[role="img"]')).toHaveCount(3);
});

test('fila por etapa é uma pizza com legenda de todas as etapas e percentual sobre a soma', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  await page.goto('/painel');
  await expect(page.locator('svg.pizza-svg')).toHaveCount(1);
  const legenda = page.locator('.pizza-legenda li');
  await expect(legenda.first()).toContainText('%');
  expect(await legenda.count()).toBeGreaterThanOrEqual(PAINEL.filaPorEtapa.length);
  await expect(page.getByText('participação na soma das etapas').first()).toBeVisible();
  await legenda.first().focus();
  await expect(page.locator('svg.pizza-svg .fatia.destaque').first()).toBeVisible();
});

test('Operador não vê o item e é levado para fora da rota, sem chamar o painel', async ({ page }) => {
  const api = await simularApi(page, { usuario: OPERADOR });
  await page.goto('/painel');
  await expect(page).toHaveURL(/\/consulta$/);
  await expect(page.locator('.top .nav a', { hasText: 'Painel' })).toHaveCount(0);
  expect(api.chamadas).not.toContain('/api/painel');
});

test('ver como tabela mostra os mesmos números', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  await page.goto('/painel');
  await page.getByRole('button', { name: 'Ver como tabela' }).first().click();
  const linha = page.locator('table').first().locator('tbody tr').first();
  await expect(linha).toContainText('10');
  await expect(linha).toContainText('4');
  await expect(page.locator('table').first().locator('tfoot')).toContainText('19');
});

test('nomes ocultos: nenhum nome de servidor na tela', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, {
    ...PAINEL,
    nomesOcultos: true,
    cargaPorResponsavel: [{ rotulo: 'Área técnica', total: 3, tipo: 'AREA' }],
  });
  await page.goto('/painel');
  await expect(page.getByText('Nomes ocultos por configuração')).toBeVisible();
  await expect(page.locator('body')).not.toContainText('Servidor Exemplo');
});

test('estados DESATUALIZADO e INDISPONIVEL', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, { ...PAINEL, estado: 'DESATUALIZADO', falhouEm: '2026-12-31T11:45:00-04:00' });
  await page.goto('/painel');
  await expect(page.getByText('Última leitura falhou')).toBeVisible();
  await expect(page.locator('[role="img"]')).toHaveCount(3);

  await page.unroute('**/api/painel');
  await responderPainel(page, { ...PAINEL, estado: 'INDISPONIVEL', geradoEm: null, serie: [], filaPorEtapa: [], cargaPorResponsavel: [] });
  await page.reload();
  await expect(page.getByText('Painel indisponível')).toBeVisible();
  await expect(page.locator('[role="img"]')).toHaveCount(0);
});

test('429 ao atualizar mostra o tempo que falta', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  await page.route('**/api/painel/atualizacao', (route) =>
    route.fulfill({ status: 429, json: { status: 429, errorCode: 'ATUALIZACAO_RECENTE', tentarEmSegundos: 75 } }),
  );
  await page.goto('/painel');
  await page.getByRole('button', { name: 'Atualizar agora' }).click();
  await expect(page.getByText('Tente de novo em 75 segundos')).toBeVisible();
});

test('PREPARANDO mostra o selo e some quando o painel fica pronto', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, { ...PAINEL, estado: 'PREPARANDO', geradoEm: null, serie: [], filaPorEtapa: [], cargaPorResponsavel: [] });
  await page.goto('/painel');
  await expect(page.getByText('Preparando o painel')).toBeVisible();
  await expect(page.locator('[role="img"]')).toHaveCount(0);
});

/** Lista por analista (S11, bloco 9): identificadores opacos e nomes fictícios. */
const ANALISTAS = {
  estado: 'PRONTO',
  ano: 2026,
  geradoEm: '2026-12-31T11:30:00-04:00',
  falhouEm: null,
  truncado: false,
  nomesOcultos: false,
  analistas: [
    { analista: 'aaaaaaaaaaaaaaaa', rotulo: 'Servidor Exemplo 01', total: 4, tipo: 'SERVIDOR' },
    { analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Área técnica', total: 3, tipo: 'AREA' },
    { analista: 'cccccccccccccccc', rotulo: 'Não atribuída', total: 1, tipo: 'NAO_ATRIBUIDA' },
  ],
  etapas: ['Hídrico · Cadastro do requerimento', 'Hídrico · Análise técnica', 'Etapa não mapeada'],
  avisos: [],
};

function requerimentosDe(analista: string, rotulo: string, total: number, quantidade: number, requerimentos: object[]) {
  return {
    estado: 'PRONTO', ano: 2026, geradoEm: '2026-12-31T11:30:00-04:00', truncado: false, analista, rotulo,
    tipo: 'SERVIDOR', total, quantidade, limitada: false, requerimentos, avisos: [],
  };
}

const REQUERIMENTOS_DE_01 = [
  { id: 1, numero: 'REQ-1', interessado: 'Interessado Exemplo LTDA', protocolo: '2026-02-10', etapas: ['Hídrico · Análise técnica'], responsavel: 'Servidor Exemplo 01' },
  { id: 2, numero: 'REQ-2', interessado: 'Interessada Exemplo 02', protocolo: '2026-03-01', etapas: ['Hídrico · Cadastro do requerimento'], responsavel: 'Servidor Exemplo 01' },
];

async function responderAnalistas(page: Page, corpo: object = ANALISTAS): Promise<string[]> {
  const pedidos: string[] = [];
  await page.route('**/api/painel/analistas', (route: Route) => route.fulfill({ json: corpo }));
  await page.route('**/api/painel/analistas/*/requerimentos*', (route: Route) => {
    const url = new URL(route.request().url());
    pedidos.push(url.pathname + url.search);
    const busca = url.searchParams.get('busca');
    const etapa = url.searchParams.get('etapa');
    let itens = REQUERIMENTOS_DE_01;
    if (busca) itens = itens.filter((r) => r.interessado.toLowerCase().includes(busca.toLowerCase()));
    if (etapa) itens = itens.filter((r) => r.etapas.includes(etapa));
    return route.fulfill({ json: requerimentosDe('aaaaaaaaaaaaaaaa', 'Servidor Exemplo 01', 4, itens.length, itens) });
  });
  return pedidos;
}

test('aba lateral: o botão abre a lista, com filtros, e Fechar devolve o foco ao dashboard', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  const pedidos = await responderAnalistas(page);
  await page.goto('/painel');

  const botao = page.getByRole('button', { name: 'Processos por analista' });
  await botao.click();
  const aba = page.getByRole('dialog', { name: 'Processos por analista' });
  await expect(aba).toBeVisible();
  await expect(aba).toContainText('4 requerimentos (o total da barra)');
  await expect(aba).toContainText('Interessado Exemplo LTDA');
  await expect(aba).toContainText('Hídrico · Análise técnica');

  await aba.getByLabel('Etapa', { exact: true }).selectOption('Hídrico · Análise técnica');
  await expect(aba).toContainText('1 com os filtros');
  await aba.getByLabel('Buscar por nome do interessado ou número').fill('ltda');
  await expect(aba.locator('.painel-analistas-lista li')).toHaveCount(1);
  await expect.poll(() => pedidos.some((p) => p.includes('etapa=') && p.includes('busca=ltda'))).toBe(true);
  // nenhum identificador técnico nem CPF/CNPJ no DOM
  const corpo = (await aba.textContent()) ?? '';
  expect(corpo).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}|\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}|(?<!\d)\d{11}(?!\d)/);

  await aba.getByRole('button', { name: 'Fechar' }).last().click();
  await expect(aba).toHaveCount(0);
  await expect(botao).toBeFocused();
});

test('aba lateral: o clique na barra do analista abre a lista dele', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  const pedidos = await responderAnalistas(page);
  await page.goto('/painel');
  await page.locator('.barra-h.clicavel', { hasText: 'Área técnica' }).click();
  await expect(page.getByRole('dialog', { name: 'Processos por analista' })).toBeVisible();
  await expect.poll(() => pedidos.some((p) => p.includes('/bbbbbbbbbbbbbbbb/'))).toBe(true);
});

test('aba lateral: selo de lista parcial quando o painel foi truncado e nota de nomes ocultos', async ({ page }) => {
  await simularApi(page, { usuario: GESTOR });
  await responderPainel(page, PAINEL);
  await responderAnalistas(page, {
    ...ANALISTAS,
    truncado: true,
    nomesOcultos: true,
    avisos: [{ codigo: 'TRUNCADO', texto: 'Mostrando os 20000 primeiros requerimentos do ano.' }],
    analistas: [{ analista: 'bbbbbbbbbbbbbbbb', rotulo: 'Área técnica', total: 3, tipo: 'AREA' }],
  });
  // com os nomes ocultos só há categorias sem nome: a lista da categoria também não traz nome de servidor
  await page.route('**/api/painel/analistas/*/requerimentos*', (route: Route) =>
    route.fulfill({
      json: {
        ...requerimentosDe('bbbbbbbbbbbbbbbb', 'Área técnica', 3, 1, [
          { id: 3, numero: 'REQ-3', interessado: 'Interessado Exemplo 03', protocolo: '2026-04-01', etapas: ['Hídrico · Análise técnica'], responsavel: 'Área técnica' },
        ]),
        tipo: 'AREA',
      },
    }),
  );
  await page.goto('/painel');
  await page.getByRole('button', { name: 'Processos por analista' }).click();
  const aba = page.getByRole('dialog', { name: 'Processos por analista' });
  await expect(aba).toContainText('Mostrando os 20000 primeiros requerimentos do ano.');
  await expect(aba).toContainText('Nomes ocultos por configuração');
  await expect(aba).not.toContainText('Servidor Exemplo');
});

for (const largura of [360, 1280]) {
  test(`aba lateral sem rolagem horizontal em ${largura} px`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 800 });
    await simularApi(page, { usuario: GESTOR });
    await responderPainel(page, PAINEL);
    await responderAnalistas(page);
    await page.goto('/painel');
    await page.getByRole('button', { name: 'Processos por analista' }).click();
    await expect(page.getByRole('dialog', { name: 'Processos por analista' })).toContainText('Interessado Exemplo LTDA');
    const cabe = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
    expect(cabe).toBe(true);
  });
}

for (const largura of [360, 1280]) {
  test(`sem rolagem horizontal em ${largura} px, também na tabela`, async ({ page }) => {
    await page.setViewportSize({ width: largura, height: 800 });
    await simularApi(page, { usuario: GESTOR });
    await responderPainel(page, PAINEL);
    await page.goto('/painel');
    await expect(page.locator('[role="img"]')).toHaveCount(3);
    const cabe = () => page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
    expect(await cabe()).toBe(true);
    for (let i = 0; i < 3; i++) {
      await page.locator('section.painel-grafico').nth(i).getByRole('button', { name: 'Ver como tabela' }).click();
    }
    await expect(page.locator('table')).toHaveCount(3);
    expect(await cabe()).toBe(true);
  });
}
