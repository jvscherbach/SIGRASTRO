import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, GESTOR, OPERADOR, REQUERIMENTO, UsuarioSimulado } from './api-simulada';

/**
 * S8, bloco 8: seção "Tramitação" (linha do tempo com quem esteve) no detalhe do requerimento, e o teste de regressão da
 * P39 (o termo "Camunda" não aparece em nenhuma tela de consulta, de nenhum perfil). API simulada (só desenvolvimento),
 * dados fictícios no formato de `GET /api/requerimentos/{id}/tramitacao` (docs/07), com os campos do Administrador
 * misturados para provar que a tela não os mostra.
 */
const dia = (n: number, hora = 9): string => `2026-03-${String(n).padStart(2, '0')}T${String(hora).padStart(2, '0')}:00:00`;

type Tipo = 'TAREFA_HUMANA' | 'ESPERA' | 'AUTOMATICA' | 'EMISSAO';
const etapa = (id: string, rotulo: string, tipo: Tipo = 'TAREFA_HUMANA') => ({ id, rotulo, tipo });
const CADASTRO = etapa('cadastro_do_requerimento', 'Cadastro do requerimento');
const CONFERENCIA = etapa('conferencia_dos_documentos', 'Conferência dos documentos');
const TAXA = etapa('emissao_da_taxa', 'Emissão da taxa', 'AUTOMATICA');
const PAGAMENTO = etapa('aguardando_pagamento', 'Aguardando pagamento', 'ESPERA');
const ANALISE = etapa('analise_do_processo', 'Análise do processo');
const REVISAO = etapa('revisao_do_parecer_tecnico', 'Revisão do parecer técnico');
const VALIDACAO = etapa('validacao_do_parecer_tecnico', 'Validação do parecer técnico');
const EMISSAO = etapa('emissao_do_ato', 'Emissão do ato', 'EMISSAO');
const CORRECAO = etapa('correcao_do_requerimento', 'Correção do requerimento');
const VERIFICACAO = etapa('verificacao_da_revisao_do_processo', 'Verificação da revisão do processo');

function pessoa(papel: string, nome: string | null, estado = 'CONCLUIU') {
  return { tipo: nome ? 'SERVIDOR' : 'NAO_IDENTIFICADO', papel, nome, inicio: dia(3), fim: estado === 'PENDENTE' ? null : dia(4), estado };
}

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
    // campos do Administrador misturados: a tela os ignora
    idTecnico: 'taskFicticiaDoAdministrador',
    repeticoes: 1,
    ...sobre,
  };
}

const volta = (codigo: string, texto: string) => ({ codigo, texto });

function tramitacao(passagens: object[], sobre: object = {}) {
  return {
    idRequerimento: 101,
    numeroRequerimento: '3331/2025',
    numeroProcesso: '2825/2025',
    situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
    modelo: 'Outorga de recursos hídricos',
    estado: 'ATIVO',
    aproximado: false,
    truncado: false,
    passagens,
    complementacoes: [],
    taxa: { principal: 'TAXAVEL', correcao: null },
    final: null,
    avisos: [],
    planoB: null,
    idsNaoMapeados: ['taskFicticiaNaoMapeada'],
    diagnosticos: ['ROTA_DIVERGE_DO_STATUS'],
    ...sobre,
  };
}

