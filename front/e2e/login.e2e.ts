import { expect, test } from '@playwright/test';

import { simularApi } from './api-simulada';

/** R2, bloco 8: a caixa de login fica centralizada na janela e o fundo é de uma cor só. */
for (const tela of [
  { largura: 1920, altura: 1080 },
  { largura: 1280, altura: 720 },
]) {
  test(`login: caixa centralizada em ${tela.largura}px`, async ({ page }) => {
    await page.setViewportSize({ width: tela.largura, height: tela.altura });
    await simularApi(page, { comSessao: false });
    await page.goto('/login');
    const caixa = page.locator('.login-card');
    await caixa.waitFor();

    const r = (await caixa.boundingBox())!;
    expect(Math.abs(r.x + r.width / 2 - tela.largura / 2)).toBeLessThanOrEqual(2);
    expect(r.width).toBeLessThanOrEqual(440);
    await expect(page.locator('.login')).toHaveCSS('background-color', 'rgb(18, 48, 43)');
    await expect(page.locator('.login .marca img')).toBeVisible();
  });
}

test('login: em 390px não há rolagem horizontal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await simularApi(page, { comSessao: false });
  await page.goto('/login');
  await page.locator('.login-card').waitFor();
  const estoura = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
  expect(estoura).toBe(false);
});
