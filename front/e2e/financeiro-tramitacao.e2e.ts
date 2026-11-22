import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * S9, bloco 9: marcas financeiras na linha do tempo e no fluxograma da Tramitação, e o aviso "Taxa vencida" em "Onde está
 * agora". API simulada (só desenvolvimento), dados fictícios no formato de `GET /api/requerimentos/{id}/tramitacao` com
 * `passagens[].marcasFinanceiras` (docs/07).
 */
const dia = (n: number): string => `2026-03-${String(n).padStart(2, '0')}T09:00:00`;

type Tipo = 'TAREFA_HUMANA' | 'ESPERA' | 'AUTOMATICA' | 'EMISSAO';
const etapa = (id: string, rotulo: string, tipo: Tipo = 'TAREFA_HUMANA') => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');
const TAXA_CORRECAO = etapa('emissao_da_taxa_correcao', 'Emissão da taxa (correção)', 'AUTOMATICA');
const PAGAMENTO_CORRECAO = etapa('aguardando_pagamento_correcao', 'Aguardando pagamento (correção)', 'ESPERA');

const marca = (tipo: string, rotulo: string, data: string | null, sobre: object = {}) => ({ tipo, rotulo, data, valor: null, dataAConfirmar: false, ...sobre });

function passagem(e: ReturnType<typeof etapa>, entrada: number, saida: number | null, sobre: object = {}) {
  return {
    etapa: { ...e },
    ciclo: 1,
    ramo: 'PRINCIPAL',
    entrada: dia(entrada),
    saida: saida === null ? null : dia(saida),
    duracaoSegundos: saida === null ? null : (saida - entrada) * 86400,
    atual: saida === null,
    emParalelo: false,
    processoConcluido: false,
    motivoDaVolta: null,
    responsaveis: [],
    participantes: [],
    atos: [],
    marcasFinanceiras: [],
    idTecnico: 'taskFicticiaDoAdministrador',
    repeticoes: 1,
    ...sobre,
  };
}

function tramitacao(passagens: object[], sobre: object = {}) {
  return {
    idRequerimento: 101,
    numeroRequerimento: '3331/2025',
    numeroProcesso: '2825/2025',
    situacaoLicenciamento: { codigo: 'AGUARDANDO_PAGAMENTO_DE_TAXAS', descricao: 'Aguardando pagamento de taxas' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    aproximado: false,
    truncado: false,
    passagens,
    complementacoes: [],
    taxa: { principal: 'TAXAVEL', correcao: 'TAXAVEL' },
    final: null,
    avisos: [],
    planoB: null,
    ...sobre,
  };
}

/** HC07 + FIN11: taxa inicial paga e a taxa da correção aguardando, vencida por data. */
const FIN11 = tramitacao([
  passagem(CADASTRO, 1, 2),
  passagem(TAXA, 2, 2, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-02'), marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0011' })] }),
  passagem(PAGAMENTO, 2, 5, { marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-12'), marca('PAGA_EM', 'Paga em', '2026-03-05')] }),
  passagem(ANALISE, 5, 8),
  passagem(CORRECAO, 8, 10, { ramo: 'CORRECAO' }),
  passagem(TAXA_CORRECAO, 10, 10, {
    ramo: 'CORRECAO',
    marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-10'), marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0111' }), marca('DA_CORRECAO', 'da correção', null)],
  }),
  passagem(PAGAMENTO_CORRECAO, 10, null, {
    ramo: 'CORRECAO',
    marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-19'), marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19'), marca('DA_CORRECAO', 'da correção', null)],
  }),
]);

/** H18/FIN15: instância encerrada por taxa não paga no prazo, com a taxa vencida por data. */
const FIN15 = tramitacao(
  [
    passagem(CADASTRO, 1, 2),
    passagem(TAXA, 2, 2, { marcasFinanceiras: [marca('EMITIDA_EM', 'Emitida em', '2026-03-02'), marca('NUMERO_DAR', 'DAR', null, { valor: 'FICT-0015' })] }),
    passagem(PAGAMENTO, 2, 19, {
      atual: false,
      marcasFinanceiras: [marca('VENCE_EM', 'Vence em', '2026-03-19'), marca('TAXA_VENCIDA', 'Taxa vencida', '2026-03-19')],
    }),
  ],
  {
    estado: 'ENCERRADO',
    taxa: { principal: 'TAXAVEL', correcao: null },
    final: { rotulo: 'Encerrado por taxa não paga no prazo', tipo: 'FINAL', encerradoEm: dia(19) },
    avisos: [{ codigo: 'FLUXO_ENCERRADO_SEM_STATUS', texto: 'O fluxo foi encerrado por taxa não paga no prazo; o SIGA ainda mostra "aguardando pagamento".', gravidade: 'ATENCAO' }],
  },
);

