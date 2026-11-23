import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, UsuarioSimulado } from './api-simulada';

/** S5, bloco 6: Auditoria › Acessos, só Administrador, no mesmo componente da tela de documentos. API simulada. */
function evento(id: number, tipo: string, rotulo: string, sobre: object = {}) {
  return {
    id,
    dataHora: '2026-10-21T09:00:00-04:00',
    tipo,
    rotulo,
    usuario: { login: 'admin.demo', nome: 'Admin Demo', perfil: 'ADMIN' },
    idRequerimento: null,
    numeroProcesso: null,
    numeroRequerimento: null,
    sigiloso: false,
    arquivo: null,
    idPedido: null,
    detalhe: null,
    ...sobre,
  };
}

const EVENTOS = [
  evento(3, 'CONFIG', 'Configuração alterada', { detalhe: '1 parâmetro alterado' }),
  evento(2, 'PERFIL', 'Troca de perfil', { detalhe: 'Perfil novo: Administrador' }),
  evento(1, 'LOGIN', 'Acesso'),
];

interface Controle {
  chamadasDeAuditoria: string[];
  listas: URLSearchParams[];
  exportacoes: { autorizacao: string | undefined; url: string }[];
}

async function abrir(page: Page, usuario: UsuarioSimulado, rota = '/auditoria/acessos'): Promise<Controle> {
  const c: Controle = { chamadasDeAuditoria: [], listas: [], exportacoes: [] };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    if (caminho.startsWith('/api/auditoria')) c.chamadasDeAuditoria.push(caminho);
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/auditoria/acessos') {
      c.listas.push(url.searchParams);
      return route.fulfill({
        json: {
          conteudo: EVENTOS,
          pagina: 0,
          tamanho: 20,
          totalElementos: EVENTOS.length,
          totalPaginas: 1,
          indicadores: { entradas: 5, alteracoes: 2, usuarios: 3 },
        },
      });
    }
    if (caminho === '/api/auditoria/acessos/3') {
      return route.fulfill({
        json: {
          ...EVENTOS[0],
          origem: '10.0.0.7',
          correlationId: 'corr-9',
          documentos: [],
          alteracoes: [{ item: 'Expiração do dossiê (min)', valorAnterior: '60', valorNovo: '90' }],
        },
      });
    }
    if (caminho === '/api/auditoria/acessos/exportacao') {
      c.exportacoes.push({ autorizacao: route.request().headers()['authorization'], url: route.request().url() });
      return route.fulfill({
        status: 200,
        body: '﻿"Data";"Evento"\n',
        headers: { 'Content-Type': 'text/csv; charset=UTF-8', 'Content-Disposition': 'attachment; filename="auditoria-acessos-20261021.csv"' },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto(rota);
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

test('Administrador abre Auditoria pelo menu (item único) e a tela de acessos com indicadores', async ({ page }) => {
  const c = await abrir(page, ADMIN, '/consulta');
  await expect(page.locator('.top .nav a', { hasText: 'Auditoria' })).toHaveCount(1);
  await expect(page.locator('.top .nav a', { hasText: 'Acessos' })).toHaveCount(0);

  // as abas "Documentos" e "Acessos" ficam no topo da tela (R2, bloco 12); a aba Acessos leva à rota de acessos
  await page.locator('.top .nav a', { hasText: 'Auditoria' }).click();
  await expect(page).toHaveURL(/\/auditoria\/documentos$/);
  await page.locator('.sub-tabs a', { hasText: 'Acessos' }).click();

  await expect(page).toHaveURL(/\/auditoria\/acessos$/);
  await expect(page.getByRole('heading', { name: 'Auditoria de acessos' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(3);
  await expect(page.locator('[data-ind="entradas"] b')).toHaveText('5');
  await expect(page.locator('[data-ind="alteracoes"] b')).toHaveText('2');
  await expect(page.locator('[data-ind="usuarios"] b')).toHaveText('3');
  await expect(page.locator('thead')).not.toContainText('Processo');
  await expect(page.locator('.aud-somente-sigilosos')).toHaveCount(0);
  await expect(page.locator('.aud-memoria')).toContainText('somem ao reiniciar o serviço');
  expect(c.listas[0].get('periodo')).toBe('7d');
  expect(c.listas[0].has('somenteSigilosos')).toBe(false);
});

for (const [nome, usuario] of [
  ['Operador', OPERADOR],
  ['Gestor', GESTOR],
] as const) {
  test(`${nome}: sem o item no menu e, digitando a URL, volta à Consulta sem chamar a auditoria`, async ({ page }) => {
    const c = await abrir(page, usuario, '/consulta');
    await expect(page.locator('.top .nav a', { hasText: 'Acessos' })).toHaveCount(0);

    await page.evaluate(() => {
      history.pushState({}, '', '/auditoria/acessos');
      dispatchEvent(new PopStateEvent('popstate'));
    });

    await expect(page).toHaveURL(/\/consulta$/);
    await expect(page.locator('.toast.erro')).toContainText('Seu perfil não tem permissão');
    expect(c.chamadasDeAuditoria).toEqual([]);
  });
}

test('detalhe com a tabela antes e depois; CSV com Authorization e sem token na URL', async ({ page }) => {
  const c = await abrir(page, ADMIN);
  await page.locator('tbody tr').first().getByRole('button', { name: /Ver detalhe/ }).click();

  const gaveta = page.getByRole('dialog', { name: /Configuração alterada/ });
  await expect(gaveta.locator('.aud-alteracoes tbody tr')).toContainText(['Expiração do dossiê (min)']);
  await expect(gaveta.locator('.aud-alteracoes tbody td').nth(1)).toHaveText('60');
  await expect(gaveta.locator('.aud-alteracoes tbody td').nth(2)).toHaveText('90');
  await expect(gaveta).toContainText('10.0.0.7');
  await page.keyboard.press('Escape');

  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Exportar CSV' }).click();
  expect((await download).suggestedFilename()).toBe('auditoria-acessos-20261021.csv');
  expect(c.exportacoes[0].autorizacao).toBe('Bearer token-de-teste');
  expect(c.exportacoes[0].url).not.toContain('token-de-teste');
});

test('quadro do topo com a mesma altura do início ao filtro vazio; celular sem rolagem horizontal', async ({ page }) => {
  await abrir(page, ADMIN);
  await expect(page.locator('tbody tr')).toHaveCount(3);
  const altura = () => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  const inicial = await altura();

  await page.getByLabel('Tipo de evento').selectOption('LOGIN');
  await expect(page.locator('tbody tr')).toHaveCount(3);
  expect(await altura()).toBe(inicial);

  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
});
