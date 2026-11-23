import { defineConfig } from '@playwright/test';

import base from './playwright.config';

/**
 * R2, bloco 15: gera as capturas das telas principais (1920 e 390 px) em `test-results/capturas/`, com a API simulada,
 * para comparar com o protótipo aprovado. Não é verificação: só fotografa. Rode com `npm run capturas`.
 * A pasta `test-results/` está no .gitignore (imagens não são versionadas) e o `npm run e2e` a limpa ao começar.
 */
export default defineConfig({
  ...base,
  testMatch: '**/capturas.captura.ts',
  outputDir: './test-results/capturas-execucao',
});
