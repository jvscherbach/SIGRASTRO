import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR } from './api-simulada';

/**
 * Fluxo completo da S2 no navegador, com a API simulada: lista de documentos, seleção, gaveta de geração, polling,
 * pré-visualização (Blob em moldura) e download (Blob com o nome do Content-Disposition). Nenhum sistema externo.
 */
const PDF_MINIMO = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R/Size 4>>\n%%EOF',
);

const REQUERIMENTO = {
  id: 101,
  identificacao: { rotulo: 'Processo', numero: '2825/2025', protocolado: true },
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  tipoRequerimento: 'Requerimento de Outorga',
  prioridade: { codigo: 'NORMAL', descricao: 'Atendimento normal', criterios: [] },
  situacao: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  abertura: '2025-03-31T10:12:00',
  atualizacao: '2025-04-29T09:14:00',
  interessado: { nome: 'Associação Residencial Vale Verde', cpfCnpj: '12345678000190' },
  tipoProcesso: 'Uso de Recursos Hídricos',
  objeto: {
    objetivos: ['Consumo humano', 'Irrigação'],
    finalidadesAtividades: [{ finalidade: 'Abastecimento público', atividade: 'Captação' }],
    tiposObjetivo: [
      { tipo: 'SUBTERRANEA', tipoDescricao: 'Subterrânea', objetivo: 'Consumo humano' },
      { tipo: 'SUPERFICIAL', tipoDescricao: 'Superficial', objetivo: 'Irrigação' },
    ],
    atividadeLicenciamento: null,
  },
  tarefaAtual: { estado: 'SEM_TAREFA', nome: null, desde: null, fluxo: null },
};

function doc(id: string, titulo: string, sobre: object = {}) {
  return {
    origem: 'DOCUMENTO_PROCESSO',
    id,
    idRequerimento: 101,
    titulo,
    nomeArquivo: `${titulo.toLowerCase().replace(/\s+/g, '-')}.pdf`,
    origemDescricao: 'Documento do processo',
    data: '2025-04-0' + id + 'T10:00:00',
    situacao: 'VIGENTE',
    sigiloso: false,
    acessivel: true,
    extensao: 'pdf',
    tipoArquivo: 'PDF',
    selecionavelPorPadrao: true,
    bloqueio: null,
    ...sobre,
  };
}

const DOCUMENTOS = {
  itens: [
    doc('1', 'Requerimento assinado'),
    doc('2', 'Oficio'),
    doc('3', 'Calculo', { nomeArquivo: 'calculo.xlsx', extensao: 'xlsx', tipoArquivo: 'PLANILHA', selecionavelPorPadrao: false, bloqueio: 'NAO_PDF' }),
    doc('4', 'Documento sigiloso', { nomeArquivo: null, extensao: null, tipoArquivo: null, sigiloso: true, acessivel: false, selecionavelPorPadrao: false, bloqueio: 'SIGILOSO' }),
    doc('5', 'Planta', { nomeArquivo: null, extensao: '', tipoArquivo: 'DESCONHECIDO' }),
  ],
  totais: { total: 5, vigentes: 5, semEfeito: 0, naoPdf: 1, tipoNaoInformado: 1, indisponiveis: 0, sigilosos: 1 },
};

