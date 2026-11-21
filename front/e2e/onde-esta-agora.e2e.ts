import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * S7, bloco 8: bloco "Onde está agora" no detalhe do requerimento. API simulada (só desenvolvimento), dados fictícios no
 * formato de `GET /api/requerimentos/{id}/posicao` (docs/07), com os campos do Administrador misturados para provar que
 * a tela não os mostra.
 */
const ATIVO_SIMPLES = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  modelo: 'Outorga de recursos hídricos',
  estado: 'ATIVO',
  etapaAtual: {
    id: 'revisao_do_parecer_tecnico',
    rotulo: 'Revisão do parecer técnico',
    tipo: 'TAREFA_HUMANA',
    desde: '2025-04-29T09:14:00',
    desdeUltimaEntrada: false,
    responsavel: { tipo: 'SERVIDOR', papel: 'Chefia', area: null, nome: 'Servidor Exemplo 04', texto: 'Servidor Exemplo 04' },
    idTecnico: 'taskRevisarParecerTecnico',
  },
  emParalelo: [],
  complementacao: null,
  multiInstancia: null,
  avisos: [],
  final: null,
};

const ATIVO_COMPLETO = {
  ...ATIVO_SIMPLES,
  etapaAtual: {
    id: 'analise_do_processo',
    rotulo: 'Análise do processo',
    tipo: 'TAREFA_HUMANA',
    desde: '2025-04-29T09:14:00',
    desdeUltimaEntrada: true,
    responsavel: { tipo: 'VARIOS', papel: 'Analista', area: null, nome: null, texto: 'Analista' },
    idTecnico: 'taskAnalisarRequerimento',
  },
  emParalelo: [
    {
      id: 'emissao_do_ato',
      rotulo: 'Emissão do ato',
      tipo: 'EMISSAO',
      desde: '2025-04-30T10:00:00',
      desdeUltimaEntrada: false,
      responsavel: { tipo: 'AREA', papel: 'Equipe', area: 'Emissão de títulos', nome: null, texto: 'Emissão de títulos' },
      idTecnico: 'taskEmitirPortarias',
      processoFilho: 'procSurhEmissaoPortaria',
    },
    { id: null, rotulo: 'Etapa não mapeada', tipo: null, desde: null, desdeUltimaEntrada: false, responsavel: null, idTecnico: 'taskFicticiaNaoMapeada' },
  ],
  complementacao: { quantidade: 1, desde: '2025-04-20T08:00:00' },
  multiInstancia: {
    concluidos: 1,
    ativas: 2,
    total: 3,
    participantes: [
      { nome: 'Servidor Exemplo 01', papel: 'Analista', concluiu: true },
      { nome: 'Servidor Exemplo 02', papel: 'Analista', concluiu: false },
      { nome: null, papel: 'Responsável não identificado', concluiu: false },
    ],
    maisParticipantes: 0,
  },
  avisos: [{ codigo: 'ETAPA_NAO_MAPEADA', texto: 'Há uma etapa do fluxo que o Rastro ainda não conhece.', gravidade: 'INFO' }],
  idsNaoMapeados: ['taskFicticiaNaoMapeada'],
};

const SEM_PONTOS = { emParalelo: [], complementacao: null, multiInstancia: null, avisos: [], etapaAtual: null };

interface Simulacao {
  /** Respostas da posição, em ordem (a última se repete). Um número é o status de erro. */
  posicoes: (object | number)[];
  /** Enquanto existir, a posição espera por ela (para medir o estado "carregando"). */
  segurar: Promise<void> | null;
  chamadasDaPosicao: number;
}

