import { Page, Route, expect, test } from '@playwright/test';

import { GESTOR, REQUERIMENTO } from './api-simulada';

/**
 * Fluxo completo da S2 no navegador, com a API simulada: lista de documentos, seleção, gaveta de geração, polling,
 * pré-visualização (Blob em moldura) e download (Blob com o nome do Content-Disposition). Nenhum sistema externo.
 */
const PDF_MINIMO = Buffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R/Size 4>>\n%%EOF',
);

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
  totais: { total: 5, vigentes: 5, semEfeito: 0, naoPdf: 1, tipoNaoInformado: 1, indisponiveis: 0, sigilosos: 1, convertiveis: 0 },
};

const ITENS = [
  { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento assinado' },
  { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio' },
];

async function simular(page: Page, aoCriar: (corpo: unknown) => void = () => undefined) {
  let consultas = 0;
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
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
  // S6, bloco 3: nota neutra (a página não sabe se o navegador gravou) e "Tentar novamente" para baixar de novo
  await expect(page.locator('.d-body')).toContainText('Arquivo entregue ao navegador');
  await expect(page.locator('.gaveta-entregue.lime')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
});

test('Operador: documento sigiloso aparece mascarado e sem seleção', async ({ page }) => {
  await simular(page);
  await page.goto('/consulta/requerimentos/101');

  const linha = page.locator('tbody tr', { hasText: 'Documento sigiloso' });
  await expect(linha).toContainText('Requer perfil autorizado');
  await expect(linha.locator('input[type=checkbox]')).toBeDisabled();
});

// --- pedido à espera de decisão (S2.2): a gaveta lista as falhas e continua sem reiniciar o progresso

const TRES_ITENS = [
  { origem: 'DOCUMENTO_PROCESSO', id: '1', titulo: 'Requerimento assinado' },
  { origem: 'DOCUMENTO_PROCESSO', id: '2', titulo: 'Oficio' },
  { origem: 'DOCUMENTO_PROCESSO', id: '5', titulo: 'Planta' },
];

function andamento(status: string, situacoes: string[], sobre: object = {}) {
  const itens = TRES_ITENS.map((i, n) => ({
    ...i,
    situacao: situacoes[n],
    ...(situacoes[n] === 'FALHA' || situacoes[n] === 'REMOVIDO' ? { motivo: 'tempo limite excedido ao obter o arquivo' } : {}),
  }));
  return {
    id: 'pedido-1',
    status,
    total: 3,
    processados: situacoes.filter((s) => s === 'OK' || s === 'REMOVIDO').length,
    erro: null,
    falhas: itens
      .filter((i) => i.situacao === 'FALHA')
      .map((i) => ({ origem: i.origem, id: i.id, titulo: i.titulo, motivo: 'tempo limite excedido ao obter o arquivo' })),
    paginas: status === 'CONCLUIDO' ? 5 : null,
    expiraEm: '2099-01-01T00:00:00',
    itens,
    ...sobre,
  };
}

/** API simulada com um roteiro de andamentos: cada GET devolve o próximo; o último se repete. */
async function simularDecisao(page: Page, roteiro: object[], chamadas: string[]) {
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const caminho = new URL(route.request().url()).pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho === '/api/dossies' && metodo === 'POST') {
      chamadas.push('POST');
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    if (caminho === '/api/dossies/pedido-1/continuacao' && metodo === 'POST') {
      chamadas.push((route.request().postDataJSON() as { acao: string }).acao);
      return route.fulfill({ status: 202, body: '' });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'DELETE') {
      chamadas.push('DELETE');
      return route.fulfill({ status: 204, body: '' });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      return route.fulfill({ json: roteiro.length > 1 ? roteiro.shift() : roteiro[0] });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
}

async function abrirGaveta(page: Page): Promise<number> {
  await page.goto('/consulta/requerimentos/101');
  await expect(page.locator('tbody tr')).toHaveCount(5);
  const altura = await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  await page.getByRole('button', { name: 'Gerar dossiê' }).click();
  return altura;
}

test('falhas listadas; "Tentar novamente" refaz só os com problema e o progresso não volta a zero', async ({ page }) => {
  const chamadas: string[] = [];
  const pendente = andamento('PENDENTE_DECISAO', ['OK', 'FALHA', 'FALHA']);
  const retomando = andamento('OBTENDO', ['OK', 'OBTENDO', 'AGUARDANDO']);
  const pronto = andamento('CONCLUIDO', ['OK', 'OK', 'OK']);
  await simularDecisao(page, [pendente, retomando, pronto], chamadas);

  const alturaAntes = await abrirGaveta(page);
  await expect(page.locator('.d-head h2')).toHaveText('Documentos com problema');
  await expect(page.locator('.falhas li')).toHaveCount(2);
  await expect(page.locator('.falhas li').first()).toContainText('Oficio');
  await expect(page.locator('.falhas li').first()).toContainText('tempo limite excedido ao obter o arquivo');
  await expect(page.locator('.estado-rotulo')).toContainText('Aguardando sua decisão · 1/3');
  await expect(page.getByRole('button', { name: 'Remover os 2 com problema e gerar' })).toBeVisible();

  await page.getByRole('button', { name: 'Tentar novamente os 2 com problema' }).click();
  await expect(page.locator('.estado-rotulo')).toContainText('Obtendo documentos · 1/3');
  await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto', { timeout: 10_000 });
  expect(chamadas).toEqual(['POST', 'TENTAR_NOVAMENTE']);
  const alturaDepois = await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  expect(alturaDepois - alturaAntes).toBe(0);
});

test('"Remover os N com problema e gerar" mostra os removidos e conclui sem novo POST', async ({ page }) => {
  const chamadas: string[] = [];
  const pendente = andamento('PENDENTE_DECISAO', ['OK', 'FALHA', 'OK']);
  const pronto = andamento('CONCLUIDO', ['OK', 'REMOVIDO', 'OK']);
  await simularDecisao(page, [pendente, pronto], chamadas);

  await abrirGaveta(page);
  await page.getByRole('button', { name: 'Remover o 1 com problema e gerar' }).click();

  await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto', { timeout: 10_000 });
  await expect(page.locator('.step[data-situacao="REMOVIDO"]')).toContainText('Removido');
  await expect(page.locator('.estado-rotulo')).toContainText('3/3');
  expect(chamadas).toEqual(['POST', 'REMOVER_E_CONTINUAR']);
  // a tabela deixa de selecionar o documento removido
  await page.locator('.d-foot').getByRole('button', { name: 'Fechar' }).click();
  await expect(page.locator('tbody tr', { hasText: 'Oficio' }).locator('input[type=checkbox]')).not.toBeChecked();
});

test('token recusado orienta a atualizar o arquivo do token; Cancelar encerra', async ({ page }) => {
  const chamadas: string[] = [];
  const recusado = andamento('PENDENTE_DECISAO', ['OK', 'FALHA', 'AGUARDANDO'], {
    erro: {
      origem: 'DOCUMENTO_PROCESSO',
      id: '2',
      titulo: 'Oficio',
      motivo: 'Token da API de arquivos expirado ou recusado: capture um novo token.',
      codigo: 'CREDENCIAL_ARQUIVOS_RECUSADA',
    },
  });
  await simularDecisao(page, [recusado, andamento('CANCELADO', ['OK', 'FALHA', 'AGUARDANDO'])], chamadas);

  await abrirGaveta(page);
  await expect(page.locator('.gaveta-interrompida')).toContainText(
    'Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.',
  );
  await expect(page.getByRole('button', { name: 'Tentar novamente os 2 com problema' })).toBeVisible();
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.locator('.d-head h2')).toHaveText('Geração cancelada');
  expect(chamadas).toEqual(['POST', 'DELETE']);
});

test('pedido expirado à espera de decisão oferece gerar de novo', async ({ page }) => {
  await simularDecisao(page, [andamento('EXPIRADO', ['OK', 'FALHA', 'OK'])], []);

  await abrirGaveta(page);
  await expect(page.locator('.d-head h2')).toHaveText('Dossiê expirado');
  await expect(page.getByRole('button', { name: 'Gerar novamente' })).toBeVisible();
});

// --- dossiê grande (S2.2, D49): download direto por ticket, sem Blob nem pré-visualização

test('dossiê grande: sem pré-visualização; o navegador baixa pela URL do ticket, sem o token de sessão', async ({ page }) => {
  const pedidosDoArquivo: { url: string; autorizacao: string | undefined }[] = [];
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho === '/api/dossies' && metodo === 'POST') return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    if (caminho === '/api/dossies/pedido-1/arquivo/ticket' && metodo === 'POST') {
      return route.fulfill({ json: { ticket: 'TICKET-DE-TESTE', expiraEm: '2099-01-01T00:01:00' } });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      return route.fulfill({
        json: { ...andamento('CONCLUIDO', ['OK', 'OK', 'OK']), tamanhoBytes: 400 * 1024 * 1024, entregaPorTicket: true },
      });
    }
    if (caminho === '/api/dossies/pedido-1/arquivo') {
      pedidosDoArquivo.push({ url: url.search, autorizacao: route.request().headers()['authorization'] });
      return route.fulfill({
        status: 200,
        body: PDF_MINIMO,
        headers: {
          'Content-Type': 'application/pdf',
          'Content-Disposition': "attachment; filename*=UTF-8''dossie-processo-2825-2025.pdf",
        },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });

  await abrirGaveta(page);
  await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto');
  await expect(page.locator('.gaveta-grande')).toContainText('Dossiê grande (400 MB)');
  await expect(page.getByRole('button', { name: 'Pré-visualizar' })).toHaveCount(0);

  const baixando = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  const download = await baixando;

  expect(download.suggestedFilename()).toBe('dossie-processo-2825-2025.pdf');
  expect(pedidosDoArquivo).toEqual([{ url: '?disposicao=attachment&ticket=TICKET-DE-TESTE', autorizacao: undefined }]);
  await expect(page.locator('.d-body')).toContainText('Download enviado ao navegador');
  await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
});

// --- visualização de documentos (S3): modal com PDF por Blob, faixa de convertido, erro, seleção e sigilo

async function simularConteudo(page: Page, resposta: (id: string, chamada: number) => { status: number; corpo?: Buffer | object; headers?: Record<string, string>; atrasoMs?: number }) {
  const pedidos: { id: string; url: string; autorizacao: string | undefined; formato: string | null }[] = [];
  const chamadas = new Map<string, number>();
  await page.route('**/api/documentos/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const id = url.pathname.split('/')[4];
    const chamada = (chamadas.get(id) ?? 0) + 1;
    chamadas.set(id, chamada);
    pedidos.push({ id, url: route.request().url(), autorizacao: route.request().headers()['authorization'], formato: url.searchParams.get('formato') });
    const { status, corpo, headers, atrasoMs } = resposta(id, chamada);
    if (atrasoMs) await new Promise((resolver) => setTimeout(resolver, atrasoMs));
    if (corpo instanceof Buffer) {
      return route.fulfill({ status, body: corpo, headers: { 'Content-Type': 'application/pdf', ...headers } });
    }
    return route.fulfill({ status, json: corpo ?? {}, headers });
  });
  return pedidos;
}

test('Visualizar abre o PDF por Blob (token no cabeçalho, nunca na URL), fecha e não mexe no quadro do topo', async ({ page }) => {
  await simular(page);
  const pedidos = await simularConteudo(page, () => ({ status: 200, corpo: PDF_MINIMO, headers: { 'Content-Disposition': 'inline; filename="requerimento.pdf"' } }));
  await page.goto('/consulta/requerimentos/101');
  await expect(page.locator('tbody tr')).toHaveCount(5);
  const alturaAntes = await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);

  await page.locator('tbody tr', { hasText: 'Requerimento assinado' }).getByRole('button', { name: /Visualizar/ }).click();
  const modal = page.getByRole('dialog', { name: 'requerimento-assinado.pdf' });
  await expect(modal).toBeVisible();
  const moldura = modal.locator('iframe');
  await expect(moldura).toBeVisible();
  expect(await moldura.getAttribute('src')).toMatch(/^blob:/);
  await expect(modal.locator('.vis-faixa')).toHaveCount(0);

  expect(pedidos).toHaveLength(1);
  expect(pedidos[0].formato).toBe('pdf');
  expect(pedidos[0].autorizacao).toBe('Bearer token-de-teste');
  expect(pedidos[0].url).not.toContain('token-de-teste');
  expect(await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height)).toBe(alturaAntes);

  await modal.getByRole('button', { name: 'Fechar' }).click();
  await expect(page.locator('app-visualizador-documento')).toHaveCount(0);
  expect(await page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height)).toBe(alturaAntes);
});

