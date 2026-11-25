import { Page, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR } from './api-simulada';
import { TELAS_PRINCIPAIS, abrirTela, simularTudo } from './simulacao-completa';

/**
 * R2, bloco 15: layout e tema do SIGRASTRO nas telas principais (login, consulta, detalhe, dossiês, auditoria e
 * configurações). API simulada com sucesso em tudo (`simulacao-completa.ts`), dados fictícios, sem back-end.
 */
const LARGURAS = [390, 768, 1280, 1920];

const altura = (largura: number) => (largura <= 390 ? 844 : largura <= 768 ? 1024 : largura <= 1280 ? 800 : 1080);

async function rolagemHorizontal(page: Page): Promise<{ pagina: number; tela: number }> {
  return page.evaluate(() => ({ pagina: document.documentElement.scrollWidth, tela: document.documentElement.clientWidth }));
}

test.describe('1. sem rolagem horizontal da página', () => {
  for (const largura of LARGURAS) {
    for (const tela of TELAS_PRINCIPAIS) {
      test(`${tela.nome} em ${largura}px`, async ({ page }) => {
        await page.setViewportSize({ width: largura, height: altura(largura) });
        await abrirTela(page, tela);

        const medida = await rolagemHorizontal(page);
        expect(medida.tela).toBe(largura);
        expect(medida.pagina, `a página de ${tela.nome} rola na horizontal em ${largura}px`).toBeLessThanOrEqual(medida.tela);
      });
    }
  }
});