const TRES_ANALISTAS = [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', 'Servidor Exemplo 06'), pessoa('Analista', null)];

const HC01 = tramitacao(
  [
    passagem(CADASTRO, 2, 3, { responsaveis: [pessoa('Interessado (cadastrante)', null)] }),
    passagem(CONFERENCIA, 3, 4, { responsaveis: [pessoa('Conferidor', 'Servidor Exemplo 02')] }),
    passagem(TAXA, 4, 4),
    passagem(PAGAMENTO, 4, 7),
    passagem(ANALISE, 7, 9, { participantes: TRES_ANALISTAS, duracaoSegundos: 2 * 86400 + 3 * 3600 }),
    passagem(VERIFICACAO, 9, null, { processoConcluido: true }),
  ],
  { final: { rotulo: 'Concluído', tipo: 'CONCLUIDO_NA_VERIFICACAO', encerradoEm: dia(9), idTecnico: 'Event_1wiuc48' } },
);

const HC03 = tramitacao([
  passagem(CADASTRO, 2, 3),
  passagem(CONFERENCIA, 3, 4),
  passagem(CADASTRO, 4, 5, { ciclo: 2, motivoDaVolta: volta('REPROVADO_NA_CONFERENCIA', 'devolvido na conferência dos documentos') }),
  passagem(CONFERENCIA, 5, null, { ciclo: 2 }),
]);

const HC04 = tramitacao([
  passagem(ANALISE, 2, 3, { participantes: TRES_ANALISTAS }),
  passagem(REVISAO, 3, 4),
  passagem(VALIDACAO, 4, 5, { participantes: TRES_ANALISTAS }),
  passagem(ANALISE, 5, 6, { ciclo: 2, participantes: TRES_ANALISTAS, motivoDaVolta: volta('DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE', 'devolvido na validação do parecer') }),
  passagem(REVISAO, 6, 7, { ciclo: 2 }),
  passagem(VALIDACAO, 7, 8, { ciclo: 2, participantes: TRES_ANALISTAS }),
  passagem(REVISAO, 8, null, { ciclo: 3, motivoDaVolta: volta('DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO', 'devolvido na validação do parecer') }),
]);

const HC07 = tramitacao(
  [
    passagem(ANALISE, 2, 5, { participantes: TRES_ANALISTAS }),
    passagem(CORRECAO, 5, 7, { ramo: 'CORRECAO' }),
    passagem(etapa('emissao_da_taxa_correcao', 'Emissão da taxa (correção)', 'AUTOMATICA'), 7, 7, { ramo: 'CORRECAO' }),
    passagem(etapa('aguardando_pagamento_correcao', 'Aguardando pagamento (correção)', 'ESPERA'), 7, 9, { ramo: 'CORRECAO' }),
    passagem(ANALISE, 9, null, { ciclo: 2, participantes: TRES_ANALISTAS, motivoDaVolta: volta('CORRECAO_DO_REQUERIMENTO', 'depois da correção do requerimento') }),
  ],
  { taxa: { principal: 'TAXAVEL', correcao: 'TAXAVEL' } },
);

const HC10 = tramitacao([
  passagem(ANALISE, 2, 5, {
    participantes: [pessoa('Analista', 'Servidor Exemplo 05'), pessoa('Analista', 'Servidor Exemplo 06', 'ENCERRADA_SEM_ACAO'), pessoa('Analista', 'Servidor Exemplo 07', 'ENCERRADA_SEM_ACAO')],
  }),
  passagem(CORRECAO, 5, null, { ramo: 'CORRECAO' }),
]);

const HC12 = tramitacao([
  passagem(VALIDACAO, 2, 3, { participantes: TRES_ANALISTAS }),
  passagem(EMISSAO, 3, 4, {
    atos: [
      { tipo: 'AUTORIZACAO', rotulo: 'Autorização', inicio: dia(3), fim: dia(4), estado: 'EMITIDO', ocorreu: true },
      { tipo: 'PORTARIA', rotulo: 'Portaria', inicio: dia(3), fim: dia(4), estado: 'EMITIDO', ocorreu: true },
      { tipo: 'OUTROS_ATOS', rotulo: 'Outros atos', inicio: dia(3), fim: dia(4), estado: 'NAO_CONCLUIDO', ocorreu: false },
    ],
  }),
  passagem(REVISAO, 4, null, { ciclo: 2, motivoDaVolta: volta('DEVOLVIDO_NA_EMISSAO', 'devolvido na emissão do ato') }),
]);

const LC03 = tramitacao(
  [
    passagem(CADASTRO, 2, 3),
    passagem(CONFERENCIA, 3, 4),
    passagem(CORRECAO, 4, 6, { ramo: 'CORRECAO' }),
    passagem(CONFERENCIA, 6, 7, { ciclo: 2, motivoDaVolta: volta('CORRECAO_DO_REQUERIMENTO', 'depois da correção do requerimento') }),
    passagem(TAXA, 7, 7),
    passagem(PAGAMENTO, 7, null),
  ],
  { modelo: 'Licença por Adesão e Compromisso (LAC)' },
);

const POSICAO = {
  idRequerimento: 101,
  numeroRequerimento: '3331/2025',
  numeroProcesso: '2825/2025',
  situacaoLicenciamento: { codigo: 'EM_ANALISE', descricao: 'Em análise' },
  modelo: 'Outorga de recursos hídricos',
  estado: 'ATIVO',
  etapaAtual: { id: 'revisao_do_parecer_tecnico', rotulo: 'Revisão do parecer técnico', tipo: 'TAREFA_HUMANA', desde: dia(8), desdeUltimaEntrada: false, responsavel: { tipo: 'SERVIDOR', papel: 'Chefia', area: null, nome: 'Servidor Exemplo 04', texto: 'Servidor Exemplo 04' } },
  emParalelo: [],
  complementacao: null,
  multiInstancia: null,
  avisos: [],
  final: null,
};

interface Opcoes {
  tramitacoes: (object | number)[];
  posicao?: object | number;
  usuario?: UsuarioSimulado;
}

async function simular(page: Page, { tramitacoes, posicao = POSICAO, usuario = GESTOR }: Opcoes): Promise<{ chamadas: { tramitacao: number } }> {
  const contagem = { tramitacao: 0 };
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
    if (caminho === '/api/requerimentos/101/posicao') {
      return typeof posicao === 'number'
        ? route.fulfill({ status: posicao, json: { status: posicao, errorCode: 'SIGA_INDISPONIVEL', detail: 'detalhe técnico que nunca deve aparecer' } })
        : route.fulfill({ json: posicao });
    }
    if (caminho === '/api/requerimentos/101/tramitacao') {
      const resposta = tramitacoes[Math.min(contagem.tramitacao, tramitacoes.length - 1)];
      contagem.tramitacao++;
      return typeof resposta === 'number'
        ? route.fulfill({ status: resposta, json: { status: resposta, errorCode: 'SIGA_INDISPONIVEL', detail: 'detalhe técnico que nunca deve aparecer' } })
        : route.fulfill({ json: resposta });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  return { chamadas: contagem };
}

const secao = (page: Page) => page.locator('app-tramitacao');
const rotulos = (page: Page) => secao(page).locator('.tram-lista > li .tram-titulo b');

async function semIdTecnicoNemCpf(page: Page): Promise<void> {
  const texto = await secao(page).innerText();
  expect(texto).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga|ROTA_DIVERGE/);
  expect(texto).not.toMatch(/\d{3}\.\d{3}\.\d{3}-\d{2}/);
  expect(texto).not.toMatch(/(?<!\d)\d{11}(?!\d)/);
}

test.describe('desktop', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('HC01: a lista na ordem, com duração, com quem, etapa atual e final; filtro e recolher pelo teclado', async ({ page }) => {
    await simular(page, { tramitacoes: [HC01] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).getByRole('heading', { name: 'Tramitação' })).toBeVisible();
    // acima dos documentos e abaixo de "Onde está agora"
    const posicoes = await page.evaluate(() => {
      const topo = (s: string) => document.querySelector(s)!.getBoundingClientRect().top;
      return { onde: topo('app-onde-esta-agora'), tram: topo('app-tramitacao'), objeto: topo('[aria-label="Objeto do requerimento"]'), docs: topo('app-documentos-secao') };
    });
    expect(posicoes.tram).toBeGreaterThan(posicoes.onde);
    expect(posicoes.tram).toBeLessThan(posicoes.objeto);
    expect(posicoes.tram).toBeLessThan(posicoes.docs);

    await expect(rotulos(page)).toHaveText(['Cadastro do requerimento', 'Conferência dos documentos', 'Aguardando pagamento', 'Análise do processo', 'Verificação da revisão do processo']);
    await expect(secao(page)).toContainText('Duração 2 dias e 3 h');
    await expect(secao(page)).toContainText('Servidor Exemplo 02 · Conferidor');
    await expect(secao(page).getByRole('list', { name: 'Participantes da etapa' }).getByRole('listitem')).toHaveCount(3);
    await expect(secao(page).locator('.tram-item.atual')).toContainText('Etapa atual');
    await expect(secao(page).locator('.tram-final')).toContainText('Concluído em 09/03/2026');
    await expect(secao(page).locator('time').first()).toHaveAttribute('datetime', dia(2));

    // filtro: ligado por padrão; a lista completa a um clique (teclado)
    const filtro = secao(page).getByRole('checkbox', { name: 'Ocultar etapas automáticas' });
    await expect(filtro).toBeChecked();
    await filtro.focus();
    await page.keyboard.press(' ');
    await expect(filtro).not.toBeChecked();
    await expect(rotulos(page)).toHaveCount(6);
    await expect(rotulos(page).nth(2)).toHaveText('Emissão da taxa');

    // recolher e expandir pelo teclado
    const botao = secao(page).getByRole('button', { name: 'Recolher' });
    await botao.focus();
    await page.keyboard.press('Enter');
    await expect(secao(page).getByRole('button', { name: 'Expandir' })).toHaveAttribute('aria-expanded', 'false');
    await expect(secao(page).locator('.tram-lista')).toHaveCount(0);
    await page.keyboard.press('Enter');
    await expect(secao(page).locator('.tram-lista')).toBeVisible();

    await semIdTecnicoNemCpf(page);
  });

  const CENARIOS: { nome: string; resposta: object; esperado: string[]; texto: string[] }[] = [
    {
      nome: 'HC03: volta ao cadastro depois de reprovar na conferência',
      resposta: HC03,
      esperado: ['Cadastro do requerimento', 'Conferência dos documentos', 'Cadastro do requerimento', 'Conferência dos documentos'],
      texto: ['2ª passagem: devolvido na conferência dos documentos'],
    },
    {
      nome: 'HC04: devolvida na validação (análise e depois revisão)',
      resposta: HC04,
      esperado: ['Análise do processo', 'Revisão do parecer técnico', 'Validação do parecer técnico', 'Análise do processo', 'Revisão do parecer técnico', 'Validação do parecer técnico', 'Revisão do parecer técnico'],
      texto: ['2ª passagem: devolvido na validação do parecer', '3ª passagem: devolvido na validação do parecer'],
    },
    {
      nome: 'HC07: correção com nova taxa, retorno direto à análise',
      resposta: HC07,
      esperado: ['Análise do processo', 'Correção do requerimento', 'Aguardando pagamento (correção)', 'Análise do processo'],
      texto: ['Ramo da correção', '2ª passagem: depois da correção do requerimento'],
    },
    {
      nome: 'HC10: tarefas encerradas sem ação',
      resposta: HC10,
      esperado: ['Análise do processo', 'Correção do requerimento'],
      texto: ['Encerrada sem ação', 'Concluiu'],
    },
    {
      nome: 'HC12: emissão com atos e devolvida para a revisão',
      resposta: HC12,
      esperado: ['Validação do parecer técnico', 'Emissão do ato', 'Revisão do parecer técnico'],
      texto: ['Autorização: Emitido', 'Portaria: Emitido', 'Outros atos: Não concluído', '2ª passagem: devolvido na emissão do ato'],
    },
    {
      nome: 'LC03: reprova, correção e volta à conferência (LAC)',
      resposta: LC03,
      esperado: ['Cadastro do requerimento', 'Conferência dos documentos', 'Correção do requerimento', 'Conferência dos documentos', 'Aguardando pagamento'],
      texto: ['2ª passagem: depois da correção do requerimento'],
    },
  ];

  for (const c of CENARIOS) {
    test(c.nome, async ({ page }) => {
      await simular(page, { tramitacoes: [c.resposta] });
      await page.goto('/consulta/requerimentos/101');

      await expect(rotulos(page)).toHaveText(c.esperado);
      for (const t of c.texto) {
        await expect(secao(page)).toContainText(t);
      }
      await semIdTecnicoNemCpf(page);
    });
  }

  test('falha da tramitação não afeta "Onde está agora" nem o detalhe; "Tentar novamente" lê de novo', async ({ page }) => {
    const sim = await simular(page, { tramitacoes: [503, HC03] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page).getByRole('alert')).toBeVisible();
    await expect(secao(page)).not.toContainText('detalhe técnico');
    await expect(page.locator('app-onde-esta-agora .onde-etapa')).toHaveText('Revisão do parecer técnico');
    await expect(page.getByRole('heading', { name: 'Objeto do requerimento' })).toBeVisible();

    await secao(page).getByRole('button', { name: 'Tentar novamente' }).click();
    await expect(rotulos(page)).toHaveCount(4);
    expect(sim.chamadas.tramitacao).toBe(2);
  });

  test('falha da posição não afeta a tramitação', async ({ page }) => {
    await simular(page, { tramitacoes: [HC03], posicao: 503 });
    await page.goto('/consulta/requerimentos/101');

    await expect(page.locator('app-onde-esta-agora').getByRole('alert')).toBeVisible();
    await expect(rotulos(page)).toHaveCount(4);
  });

  test('o Operador não vê nome de servidor', async ({ page }) => {
    const semNomes = tramitacao([passagem(CONFERENCIA, 3, 4, { responsaveis: [pessoa('Conferidor', null)] }), passagem(ANALISE, 4, null, { participantes: [pessoa('Analista', null, 'PENDENTE')] })]);
    await simular(page, { tramitacoes: [semNomes], usuario: OPERADOR });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page)).toContainText('Conferidor');
    await expect(secao(page)).not.toContainText('Servidor Exemplo');
  });
});