test('planilha: mostra "Convertendo…" e depois a faixa "Versão convertida para PDF a partir de XLSX"', async ({ page }) => {
  await simular(page);
  await simularConteudo(page, () => ({ status: 200, corpo: PDF_MINIMO, headers: { 'X-Vistas-Convertido-De': 'XLSX' }, atrasoMs: 700 }));
  await page.goto('/consulta/requerimentos/101');

  await page.locator('tbody tr', { hasText: 'Calculo' }).getByRole('button', { name: /Visualizar/ }).click();
  const modal = page.locator('app-visualizador-documento');
  await expect(modal).toContainText('Convertendo para PDF…');
  await expect(modal.locator('.vis-faixa')).toContainText('Versão convertida para PDF a partir de XLSX');
  await expect(modal.locator('iframe')).toBeVisible();
});

test('erro na conversão: mensagem pelo errorCode e "Tentar novamente" abre o PDF', async ({ page }) => {
  await simular(page);
  const pedidos = await simularConteudo(page, (_id, chamada) =>
    chamada === 1
      ? { status: 504, corpo: { status: 504, errorCode: 'TEMPO_LIMITE_EXCEDIDO', detail: 'detalhe técnico que nunca deve aparecer', correlationId: 'c-1' } }
      : { status: 200, corpo: PDF_MINIMO },
  );
  await page.goto('/consulta/requerimentos/101');

  await page.locator('tbody tr', { hasText: 'Oficio' }).getByRole('button', { name: /Visualizar/ }).click();
  const modal = page.locator('app-visualizador-documento');
  await expect(modal.locator('.vis-erro')).toContainText('O tempo limite foi excedido');
  await expect(modal).not.toContainText('detalhe técnico');

  await modal.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(modal.locator('iframe')).toBeVisible();
  expect(pedidos).toHaveLength(2);
});

