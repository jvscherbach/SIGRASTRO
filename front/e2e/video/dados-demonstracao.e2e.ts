import { expect, test } from '@playwright/test';

import {
  CNPJ_DA_ASSOCIACAO,
  CNPJ_DO_OPERADOR,
  NOMES_FICTICIOS,
  OPERADOR_DA_DEMONSTRACAO,
  conjuntoDaDemonstracao,
  simularDemonstracao,
} from './dados-demonstracao';
import { PAGINAS_DO_PDF_DE_DEMONSTRACAO, gerarPdfDemonstracao } from './pdf-demonstracao';

/**
 * V1, bloco 2: varredura dos dados fictícios da demonstração (nenhum CPF, CNPJ, e-mail, token ou nome fora da lista) e
 * conferência de que as telas da demonstração abrem com a simulação nova. Sem navegador na varredura; sem sistema externo.
 */
function coletarChaves(valor: unknown, chaves: string[], achados: string[] = []): string[] {
  if (Array.isArray(valor)) valor.forEach((v) => coletarChaves(v, chaves, achados));
  else if (valor && typeof valor === 'object') {
    for (const [chave, v] of Object.entries(valor)) {
      if (chaves.includes(chave) && typeof v === 'string') achados.push(v);
      coletarChaves(v, chaves, achados);
    }
  }
  return achados;
}

for (const [rotulo, ocultar] of [['Gestor', false] as const, ['Operador', true] as const]) {
  test(`varredura: o conjunto da demonstração (${rotulo}) só tem dado fictício`, () => {
    const conjunto = conjuntoDaDemonstracao(ocultar);
    const texto = JSON.stringify(conjunto);

    // os dois únicos CNPJs permitidos saem antes da busca por documentos
    const semPermitidos = texto.split(CNPJ_DA_ASSOCIACAO).join('').split(CNPJ_DO_OPERADOR).join('');
    expect(semPermitidos).not.toMatch(/\d{2}\.?\d{3}\.?\d{3}\/?\d{4}-?\d{2}/); // CNPJ
    expect(semPermitidos).not.toMatch(/(?<!\d)\d{3}\.?\d{3}\.?\d{3}-?\d{2}(?!\d)/); // CPF
    expect(semPermitidos).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
    expect(texto).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/); // e-mail
    expect(texto).not.toMatch(/token|senha|password|bearer|secret/i);
    expect(texto).not.toMatch(/CPF_DE_TESTE/);

    // todo nome (interessado, servidor, participante, carga) está na lista fictícia
    const nomes = coletarChaves(conjunto, ['nome']);
    expect(nomes.length).toBeGreaterThan(0);
    for (const nome of nomes) expect(NOMES_FICTICIOS, `nome fora da lista: ${nome}`).toContain(nome);
    const rotulosDeCarga = (conjunto as { painel: { cargaPorResponsavel: { rotulo: string }[] } }).painel.cargaPorResponsavel.map((c) => c.rotulo);
    for (const r of rotulosDeCarga) expect(NOMES_FICTICIOS, `servidor fora da lista: ${r}`).toContain(r);

    // números de processo no formato N/AAAA, na faixa fictícia 9101 a 9104
    const processos = coletarChaves(conjunto, ['numeroProcesso']);
    for (const numero of processos) expect(numero).toMatch(/^910[1-4]\/2026$/);
  });
}

test('varredura: o Operador não recebe nenhum nome de servidor', () => {
  const texto = JSON.stringify((conjuntoDaDemonstracao(true) as { processos: object[] }).processos);
  expect(texto).not.toMatch(/Analista Exemplo|Conferente Exemplo|Revisor Exemplo/);
});

