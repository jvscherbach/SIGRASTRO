import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * S7, bloco 9: posição por requerimento no detalhe do processo (outros requerimentos do mesmo processo, resumidos e
 * expansíveis, cada um carregando e falhando sozinho) e a coluna opcional "Etapa atual" da busca, com limite de
 * posições simultâneas. API simulada; dados fictícios.
 */
const MAXIMO_SIMULTANEAS = 4;

function resultado(id: number) {
  return {
    id,
    identificacao: { rotulo: 'Processo', numero: '2825/2025', protocolado: true },
    numeroRequerimento: `${id}/2026`,
    numeroProcesso: '2825/2025',
    interessado: { nome: 'Associação Residencial Vale Verde', cpfCnpj: '12345678000190' },
    tipoProcesso: 'Uso de Recursos Hídricos',
    situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    abertura: '2025-03-31T10:12:00',
  };
}

function posicao(id: number, sobre: object = {}) {
  return {
    idRequerimento: id,
    numeroRequerimento: `${id}/2026`,
    numeroProcesso: '2825/2025',
    situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    etapaAtual: {
      id: 'analise_do_processo',
      rotulo: 'Análise do processo',
      tipo: 'TAREFA_HUMANA',
      desde: '2025-04-29T09:14:00',
      desdeUltimaEntrada: false,
      responsavel: { tipo: 'AREA', papel: 'Equipe', area: 'Análise técnica (fictícia)', nome: null, texto: 'Análise técnica (fictícia)' },
      idTecnico: 'taskAnalisarRequerimento',
    },
    emParalelo: [],
    complementacao: null,
    multiInstancia: null,
    avisos: [],
    final: null,
    ...sobre,
  };
}

const SEM_PONTOS = { etapaAtual: null, emParalelo: [], multiInstancia: null };

interface Simulacao {
  emAndamento: number;
  maximoVisto: number;
  pedidas: number[];
}

/** 101 é o requerimento aberto; 201 encerrado, 202 sem instância, 203 falha (500); 301 a 320 para a busca. */
async function simular(page: Page, opcoes: { demora?: number; limite?: number } = {}): Promise<Simulacao> {
  const sim: Simulacao = { emAndamento: 0, maximoVisto: 0, pedidas: [] };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/tramitacao/parametros' && opcoes.limite) {
      return route.fulfill({ json: { posicoesSimultaneas: opcoes.limite } });
    }
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
      const ids = url.searchParams.get('tipo') === 'PROCESSO' ? [101, 201, 202, 203] : Array.from({ length: 20 }, (_, i) => 301 + i);
      return route.fulfill({
        json: { conteudo: ids.map(resultado), pagina: 0, tamanho: 20, totalElementos: ids.length, totalPaginas: 1 },
      });
    }
    const m = /^\/api\/requerimentos\/(\d+)\/posicao$/.exec(caminho);
    if (m) {
      const id = Number(m[1]);
      sim.pedidas.push(id);
      sim.emAndamento++;
      sim.maximoVisto = Math.max(sim.maximoVisto, sim.emAndamento);
      await new Promise((r) => setTimeout(r, opcoes.demora ?? 0));
      sim.emAndamento--;
      if (id === 203) return route.fulfill({ status: 500, json: { status: 500, errorCode: 'ERRO_INTERNO' } });
      if (id === 201) return route.fulfill({ json: posicao(id, { ...SEM_PONTOS, estado: 'ENCERRADO', final: { rotulo: 'Concluído', encerradoEm: '2025-05-02T16:00:00', idTecnico: 'Event_1wiuc48' } }) });
      if (id === 202) return route.fulfill({ json: posicao(id, { ...SEM_PONTOS, estado: 'SEM_INSTANCIA' }) });
      return route.fulfill({ json: posicao(id) });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return sim;
}

const secao = (page: Page) => page.locator('app-requerimentos-do-processo');
const item = (page: Page, numero: string) => secao(page).getByRole('listitem').filter({ hasText: `Requerimento ${numero}` });

