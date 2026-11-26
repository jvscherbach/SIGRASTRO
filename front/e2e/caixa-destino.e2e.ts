import { expect, test } from '@playwright/test';

import { ADMIN, ANALISTA, GESTOR, OPERADOR, simularApi } from './api-simulada';

/** S11, bloco 2: o Analista cai na caixa depois do login; os demais perfis seguem na Consulta. API simulada. */
async function entrar(page: import('@playwright/test').Page, login: string): Promise<void> {
  await page.goto('/login');
  await page.locator('#login-usuario').fill(login);
  await page.locator('#login-senha').fill('senha-de-teste');
  await page.getByRole('button', { name: 'Entrar' }).click();
}

test('Analista: o login leva à Minha caixa e o menu traz o item', async ({ page }) => {
  await simularApi(page, { comSessao: false });
  await page.route('**/api/caixa**', (route) =>
    route.fulfill({
      json: {
        estado: 'PRONTO', geradoEm: '2028-06-15T12:30:00-04:00', falhouEm: null, incompleta: false, semVinculo: false,
        mensagem: null, novos: 0, resumo: { total: 0, comInteressado: 0, porEtapa: [] }, linhas: [],
      },
    }),
  );
  await entrar(page, ANALISTA.login);

  await expect(page).toHaveURL(/\/caixa$/);
  await expect(page.getByRole('heading', { name: 'Minha caixa' })).toBeVisible();
  await expect(page.locator('.top .nav a')).toHaveText(['Minha caixa', 'Consulta', 'Dossiês gerados']);
});

test('Analista: abrir a raiz com sessão também cai na caixa', async ({ page }) => {
  await simularApi(page, { usuario: ANALISTA });
  await page.goto('/');

  await expect(page).toHaveURL(/\/caixa$/);
});

for (const perfil of [OPERADOR, GESTOR, ADMIN]) {
  test(`${perfil.perfilDescricao}: o login segue na Consulta e não vê a caixa`, async ({ page }) => {
    await simularApi(page, { comSessao: false });
    await entrar(page, perfil.login);

    await expect(page).toHaveURL(/\/consulta$/);
    await expect(page.locator('.top .nav a', { hasText: 'Minha caixa' })).toHaveCount(0);
  });

  test(`${perfil.perfilDescricao}: digitar /caixa avisa e volta à Consulta`, async ({ page }) => {
    await simularApi(page, { usuario: perfil });
    await page.goto('/caixa');

    await expect(page).toHaveURL(/\/consulta$/);
    await expect(page.locator('.toast.erro')).toContainText('Seu perfil não tem permissão para acessar esta área.');
  });
}
