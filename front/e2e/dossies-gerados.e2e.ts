import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

/**
 * S4, bloco 8: tela "Dossiês gerados" com a API simulada. Situações e ações por linha, perfis (Operador sem "Gerado
 * por" nem "Só os meus"), download por Blob com o token no cabeçalho, dossiê grande por ticket, "Decidir" na gaveta
 * existente, atualização automática parada fora da tela e quadro do topo estável. Dados fictícios.
 */
const PDF_MINIMO = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R/Size 4>>\n%%EOF',
);

function pedido(id: string, situacao: string, sobre: object = {}) {
  return {
    id,
    idRequerimento: 101,
    identificacao: { rotulo: 'Processo', numero: '2825/2025' },
    situacao,
    criadoEm: '2099-01-01T10:00:00',
    expiraEm: '2099-01-01T10:31:00',
    documentos: 3,
    falhas: 0,
    listados: 4,
    tamanhoBytes: null,
    entregaPorTicket: false,
    temArquivo: false,
    arquivoGerado: false,
    ...sobre,
  };
}

const TODOS = [
  pedido('p-pronto', 'CONCLUIDO', { temArquivo: true, arquivoGerado: true, tamanhoBytes: 2 * 1024 * 1024 }),
  pedido('p-grande', 'CONCLUIDO', { temArquivo: true, arquivoGerado: true, tamanhoBytes: 300 * 1024 * 1024, entregaPorTicket: true }),
  pedido('p-decisao', 'PENDENTE_DECISAO', { falhas: 1 }),
  pedido('p-obtendo', 'OBTENDO'),
  pedido('p-vencido', 'EXPIRADO'),
  pedido('p-baixado', 'BAIXADO', { arquivoGerado: true }),
];

type Lista = 'cheia' | 'vazia' | 'erro';

interface Simulacao {
  lista: Lista;
  chamadasDaLista: string[];
  arquivos: { authorization: string | undefined; url: string }[];
  tickets: number;
  cancelados: string[];
}

