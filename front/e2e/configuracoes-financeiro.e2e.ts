import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN, OPERADOR, UsuarioSimulado } from './api-simulada';

/**
 * S9, bloco 10: Configurações › Rastro (parâmetros do financeiro) e o diagnóstico do Administrador, com a API simulada
 * (dados fictícios, só desenvolvimento). O diagnóstico lê a mesma rota do financeiro, com os campos do Administrador.
 */
interface ParametroApi {
  nome: string;
  rotulo: string;
  grupo: string;
  tipo: 'INTEIRO' | 'BOOLEANO';
  valor: string;
  padrao: string;
  minimo: number | null;
  maximo: number | null;
  opcoes: string[] | null;
  classeDeAplicacao: string;
  sobreposto: boolean;
  pendenteDeReinicio: boolean;
}

const base = { opcoes: null, classeDeAplicacao: 'IMEDIATO', sobreposto: false, pendenteDeReinicio: false, grupo: 'RASTRO' };
const CATALOGO = (): ParametroApi[] => [
  { ...base, nome: 'rastro.mostrarNomeResponsavel', rotulo: 'Mostrar o nome do responsável', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null },
  { ...base, nome: 'rastro.mostrarValores', rotulo: 'Mostrar valores das taxas (valor, desconto e UPF)', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null },
  { ...base, nome: 'rastro.financeiroLimiteLancamentos', rotulo: 'Limite de lançamentos de taxa por requerimento', tipo: 'INTEIRO', valor: '200', padrao: '200', minimo: 1, maximo: 1000 },
  { ...base, nome: 'rastro.financeiroTimeoutSegundos', rotulo: 'Tempo limite da consulta financeira (s)', tipo: 'INTEIRO', valor: '10', padrao: '10', minimo: 1, maximo: 120 },
];

const lancamento = (ordem: number, sobre: object = {}) => ({
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
  idTecnico: 9007,
  statusProcessado: 'P',
  inconsistencias: [],
  ...sobre,
});

/** FIN07 + FIN10: um válido, dois ignorados (um falso e um ruído), e uma isenção cuja data veio do fluxo. */
const FINANCEIRO_9007 = {
  idRequerimento: 9007,
  numeroRequerimento: '9007/2026',
  numeroProcesso: '9900/2026',
  estado: 'ATIVO',
  resumo: 'ISENTO',
  mostraValores: true,
  lancamentos: [lancamento(1), lancamento(2, { rotulo: null, situacao: 'ISENTA', numeroDar: null, valor: null, desconto: null, inconsistente: true })],
  isencoes: [{ texto: 'Isento de Taxa', ordem: 1, data: '2026-03-05', dataAConfirmar: true, origemDaData: 'FLUXO', naoProcessado: true }],
  avisos: [],
  ignoradosPorMotivo: { FALSO_SEM_DAR: 1, RUIDO_ISENTO_AGUARDANDO: 1 },
};

interface Controle {
  parametros: ParametroApi[];
  puts: { alteracoes: Record<string, unknown> }[];
  consultas: string[];
  parametrosLidos: number;
}

