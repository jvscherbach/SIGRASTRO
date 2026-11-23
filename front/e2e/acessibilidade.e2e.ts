import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, REQUERIMENTO, simularApi } from './api-simulada';

/**
 * R2, bloco 14: verificação transversal de teclado, foco e movimento, com a API simulada e dados fictícios.
 * - percorrer com Tab o login, a barra superior, a consulta, a tabela de documentos e as abas de Configurações: todo
 *   elemento que recebe foco tem anel visível e nome acessível;
 * - diálogos (visualizador, gaveta do dossiê): o foco entra, fica preso, o Esc fecha e o foco volta a quem abriu;
 * - com prefers-reduced-motion: reduce, nenhuma animação nem transição própria roda.
 */
const PDF_MINIMO = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R/Size 4>>\n%%EOF',
);

function doc(id: string, titulo: string, sobre: object = {}) {
  return {
    origem: 'DOCUMENTO_PROCESSO', id, idRequerimento: 101, titulo, nomeArquivo: `${titulo.toLowerCase().replace(/\s+/g, '-')}.pdf`,
    origemDescricao: 'Documento do processo', data: `2025-04-0${id}T10:00:00`, situacao: 'VIGENTE', sigiloso: false, acessivel: true,
    extensao: 'pdf', tipoArquivo: 'PDF', selecionavelPorPadrao: true, bloqueio: null, ...sobre,
  };
}

const DOCUMENTOS = {
  itens: [
    doc('1', 'Requerimento assinado'),
    doc('2', 'Oficio'),
    doc('3', 'Documento sigiloso', { nomeArquivo: null, extensao: null, tipoArquivo: null, sigiloso: true, acessivel: false, selecionavelPorPadrao: false, bloqueio: 'SIGILOSO' }),
  ],
  avisos: [],
  geometrias: [],
  totais: { total: 3, vigentes: 3, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 1, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 },
};

