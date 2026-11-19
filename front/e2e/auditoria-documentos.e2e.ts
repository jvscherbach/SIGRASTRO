import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, UsuarioSimulado } from './api-simulada';

/** S5, bloco 5: tela Auditoria › Documentos com a API simulada (dados fictícios, sem back-end). */
function evento(id: number, sobre: object = {}) {
  return {
    id,
    dataHora: '2026-10-21T10:30:00-04:00',
    tipo: 'VISUALIZACAO',
    rotulo: 'Visualização',
    usuario: { login: 'maria.teste', nome: 'Maria de Teste', perfil: 'OPERADOR_SIGILO' },
    idRequerimento: 101,
    numeroProcesso: '2825/2025',
    numeroRequerimento: '3331/2025',
    sigiloso: false,
    arquivo: 'oficio.pdf',
    idPedido: null,
    detalhe: 'Documento visualizado',
    ...sobre,
  };
}

const COMUM = evento(1);
const SIGILOSO = evento(2, { sigiloso: true, tipo: 'DOWNLOAD', rotulo: 'Download', arquivo: 'dossie-processo-2825-2025.pdf' });
const MASCARADO = evento(3, { sigiloso: true, arquivo: null, detalhe: 'Documento sigiloso' });

interface Controle {
  listas: URLSearchParams[];
  exportacoes: { params: URLSearchParams; autorizacao: string | undefined; url: string }[];
  truncar: boolean;
  eventos: object[];
  falha: boolean;
}