test.describe('celular (360 px)', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test('sem rolagem horizontal da página e controles alcançáveis por teclado', async ({ page }) => {
    await simular(page, { tramitacoes: [HC04] });
    await page.goto('/consulta/requerimentos/101');

    await expect(rotulos(page)).toHaveCount(7);
    const largura = await page.evaluate(() => ({ rolagem: document.documentElement.scrollWidth, janela: window.innerWidth }));
    expect(largura.rolagem).toBeLessThanOrEqual(largura.janela);

    await secao(page).getByRole('checkbox', { name: 'Ocultar etapas automáticas' }).focus();
    await expect(secao(page).getByRole('checkbox', { name: 'Ocultar etapas automáticas' })).toBeFocused();
  });
});

/** S8, bloco 9: fluxograma simplificado (HTML, CSS e SVG), em linha reta, só com o que passou. */
const ISENCAO = etapa('isencao_confirmada', 'Isenção confirmada', 'AUTOMATICA');
const HC02 = tramitacao([passagem(CADASTRO, 2, 3), passagem(CONFERENCIA, 3, 4), passagem(ISENCAO, 4, 4), passagem(ANALISE, 4, null, { participantes: TRES_ANALISTAS.map((x, i) => (i === 0 ? x : { ...x, estado: 'PENDENTE', fim: null })) })], {
  taxa: { principal: 'ISENTA', correcao: null },
});
/** HC15: passou do limite de linhas; o que coube vem com o aviso. */
const HC15 = tramitacao([passagem(CADASTRO, 2, 3), passagem(CONFERENCIA, 3, 4), passagem(TAXA, 4, 4), passagem(PAGAMENTO, 4, null)], {
  truncado: true,
  avisos: [{ codigo: 'HISTORICO_TRUNCADO', texto: 'Histórico incompleto: este requerimento tem registros demais e só parte do caminho é mostrada.', gravidade: 'ATENCAO' }],
});
const encerrado = (rotulo: string, modelo = 'Outorga de recursos hídricos') =>
  tramitacao([passagem(CADASTRO, 2, 3), passagem(CONFERENCIA, 3, 4)], { estado: 'ENCERRADO', modelo, final: { rotulo, tipo: 'FINAL', encerradoEm: dia(12), idTecnico: 'Event_FicticioDoAdministrador' } });