test.describe('2. login', () => {
  for (const largura of [1920, 1280]) {
    test(`caixa centralizada, fundo de uma cor, escudo e nome em ${largura}px`, async ({ page }) => {
      await page.setViewportSize({ width: largura, height: altura(largura) });
      await abrirTela(page, TELAS_PRINCIPAIS[0]);

      const caixa = (await page.locator('.login-card').boundingBox())!;
      expect(Math.abs(caixa.x + caixa.width / 2 - largura / 2)).toBeLessThanOrEqual(2);
      await expect(page.locator('.login')).toHaveCSS('background-color', 'rgb(18, 48, 43)');
      await expect(page.locator('.login')).toHaveCSS('background-image', 'none');

      const escudo = page.locator('.login .marca img');
      await expect(escudo).toBeVisible();
      await expect(escudo).toHaveAttribute('src', /sigrastro-escudo\.svg$/);
      expect(await escudo.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
      await expect(page.locator('.login .marca .wm')).toHaveText('SIGRASTRO');
    });
  }
});

test.describe('3. navegação', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  const nav = (page: Page) => page.locator('.top nav.nav a');

  for (const [rota, ativo, usuario] of [
    ['/consulta', 'Consulta', GESTOR],
    ['/consulta/requerimentos/101', 'Consulta', GESTOR],
    ['/dossies', 'Dossiês gerados', GESTOR],
    ['/auditoria/documentos', 'Auditoria', GESTOR],
    ['/auditoria/acessos', 'Auditoria', ADMIN],
    ['/configuracoes', 'Configurações', ADMIN],
  ] as const) {
    test(`${rota}: só "${ativo}" tem aria-current="page"`, async ({ page }) => {
      await simularTudo(page, { usuario });
      await page.goto(rota);
      await expect(nav(page).first()).toBeVisible();

      await expect(page.locator('.top nav.nav a[aria-current="page"]')).toHaveCount(1);
      await expect(page.locator('.top nav.nav a[aria-current="page"]')).toHaveText(ativo);
      await expect(nav(page).filter({ hasNotText: ativo })).toHaveCount((await nav(page).count()) - 1);
      for (const outro of await nav(page).filter({ hasNotText: ativo }).all()) {
        expect(await outro.getAttribute('aria-current')).toBeNull();
      }
    });
  }

  test('Auditoria é um item único que leva a /auditoria/documentos', async ({ page }) => {
    await simularTudo(page, { usuario: ADMIN });
    await page.goto('/consulta');
    await expect(nav(page)).toHaveText(['Consulta', 'Dossiês gerados', 'Painel', 'Auditoria', 'Configurações']);
    await expect(nav(page).filter({ hasText: /Auditoria|Acessos/ })).toHaveCount(1);

    await nav(page).filter({ hasText: 'Auditoria' }).click();
    await expect(page).toHaveURL(/\/auditoria\/documentos$/);
    await expect(page.getByRole('link', { name: 'Acessos', exact: true })).toBeVisible();
  });

  test('Configurações só aparece com a permissão (Administrador sim, Gestor não)', async ({ page }) => {
    await simularTudo(page, { usuario: GESTOR });
    await page.goto('/consulta');
    await expect(nav(page)).toHaveText(['Consulta', 'Dossiês gerados', 'Painel', 'Auditoria']);
    await expect(nav(page).filter({ hasText: 'Configurações' })).toHaveCount(0);
  });

  test('Operador não vê Configurações nem a aba Acessos', async ({ page }) => {
    await simularTudo(page, { usuario: OPERADOR });
    await page.goto('/consulta');
    await expect(nav(page)).toHaveText(['Consulta', 'Dossiês gerados', 'Auditoria']);
    await expect(nav(page).filter({ hasText: 'Configurações' })).toHaveCount(0);

    await nav(page).filter({ hasText: 'Auditoria' }).click();
    await expect(page).toHaveURL(/\/auditoria\/documentos$/);
    await page.locator('main table tbody tr').first().waitFor();
    await expect(page.getByRole('link', { name: 'Documentos', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Acessos', exact: true })).toHaveCount(0);
    await expect(page.getByText('Acessos', { exact: true })).toHaveCount(0);
  });

  test('em 390px a navegação mostra só os ícones, com nome acessível, e some o nome e o perfil', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await simularTudo(page, { usuario: ADMIN });
    await page.goto('/consulta');
    await expect(nav(page).first()).toBeVisible();

    for (const item of await nav(page).all()) {
      // o rótulo fica só para leitor de tela (oculto visualmente); o link segue com nome acessível
      const rotulo = (await item.locator(':scope > span').boundingBox())!;
      expect(rotulo.width).toBeLessThanOrEqual(1);
      await expect(item).toHaveAccessibleName(/^(Consulta|Dossiês gerados|Painel|Auditoria|Configurações)$/);
    }
    await expect(page.locator('.top .perfil-chip')).toBeHidden();
    await expect(page.locator('.top .who-nome')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Sair' })).toBeVisible();
  });
});

test.describe('4. tema único: Cerrado', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  test('Configurações não tem aba Aparência nem seletor de tema ou bioma', async ({ page }) => {
    await abrirTela(page, TELAS_PRINCIPAIS.find((t) => t.nome === 'configuracoes')!);

    const abas = page.getByRole('tab');
    await expect(abas.first()).toBeVisible();
    expect(await abas.allInnerTexts()).not.toContain('Aparência');
    await expect(page.getByText(/apar[eê]ncia|bioma|pantanal|amaz[oô]nia/i)).toHaveCount(0);
    await expect(page.getByRole('combobox', { name: /tema|bioma/i })).toHaveCount(0);
    await expect(page.getByRole('radio', { name: /cerrado|pantanal|amaz[oô]nia/i })).toHaveCount(0);
    await expect(page.locator('[data-bioma], [data-tema]')).toHaveCount(0);
  });

  for (const tela of TELAS_PRINCIPAIS.filter((t) => t.usuario)) {
    test(`${tela.nome}: a faixa do topo mostra a paisagem do Cerrado, decorativa`, async ({ page }) => {
      await abrirTela(page, tela);

      const paisagem = page.locator('.dash-hero img.dash-scene').first();
      await expect(paisagem).toBeVisible();
      await expect(paisagem).toHaveAttribute('src', /cerrado\.svg$/);
      await expect(paisagem).toHaveAttribute('alt', '');
      expect(await paisagem.evaluate((img: HTMLImageElement) => img.complete && img.naturalWidth > 0)).toBe(true);
      await expect(page.locator('html[data-bioma], body[data-bioma]')).toHaveCount(0);
    });
  }
});

test.describe('5 e 6. sem erro de console, sem marca antiga, sem requisição a terceiros', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  const MARCA_ANTIGA = [/SEMA\s*·\s*Mato Grosso/i, /bras[aã]o/i, /consulta ao SIGA Licenciamento/i];

  for (const tela of TELAS_PRINCIPAIS) {
    test(tela.nome, async ({ page }) => {
      const sim = await abrirTela(page, tela);

      expect(sim.naoSimuladas, 'pedidos à API sem simulação (o teste precisa de resposta para todos)').toEqual([]);
      expect(sim.errosDoConsole).toEqual([]);
      expect(sim.externas.filter((origem) => /fonts\.(googleapis|gstatic)\.com/.test(origem))).toEqual([]);
      expect(sim.externas).toEqual([]);

      const texto = await page.evaluate(() => {
        const rotulos = Array.from(document.querySelectorAll('[aria-label], [title], [alt]')).flatMap((e) =>
          ['aria-label', 'title', 'alt'].map((a) => e.getAttribute(a) ?? ''),
        );
        return [document.title, document.body.innerText, ...rotulos].join('\n');
      });
      for (const proibido of MARCA_ANTIGA) expect(texto).not.toMatch(proibido);

      const imagens = await page.evaluate(() => Array.from(document.images, (img) => img.getAttribute('src') ?? ''));
      expect(imagens.filter((src) => /bras[aã]o|logo-sema/i.test(src))).toEqual([]);
      const css = await page.evaluate(() => document.documentElement.outerHTML);
      expect(css).not.toContain('fonts.googleapis.com');
    });
  }
});
