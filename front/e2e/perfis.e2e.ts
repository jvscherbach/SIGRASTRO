import { Page, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, UsuarioSimulado, simularApi } from './api-simulada';

/** RN08: menu e rotas por perfil, decididos pelas `permissoes` de /auth/me. API simulada, dados fictícios. */
const PERFIS: { nome: string; usuario: UsuarioSimulado; menu: string[] }[] = [
  { nome: 'Operador', usuario: OPERADOR, menu: ['Consulta', 'Dossiês gerados', 'Auditoria', 'Documentos'] },
  { nome: 'Gestor', usuario: GESTOR, menu: ['Consulta', 'Dossiês gerados', 'Auditoria', 'Documentos'] },
  {
    nome: 'Administrador',
    usuario: ADMIN,
    menu: ['Consulta', 'Dossiês gerados', 'Auditoria', 'Documentos', 'Acessos', 'Configurações'],
  },
];

const ROTAS_RESTRITAS = [
  { rota: '/configuracoes', titulo: 'Configurações' },
  { rota: '/auditoria/acessos', titulo: 'Auditoria de acessos' },
];

const itensDoMenu = (page: Page) => page.locator('.sidebar .nav-item');

async function alturaDoQuadro(page: Page): Promise<number> {
  await page.locator('.dash-hero').waitFor();
  return page.evaluate(() => document.querySelector('.dash-hero')!.getBoundingClientRect().height);
}

for (const perfil of PERFIS) {
  test(`${perfil.nome} vê só os itens do seu perfil, sem cadeado`, async ({ page }) => {
    await simularApi(page, { usuario: perfil.usuario });
    await page.goto('/consulta');

    await expect(itensDoMenu(page)).toHaveText(perfil.menu);
    await expect(page.locator('.sidebar .nav-lock, .sidebar [aria-label="Restrito"]')).toHaveCount(0);
  });
}

for (const perfil of [PERFIS[0], PERFIS[1]]) {
  for (const restrita of ROTAS_RESTRITAS) {
    test(`${perfil.nome}: digitar ${restrita.rota} avisa e volta à Consulta, sem chamar o servidor`, async ({ page }) => {
      const api = await simularApi(page, { usuario: perfil.usuario });
      await page.goto('/consulta');
      await expect(itensDoMenu(page).first()).toBeVisible();
      const antes = api.chamadas.length;

      // navegação dentro da aplicação, como um link digitado sem recarregar a sessão
      await page.evaluate((rota) => {
        history.pushState({}, '', rota);
        dispatchEvent(new PopStateEvent('popstate'));
      }, restrita.rota);

      await expect(page).toHaveURL(/\/consulta$/);
      await expect(page.locator('.toast.erro')).toContainText('Seu perfil não tem permissão para acessar esta área.');
      await expect(page.getByRole('heading', { name: restrita.titulo })).toHaveCount(0);
      expect(api.chamadas.slice(antes)).toEqual([]);
    });

    test(`${perfil.nome}: abrir ${restrita.rota} direto no navegador também volta à Consulta`, async ({ page }) => {
      await simularApi(page, { usuario: perfil.usuario });
      await page.goto(restrita.rota);

      await expect(page).toHaveURL(/\/consulta$/);
      await expect(page.locator('.toast.erro')).toContainText('Seu perfil não tem permissão');
    });
  }
}

test('Administrador abre as telas restritas (Acessos e Configurações)', async ({ page }) => {
  await simularApi(page, { usuario: ADMIN });
  await page.goto('/auditoria/acessos');
  await expect(page).toHaveURL(/\/auditoria\/acessos$/);
  await expect(page.getByRole('heading', { name: 'Auditoria de acessos' })).toBeVisible();

  await page.goto('/configuracoes');
  await expect(page).toHaveURL(/\/configuracoes$/);
  await expect(page.getByRole('heading', { name: 'Configurações' })).toBeVisible();
});

test('trocar de usuário no login atualiza o menu sem recarregar a página', async ({ page }) => {
  await simularApi(page, { comSessao: false });
  await page.goto('/consulta');
  await expect(page).toHaveURL(/\/login/);
  await page.evaluate(() => ((window as unknown as { semRecarga: boolean }).semRecarga = true));

  await page.getByRole('button', { name: /Operador Demo/ }).click();
  await expect(itensDoMenu(page)).toHaveText(PERFIS[0].menu);

  await page.locator('.user-btn').click();
  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.getByRole('button', { name: /Admin Demo/ }).click();
  await expect(itensDoMenu(page)).toHaveText(PERFIS[2].menu);

  expect(await page.evaluate(() => (window as unknown as { semRecarga?: boolean }).semRecarga)).toBe(true);
});

test('sem token, qualquer tela volta ao login', async ({ page }) => {
  await simularApi(page, { comSessao: false });
  for (const rota of ['/consulta', '/dossies', '/configuracoes']) {
    await page.goto(rota);
    await expect(page).toHaveURL(/\/login/);
  }
});

for (const tela of [
  { largura: 1280, altura: 720 },
  { largura: 1920, altura: 1080 },
  { largura: 390, altura: 844 },
]) {
  test(`quadro do topo da Consulta tem a mesma altura nos três perfis em ${tela.largura}x${tela.altura} (0 px)`, async ({ page }) => {
    await page.setViewportSize({ width: tela.largura, height: tela.altura });
    const alturas: number[] = [];
    for (const perfil of PERFIS) {
      await page.unrouteAll({ behavior: 'ignoreErrors' });
      await simularApi(page, { usuario: perfil.usuario });
      await page.goto('/consulta');
      await expect(itensDoMenu(page)).toHaveText(perfil.menu);
      alturas.push(await alturaDoQuadro(page));
      if (perfil.usuario === OPERADOR) {
        // o aviso do Operador cabe inteiro no espaço reservado, sem corte
        const aviso = page.locator('.hint-perfil');
        await expect(aviso).toContainText('apenas processos e requerimentos em que você é o interessado');
        expect(await aviso.evaluate((el) => el.scrollHeight <= el.clientHeight)).toBe(true);
      }
    }
    expect(alturas[0]).toBeGreaterThan(0);
    expect(alturas.map((altura) => altura - alturas[0])).toEqual([0, 0, 0]);
  });
}
