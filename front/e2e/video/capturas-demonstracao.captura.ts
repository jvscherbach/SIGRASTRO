import { join } from 'node:path';

import { Page, test } from '@playwright/test';

import { GESTOR_DA_DEMONSTRACAO, OPERADOR_DA_DEMONSTRACAO, RELOGIO_DA_DEMONSTRACAO, simularDemonstracao } from './dados-demonstracao';

/**
 * V1, bloco 3: uma captura 1920x1080 por momento do roteiro, na ordem das cenas, com os dados fictícios da demonstração.
 * Saem em `front/test-results/video/capturas/NN-cena.png` (fora do Git). Só fotografa; não verifica nada.
 */
const PASTA = join(__dirname, '..', '..', 'test-results', 'video', 'capturas');

test.use({ viewport: { width: 1920, height: 1080 } });

async function preparar(page: Page, operador = false): Promise<void> {
  await page.clock.setFixedTime(new Date(RELOGIO_DA_DEMONSTRACAO));
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await simularDemonstracao(page, { usuario: operador ? OPERADOR_DA_DEMONSTRACAO : GESTOR_DA_DEMONSTRACAO });
}

async function fotografar(page: Page, nome: string, paginaInteira = false): Promise<void> {
  await page.waitForLoadState('networkidle', { timeout: 3000 }).catch(() => undefined); // o acompanhamento do dossiê mantém a rede ocupada
  await page.screenshot({ path: join(PASTA, `${nome}.png`), fullPage: paginaInteira, animations: 'disabled' });
}

async function abrirDetalhe(page: Page, id: number): Promise<void> {
  await page.goto(`/consulta/requerimentos/${id}`);
  await page.locator('app-onde-esta-agora').waitFor();
  await page.locator('app-tramitacao .tram-lista > li').first().waitFor();
  await page.locator('app-financeiro').waitFor();
  await page.waitForLoadState('networkidle');
}

test('01 abertura, 02 painel e 03 consulta (Gestor)', async ({ page }) => {
  await preparar(page);
  await page.goto('/consulta');
  await page.locator('#consulta-numero').waitFor();
  await fotografar(page, '01-abertura');

  await page.goto('/painel');
  await page.locator('main canvas, main svg, main figure, main [role="img"]').first().waitFor();
  await fotografar(page, '02-painel');
  await fotografar(page, '02-painel-completa', true);

  await page.goto('/consulta');
  await page.getByRole('button', { name: 'Processo', exact: true }).click();
  await page.locator('#consulta-numero').fill('9101/2026');
  await fotografar(page, '03a-consulta-digitada');
  await page.getByRole('button', { name: 'Consultar' }).click();
  await page.locator('tbody tr').first().waitFor();
  await fotografar(page, '03b-consulta-resultado');
});

test('04 detalhe: concluído e vários analistas (Gestor)', async ({ page }) => {
  await preparar(page);
  await abrirDetalhe(page, 9101);
  await fotografar(page, '04a-detalhe-concluido');
  await fotografar(page, '04a-detalhe-concluido-completa', true);
  await abrirDetalhe(page, 9102);
  await fotografar(page, '04b-detalhe-varios-analistas');
  await fotografar(page, '04b-detalhe-varios-analistas-completa', true);
});

test('05 taxa vencida e 06 dossiê (Gestor)', async ({ page }) => {
  await preparar(page);
  await abrirDetalhe(page, 9103);
  await page.locator('app-financeiro').scrollIntoViewIfNeeded();
  await fotografar(page, '05-taxa-vencida');

  await abrirDetalhe(page, 9101);
  await page.locator('app-documentos-secao').scrollIntoViewIfNeeded();
  await fotografar(page, '06a-dossie-lista-gestor');
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await page.getByRole('dialog', { name: /Dossiê pronto/ }).waitFor({ timeout: 15_000 });
  await fotografar(page, '06b-dossie-pronto-gestor');
  await page.getByRole('button', { name: 'Pré-visualizar' }).click();
  await page.locator('.previa iframe').waitFor();
  await page.waitForTimeout(2500); // o visualizador de PDF do Chromium desenha depois do iframe
  await fotografar(page, '06c-dossie-pdf-aberto');
});

test('04d e 07 detalhe do 9104: Gestor e Interessado lado a lado', async ({ browser }) => {
  const gestor = await browser.newPage({ viewport: { width: 1920, height: 1080 }, locale: 'pt-BR', timezoneId: 'America/Cuiaba' });
  await preparar(gestor);
  await abrirDetalhe(gestor, 9104);
  await fotografar(gestor, '04d-detalhe-9104-visto-pelo-gestor');
  await fotografar(gestor, '04d-detalhe-9104-visto-pelo-gestor-completa', true);
  await gestor.close();

  const operador = await browser.newPage({ viewport: { width: 1920, height: 1080 }, locale: 'pt-BR', timezoneId: 'America/Cuiaba' });
  await preparar(operador, true);
  await operador.goto('/consulta');
  await operador.locator('#consulta-numero').waitFor();
  await fotografar(operador, '07a-interessado-consulta');
  await abrirDetalhe(operador, 9104);
  await fotografar(operador, '07b-interessado-detalhe');
  await fotografar(operador, '07b-interessado-detalhe-completa', true);
  await operador.locator('app-documentos-secao').scrollIntoViewIfNeeded();
  await operador.getByRole('button', { name: 'Gerar dossiê' }).click();
  await operador.getByRole('dialog', { name: /Dossiê pronto/ }).waitFor({ timeout: 15_000 });
  await fotografar(operador, '07c-interessado-dossie-pronto');
  await operador.close();
});
