import { Page, expect, test } from '@playwright/test';

import { ADMIN, simularApi } from './api-simulada';

/**
 * R1: o produto se chama Rastro. Nenhum texto visível (nem o `title`, nem o rótulo de acessibilidade) pode trazer o
 * nome antigo, e o módulo do dossiê mantém o rótulo "Vistas do Processo".
 */
const NOME_ANTIGO = /siga\s*vistas/i;

async function textoVisivel(page: Page): Promise<string> {
  return page.evaluate(() => {
    const rotulos = Array.from(document.querySelectorAll('[aria-label], [title], [alt], [placeholder]')).flatMap((e) =>
      ['aria-label', 'title', 'alt', 'placeholder'].map((a) => e.getAttribute(a) ?? ''),
    );
    return [document.title, document.body.innerText, ...rotulos].join('\n');
  });
}

test.describe('nome do produto: Rastro', () => {
  test('o título do documento da página inicial é "Rastro"', async ({ page }) => {
    await simularApi(page, { comSessao: false });
    await page.goto('/login');
    // O index.html traz o título base; as rotas acrescentam o nome da tela depois dele.
    const html = await page.evaluate(async () => (await fetch('/index.html')).text());
    expect(html).toMatch(/<title>Rastro<\/title>/);
  });

  test('login mostra Rastro e o rótulo curto, sem o nome antigo', async ({ page }) => {
    await simularApi(page, { comSessao: false });
    await page.goto('/login');
    await page.locator('.login-art .brand').waitFor();

    await expect(page.locator('.login-art .brand b')).toHaveText('RASTRO');
    await expect(page.locator('.login-art .brand small')).toHaveText('consulta ao SIGA Licenciamento (protótipo)');
    await expect(page).toHaveTitle('Entrar · Rastro');
    expect(await textoVisivel(page)).not.toMatch(NOME_ANTIGO);
  });

  for (const [rota, titulo] of [
    ['/consulta', 'Consulta · Rastro'],
    ['/dossies', 'Dossiês gerados · Rastro'],
    ['/auditoria/documentos', 'Auditoria de documentos · Rastro'],
    ['/configuracoes', 'Configurações · Rastro'],
  ] as const) {
    test(`${rota}: topo, rodapé e título trazem Rastro`, async ({ page }) => {
      await simularApi(page, { usuario: ADMIN });
      await page.goto(rota);
      await page.locator('.sidebar .brand').waitFor();

      await expect(page.locator('.sidebar .brand b')).toHaveText('RASTRO');
      await expect(page.locator('.page-footer')).toContainText('Rastro · Complemento de consulta ao SIGA Licenciamento');
      await expect(page.locator('.crumb-top')).toContainText('Vistas do Processo');
      await expect(page).toHaveTitle(titulo);
      expect(await textoVisivel(page)).not.toMatch(NOME_ANTIGO);
    });
  }
});