/** FIN16: sem instância (plano B), com a taxa paga: nenhuma marca nas passagens. */
const SELO = 'Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo';
const FIN16 = tramitacao([], {
  estado: 'SEM_INSTANCIA',
  aproximado: true,
  taxa: null,
  situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  avisos: [{ codigo: 'HISTORICO_APROXIMADO', texto: SELO, gravidade: 'INFO' }],
  planoB: {
    selo: SELO,
    semDetalhamento: false,
    etapas: [{ etapa: { ...PAGAMENTO }, entrada: '2021-05-11T09:00:00', saida: null, eventos: 2 }],
    marcos: [],
    situacao: 'EM_ANALISE',
    eventosIgnorados: 0,
    eventosTruncados: false,
  },
});

const POSICAO_FIN15 = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  situacaoLicenciamento: { codigo: 'AGUARDANDO_PAGAMENTO_DE_TAXAS', descricao: 'Aguardando pagamento de taxas' },
  modelo: 'Outorga de recursos hídricos',
  estado: 'ENCERRADO',
  etapaAtual: null,
  emParalelo: [],
  complementacao: null,
  multiInstancia: null,
  avisos: [
    { codigo: 'FLUXO_ENCERRADO_SEM_STATUS', texto: 'O fluxo foi encerrado por taxa não paga no prazo; o SIGA ainda mostra "aguardando pagamento".', gravidade: 'ATENCAO' },
    {
      codigo: 'TAXA_VENCIDA',
      texto: 'Taxa vencida em 19/03/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.',
      gravidade: 'ATENCAO',
    },
  ],
  final: { rotulo: 'Encerrado por taxa não paga no prazo', encerradoEm: dia(19) },
};

async function simular(page: Page, tramitacaoDaTela: object, posicao: object = POSICAO_FIN15): Promise<void> {
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
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
    if (caminho === '/api/busca') return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 } });
    if (caminho === '/api/requerimentos/101/posicao') return route.fulfill({ json: posicao });
    if (caminho === '/api/requerimentos/101/tramitacao') return route.fulfill({ json: tramitacaoDaTela });
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

const secao = (page: Page) => page.locator('app-tramitacao');
const fluxo = (page: Page) => secao(page).locator('.fluxo');
const caixa = (page: Page, rotulo: string) => fluxo(page).locator('.caixa').filter({ has: page.locator('.caixa-rotulo', { hasText: new RegExp(`^${rotulo.replace(/[()]/g, '\\$&')}$`) }) });
const itemDaLista = (page: Page, rotulo: string) =>
  secao(page).locator('.tram-lista > li').filter({ has: page.locator('.tram-titulo b', { hasText: new RegExp(`^${rotulo.replace(/[()]/g, '\\$&')}$`) }) });

