import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

/**
 * S9, bloco 8: seção "Taxas e pagamentos" no detalhe do requerimento. API simulada (só desenvolvimento), dados fictícios no
 * formato de `GET /api/requerimentos/{id}/financeiro` (docs/07), com campos do Administrador misturados para provar que a
 * tela não os mostra.
 */
function lancamento(ordem: number, sobre: object = {}) {
  return {
    ordem,
    rotulo: `${ordem}ª taxa`,
    situacao: 'PAGA',
    vencida: false,
    vencidaPorData: false,
    inconsistente: false,
    emitidaEm: '2026-03-04',
    venceEm: '2026-03-14',
    pagaEm: '2026-03-07',
    numeroDar: 'FICT-0001',
    valor: 980,
    desconto: 0,
    upf: 28,
    // campos do Administrador misturados: a tela os ignora
    idTecnico: 9001,
    statusProcessado: 'P',
    inconsistencias: [],
    ...sobre,
  };
}

function financeiro(sobre: object = {}) {
  return {
    idRequerimento: 101,
    numeroRequerimento: '3331/2025',
    numeroProcesso: '2825/2025',
    estado: 'ATIVO',
    resumo: 'QUITADO',
    mostraValores: true,
    lancamentos: [lancamento(1)],
    isencoes: [],
    avisos: [],
    ignoradosPorMotivo: { FALSO_SEM_DAR: 1, RUIDO_ISENTO_AGUARDANDO: 0 },
    ...sobre,
  };
}

const AVISO_VENCIDA = {
  codigo: 'TAXA_VENCIDA',
  texto: 'Taxa vencida em 19/03/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.',
  gravidade: 'ATENCAO',
};

const FIN01 = financeiro();
const FIN03 = financeiro({
  resumo: 'VENCIDO',
  lancamentos: [lancamento(1, { situacao: 'AGUARDANDO_PAGAMENTO', pagaEm: null, venceEm: '2026-03-19', vencida: true, vencidaPorData: true, numeroDar: 'FICT-0003' })],
  avisos: [AVISO_VENCIDA],
});
const FIN05 = financeiro({
  resumo: 'ISENTO',
  lancamentos: [lancamento(1, { rotulo: null, situacao: 'ISENTA', numeroDar: null, venceEm: null, pagaEm: null, valor: null, desconto: null })],
  isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: '2026-03-04', dataAConfirmar: false }],
});
const FIN06 = financeiro({
  resumo: 'ISENTO',
  lancamentos: [lancamento(1, { rotulo: null, situacao: 'ISENTA', numeroDar: null, venceEm: null, pagaEm: null, valor: null, desconto: null })],
  isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: null, dataAConfirmar: true }],
});
const FIN11 = financeiro({
  resumo: 'EM_ABERTO',
  lancamentos: [
    lancamento(1, { numeroDar: 'FICT-0011' }),
    lancamento(2, { rotulo: 'Taxa da correção', situacao: 'AGUARDANDO_PAGAMENTO', pagaEm: null, emitidaEm: '2026-03-16', venceEm: '2026-03-30', numeroDar: 'FICT-0111' }),
  ],
});
const FIN13 = financeiro({ resumo: 'SEM_TAXA', lancamentos: [] });
const FIN15 = FIN03;

interface Opcoes {
  financeiros: (object | number)[];
  usuario?: UsuarioSimulado;
  /** Responde a tramitação com erro, para provar a independência. */
  tramitacaoFora?: boolean;
}

async function simular(page: Page, { financeiros, usuario = GESTOR, tramitacaoFora = false }: Opcoes): Promise<{ chamadas: { financeiro: number } }> {
  const contagem = { financeiro: 0 };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') {
      return route.fulfill({
        json: {
          itens: [],
          totais: { total: 0, vigentes: 0, semEfeito: 0, naoPdf: 0, tipoNaoInformado: 0, indisponiveis: 0, sigilosos: 0, convertiveis: 0, geometrias: 0, geometriasOmitidas: 0 },
          geometrias: [],
        },
      });
    }
    if (caminho === '/api/busca') {
      return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 } });
    }
    if (caminho === '/api/requerimentos/101/tramitacao' && tramitacaoFora) {
      return route.fulfill({ status: 503, json: { status: 503, errorCode: 'SIGA_INDISPONIVEL', detail: 'detalhe técnico que nunca deve aparecer' } });
    }
    if (caminho === '/api/requerimentos/101/financeiro') {
      const resposta = financeiros[Math.min(contagem.financeiro, financeiros.length - 1)];
      contagem.financeiro++;
      return typeof resposta === 'number'
        ? route.fulfill({ status: resposta, json: { status: resposta, errorCode: 'SIGA_INDISPONIVEL', detail: 'detalhe técnico que nunca deve aparecer' } })
        : route.fulfill({ json: resposta });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return { chamadas: contagem };
}

