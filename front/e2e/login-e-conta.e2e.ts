import { expect, test } from '@playwright/test';

import { OPERADOR, SENHA_DE_TESTE, simularApi } from './api-simulada';

/** L1, bloco 4: login sem demonstração, bloqueio por tentativas e troca da própria senha. API simulada, dados fictícios. */

test('o login mostra só usuário e senha: sem botões de entrada rápida e sem campos preenchidos', async ({ page }) => {
  await simularApi(page, { comSessao: false });
  await page.goto('/login');

  await expect(page.locator('#login-usuario')).toHaveValue('');
  await expect(page.locator('#login-senha')).toHaveValue('');
  await expect(page.getByRole('button')).toHaveCount(1);
  await expect(page.locator('body')).not.toContainText('demonstração');
  await expect(page.locator('body')).not.toContainText('autenticação real');
});

test('credencial errada mostra a mensagem única e limpa a senha', async ({ page }) => {
  const api = await simularApi(page, { comSessao: false });
  await page.goto('/login');

  await page.locator('#login-usuario').fill('alguem');
  await page.locator('#login-senha').fill('senha-errada-123');
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.locator('.login-card [role="alert"]')).toContainText('Usuário ou senha inválidos.');
  await expect(page.locator('#login-senha')).toHaveValue('');
  await expect(page).toHaveURL(/\/login/);
  expect(api.chamadas).toContain('/api/auth/login');
});

test('bloqueio temporário (429) mostra o tempo restante e não entra', async ({ page }) => {
  const api = await simularApi(page, { comSessao: false });
  api.estadoDoLogin = 'bloqueado';
  await page.goto('/login');

  await page.locator('#login-usuario').fill(OPERADOR.login);
  await page.locator('#login-senha').fill(SENHA_DE_TESTE);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page.locator('.login-card [role="alert"]')).toContainText('Muitas tentativas. Tente de novo em 14 minutos.');
  await expect(page).toHaveURL(/\/login/);
});

test('Minha conta: troca a própria senha, limpa os campos e não deixa o valor na página', async ({ page }) => {
  const api = await simularApi(page, { usuario: OPERADOR });
  await page.goto('/conta');

  await expect(page.getByRole('heading', { name: 'Minha conta' })).toBeVisible();
  await expect(page.locator('.top .nav a[aria-current="page"]')).toHaveText('Minha conta');
  await expect(page.locator('main')).toContainText('De 12 a 200 caracteres.');

  const nova = 'outra-senha-de-teste-456';
  await page.getByLabel('Senha atual').fill(SENHA_DE_TESTE);
  await page.getByLabel('Nova senha', { exact: true }).fill(nova);
  await page.getByLabel('Confirmação da nova senha').fill(nova);
  await page.getByRole('button', { name: 'Trocar senha' }).click();

  await expect(page.locator('.mc-sucesso')).toContainText('Senha alterada');
  await expect(page.getByLabel('Senha atual')).toHaveValue('');
  await expect(page.getByLabel('Nova senha', { exact: true })).toHaveValue('');
  expect(api.trocasDeSenha).toEqual([{ senhaAtual: SENHA_DE_TESTE, novaSenha: nova, confirmacao: nova }]);
  expect(await page.locator('body').innerText()).not.toContain(nova);
  expect(page.url()).not.toContain(nova);
});

test('Minha conta: senha atual errada aparece no campo e foca nele', async ({ page }) => {
  await simularApi(page, { usuario: OPERADOR });
  await page.goto('/conta');

  const nova = 'outra-senha-de-teste-456';
  await page.getByLabel('Senha atual').fill('senha-errada-123');
  await page.getByLabel('Nova senha', { exact: true }).fill(nova);
  await page.getByLabel('Confirmação da nova senha').fill(nova);
  await page.getByRole('button', { name: 'Trocar senha' }).click();

  await expect(page.locator('#mc-senhaAtual-erro')).toContainText('A senha atual não confere.');
  await expect(page.getByLabel('Senha atual')).toBeFocused();
});