test('o painel tem 12 meses com protocolados acima dos concluídos, 5 a 8 servidores e nenhuma etapa não mapeada', () => {
  const { painel } = conjuntoDaDemonstracao(false) as {
    painel: { serie: { mes: number; protocolados: number; concluidos: number }[]; cargaPorResponsavel: unknown[]; etapaNaoMapeada: number; filaPorEtapa: unknown[] };
  };
  expect(painel.serie).toHaveLength(12);
  for (const m of painel.serie.filter((x) => x.protocolados > 0)) expect(m.protocolados).toBeGreaterThan(m.concluidos);
  expect(painel.cargaPorResponsavel.length).toBeGreaterThanOrEqual(5);
  expect(painel.cargaPorResponsavel.length).toBeLessThanOrEqual(8);
  expect(painel.filaPorEtapa.length).toBeGreaterThanOrEqual(4);
  expect(painel.etapaNaoMapeada).toBe(0);
});

test('o PDF fictício é válido, tem 4 páginas, uma tabela e nenhum documento real', () => {
  const pdf = gerarPdfDemonstracao();
  const bruto = pdf.toString('latin1');
  expect(bruto.startsWith('%PDF-1.4')).toBe(true);
  expect(bruto.trimEnd().endsWith('%%EOF')).toBe(true);
  expect((bruto.match(/\/Type \/Page /g) ?? []).length).toBe(PAGINAS_DO_PDF_DE_DEMONSTRACAO);
  expect(bruto).toContain('Planilha de acompanhamento \\(exemplo\\)');
  // o deslocamento do xref confere
  const inicio = Number(/startxref\n(\d+)\n/.exec(bruto)?.[1]);
  expect(bruto.slice(inicio, inicio + 4)).toBe('xref');
  expect(bruto).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  expect(bruto).not.toMatch(/[\w.+-]+@[\w-]+\.[\w.]+/);
});

test.describe('telas da demonstração (Gestor)', () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test('painel com três gráficos, busca por 9101/2026 e abertura do processo concluído', async ({ page }) => {
    const sim = await simularDemonstracao(page);
    await page.goto('/painel');
    await expect(page.locator('[role="img"]')).toHaveCount(3);

    await page.goto('/consulta');
    await page.getByRole('button', { name: 'Processo', exact: true }).click();
    await page.locator('#consulta-numero').fill('9101/2026');
    await page.keyboard.press('Enter');
    // resultado único: a consulta já abre o processo
    await expect(page).toHaveURL(/\/consulta\/requerimentos\/9101$/);
    await expect(page.locator('app-onde-esta-agora .onde-etapa')).toHaveText('Concluído');
    expect(sim.naoSimuladas).toEqual([]);
  });

  test('9102 mostra os três analistas; 9103 mostra a taxa vencida', async ({ page }) => {
    await simularDemonstracao(page);
    await page.goto('/consulta/requerimentos/9102');
    await expect(page.locator('app-onde-esta-agora')).toContainText('2 de 3 concluídos');
    await expect(page.locator('app-onde-esta-agora')).toContainText('Analista Exemplo 03');

    await page.goto('/consulta/requerimentos/9103');
    await expect(page.locator('app-onde-esta-agora')).toContainText('Taxa vencida em 05/11/2026');
  });

  test('o dossiê é gerado e o PDF fictício abre na pré-visualização', async ({ page }) => {
    await simularDemonstracao(page);
    await page.goto('/consulta/requerimentos/9101');
    await page.getByRole('button', { name: 'Gerar dossiê' }).click();
    await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto', { timeout: 15_000 });
    await page.getByRole('button', { name: 'Pré-visualizar' }).click();
    await expect(page.locator('.previa iframe')).toBeVisible();
  });
});

test.describe('telas da demonstração (Operador)', () => {
  test.use({ viewport: { width: 1920, height: 1080 } });

  test('vê só o 9104 e não aparece o nome do responsável, só o papel', async ({ page }) => {
    await simularDemonstracao(page, { usuario: OPERADOR_DA_DEMONSTRACAO });
    await page.goto('/consulta');
    await page.getByRole('button', { name: 'Processo', exact: true }).click();
    await page.locator('#consulta-numero').fill('9101/2026');
    await page.keyboard.press('Enter');
    await expect(page.locator('main table tbody tr')).toHaveCount(0);

    await page.goto('/consulta/requerimentos/9104');
    await expect(page.locator('app-onde-esta-agora')).toContainText('Analista');
    await expect(page.locator('body')).not.toContainText('Analista Exemplo');
  });
});
