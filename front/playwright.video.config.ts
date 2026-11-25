import { defineConfig } from '@playwright/test';

import base from './playwright.config';

/**
 * V1, bloco 3: fotografa as cenas da demonstração (1920x1080) em `test-results/video/capturas/`, com a API simulada da
 * demonstração. Só fotografa; saídas fora do Git. Rode por `node tools/video-demo/capturar.mjs` ou `npm run video:capturar`.
 */
export default defineConfig({
  ...base,
  testMatch: '**/capturas-demonstracao.captura.ts',
  outputDir: './test-results/video/execucao',
  use: { ...base.use, viewport: { width: 1920, height: 1080 } },
});