async function simular(page: Page, posicoes: (object | number)[]): Promise<Simulacao> {
  const sim: Simulacao = { posicoes, segurar: null, chamadasDaPosicao: 0 };
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
    if (caminho === '/api/requerimentos/101/posicao') {
      const resposta = sim.posicoes[Math.min(sim.chamadasDaPosicao, sim.posicoes.length - 1)];
      sim.chamadasDaPosicao++;
      if (sim.segurar) await sim.segurar;
      if (typeof resposta === 'number') {
        return route.fulfill({ status: resposta, json: { status: resposta, errorCode: 'SIGA_INDISPONIVEL', detail: 'detalhe técnico que nunca deve aparecer' } });
      }
      return route.fulfill({ json: resposta });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

const bloco = (page: Page) => page.locator('app-onde-esta-agora');

/** Nenhum id técnico nem CPF no texto do bloco. */
async function semIdTecnicoNemCpf(page: Page): Promise<void> {
  const texto = await bloco(page).innerText();
  expect(texto).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga/);
  expect(texto).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
  expect(texto).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
}

test.describe('desktop', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('ativo: etapa, com quem, x de y, participantes, paralelos pelo teclado e avisos, sem id técnico', async ({ page }) => {
    await simular(page, [ATIVO_COMPLETO]);
    await page.goto('/consulta/requerimentos/101');

    await expect(bloco(page).getByRole('heading', { name: 'Onde está agora' })).toBeVisible();
    await expect(bloco(page).locator('.onde-etapa')).toHaveText('Análise do processo');
    await expect(bloco(page)).toContainText('1 de 3 concluídos');
    await expect(bloco(page)).toContainText('desde a última entrada nesta etapa');
    await expect(bloco(page)).toContainText('Complementação em andamento');
    await expect(bloco(page).getByRole('list', { name: 'Participantes da etapa' }).getByRole('listitem')).toHaveCount(3);
    await expect(bloco(page).getByRole('list', { name: 'Avisos sobre a posição' })).toContainText('Rastro ainda não conhece');
    await expect(bloco(page).locator('time').first()).toHaveAttribute('title', '29/04/2025 09:14');

    const paralelo = bloco(page).getByRole('button', { name: '+2 em paralelo' });
    await paralelo.focus();
    await page.keyboard.press('Enter');
    await expect(paralelo).toHaveAttribute('aria-expanded', 'true');
    await expect(bloco(page).getByRole('list', { name: 'Etapas em paralelo' })).toContainText('Emissão do ato');
    await page.keyboard.press(' ');
    await expect(paralelo).toHaveAttribute('aria-expanded', 'false');

    await semIdTecnicoNemCpf(page);
  });

  test('o quadro do topo não muda de altura e os documentos não saltam mais de uma linha ao carregar', async ({ page }) => {
    const sim = await simular(page, [ATIVO_SIMPLES]);
    let soltar!: () => void;
    sim.segurar = new Promise<void>((r) => (soltar = r));
    await page.goto('/consulta/requerimentos/101');
    await expect(bloco(page).getByRole('status')).toContainText('Carregando a posição');

    const medir = () =>
      page.evaluate(() => ({
        quadro: document.querySelector('.dash-hero')!.getBoundingClientRect().height,
        objeto: document.querySelector('[aria-label="Objeto do requerimento"]')!.getBoundingClientRect().top + window.scrollY,
      }));
    const antes = await medir();
    soltar();
    await expect(bloco(page).locator('.onde-etapa')).toHaveText('Revisão do parecer técnico');
    const depois = await medir();

    expect(depois.quadro).toBe(antes.quadro);
    expect(Math.abs(depois.objeto - antes.objeto)).toBeLessThanOrEqual(24);
  });

  test('indisponível: mensagem neutra e "Tentar novamente" lê de novo', async ({ page }) => {
    const sim = await simular(page, [{ ...ATIVO_SIMPLES, ...SEM_PONTOS, estado: 'INDISPONIVEL' }, ATIVO_SIMPLES]);
    await page.goto('/consulta/requerimentos/101');

    await expect(bloco(page)).toContainText('Não foi possível consultar o andamento agora');
    await bloco(page).getByRole('button', { name: 'Tentar novamente' }).click();
    await expect(bloco(page).locator('.onde-etapa')).toHaveText('Revisão do parecer técnico');
    expect(sim.chamadasDaPosicao).toBe(2);
  });

  test('erro na posição não derruba o detalhe', async ({ page }) => {
    await simular(page, [503]);
    await page.goto('/consulta/requerimentos/101');

    await expect(bloco(page).getByRole('alert')).toBeVisible();
    await expect(bloco(page)).not.toContainText('detalhe técnico');
    await expect(page.locator('h1')).toHaveText('2825/2025');
    await expect(page.getByRole('heading', { name: 'Objeto do requerimento' })).toBeVisible();
  });

  test('encerrado, sem instância e não suportado', async ({ page }) => {
    await simular(page, [
      { ...ATIVO_SIMPLES, ...SEM_PONTOS, estado: 'ENCERRADO', final: { rotulo: 'Concluído', encerradoEm: '2025-05-02T16:00:00', idTecnico: 'Event_1wiuc48' } },
      { ...ATIVO_SIMPLES, ...SEM_PONTOS, estado: 'SEM_INSTANCIA' },
      { ...ATIVO_SIMPLES, ...SEM_PONTOS, estado: 'NAO_SUPORTADO', modelo: null },
    ]);

    await page.goto('/consulta/requerimentos/101');
    await expect(bloco(page).locator('.onde-etapa')).toHaveText('Concluído');
    await expect(bloco(page)).toContainText('Encerrado em 02/05/2025 16:00');
    await semIdTecnicoNemCpf(page);

    await page.reload();
    await expect(bloco(page)).toContainText('Histórico detalhado indisponível para este requerimento.');

    await page.reload();
    await expect(bloco(page)).toContainText('Fluxo ainda não suportado pelo Rastro.');
  });
});

test.describe('celular', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('sem rolagem horizontal, com a lista de paralelos aberta', async ({ page }) => {
    await simular(page, [ATIVO_COMPLETO]);
    await page.goto('/consulta/requerimentos/101');
    await expect(bloco(page).locator('.onde-etapa')).toHaveText('Análise do processo');
    await bloco(page).getByRole('button', { name: '+2 em paralelo' }).click();

    const larguras = await page.evaluate(() => ({
      pagina: document.documentElement.scrollWidth,
      tela: document.documentElement.clientWidth,
    }));
    expect(larguras.pagina).toBeLessThanOrEqual(larguras.tela);
    await semIdTecnicoNemCpf(page);
  });
});