/** Detalhe com documentos e uma geração que nunca termina (a gaveta fica aberta com a animação de progresso). */
async function simularDetalhe(page: Page): Promise<void> {
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho.startsWith('/api/documentos/')) {
      return route.fulfill({ body: PDF_MINIMO, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="requerimento.pdf"' } });
    }
    if (caminho === '/api/dossies' && metodo === 'POST') return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      return route.fulfill({
        json: {
          id: 'pedido-1', status: 'OBTENDO', total: 2, processados: 1, erro: null, falhas: [], paginas: null, expiraEm: '2099-01-01T00:00:00',
          itens: [
            { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento assinado', situacao: 'OK' },
            { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio', situacao: 'OBTENDO' },
          ],
        },
      });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'DELETE') return route.fulfill({ status: 204 });
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

interface Parada {
  descricao: string;
  nome: string;
  anel: boolean;
}

/** Descreve o elemento com foco: nome acessível (simplificado) e se o anel de foco está visível. */
async function paradaAtual(page: Page): Promise<Parada> {
  return page.evaluate(() => {
    const e = document.activeElement as HTMLElement;
    const porId = (ids: string) => ids.split(/\s+/).map((id) => document.getElementById(id)?.textContent ?? '').join(' ');
    const rotulos = (e as HTMLInputElement).labels ? [...((e as HTMLInputElement).labels ?? [])].map((l) => l.textContent ?? '').join(' ') : '';
    const nome = (
      e.getAttribute('aria-label') ||
      (e.getAttribute('aria-labelledby') ? porId(e.getAttribute('aria-labelledby')!) : '') ||
      rotulos ||
      e.getAttribute('title') ||
      (e.textContent ?? '') ||
      e.querySelector('img')?.getAttribute('alt') ||
      ''
    ).trim();
    const s = getComputedStyle(e);
    // todo anel de foco do padrão é um outline de 3px (o anel interno --ink é sombra e não conta sozinho)
    const anel = s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) >= 2;
    return { descricao: `${e.tagName.toLowerCase()}${e.id ? '#' + e.id : ''}.${e.className}`.slice(0, 80), nome, anel };
  });
}

/** Aperta Tab `vezes` vezes e devolve cada parada (até sair da página). */
async function percorrer(page: Page, vezes: number): Promise<Parada[]> {
  const paradas: Parada[] = [];
  for (let i = 0; i < vezes; i++) {
    await page.keyboard.press('Tab');
    if (await page.evaluate(() => document.activeElement === document.body)) break;
    paradas.push(await paradaAtual(page));
  }
  return paradas;
}

function semFalhas(paradas: Parada[]): void {
  expect(paradas.length).toBeGreaterThan(2);
  expect(paradas.filter((p) => !p.anel).map((p) => p.descricao), 'sem anel de foco').toEqual([]);
  expect(paradas.filter((p) => !p.nome).map((p) => p.descricao), 'sem nome acessível').toEqual([]);
}

test('login: todo elemento alcançado pelo Tab tem anel de foco e nome acessível', async ({ page }) => {
  await simularApi(page, { comSessao: false });
  await page.goto('/login');
  await page.locator('.login-card').waitFor();
  semFalhas(await percorrer(page, 15));
});

test('barra superior e consulta: Tab alcança marca, navegação, Sair e o formulário com anel e nome', async ({ page }) => {
  await simularApi(page, { usuario: ADMIN });
  await page.goto('/consulta');
  await page.locator('.top .nav').waitFor();
  const paradas = await percorrer(page, 20);
  semFalhas(paradas);
  const nomes = paradas.map((p) => p.nome);
  expect(nomes).toContain('SIGRASTRO, início');
  expect(nomes.some((n) => n.includes('Consulta'))).toBe(true);
  expect(nomes).toContain('Sair');
});

test('item ativo da navegação: aria-current="page" e sublinhado --marker', async ({ page }) => {
  await simularApi(page, { usuario: ADMIN });
  await page.goto('/consulta');
  const ativo = page.locator('.top .nav a[aria-current="page"]');
  await expect(ativo).toHaveCount(1);
  await expect(ativo).toHaveCSS('border-bottom-color', 'rgb(232, 183, 18)');
});

test('tabela de documentos: o Tab percorre seleção, ordenação e ações com anel e nome', async ({ page }) => {
  await simularDetalhe(page);
  await page.goto('/consulta/requerimentos/101');
  await expect(page.locator('section[aria-label="Documentos"] tbody tr')).toHaveCount(3);
  await page.locator('section[aria-label="Documentos"]').getByRole('checkbox').first().focus();
  semFalhas(await percorrer(page, 25));
});

test('visualizador: o foco entra no diálogo, fica preso, o Esc fecha e o foco volta ao botão', async ({ page }) => {
  await simularDetalhe(page);
  await page.goto('/consulta/requerimentos/101');
  const botao = page.locator('tbody tr', { hasText: 'Requerimento assinado' }).getByRole('button', { name: /Visualizar/ });
  await botao.focus();
  await page.keyboard.press('Enter');

  const modal = page.getByRole('dialog', { name: 'requerimento-assinado.pdf' });
  await expect(modal).toBeFocused();
  for (let i = 0; i < 8; i++) {
    await page.keyboard.press('Tab');
    expect(await modal.evaluate((m) => m.contains(document.activeElement))).toBe(true);
  }
  await page.keyboard.press('Shift+Tab');
  expect(await modal.evaluate((m) => m.contains(document.activeElement))).toBe(true);

  await page.keyboard.press('Escape');
  await expect(page.locator('app-visualizador-documento')).toHaveCount(0);
  await expect(botao).toBeFocused();
});

test('gaveta do dossiê: o foco entra, fica preso, o Esc minimiza e o foco volta a "Gerar dossiê"', async ({ page }) => {
  await simularDetalhe(page);
  await page.goto('/consulta/requerimentos/101');
  const gerar = page.getByRole('button', { name: 'Gerar dossiê' });
  await gerar.focus();
  await page.keyboard.press('Enter');

  const gaveta = page.getByRole('dialog', { name: /Gerando dossiê|Enviando/ });
  await expect(gaveta).toBeFocused();
  for (let i = 0; i < 6; i++) {
    await page.keyboard.press('Tab');
    expect(await gaveta.evaluate((g) => g.contains(document.activeElement))).toBe(true);
    const p = await paradaAtual(page);
    expect(p.anel, p.descricao).toBe(true);
    expect(p.nome, p.descricao).not.toBe('');
  }

  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(gerar).toBeFocused();
});

test('abas de Configurações: setas trocam a aba e o foco tem anel visível', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: ADMIN });
    if (caminho === '/api/configuracoes/usuarios') return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 0, totalElementos: 0, totalPaginas: 0 } });
    if (caminho === '/api/configuracoes/parametros') return route.fulfill({ json: { parametros: [] } });
    if (caminho === '/api/configuracoes/conexoes') return route.fulfill({ json: { conexoes: [], tempoLimiteDoTesteSegundos: 15 } });
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto('/configuracoes');
  const primeira = page.getByRole('tab').first();
  await primeira.focus();
  await page.keyboard.press('ArrowRight');
  const p = await paradaAtual(page);
  expect(await page.evaluate(() => document.activeElement?.getAttribute('role'))).toBe('tab');
  expect(p.anel, p.descricao).toBe(true);
  expect(p.nome).not.toBe('');
});

test.describe('movimento reduzido', () => {
  test.use({ reducedMotion: 'reduce' });

  test('nenhuma animação nem transição própria roda (gaveta com progresso aberta)', async ({ page }) => {
    await simularDetalhe(page);
    await page.goto('/consulta/requerimentos/101');
    await page.getByRole('button', { name: 'Gerar dossiê' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();

    const animados = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('body *')]
        .filter((e) => {
          const s = getComputedStyle(e);
          const anima = s.animationName !== 'none' && parseFloat(s.animationDuration) > 0;
          const transita = s.transitionDuration.split(',').some((d) => parseFloat(d) > 0);
          return anima || transita;
        })
        .map((e) => `${e.tagName.toLowerCase()}.${e.className}`),
    );
    expect(animados).toEqual([]);
  });

  test('a paisagem do Cerrado traz a própria regra de movimento reduzido', async ({ request }) => {
    const svg = await (await request.get('/assets/img/cerrado.svg')).text();
    expect(svg).toContain('prefers-reduced-motion');
  });
});