test.describe('marcas financeiras (desktop)', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('HC07 + FIN11: a linha do tempo mostra emissão, DAR, vencimento, pagamento, vencida e "da correção"', async ({ page }) => {
    await simular(page, FIN11);
    await page.goto('/consulta/requerimentos/101');

    // A emissão da taxa é automática, mas continua à vista com o filtro ligado, porque traz as marcas.
    await expect(secao(page).locator('.tram-filtro input')).toBeChecked();
    await expect(itemDaLista(page, 'Emissão da taxa').locator('.tram-marcas-fin li')).toHaveText(['Emitida em 02/03/2026', 'DAR FICT-0011']);
    await expect(itemDaLista(page, 'Aguardando pagamento').locator('.tram-marcas-fin li')).toHaveText(['Vence em 12/03/2026', 'Paga em 05/03/2026']);
    await expect(itemDaLista(page, 'Emissão da taxa (correção)').locator('.tram-marcas-fin li')).toHaveText(['Emitida em 10/03/2026', 'DAR FICT-0111', 'da correção']);
    await expect(itemDaLista(page, 'Aguardando pagamento (correção)').locator('.tram-marcas-fin li')).toHaveText([
      'Vence em 19/03/2026',
      'Taxa vencida em 19/03/2026',
      'da correção',
    ]);
    await expect(itemDaLista(page, 'Análise do processo').locator('.tram-marcas-fin')).toHaveCount(0);
  });

  test('HC07 + FIN11: o fluxograma mostra o pagamento, o vencimento e o selo, sem caixa nova nem etapa futura', async ({ page }) => {
    await simular(page, FIN11);
    await page.goto('/consulta/requerimentos/101');

    await expect(caixa(page, 'Aguardando pagamento').locator('.caixa-fin')).toHaveText('Paga em 05/03/2026');
    await expect(caixa(page, 'Aguardando pagamento').locator('.caixa-vencida')).toHaveCount(0);
    await expect(caixa(page, 'Aguardando pagamento (correção)').locator('.caixa-fin')).toHaveText('Vence em 19/03/2026');
    await expect(caixa(page, 'Aguardando pagamento (correção)').locator('.caixa-vencida')).toContainText('Taxa vencida');
    await expect(fluxo(page).locator('.caixa')).toHaveCount(7);
    // a dica por teclado traz as marcas da emissão
    await caixa(page, 'Emissão da taxa').focus();
    await expect(caixa(page, 'Emissão da taxa').locator('.dica')).toContainText('DAR FICT-0011');
  });

  test('H18 + FIN15: aviso de taxa vencida com a situação do SIGA e o final "Encerrado por taxa não paga no prazo"', async ({ page }) => {
    await simular(page, FIN15);
    await page.goto('/consulta/requerimentos/101');

    const onde = page.locator('app-onde-esta-agora');
    await expect(onde.locator('.onde-situacao')).toContainText('Situação no Licenciamento: Aguardando pagamento de taxas');
    await expect(onde.locator('.onde-avisos li')).toHaveCount(2);
    await expect(onde.locator('.onde-avisos li').nth(1)).toContainText('Taxa vencida em 19/03/2026');
    await expect(onde.locator('.onde-avisos li').nth(1)).toContainText('O SIGA ainda não marca a taxa como vencida');
    await expect(onde).toContainText('Encerrado por taxa não paga no prazo');
    // o fluxograma termina na bola preta do final, com a espera marcada como vencida
    await expect(fluxo(page).locator('.final-rotulo')).toHaveText('Encerrado por taxa não paga no prazo');
    await expect(caixa(page, 'Aguardando pagamento').locator('.caixa-vencida')).toContainText('Taxa vencida');
    await expect(caixa(page, 'Aguardando pagamento').locator('.caixa-fin')).toHaveText('Vence em 19/03/2026');
  });

  test('FIN16 (plano B): nenhuma marca nem fluxograma; o bloco "Taxas e pagamentos" é de outra seção', async ({ page }) => {
    await simular(page, FIN16, { ...POSICAO_FIN15, avisos: [] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).locator('.tram-selo')).toBeVisible();
    await expect(secao(page).locator('.tram-marcas-fin')).toHaveCount(0);
    await expect(fluxo(page)).toHaveCount(0);
    await expect(page.locator('app-onde-esta-agora .onde-situacao')).toHaveCount(0);
  });

  test('impressão em preto e branco: o selo "Taxa vencida" se distingue por borda e texto, não só pela cor', async ({ page }) => {
    await simular(page, FIN11);
    await page.goto('/consulta/requerimentos/101');

    await page.evaluate(() => document.body.classList.add('imprimindo-tramitacao'));
    await page.emulateMedia({ media: 'print' });

    const selo = caixa(page, 'Aguardando pagamento (correção)').locator('.caixa-vencida');
    await expect(selo).toBeVisible();
    await expect(selo).toHaveCSS('border-top-style', 'double');
    await expect(selo).toHaveCSS('color', 'rgb(0, 0, 0)');
    await expect(selo).toContainText('Taxa vencida');
  });
});

test.describe('marcas financeiras (celular 360 px)', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test('sem rolagem horizontal da página, com marcas na lista e no fluxograma', async ({ page }) => {
    await simular(page, FIN11);
    await page.goto('/consulta/requerimentos/101');

    await expect(itemDaLista(page, 'Aguardando pagamento (correção)').locator('.tram-marcas-fin li')).toHaveCount(3);
    await expect(caixa(page, 'Aguardando pagamento (correção)').locator('.caixa-vencida')).toBeVisible();
    const medidas = await page.evaluate(() => ({ rolagem: document.documentElement.scrollWidth, janela: window.innerWidth }));
    expect(medidas.rolagem).toBeLessThanOrEqual(medidas.janela);
  });
});