const fluxo = (page: Page) => page.locator('app-fluxograma-simplificado');
const caixas = (page: Page) => fluxo(page).locator('.caixa .caixa-rotulo');

test.describe('fluxograma simplificado (desktop)', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('HC01: caixas na ordem, etapa atual destacada, estado em texto, data e "Processo concluído"; sem bola (sem evento de fim)', async ({ page }) => {
    await simular(page, { tramitacoes: [HC01] });
    await page.goto('/consulta/requerimentos/101');

    await expect(caixas(page)).toHaveText(['Cadastro do requerimento', 'Conferência dos documentos', 'Emissão da taxa', 'Aguardando pagamento', 'Análise do processo', 'Verificação da revisão do processo']);
    await expect(fluxo(page).locator('.caixa.atual')).toContainText('Etapa atual');
    await expect(fluxo(page).locator('.caixa.atual')).toContainText('Processo concluído');
    await expect(fluxo(page).locator('.caixa').first()).toContainText('02/03/2026');
    await expect(fluxo(page).locator('.caixa').nth(4)).toContainText('3 analistas');
    await expect(fluxo(page).locator('svg.bola')).toHaveCount(0);
    await expect(fluxo(page).getByRole('group')).toHaveAttribute('aria-label', /6 etapas percorridas/);
  });

  test('HC02: isento mostra "Isenção confirmada" e a análise com "1 de 3 concluídos"', async ({ page }) => {
    await simular(page, { tramitacoes: [HC02] });
    await page.goto('/consulta/requerimentos/101');

    await expect(caixas(page)).toHaveText(['Cadastro do requerimento', 'Conferência dos documentos', 'Isenção confirmada', 'Análise do processo']);
    await expect(fluxo(page).locator('.caixa').last()).toContainText('1 de 3 concluídos');
  });

  test('HC03: a volta é nova caixa com "2ª passagem", sem seta de retorno', async ({ page }) => {
    await simular(page, { tramitacoes: [HC03] });
    await page.goto('/consulta/requerimentos/101');

    await expect(caixas(page)).toHaveText(['Cadastro do requerimento', 'Conferência dos documentos', 'Cadastro do requerimento', 'Conferência dos documentos']);
    await expect(fluxo(page).locator('.caixa-passagem')).toHaveText(['2ª passagem', '2ª passagem']);
    // uma seta entre cada caixa e nenhuma de volta: n - 1 setas na linha
    await expect(fluxo(page).locator('svg.seta')).toHaveCount(3);
  });

  test('HC07: a correção numa faixa identificada, com a taxa e o pagamento dela', async ({ page }) => {
    await simular(page, { tramitacoes: [HC07] });
    await page.goto('/consulta/requerimentos/101');

    const faixa = fluxo(page).locator('li.faixa');
    await expect(faixa.locator('.faixa-rotulo')).toHaveText('Correção do requerimento');
    await expect(faixa.locator('.caixa .caixa-rotulo')).toHaveText(['Correção do requerimento', 'Emissão da taxa (correção)', 'Aguardando pagamento (correção)']);
    await expect(caixas(page)).toHaveCount(5);
  });

  test('HC12: "Emissão do ato" é uma caixa só e lista só os atos que ocorreram', async ({ page }) => {
    await simular(page, { tramitacoes: [HC12] });
    await page.goto('/consulta/requerimentos/101');

    const emissao = fluxo(page).locator('.caixa', { hasText: 'Emissão do ato' });
    await expect(emissao).toHaveCount(1);
    await expect(emissao.locator('.caixa-detalhe')).toHaveText('Autorização, Portaria');
  });

  test('HC15: o que coube é desenhado e o aviso de histórico incompleto aparece', async ({ page }) => {
    await simular(page, { tramitacoes: [HC15] });
    await page.goto('/consulta/requerimentos/101');

    await expect(caixas(page)).toHaveCount(4);
    await expect(fluxo(page).locator('.caixa.aguardando')).toContainText('Aguardando');
    await expect(secao(page)).toContainText('Histórico incompleto');
  });

  for (const [rotulo, modelo] of [
    ['Concluído', 'Outorga de recursos hídricos'],
    ['Cancelado pelo cadastrante', 'Outorga de recursos hídricos'],
    ['Expirado no cadastro', 'Outorga de recursos hídricos'],
    ['Encerrado por taxa não paga no prazo', 'Outorga de recursos hídricos'],
    ['Expirado na correção', 'Licença por Adesão e Compromisso (LAC)'],
    ['Cancelado por falta de pagamento', 'Licença por Adesão e Compromisso (LAC)'],
  ] as const) {
    test(`final "${rotulo}": bola preta com o rótulo escrito em cima`, async ({ page }) => {
      await simular(page, { tramitacoes: [encerrado(rotulo, modelo)] });
      await page.goto('/consulta/requerimentos/101');

      const final = fluxo(page).locator('li.final');
      await expect(final.locator('.final-rotulo')).toHaveText(rotulo);
      await expect(final.locator('svg.bola')).toHaveAttribute('aria-label', `Final: ${rotulo}`);
      const [rotuloY, bolaY] = await Promise.all([
        final.locator('.final-rotulo').evaluate((e) => e.getBoundingClientRect().bottom),
        final.locator('svg.bola').evaluate((e) => e.getBoundingClientRect().top),
      ]);
      expect(rotuloY).toBeLessThanOrEqual(bolaY + 1);
      await expect(fluxo(page).locator('polygon')).toHaveCount(0);
      expect(await fluxo(page).innerText()).not.toMatch(/task|gtw|gwt|Event_|Activity_|procSurh|procOutorga/);
    });
  }

  test('o arquivado do LAC leva o rótulo curto "Arquivado"', async ({ page }) => {
    await simular(page, { tramitacoes: [encerrado('Arquivado (termo não enviado no prazo)', 'Licença por Adesão e Compromisso (LAC)')] });
    await page.goto('/consulta/requerimentos/101');

    await expect(fluxo(page).locator('.final-rotulo')).toHaveText('Arquivado');
  });

  test('tooltip por teclado: a dica aparece no foco e traz entrada, saída e duração', async ({ page }) => {
    await simular(page, { tramitacoes: [HC01] });
    await page.goto('/consulta/requerimentos/101');

    const primeira = fluxo(page).locator('.caixa').first();
    const dica = primeira.locator('.dica');
    await expect(dica).toBeHidden();
    await primeira.focus();
    await expect(dica).toBeVisible();
    await expect(dica).toContainText('Entrada: 02/03/2026 09:00');
    await expect(dica).toContainText('Duração: 1 dia');
    await expect(primeira).toHaveAttribute('aria-describedby', (await dica.getAttribute('id'))!);
  });

  test('plano B, não suportado e indisponível não desenham fluxograma', async ({ page }) => {
    await simular(page, { tramitacoes: [tramitacao([], { estado: 'NAO_SUPORTADO', modelo: null })] });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page)).toContainText('Fluxo ainda não suportado pelo Rastro.');
    await expect(fluxo(page).locator('.fluxo')).toHaveCount(0);
    await expect(secao(page).getByRole('button', { name: 'Imprimir tramitação' })).toHaveCount(0);
  });

  test('impressão em preto e branco: só a tramitação sai, e o estado se distingue por forma e texto', async ({ page }) => {
    await simular(page, { tramitacoes: [HC15] });
    await page.goto('/consulta/requerimentos/101');
    await expect(secao(page).getByRole('button', { name: 'Imprimir tramitação' })).toBeVisible();

    await page.evaluate(() => document.body.classList.add('imprimindo-tramitacao'));
    await page.emulateMedia({ media: 'print' });

    await expect(page.locator('.dash-hero')).toHaveCSS('visibility', 'hidden');
    await expect(secao(page)).toHaveCSS('visibility', 'visible');
    const estilos = await fluxo(page).locator('.caixa').evaluateAll((els) => els.map((e) => ({ classe: e.className, borda: getComputedStyle(e).borderTopStyle, largura: getComputedStyle(e).borderTopWidth })));
    expect(estilos.find((e) => e.classe.includes('concluida'))!.borda).toBe('solid');
    expect(estilos.find((e) => e.classe.includes('aguardando'))!.borda).toBe('dashed');
    // o texto do estado está lá, não só a cor
    await expect(fluxo(page).locator('.caixa-estado').last()).toContainText('Aguardando');
    await expect(secao(page).locator('.tram-acoes')).toBeHidden();
  });
});