const ITENS = [
  { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento assinado' },
  { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio' },
];

async function simular(page: Page, aoCriar: (corpo: unknown) => void = () => undefined) {
  let consultas = 0;
  await page.addInitScript(() => sessionStorage.setItem('vistas.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho === '/api/dossies' && metodo === 'POST') {
      aoCriar(route.request().postDataJSON());
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      consultas += 1;
      const concluido = consultas >= 2;
      return route.fulfill({
        json: {
          id: 'pedido-1',
          status: concluido ? 'CONCLUIDO' : 'OBTENDO',
          total: 2,
          processados: concluido ? 2 : 1,
          erro: null,
          falhas: [],
          paginas: concluido ? 6 : null,
          expiraEm: '2099-01-01T00:00:00',
          itens: ITENS.map((i, n) => ({ ...i, situacao: concluido || n === 0 ? 'OK' : 'OBTENDO' })),
        },
      });
    }
    if (caminho === '/api/dossies/pedido-1/arquivo') {
      const anexo = url.searchParams.get('disposicao') === 'attachment';
      return route.fulfill({
        status: 200,
        body: PDF_MINIMO,
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': `${anexo ? 'attachment' : 'inline'}; filename*=UTF-8''dossie-processo-2825-2025.pdf`,
        },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

test.use({ viewport: { width: 1280, height: 900 } });

test('lista, seleção padrão, geração com acompanhamento, pré-visualização e download', async ({ page }) => {
  let corpoDoPedido: unknown = null;
  await simular(page, (corpo) => (corpoDoPedido = corpo));
  await page.goto('/consulta/requerimentos/101');

  // lista: seleção total dos selecionáveis (inclusive tipo não informado); planilha e sigiloso bloqueados
  await expect(page.locator('tbody tr')).toHaveCount(5);
  await expect(page.locator('.selbar b')).toHaveText('3');
  await expect(page.locator('.dash-sec-head p').first()).toContainText('5 documento(s) · 5 vigente(s) · 0 sem efeito · 0 indisponível(is)');
  await expect(page.locator('tbody tr', { hasText: 'Calculo' }).locator('input[type=checkbox]')).toBeDisabled();
  await expect(page.locator('tbody tr', { hasText: 'Documento sigiloso' }).locator('input[type=checkbox]')).toBeDisabled();
  await expect(page.locator('.nao-pdf')).toContainText('1 documento(s) não são PDF');
  await expect(page.locator('.tipo-nao-informado')).toContainText('1 documento(s) sem tipo informado');
  await expect(page.locator('tbody tr', { hasText: 'Planta' })).toContainText('tipo não informado');
  await expect(page.locator('tbody tr', { hasText: 'Planta' }).locator('input[type=checkbox]')).toBeEnabled();
  // objeto em cartões
  await expect(page.locator('.objeto-card[aria-label="Objetivos"] li')).toHaveCount(2);

  const alturaAntes = await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  await expect(page.getByRole('dialog', { name: /Gerando dossiê|Dossiê pronto/ })).toBeVisible();
  expect(corpoDoPedido).toEqual({
    idRequerimento: 101,
    documentos: [
      { origem: 'DOCUMENTO_PROCESSO', id: '1' },
      { origem: 'DOCUMENTO_PROCESSO', id: '2' },
      { origem: 'DOCUMENTO_PROCESSO', id: '5' },
    ],
  });

  // acompanhamento até o fim (polling)
  await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto', { timeout: 10_000 });
  await expect(page.locator('.d-head .ordem-cronologica')).toHaveText('O dossiê segue sempre a ordem cronológica.');
  await expect(page.locator('.d-body')).toContainText('6 páginas');
  const alturaDepois = await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  expect(alturaDepois).toBe(alturaAntes);

  // pré-visualização por Blob
  await page.getByRole('button', { name: 'Pré-visualizar' }).click();
  const moldura = page.locator('.previa iframe');
  await expect(moldura).toBeVisible();
  expect(await moldura.getAttribute('src')).toMatch(/^blob:/);
  await page.getByRole('button', { name: 'Fechar pré-visualização' }).click();
  await expect(page.locator('.previa')).toHaveCount(0);

  // download por Blob com o nome do cabeçalho
  const baixando = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  const download = await baixando;
  expect(download.suggestedFilename()).toBe('dossie-processo-2825-2025.pdf');
  await expect(page.locator('.d-body')).toContainText('Arquivo baixado');
});

test('Operador: documento sigiloso aparece mascarado e sem seleção', async ({ page }) => {
  await simular(page);
  await page.goto('/consulta/requerimentos/101');

  const linha = page.locator('tbody tr', { hasText: 'Documento sigiloso' });
  await expect(linha).toContainText('Requer perfil autorizado');
  await expect(linha.locator('input[type=checkbox]')).toBeDisabled();
});