test('selecionar e desmarcar pelo modal reflete na tabela', async ({ page }) => {
  await simular(page);
  await simularConteudo(page, () => ({ status: 200, corpo: PDF_MINIMO }));
  await page.goto('/consulta/requerimentos/101');
  const linha = page.locator('tbody tr', { hasText: 'Oficio' });
  await expect(page.locator('.selbar b')).toHaveText('3');

  await linha.getByRole('button', { name: /Visualizar/ }).click();
  await page.getByRole('button', { name: 'Tirar do dossiê' }).click();
  await expect(linha.locator('input[type=checkbox]')).not.toBeChecked();
  await expect(page.locator('.selbar b')).toHaveText('2');

  await page.getByRole('button', { name: 'Incluir no dossiê' }).click();
  await expect(linha.locator('input[type=checkbox]')).toBeChecked();
  await expect(page.locator('.selbar b')).toHaveText('3');
});

test('sigiloso sem perfil: o modal mostra o motivo e nenhuma requisição de conteúdo é feita', async ({ page }) => {
  await simular(page);
  const pedidos = await simularConteudo(page, () => ({ status: 200, corpo: PDF_MINIMO }));
  await page.goto('/consulta/requerimentos/101');

  await page.locator('tbody tr', { hasText: 'Documento sigiloso' }).getByRole('button', { name: /Visualizar/ }).click();

  await expect(page.locator('app-visualizador-documento .vis-sigiloso')).toContainText('sigiloso e não está disponível para o seu perfil');
  expect(pedidos).toHaveLength(0);
});