test.describe('fluxograma simplificado (celular 360 px)', () => {
  test.use({ viewport: { width: 360, height: 740 } });

  test('a linha quebra em várias linhas, sem rolagem horizontal da página', async ({ page }) => {
    await simular(page, { tramitacoes: [HC07] });
    await page.goto('/consulta/requerimentos/101');

    await expect(caixas(page)).toHaveCount(5);
    const medidas = await page.evaluate(() => ({ rolagem: document.documentElement.scrollWidth, janela: window.innerWidth }));
    expect(medidas.rolagem).toBeLessThanOrEqual(medidas.janela);
    const topos = await fluxo(page).locator('.caixa').evaluateAll((els) => new Set(els.map((e) => Math.round(e.getBoundingClientRect().top))).size);
    expect(topos).toBeGreaterThan(1);
  });

  test('com final, ainda sem rolagem horizontal', async ({ page }) => {
    await simular(page, { tramitacoes: [encerrado('Cancelado por falta de pagamento', 'Licença por Adesão e Compromisso (LAC)')] });
    await page.goto('/consulta/requerimentos/101');

    await expect(fluxo(page).locator('svg.bola')).toBeVisible();
    const medidas = await page.evaluate(() => ({ rolagem: document.documentElement.scrollWidth, janela: window.innerWidth }));
    expect(medidas.rolagem).toBeLessThanOrEqual(medidas.janela);
  });
});

