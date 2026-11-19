import { expect, test } from '@playwright/test';

import { simularApi } from './api-simulada';

/**
 * R1, bloco 6 (D75): quem já estava logado com a chave antiga `vistas.token` continua logado depois da troca para
 * `rastro.token`, e a chave antiga some. A sessão de teste é fictícia.
 */
test('sessão guardada na chave antiga vistas.token continua valendo e migra para rastro.token', async ({ page }) => {
  const api = await simularApi(page, { comSessao: false });
  await page.addInitScript(() => {
    // Só na primeira carga: depois da migração a chave antiga não pode voltar.
    if (!sessionStorage.getItem('migracao.preparada')) {
      sessionStorage.setItem('migracao.preparada', 'sim');
      sessionStorage.setItem('vistas.token', 'token-de-teste');
    }
  });

  await page.goto('/consulta');

  await expect(page).toHaveURL(/\/consulta/);
  expect(api.chamadas).toContain('/api/auth/me');
  const chaves = await page.evaluate(() => ({
    nova: sessionStorage.getItem('rastro.token'),
    antiga: sessionStorage.getItem('vistas.token'),
  }));
  expect(chaves).toEqual({ nova: 'token-de-teste', antiga: null });
});