test.describe('desktop', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('processo com 3 outros requerimentos: um com falha, um sem instância, um encerrado; cada um sozinho', async ({ page }) => {
    await simular(page);
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).getByRole('heading', { name: 'Outros requerimentos deste processo' })).toBeVisible();
    await expect(secao(page).locator('.rdp-lista > li')).toHaveCount(3);

    await expect(item(page, '201/2026')).toContainText('Concluído');
    await expect(item(page, '201/2026').getByRole('img', { name: 'Estado: Encerrado' })).toBeVisible();
    await expect(item(page, '202/2026')).toContainText('Histórico detalhado indisponível');
    await expect(item(page, '202/2026').getByRole('img', { name: 'Estado: Sem histórico detalhado' })).toBeVisible();
    await expect(item(page, '203/2026')).toContainText('Não foi possível consultar');
    await expect(item(page, '203/2026').getByRole('img', { name: 'Estado: Indisponível' })).toBeVisible();

    // A falha de um não afeta o requerimento aberto nem os demais.
    await expect(page.locator('app-onde-esta-agora').first().locator('.onde-etapa')).toHaveText('Análise do processo');

    const abrir = item(page, '201/2026').getByRole('button', { name: 'Ver posição completa' });
    await abrir.focus();
    await page.keyboard.press('Enter');
    await expect(item(page, '201/2026').getByRole('button', { name: 'Ver menos' })).toHaveAttribute('aria-expanded', 'true');
    await expect(item(page, '201/2026')).toContainText('Encerrado em 02/05/2025 16:00');

    const texto = await secao(page).innerText();
    expect(texto).not.toMatch(/task|gtw|Event_|Activity_|procSurh/);
  });

  test(`coluna "Etapa atual" da busca: nunca mais de ${MAXIMO_SIMULTANEAS} posições ao mesmo tempo`, async ({ page }) => {
    const sim = await simular(page, { demora: 150 });
    await page.goto('/consulta');
    // R2, bloco 9: a alternância virou controle segmentado (botões com aria-pressed) no lugar de role="radio".
    const porRequerimento = page.getByRole('button', { name: 'Requerimento', exact: true });
    await porRequerimento.click();
    await expect(porRequerimento).toHaveAttribute('aria-pressed', 'true');
    await page.locator('#consulta-numero').fill('3001/2026');
    await page.getByRole('button', { name: 'Consultar' }).click();
    await expect(page.locator('tbody tr')).toHaveCount(20);
    expect(sim.pedidas).toHaveLength(0); // desligada por padrão

    await page.getByRole('button', { name: 'Mostrar etapa atual' }).click();
    await expect(page.getByRole('columnheader', { name: 'Etapa atual' })).toBeVisible();
    await expect(page.locator('td.etapa-atual').filter({ hasText: 'Análise do processo' })).toHaveCount(20);

    expect(sim.pedidas).toHaveLength(20);
    expect(sim.maximoVisto).toBeLessThanOrEqual(MAXIMO_SIMULTANEAS);
  });

  test('o limite vem das Configurações › Rastro (S7, bloco 10): com 2, nunca mais de 2 ao mesmo tempo', async ({ page }) => {
    const sim = await simular(page, { demora: 150, limite: 2 });
    await page.goto('/consulta');
    await page.locator('#consulta-numero').fill('3001/2026');
    await page.getByRole('button', { name: 'Consultar' }).click();
    await expect(page.locator('tbody tr')).toHaveCount(20);

    await page.getByRole('button', { name: 'Mostrar etapa atual' }).click();
    await expect(page.locator('td.etapa-atual').filter({ hasText: 'Análise do processo' })).toHaveCount(20);

    expect(sim.maximoVisto).toBeLessThanOrEqual(2);
  });
});

test.describe('celular', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('detalhe com os outros requerimentos abertos, sem rolagem horizontal', async ({ page }) => {
    await simular(page);
    await page.goto('/consulta/requerimentos/101');
    await expect(secao(page).locator('.rdp-lista > li')).toHaveCount(3);
    await item(page, '201/2026').getByRole('button', { name: 'Ver posição completa' }).click();
    await expect(item(page, '201/2026')).toContainText('Encerrado em');

    const larguras = await page.evaluate(() => ({
      pagina: document.documentElement.scrollWidth,
      tela: document.documentElement.clientWidth,
    }));
    expect(larguras.pagina).toBeLessThanOrEqual(larguras.tela);
  });
});
