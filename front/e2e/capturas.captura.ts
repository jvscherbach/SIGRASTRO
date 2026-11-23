import { join } from 'node:path';

import { test } from '@playwright/test';

import { TELAS_PRINCIPAIS, abrirTela } from './simulacao-completa';

/**
 * R2, bloco 15: capturas de página inteira de cada tela principal em 1920 e 390 px, com dados fictícios da API
 * simulada. Saem em `front/test-results/capturas/<tela>-<largura>.png` (fora do Git). Só roda pela configuração
 * própria (`npm run capturas`); o `npm run e2e` não a inclui.
 */
const PASTA = join(__dirname, '..', 'test-results', 'capturas');

for (const largura of [1920, 390]) {
  for (const tela of TELAS_PRINCIPAIS) {
    test(`captura: ${tela.nome} em ${largura}px`, async ({ page }) => {
      await page.setViewportSize({ width: largura, height: largura === 390 ? 844 : 1080 });
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await abrirTela(page, tela);
      await page.screenshot({ path: join(PASTA, `${tela.nome}-${largura}.png`), fullPage: true, animations: 'disabled' });
    });
  }
}