const secao = (page: Page) => page.locator('app-financeiro');
const linhas = (page: Page) => secao(page).locator('tbody tr');

async function semChaveNemCpf(page: Page): Promise<void> {
  const texto = await secao(page).innerText();
  expect(texto).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
  expect(texto).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
  expect(texto).not.toMatch(/chave|ged|idTecnico|titula|FALSO_SEM_DAR|RUIDO_ISENTO|ignorad|statusProcessado/i);
}

async function semRolagemHorizontal(page: Page): Promise<void> {
  const { largura, janela } = await page.evaluate(() => ({ largura: document.documentElement.scrollWidth, janela: window.innerWidth }));
  expect(largura).toBeLessThanOrEqual(janela);
}

test.describe('desktop', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('FIN01: taxa paga mostra emitida, vence, paga, DAR, valor e desconto em reais, e o selo Quitado', async ({ page }) => {
    await simular(page, { financeiros: [FIN01] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).getByRole('heading', { name: 'Taxas e pagamentos' })).toBeVisible();
    await expect(secao(page).locator('.fin-resumo .tag')).toHaveText('Quitado');
    await expect(linhas(page)).toHaveCount(1);
    const celulas = (await linhas(page).first().locator('th, td').allTextContents()).map((t) => t.replace(/\s+/g, ' ').trim());
    expect(celulas).toEqual(['1ª taxa', 'Paga', '04/03/2026', '14/03/2026', '07/03/2026', 'FICT-0001', 'R$ 980,00', 'R$ 0,00']);
    await semChaveNemCpf(page);
  });

  test('FIN03: vencida por data: a situação segue "Aguardando pagamento", com a marca e o aviso em destaque', async ({ page }) => {
    await simular(page, { financeiros: [FIN03] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.fin-resumo .tag')).toHaveText('Vencido');
    await expect(linhas(page).first()).toContainText('Aguardando pagamento');
    await expect(linhas(page).first()).toContainText('Taxa vencida');
    await expect(secao(page).locator('.fin-avisos li.atencao')).toContainText('Taxa vencida em 19/03/2026');
    await expect(secao(page).locator('.fin-avisos li.atencao')).toContainText('O SIGA ainda não marca a taxa como vencida');
    await semChaveNemCpf(page);
  });

  test('FIN15: a mesma taxa vencida por data, com o fluxo encerrado, ainda mostra o aviso', async ({ page }) => {
    await simular(page, { financeiros: [FIN15] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.fin-avisos li.atencao')).toContainText('Taxa vencida em 19/03/2026');
  });

  test('FIN05: "Isento de Taxa em dd/mm/aaaa" sem nota; FIN06: sem data, "data não registrada"', async ({ page }) => {
    await simular(page, { financeiros: [FIN05] });
    await page.goto('/consulta/requerimentos/101');
    await expect(secao(page).locator('.fin-resumo .tag')).toHaveText('Isento de taxa');
    await expect(secao(page).locator('.fin-isencoes li')).toContainText('Isento de Taxa em 04/03/2026');
    await expect(secao(page).locator('.fin-isencoes li')).not.toContainText('data a confirmar');
    await expect(linhas(page).first().locator('th')).toContainText('Isenção');

    await page.unrouteAll({ behavior: 'ignoreErrors' });
    await simular(page, { financeiros: [FIN06] });
    await page.reload();
    await expect(secao(page).locator('.fin-isencoes li')).toContainText('data não registrada');
  });

  test('FIN11: a taxa inicial e a taxa da correção, na ordem', async ({ page }) => {
    await simular(page, { financeiros: [FIN11] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.fin-resumo .tag')).toHaveText('Em aberto');
    await expect(linhas(page).locator('th')).toHaveText(['1ª taxa', 'Taxa da correção']);
  });

  test('FIN13: sem taxa mostra o selo e a frase, sem tabela', async ({ page }) => {
    await simular(page, { financeiros: [FIN13] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.fin-resumo .tag')).toHaveText('Sem taxa');
    await expect(secao(page)).toContainText('Este requerimento não tem taxa registrada.');
    await expect(secao(page).locator('table')).toHaveCount(0);
  });

  test('parâmetro de valores desligado: sem colunas de valor e desconto, e sem R$ na seção; o DAR continua', async ({ page }) => {
    await simular(page, {
      financeiros: [financeiro({ mostraValores: false, lancamentos: [lancamento(1, { valor: undefined, desconto: undefined, upf: undefined })] })],
    });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('thead th')).toHaveText(['Taxa', 'Situação', 'Emitida em', 'Vence em', 'Paga em', 'Nº do DAR']);
    await expect(secao(page)).not.toContainText('R$');
    await expect(secao(page)).toContainText('FICT-0001');
  });

  test('o Operador vê a mesma seção, sem campos de Administrador', async ({ page }) => {
    await simular(page, { financeiros: [FIN01], usuario: OPERADOR });
    await page.goto('/consulta/requerimentos/101');

    await expect(linhas(page)).toHaveCount(1);
    await semChaveNemCpf(page);
  });

  test('INDISPONIVEL: aviso neutro e "Tentar novamente" lê de novo; o resto da tela segue', async ({ page }) => {
    const { chamadas } = await simular(page, { financeiros: [financeiro({ estado: 'INDISPONIVEL', resumo: null, lancamentos: [] }), FIN01] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page)).toContainText('Não foi possível consultar as taxas agora. O restante da página não foi afetado.');
    await expect(page.getByRole('heading', { name: 'Objeto do requerimento' })).toBeVisible();
    await secao(page).getByRole('button', { name: 'Tentar novamente' }).click();
    await expect(linhas(page)).toHaveCount(1);
    expect(chamadas.financeiro).toBe(2);
  });

  test('erro de rede da seção não afeta a página; a tramitação fora não afeta as taxas', async ({ page }) => {
    await simular(page, { financeiros: [503, FIN01], tramitacaoFora: true });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).getByRole('alert')).toBeVisible();
    await expect(page.locator('app-tramitacao').getByRole('alert')).toBeVisible();
    await secao(page).getByRole('button', { name: 'Tentar novamente' }).click();
    await expect(linhas(page)).toHaveCount(1);
    await expect(page.locator('app-tramitacao').getByRole('alert')).toBeVisible();
  });

  test('recolher pelo teclado esconde a tabela', async ({ page }) => {
    await simular(page, { financeiros: [FIN01] });
    await page.goto('/consulta/requerimentos/101');

    const botao = secao(page).getByRole('button', { name: 'Recolher' });
    await botao.focus();
    await page.keyboard.press('Enter');
    await expect(secao(page).locator('table')).toHaveCount(0);
    await expect(secao(page).getByRole('button', { name: 'Expandir' })).toHaveAttribute('aria-expanded', 'false');
  });
});

test.describe('celular (360 px)', () => {
  test.use({ viewport: { width: 360, height: 800 } });

  test('a lista vira cartões, sem rolagem horizontal da página, com os rótulos de cada dado', async ({ page }) => {
    await simular(page, { financeiros: [FIN11] });
    await page.goto('/consulta/requerimentos/101');

    await expect(linhas(page)).toHaveCount(2);
    await semRolagemHorizontal(page);
    // Cada célula se identifica pelo rótulo (cartão), e o cabeçalho de coluna fica só para leitor de tela.
    await expect(linhas(page).first().locator('td').first()).toHaveAttribute('data-label', 'Situação');
    const cabecalhoVisivel = await secao(page).locator('thead').evaluate((el) => el.getBoundingClientRect().width);
    expect(cabecalhoVisivel).toBeLessThanOrEqual(1);
    const caixa = await secao(page).locator('table').boundingBox();
    expect(caixa!.width).toBeLessThanOrEqual(360);
  });

  test('com aviso de taxa vencida e sem valores, ainda sem rolagem horizontal', async ({ page }) => {
    await simular(page, { financeiros: [{ ...FIN03, mostraValores: false }] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.fin-avisos li.atencao')).toBeVisible();
    await semRolagemHorizontal(page);
  });
});
