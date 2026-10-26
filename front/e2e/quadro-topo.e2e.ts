import { Page, expect, test } from '@playwright/test';

import { EstadoDaBusca, simularApi } from './api-simulada';

/**
 * A1: o quadro do topo tem altura estável em todos os estados da busca. Mede a altura renderizada do quadro e o
 * tamanho da ilustração (se a altura muda, a ilustração muda de escala) e exige diferença de 0 px em relação ao
 * estado inicial.
 */
const TELAS = [
  { nome: '1280x720', largura: 1280, altura: 720 },
  { nome: '1920x1080', largura: 1920, altura: 1080 },
  { nome: '390x844 (celular)', largura: 390, altura: 844 },
];

interface Medida {
  quadro: number;
  ilustracao: { largura: number; altura: number };
}

async function medir(page: Page): Promise<Medida> {
  return page.evaluate(() => {
    const quadro = document.querySelector('.dash-hero')!.getBoundingClientRect();
    const cena = document.querySelector('.dash-scene svg')!.getBoundingClientRect();
    return { quadro: quadro.height, ilustracao: { largura: cena.width, altura: cena.height } };
  });
}

for (const tela of TELAS) {
  test.describe(`quadro do topo — ${tela.nome}`, () => {
    test.use({ viewport: { width: tela.largura, height: tela.altura } });

    async function abrir(page: Page) {
      const api = await simularApi(page);
      await page.goto('/consulta');
      await page.locator('.dash-hero').waitFor();
      await page.locator('.dash-scene svg').waitFor();
      return api;
    }

    test('estado inicial mede o quadro e a ilustração', async ({ page }) => {
      await abrir(page);
      const inicial = await medir(page);

      expect(inicial.quadro).toBeGreaterThan(0);
      // a ilustração preenche o quadro: o SVG tem a altura do quadro
      expect(Math.abs(inicial.ilustracao.altura - inicial.quadro)).toBeLessThan(3); // bordas de 1px do quadro
    });

    test('critério ausente (validação local): altura igual à inicial', async ({ page }) => {
      await abrir(page);
      const inicial = await medir(page);

      await page.getByRole('button', { name: 'Consultar' }).click();
      await expect(page.locator('.search-feedback .alert')).toContainText('Informe ao menos um critério');

      expect(await medir(page)).toEqual(inicial);
    });

    const estados: { rotulo: string; estado: EstadoDaBusca; texto: RegExp }[] = [
      { rotulo: '400 com mensagem enorme (cortada em duas linhas)', estado: '400', texto: /palavra0/ },
      { rotulo: '403', estado: '403', texto: /não tem você como interessado/ },
      { rotulo: '404', estado: '404', texto: /não encontrado/ },
      { rotulo: '503', estado: '503', texto: /indisponível/ },
      { rotulo: 'erro de rede', estado: 'rede', texto: /conectar ao servidor/ },
    ];
    for (const { rotulo, estado, texto } of estados) {
      test(`${rotulo}: altura do quadro e escala da ilustração iguais ao estado inicial`, async ({ page }) => {
        const api = await abrir(page);
        const inicial = await medir(page);
        api.estadoDaBusca = estado;

        await page.locator('#consulta-numero').fill('3331/2025');
        await page.getByRole('button', { name: 'Consultar' }).click();
        await expect(page.locator('.search-feedback .alert')).toContainText(texto);

        expect(await medir(page)).toEqual(inicial);
      });
    }

    test('carregando: altura igual à inicial; ao limpar, volta ao mesmo valor', async ({ page }) => {
      const api = await abrir(page);
      const inicial = await medir(page);
      api.estadoDaBusca = 'lento';

      await page.locator('#consulta-numero').fill('3331/2025');
      await page.getByRole('button', { name: 'Consultar' }).click();
      await expect(page.getByRole('button', { name: 'Consultar' })).toBeDisabled();
      expect(await medir(page)).toEqual(inicial);

      await expect(page.getByRole('button', { name: 'Consultar' })).toBeEnabled();
      api.estadoDaBusca = '404';
      await page.getByRole('button', { name: 'Consultar' }).click();
      await expect(page.locator('.search-feedback .alert')).toBeVisible();
      await page.getByRole('button', { name: 'Limpar' }).click();
      expect(await medir(page)).toEqual(inicial);
    });

  });
}