test('a coluna do documento mostra o nome do arquivo e o tipo/título na linha de baixo (S6)', async ({ page }) => {
  await simular(page);
  await page.goto('/consulta/requerimentos/101');

  const linha = page.locator('tbody tr', { hasText: 'Requerimento assinado' });
  await expect(linha.locator('b')).toHaveText('requerimento-assinado.pdf');
  await expect(linha.locator('small')).toContainText('Requerimento assinado');
  // sem nome de arquivo (tipo não informado): o título aparece como nome
  await expect(page.locator('tbody tr', { hasText: 'Planta' }).locator('b')).toHaveText('Planta');
});

// --- disco cheio (S6, bloco 3): erro real, nunca sucesso, ações explicadas

const SEM_ESPACO = 'Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.';

async function simularArmazenamento(
  page: Page,
  opcoes: { post?: 'ok' | '507'; status?: 'CONCLUIDO' | 'FALHA'; arquivo?: 'pdf' | 'naoPdf' },
): Promise<{ downloads: number; cancelados: number }> {
  const contagem = { downloads: 0, cancelados: 0 };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: GESTOR });
    if (caminho === '/api/requerimentos/101') return route.fulfill({ json: REQUERIMENTO });
    if (caminho === '/api/requerimentos/101/documentos') return route.fulfill({ json: DOCUMENTOS });
    if (caminho === '/api/dossies' && metodo === 'POST') {
      if (opcoes.post === '507') {
        return route.fulfill({
          status: 507,
          contentType: 'application/problem+json',
          body: JSON.stringify({ status: 507, errorCode: 'ARMAZENAMENTO_INSUFICIENTE', detail: SEM_ESPACO }),
        });
      }
      return route.fulfill({ status: 202, json: { id: 'pedido-1' } });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'DELETE') {
      contagem.cancelados += 1;
      return route.fulfill({ status: 204 });
    }
    if (caminho === '/api/dossies/pedido-1' && metodo === 'GET') {
      const falha = opcoes.status === 'FALHA';
      return route.fulfill({
        json: {
          id: 'pedido-1',
          status: falha ? 'FALHA' : 'CONCLUIDO',
          total: 2,
          processados: falha ? 0 : 2,
          erro: falha ? { motivo: SEM_ESPACO, codigo: 'ARMAZENAMENTO_INSUFICIENTE' } : null,
          falhas: [],
          paginas: falha ? null : 6,
          expiraEm: '2099-01-01T00:00:00',
          itens: ITENS.map((i) => ({ ...i, situacao: falha ? 'AGUARDANDO' : 'OK' })),
        },
      });
    }
    if (caminho === '/api/dossies/pedido-1/arquivo') {
      contagem.downloads += 1;
      return route.fulfill({
        status: 200,
        body: opcoes.arquivo === 'naoPdf' ? Buffer.from('<html>pagina de erro</html>') : PDF_MINIMO,
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': "attachment; filename*=UTF-8''dossie.pdf" },
      });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return contagem;
}