async function abrir(page: Page, usuario: UsuarioSimulado, sobre: Partial<Controle> = {}): Promise<Controle> {
  const c: Controle = { listas: [], exportacoes: [], truncar: false, eventos: [COMUM, SIGILOSO], falha: false, ...sobre };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/auditoria/documentos') {
      c.listas.push(url.searchParams);
      if (c.falha) return route.fulfill({ status: 503, json: { status: 503, errorCode: 'ERRO_INTERNO' } });
      const todos = c.eventos;
      return route.fulfill({
        json: {
          conteudo: todos,
          pagina: Number(url.searchParams.get('pagina') ?? 0),
          tamanho: 20,
          totalElementos: todos.length,
          totalPaginas: 1,
          indicadores: { dossiesGerados: 4, eventosComSigilosos: 2, usuarios: 3 },
        },
      });
    }
    if (caminho === '/api/auditoria/documentos/exportacao') {
      c.exportacoes.push({ params: url.searchParams, autorizacao: route.request().headers()['authorization'], url: route.request().url() });
      return route.fulfill({
        status: 200,
        body: '﻿"Data";"Evento"\n"21/10/2026";"Visualização"\n',
        headers: {
          'Content-Type': 'text/csv; charset=UTF-8',
          'Content-Disposition': 'attachment; filename="auditoria-documentos-20261021.csv"',
          ...(c.truncar ? { 'X-Vistas-Truncado': 'true' } : {}),
        },
      });
    }
    if (caminho === '/api/auditoria/documentos/2') {
      return route.fulfill({
        json: {
          ...SIGILOSO,
          origem: '10.0.0.7',
          correlationId: 'corr-1',
          alteracoes: [],
          documentos: [
            { origem: 'JUNTADA', id: '2', titulo: 'Ofício', nomeDoArquivo: 'oficio.pdf', sigiloso: false, ordem: 1 },
            { origem: 'IMOVEL', id: '9', titulo: 'Matrícula reservada', nomeDoArquivo: 'matricula.pdf', sigiloso: true, ordem: 2 },
          ],
        },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto('/auditoria/documentos');
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

test('Gestor: indicadores, lista com destaque lateral nos sigilosos, faixa de memória e "somente sigilosos"', async ({ page }) => {
  const c = await abrir(page, GESTOR);

  await expect(page.locator('tbody tr')).toHaveCount(2);
  await expect(page.locator('[data-ind="dossiesGerados"] b')).toHaveText('4');
  await expect(page.locator('[data-ind="eventosComSigilosos"] b')).toHaveText('2');
  await expect(page.locator('.aud-memoria')).toContainText('Neste protótipo, os registros ficam na memória e somem ao reiniciar o serviço.');
  await expect(page.locator('tbody tr').first()).toContainText('21/10/2026 10:30');
  await expect(page.locator('tbody tr').first()).toContainText('VISUALIZACAO');
  await expect(page.locator('tbody tr.aud-sigiloso')).toHaveCount(1);
  expect(c.listas[0].get('periodo')).toBe('7d');

  await page.locator('.aud-somente-sigilosos input').check();
  await expect.poll(() => c.listas.at(-1)?.get('somenteSigilosos')).toBe('true');
});

test('Operador: sem "somente sigilosos", texto dos seus processos e documento sigiloso mascarado', async ({ page }) => {
  await abrir(page, OPERADOR, { eventos: [COMUM, MASCARADO] });

  await expect(page.locator('tbody tr')).toHaveCount(2);
  await expect(page.locator('.aud-somente-sigilosos')).toHaveCount(0);
  await expect(page.locator('.aud-escopo')).toContainText('feitos por qualquer usuário');
  await expect(page.locator('tbody tr.aud-sigiloso')).toContainText('Documento sigiloso');
});

test('período, busca, tipo e usuário viram parâmetros da consulta', async ({ page }) => {
  const c = await abrir(page, ADMIN);

  await page.getByRole('button', { name: 'Últimos 30 dias' }).click();
  await expect.poll(() => c.listas.at(-1)?.get('periodo')).toBe('30d');
  await page.locator('.search input').fill('ofício');
  await expect.poll(() => c.listas.at(-1)?.get('texto')).toBe('ofício');
  await page.getByLabel('Tipo de evento').selectOption('FALHA');
  await expect.poll(() => c.listas.at(-1)?.get('tipo')).toBe('FALHA');
  await page.getByLabel('Login do usuário').fill('maria.teste');
  await expect.poll(() => c.listas.at(-1)?.get('usuario')).toBe('maria.teste');
  expect(c.listas.at(-1)?.get('pagina')).toBe('0');
});

test('detalhe: documentos com o sigilo de cada um e origem; fecha com Esc', async ({ page }) => {
  await abrir(page, GESTOR);

  await page.locator('tbody tr').nth(1).getByRole('button', { name: /Ver detalhe/ }).click();
  const gaveta = page.getByRole('dialog', { name: /Download/ });
  await expect(gaveta).toBeVisible();
  await expect(gaveta).toContainText('10.0.0.7');
  await expect(gaveta.locator('.aud-documentos li')).toHaveCount(2);
  await expect(gaveta.locator('.aud-documentos li.sigiloso')).toContainText('Matrícula reservada');

  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  await page.locator('tbody tr').nth(1).getByRole('button', { name: /Ver detalhe/ }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.locator('.scrim').click({ position: { x: 5, y: 5 } });
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('CSV: Blob com Authorization, sem token na URL, nome do servidor e aviso de truncamento', async ({ page }) => {
  const c = await abrir(page, GESTOR, { truncar: true });
  await page.getByRole('button', { name: 'Últimos 30 dias' }).click();
  await expect.poll(() => c.listas.at(-1)?.get('periodo')).toBe('30d');

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar CSV' }).click();

  expect((await download).suggestedFilename()).toBe('auditoria-documentos-20261021.csv');
  expect(c.exportacoes).toHaveLength(1);
  expect(c.exportacoes[0].autorizacao).toBe('Bearer token-de-teste');
  expect(c.exportacoes[0].url).not.toContain('token-de-teste');
  expect(c.exportacoes[0].params.get('periodo')).toBe('30d');
  await expect(page.locator('.aud-truncado')).toContainText('cortado');
});

test('vazio e erro com "Tentar novamente"', async ({ page }) => {
  const c = await abrir(page, GESTOR, { falha: true });
  await expect(page.locator('.aud-erro')).toBeVisible();

  c.falha = false;
  c.eventos = [];
  await page.getByRole('button', { name: 'Tentar novamente' }).click();

  await expect(page.locator('.aud-vazio')).toContainText('Nenhum evento encontrado');
});

test('quadro do topo com a mesma altura nos três estados da lista', async ({ page }) => {
  const c = await abrir(page, GESTOR, { falha: true });
  const altura = () => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  await expect(page.locator('.aud-erro')).toBeVisible();
  const erro = await altura();

  c.falha = false;
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.locator('tbody tr')).toHaveCount(2);
  const comLista = await altura();

  c.eventos = [];
  await page.getByRole('button', { name: 'Hoje' }).click();
  await expect(page.locator('.aud-vazio')).toBeVisible();

  expect(comLista).toBe(erro);
  expect(await altura()).toBe(erro);
});

test.describe('celular', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('sem rolagem horizontal da página, nem com a gaveta de detalhe aberta', async ({ page }) => {
    await abrir(page, GESTOR);
    await expect(page.locator('tbody tr')).toHaveCount(2);
    const rolagem = () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(await rolagem()).toBeLessThanOrEqual(0);

    await page.locator('tbody tr').nth(1).getByRole('button', { name: /Ver detalhe/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    expect(await rolagem()).toBeLessThanOrEqual(0);
  });
});
