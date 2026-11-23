import { Page, expect, test } from '@playwright/test';

import { ADMIN, simularApi } from './api-simulada';

/**
 * R1, atualizado na R2 (bloco 15): o nome visível do produto é SIGRASTRO (D90). Nenhum texto visível (nem o `title`, nem
 * o rótulo de acessibilidade) pode trazer o nome antigo "SIGA Vistas", e o módulo do dossiê mantém o rótulo "Vistas do
 * Processo" (agora no sobretítulo da faixa do topo; a trilha textual e o rodapé saíram com o menu lateral, no bloco 6).
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

test.describe('nome do produto: SIGRASTRO', () => {
  test('o título do documento da página inicial é "SIGRASTRO"', async ({ page }) => {
    await simularApi(page, { comSessao: false });
    await page.goto('/login');
    // O index.html traz o título base; as rotas acrescentam o nome da tela antes dele.
    const html = await page.evaluate(async () => (await fetch('/index.html')).text());
    expect(html).toMatch(/<title>SIGRASTRO<\/title>/);
  });

  test('login mostra SIGRASTRO e a frase de apoio, sem o nome antigo', async ({ page }) => {
    await simularApi(page, { comSessao: false });
    await page.goto('/login');
    await page.locator('.login .marca').waitFor();

    await expect(page.locator('.login .marca .wm')).toHaveText('SIGRASTRO');
    await expect(page.locator('.login .marca p')).toHaveText('Acompanhamento de processos de licenciamento ambiental');
    await expect(page).toHaveTitle('Entrar · SIGRASTRO');
    expect(await textoVisivel(page)).not.toMatch(NOME_ANTIGO);
  });

  for (const [rota, titulo] of [
    ['/consulta', 'Consulta · SIGRASTRO'],
    ['/dossies', 'Dossiês gerados · SIGRASTRO'],
    ['/auditoria/documentos', 'Auditoria de documentos · SIGRASTRO'],
    ['/configuracoes', 'Configurações · SIGRASTRO'],
  ] as const) {
    test(`${rota}: barra superior, faixa e título trazem SIGRASTRO`, async ({ page }) => {
      await simularApi(page, { usuario: ADMIN });
      await page.goto(rota);
      await page.locator('.top .logo app-marca').waitFor();

      // a marca da barra é um link para o início com nome acessível próprio
      await expect(page.locator('.top .logo')).toHaveAttribute('aria-label', 'SIGRASTRO, início');
      await expect(page.locator('.top .logo .wm')).toHaveText('SIGRASTRO');
      await expect(page.locator('.top .logo .wm .l')).toHaveText('SIG');
      // sem rodapé nem trilha textual do layout antigo
      await expect(page.locator('.page-footer, .crumb-top, .sidebar')).toHaveCount(0);
      await expect(page.locator('.dash-hero .eyebrow').first()).toHaveText('Vistas do Processo');
      await expect(page).toHaveTitle(titulo);
      expect(await textoVisivel(page)).not.toMatch(NOME_ANTIGO);
    });
  }
});