test('disco cheio no POST (507): mensagem real e Tentar novamente, sem sucesso', async ({ page }) => {
  await simularArmazenamento(page, { post: '507' });

  await abrirGaveta(page);

  await expect(page.locator('.d-head h2')).toHaveText('Não foi possível gerar');
  await expect(page.locator('.d-body')).toContainText(SEM_ESPACO);
  await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
  await expect(page.locator('.d-body')).not.toContainText('Dossiê pronto');
});

test('disco cheio na geração (FALHA): motivo, Gerar novamente e Descartar com explicação; Descartar apaga o pedido', async ({ page }) => {
  const contagem = await simularArmazenamento(page, { status: 'FALHA' });

  await abrirGaveta(page);

  await expect(page.locator('.d-head h2')).toHaveText('Não foi possível gerar o dossiê');
  await expect(page.locator('.gaveta-falha')).toContainText(SEM_ESPACO);
  await expect(page.getByRole('button', { name: 'Baixar PDF' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Gerar novamente' })).toBeVisible();
  await expect(page.getByText('Apaga este pedido do servidor e fecha a janela.')).toBeVisible();

  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(page.locator('.drawer')).toHaveCount(0);
  expect(contagem.cancelados).toBe(1);
});

test('corpo que não é PDF nunca vira download nem "entregue"; Tentar novamente baixa de novo', async ({ page }) => {
  const contagem = await simularArmazenamento(page, { arquivo: 'naoPdf' });
  let baixou = false;
  page.on('download', () => (baixou = true));

  await abrirGaveta(page);
  await expect(page.locator('.d-head h2')).toHaveText('Dossiê pronto');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();

  await expect(page.locator('.gaveta-erro')).toContainText('incompleto ou não é um PDF');
  await expect(page.locator('.d-body')).not.toContainText('entregue ao navegador');
  await expect(page.getByRole('button', { name: 'Gerar novamente' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Descartar' })).toBeVisible();
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect.poll(() => contagem.downloads).toBe(2);
  expect(baixou).toBe(false);
});