/** P39 (D83): o termo do motor de fluxo não aparece em nenhuma tela de consulta, para nenhum perfil. */
test.describe('P39: o termo "Camunda" fora das telas de consulta', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  for (const [perfil, usuario] of [['Operador', OPERADOR], ['Gestor', GESTOR], ['Administrador', ADMIN]] as const) {
    test(`${perfil}: busca e detalhe (onde está agora, tramitação, objeto e documentos) sem o termo`, async ({ page }) => {
      await simular(page, { tramitacoes: [HC01], usuario });

      await page.goto('/consulta');
      await expect(page.locator('body')).not.toContainText(/camunda/i);

      await page.goto('/consulta/requerimentos/101');
      await expect(rotulos(page)).toHaveCount(5);
      await expect(page.locator('app-onde-esta-agora .onde-etapa')).toBeVisible();
      await expect(page.locator('body')).not.toContainText(/camunda/i);
      expect(await page.content()).not.toMatch(/camunda/i);
    });
  }

  test('INDISPONIVEL e erro de rede também não citam o motor de fluxo', async ({ page }) => {
    await simular(page, { tramitacoes: [tramitacao([], { estado: 'INDISPONIVEL' })], posicao: 503 });
    await page.goto('/consulta/requerimentos/101');

    await expect(secao(page)).toContainText('Não foi possível consultar a tramitação agora');
    await expect(page.locator('body')).not.toContainText(/camunda/i);
  });
});
