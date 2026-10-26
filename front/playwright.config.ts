import { defineConfig } from '@playwright/test';

/**
 * Testes e2e do front (só desenvolvimento): a API é simulada por page.route, então nada fala com o back-end, o SIGA
 * ou qualquer sistema externo. O Angular sobe em modo de desenvolvimento na porta 4300.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4300',
    browserName: 'chromium',
    locale: 'pt-BR',
    timezoneId: 'America/Cuiaba',
  },
  webServer: {
    command: 'npx ng serve --port 4300 --host localhost',
    url: 'http://localhost:4300',
    reuseExistingServer: true,
    timeout: 180_000,
  },
});