async function simular(page: Page, usuario: UsuarioSimulado, lista: Lista = 'cheia'): Promise<Simulacao> {
  const sim: Simulacao = { lista, chamadasDaLista: [], arquivos: [], tickets: 0, cancelados: [] };
  const comGeradoPor = usuario !== OPERADOR;
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') {
      return route.fulfill({ json: { itens: [], avisos: [], geometrias: [], totais: { total: 0, vigentes: 0, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0 } } });
    }
    if (caminho === '/api/dossies' && metodo === 'GET') {
      sim.chamadasDaLista.push(url.search);
      if (sim.lista === 'erro') return route.fulfill({ status: 500, json: { status: 500, errorCode: 'ERRO_INTERNO', detail: 'stack técnico' } });
      const conteudo = sim.lista === 'vazia' ? [] : TODOS.map((p) => (comGeradoPor ? { ...p, geradoPor: 'Gestor' } : p));
      return route.fulfill({ json: { conteudo, pagina: 0, tamanho: 20, totalElementos: conteudo.length, totalPaginas: 1 } });
    }
    if (caminho === '/api/dossies/p-pronto/arquivo') {
      sim.arquivos.push({ authorization: route.request().headers()['authorization'], url: route.request().url() });
      const anexo = url.searchParams.get('disposicao') === 'attachment';
      return route.fulfill({
        body: PDF_MINIMO,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `${anexo ? 'attachment' : 'inline'}; filename*=UTF-8''dossie-processo-2825-2025.pdf` },
      });
    }
    if (caminho === '/api/dossies/p-grande/arquivo/ticket' && metodo === 'POST') {
      sim.tickets += 1;
      return route.fulfill({ json: { ticket: 'ticket-de-uso-unico', expiraEm: '2099-01-01T10:01:00' } });
    }
    if (caminho === '/api/dossies/p-grande/arquivo') {
      return route.fulfill({ body: PDF_MINIMO, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="dossie-grande.pdf"' } });
    }
    if (caminho === '/api/dossies/p-decisao' && metodo === 'GET') {
      return route.fulfill({
        json: {
          id: 'p-decisao',
          status: 'PENDENTE_DECISAO',
          total: 2,
          processados: 2,
          erro: null,
          falhas: [{ origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio', motivo: 'arquivo não encontrado no repositório' }],
          paginas: null,
          expiraEm: '2099-01-01T10:31:00',
          itens: [
            { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento', situacao: 'OK' },
            { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio', situacao: 'FALHA', motivo: 'arquivo não encontrado no repositório' },
          ],
        },
      });
    }
    if (metodo === 'DELETE' && caminho.startsWith('/api/dossies/')) {
      sim.cancelados.push(caminho.split('/').pop()!);
      return route.fulfill({ status: 204 });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

const linha = (page: Page, id: string) => page.locator(`tbody tr[data-situacao]`).nth(TODOS.findIndex((p) => p.id === id));
const botoesDa = (page: Page, id: string) => linha(page, id).locator('.acoes-dossie button');
const alturaDoQuadro = (page: Page) => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);

test.use({ viewport: { width: 1280, height: 900 } });

test('cada situação com a sua etiqueta e as suas ações', async ({ page }) => {
  await simular(page, GESTOR);
  await page.goto('/dossies');

  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(TODOS.length);
  const esperado: Record<string, { rotulo: string; acoes: string[] }> = {
    'p-pronto': { rotulo: 'Pronto', acoes: ['Pré-visualizar', 'Baixar PDF', 'Abrir requerimento'] },
    'p-grande': { rotulo: 'Pronto', acoes: ['Baixar PDF', 'Abrir requerimento'] },
    'p-decisao': { rotulo: 'Aguardando decisão', acoes: ['Decidir', 'Abrir requerimento'] },
    'p-obtendo': { rotulo: 'Obtendo documentos', acoes: ['Cancelar', 'Abrir requerimento'] },
    'p-vencido': { rotulo: 'Expirado · nenhum arquivo gerado', acoes: ['Abrir requerimento'] },
    'p-baixado': { rotulo: 'Arquivo baixado', acoes: ['Abrir requerimento'] },
  };
  for (const [id, { rotulo, acoes }] of Object.entries(esperado)) {
    await expect(linha(page, id).locator('.tag')).toHaveText(rotulo);
    await expect(botoesDa(page, id)).toHaveText(acoes);
  }
  await expect(linha(page, 'p-decisao')).toContainText('1 com problema');
  await expect(linha(page, 'p-pronto')).toContainText('2 MB');
});

test('Operador: sem "Gerado por" e sem "Só os meus"', async ({ page }) => {
  await simular(page, OPERADOR);
  await page.goto('/dossies');

  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(TODOS.length);
  await expect(page.locator('thead')).not.toContainText('Gerado por');
  await expect(page.locator('.somente-meus')).toHaveCount(0);
});

test('Gestor: "Gerado por" e filtro "Só os meus"', async ({ page }) => {
  const sim = await simular(page, GESTOR);
  await page.goto('/dossies');

  await expect(page.locator('thead')).toContainText('Gerado por');
  await expect(linha(page, 'p-pronto').locator('.gerado-por')).toHaveText('Gestor');
  await page.getByLabel('Só os meus').check();
  await expect.poll(() => sim.chamadasDaLista.at(-1)).toContain('somenteMeus=true');
});

test('filtro por situação', async ({ page }) => {
  await simular(page, GESTOR);
  await page.goto('/dossies');
  await page.getByLabel('Situação').selectOption({ label: 'Arquivo baixado' });
  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(1);
  await expect(page.locator('tbody tr[data-situacao]')).toHaveAttribute('data-situacao', 'BAIXADO');
});

test('Baixar PDF por Blob com o token no cabeçalho; pré-visualizar em moldura', async ({ page }) => {
  const sim = await simular(page, GESTOR);
  await page.goto('/dossies');

  await botoesDa(page, 'p-pronto').filter({ hasText: 'Pré-visualizar' }).click();
  await expect(page.locator('.previa iframe')).toBeVisible();
  await page.getByRole('button', { name: 'Fechar pré-visualização' }).click();
  await expect(page.locator('.previa')).toHaveCount(0);

  const download = page.waitForEvent('download');
  await botoesDa(page, 'p-pronto').filter({ hasText: 'Baixar PDF' }).click();
  expect((await download).suggestedFilename()).toBe('dossie-processo-2825-2025.pdf');

  expect(sim.arquivos).toHaveLength(2);
  for (const pedidoDeArquivo of sim.arquivos) {
    expect(pedidoDeArquivo.authorization).toBe('Bearer token-de-teste');
    expect(pedidoDeArquivo.url).not.toContain('token-de-teste');
  }
});

test('dossiê grande: ticket e download direto, só o ticket na URL', async ({ page }) => {
  const sim = await simular(page, GESTOR);
  await page.goto('/dossies');

  const pedidoDireto = page.waitForRequest((r) => r.url().includes('/api/dossies/p-grande/arquivo?'));
  await botoesDa(page, 'p-grande').filter({ hasText: 'Baixar PDF' }).click();
  const url = (await pedidoDireto).url();
  expect(sim.tickets).toBe(1);
  expect(url).toContain('ticket=ticket-de-uso-unico');
  expect(url).not.toContain('token-de-teste');
});

test('Decidir abre a gaveta de geração com o pedido existente', async ({ page }) => {
  await simular(page, GESTOR);
  await page.goto('/dossies');

  await botoesDa(page, 'p-decisao').filter({ hasText: 'Decidir' }).click();
  const gaveta = page.getByRole('dialog');
  await expect(gaveta).toContainText('Documentos com problema');
  await expect(gaveta).toContainText('arquivo não encontrado no repositório');
  await expect(gaveta.getByRole('button', { name: /Tentar novamente o 1 com problema/ })).toBeVisible();
});

test('Cancelar e Abrir requerimento', async ({ page }) => {
  const sim = await simular(page, GESTOR);
  await page.goto('/dossies');

  await botoesDa(page, 'p-obtendo').filter({ hasText: 'Cancelar' }).click();
  await expect.poll(() => sim.cancelados).toEqual(['p-obtendo']);

  await botoesDa(page, 'p-pronto').filter({ hasText: 'Abrir requerimento' }).click();
  await expect(page).toHaveURL(/\/consulta\/requerimentos\/101$/);
});

test('histórico vazio e erro com "Tentar novamente"; quadro do topo com 0 px', async ({ page }) => {
  const sim = await simular(page, OPERADOR, 'vazia');
  await page.goto('/dossies');
  await expect(page.locator('.dossies-vazio')).toContainText('Você ainda não gerou dossiês nesta sessão do serviço');
  await expect(page.locator('.nota-historico')).toContainText('some quando o serviço é reiniciado');
  const alturaVazia = await alturaDoQuadro(page);

  sim.lista = 'erro';
  await page.getByRole('button', { name: 'Atualizar' }).click();
  await expect(page.locator('.dossies-erro')).toContainText('Ocorreu um erro inesperado.');
  await expect(page.locator('.dossies-erro')).not.toContainText('stack');
  expect(await alturaDoQuadro(page)).toBe(alturaVazia);

  sim.lista = 'cheia';
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(TODOS.length);
  expect(await alturaDoQuadro(page)).toBe(alturaVazia);
});

test('atualização automática a cada 30 s, parada ao sair da tela', async ({ page }) => {
  await page.clock.install();
  const sim = await simular(page, GESTOR);
  await page.goto('/dossies');
  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(TODOS.length);
  expect(sim.chamadasDaLista).toHaveLength(1);

  await page.clock.runFor(30_000);
  await expect.poll(() => sim.chamadasDaLista.length).toBe(2);

  await page.getByRole('link', { name: 'Consulta', exact: true }).click();
  await expect(page).toHaveURL(/\/consulta$/);
  await page.clock.runFor(120_000);
  expect(sim.chamadasDaLista).toHaveLength(2);
});

test('no celular, a página não rola na horizontal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await simular(page, GESTOR);
  await page.goto('/dossies');
  await expect(page.locator('tbody tr[data-situacao]')).toHaveCount(TODOS.length);
  const medida = await page.evaluate(() => ({ pagina: document.documentElement.scrollWidth, tela: document.documentElement.clientWidth }));
  expect(medida.pagina).toBe(medida.tela);
});