async function abrir(page: Page, usuario: UsuarioSimulado = ADMIN): Promise<Controle> {
  const c: Controle = { parametros: CATALOGO(), puts: [], consultas: [], parametrosLidos: 0 };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: usuario });
    if (caminho === '/api/busca') return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 20, totalElementos: 0, totalPaginas: 0 } });
    if (caminho === '/api/configuracoes/usuarios') {
      return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 0, totalElementos: 0, totalPaginas: 0 } });
    }
    if (caminho === '/api/configuracoes/parametros' && metodo === 'GET') {
      c.parametrosLidos++;
      return route.fulfill({ json: { parametros: c.parametros } });
    }
    if (caminho === '/api/configuracoes/parametros' && metodo === 'PUT') {
      const corpo = route.request().postDataJSON() as { alteracoes: Record<string, unknown> };
      c.puts.push(corpo);
      for (const q of c.parametros) {
        if (q.nome in corpo.alteracoes) {
          q.valor = String(corpo.alteracoes[q.nome]);
          q.sobreposto = q.valor !== q.padrao;
        }
      }
      return route.fulfill({ json: { parametros: c.parametros } });
    }
    const financeiro = /^\/api\/requerimentos\/(\d+)\/financeiro$/.exec(caminho);
    if (financeiro) {
      c.consultas.push(financeiro[1]);
      if (financeiro[1] === '9007') return route.fulfill({ json: FINANCEIRO_9007 });
      if (financeiro[1] === '9500') {
        return route.fulfill({ json: { ...FINANCEIRO_9007, estado: 'INDISPONIVEL', resumo: null, lancamentos: [], isencoes: [] } });
      }
      return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO', detail: 'detalhe técnico que nunca deve aparecer' } });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto('/configuracoes');
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

const aba = (page: Page, nome: string) => page.getByRole('tab', { name: nome }).click();
const campo = (page: Page, nome: string) => page.locator(`[data-param="${nome}"]`);
const diagnostico = (page: Page) => page.locator('app-diagnostico-financeiro');

test('aba Rastro: os três parâmetros do financeiro, com faixa, padrão e a etiqueta "Vale já", sem credencial', async ({ page }) => {
  await abrir(page);
  await aba(page, 'Rastro');

  await expect(campo(page, 'rastro.mostrarValores').locator('.tag').first()).toHaveText('Vale já');
  await expect(campo(page, 'rastro.financeiroLimiteLancamentos')).toContainText('Faixa: 1 a 1000');
  await expect(campo(page, 'rastro.financeiroLimiteLancamentos')).toContainText('Padrão do ambiente: 200');
  await expect(campo(page, 'rastro.financeiroTimeoutSegundos')).toContainText('Faixa: 1 a 120');
  await expect(page.locator('.cfg-fixo')).toContainText('dinheiro público');
  const texto = await page.locator('main').innerText();
  expect(texto).not.toMatch(/senha|token|SIGA_DB|http(s)?:\/\//i);
});

test('editar e salvar envia um único PUT com os três parâmetros e mostra o resumo', async ({ page }) => {
  const c = await abrir(page);
  await aba(page, 'Rastro');

  await page.getByLabel('Mostrar valores das taxas (valor, desconto e UPF)').uncheck();
  await campo(page, 'rastro.financeiroLimiteLancamentos').getByRole('spinbutton').fill('50');
  await campo(page, 'rastro.financeiroTimeoutSegundos').getByRole('spinbutton').fill('30');
  await expect(page.locator('.cfg-barra')).toContainText('3 alterações pendentes');
  expect(c.puts).toEqual([]);
  await page.getByRole('button', { name: 'Salvar alterações' }).click();

  await expect(page.locator('.cfg-resumo')).toContainText('3 alterações salvas');
  expect(c.puts).toEqual([
    { alteracoes: { 'rastro.mostrarValores': false, 'rastro.financeiroLimiteLancamentos': 50, 'rastro.financeiroTimeoutSegundos': 30 } },
  ]);
});

test('diagnóstico: ignorados por motivo e origem da data da isenção, sem valor, DAR, id de lançamento nem CPF', async ({ page }) => {
  const c = await abrir(page);
  await aba(page, 'Rastro');

  await diagnostico(page).getByLabel('Identificador do requerimento').fill('9007');
  await diagnostico(page).getByRole('button', { name: 'Consultar' }).click();

  const resultado = diagnostico(page).locator('.diag-resultado');
  await expect(resultado).toContainText('Requerimento 9007/2026');
  await expect(resultado.locator('.diag-lista')).toContainText('Lançamentos mantidos');
  await expect(resultado.locator('[aria-label="Lançamentos ignorados, por motivo"] li')).toHaveText([
    /Cancelado sem número de DAR.*1/,
    /Aguardando emissão marcado como isento.*1/,
  ]);
  await expect(resultado.locator('[aria-label="Origem da data de cada isenção"] li')).toContainText('1ª isenção: data da passagem "Isenção confirmada" da tramitação (a confirmar)');
  await expect(resultado.locator('[aria-label="Origem da data de cada isenção"] li')).toContainText('não processado');
  expect(c.consultas).toEqual(['9007']);
  const texto = await diagnostico(page).innerText();
  expect(texto).not.toMatch(/FICT-|R\$|980|9007\b(?!\/)|\d{3}\.\d{3}\.\d{3}-\d{2}|(?<!\d)\d{11}(?!\d)|idTecnico|statusProcessado/);
});

test('diagnóstico: identificador vazio, inexistente e consulta indisponível mostram aviso neutro', async ({ page }) => {
  await abrir(page);
  await aba(page, 'Rastro');
  const consultar = diagnostico(page).getByRole('button', { name: 'Consultar' });

  await consultar.click();
  await expect(diagnostico(page).getByRole('alert')).toContainText('Informe o identificador do requerimento');

  await diagnostico(page).getByLabel('Identificador do requerimento').fill('1234');
  await consultar.click();
  await expect(diagnostico(page).getByRole('alert')).toBeVisible();
  await expect(diagnostico(page)).not.toContainText('detalhe técnico que nunca deve aparecer');
  await expect(diagnostico(page).locator('.diag-resultado')).toHaveCount(0);

  await diagnostico(page).getByLabel('Identificador do requerimento').fill('9500');
  await consultar.click();
  await expect(diagnostico(page).getByRole('alert')).toContainText('Não foi possível consultar o financeiro agora');
});

test('o Operador não vê a seção: é levado à consulta e nenhum parâmetro é lido', async ({ page }) => {
  const c = await abrir(page, OPERADOR);

  await expect(page).toHaveURL(/\/consulta$/);
  await expect(page.locator('app-diagnostico-financeiro')).toHaveCount(0);
  expect(c.parametrosLidos).toBe(0);
});

test.describe('celular (360 px)', () => {
  test.use({ viewport: { width: 360, height: 800 } });

  test('o diagnóstico cabe na tela, sem rolagem horizontal', async ({ page }) => {
    await abrir(page);
    await aba(page, 'Rastro');
    await diagnostico(page).getByLabel('Identificador do requerimento').fill('9007');
    await diagnostico(page).getByRole('button', { name: 'Consultar' }).click();

    await expect(diagnostico(page).locator('.diag-resultado')).toBeVisible();
    const medidas = await page.evaluate(() => ({ rolagem: document.documentElement.scrollWidth, janela: window.innerWidth }));
    expect(medidas.rolagem).toBeLessThanOrEqual(medidas.janela);
  });
});
