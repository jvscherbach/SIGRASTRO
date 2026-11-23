import { Page, Route, expect, test } from '@playwright/test';

import { ADMIN } from './api-simulada';

/** S5, bloco 10: Configurações › parâmetros, conexões e retenção com a API simulada (dados fictícios). */
interface ParametroApi {
  nome: string;
  rotulo: string;
  grupo: string;
  tipo: 'INTEIRO' | 'BOOLEANO' | 'OPCAO';
  valor: string;
  padrao: string;
  minimo: number | null;
  maximo: number | null;
  opcoes: string[] | null;
  classeDeAplicacao: string;
  sobreposto: boolean;
  pendenteDeReinicio: boolean;
}

function p(sobre: Partial<ParametroApi> & Pick<ParametroApi, 'nome' | 'rotulo' | 'grupo'>): ParametroApi {
  return {
    tipo: 'INTEIRO',
    valor: '30',
    padrao: '30',
    minimo: 5,
    maximo: 240,
    opcoes: null,
    classeDeAplicacao: 'PROXIMA_GERACAO',
    sobreposto: false,
    pendenteDeReinicio: false,
    ...sobre,
  };
}

const CATALOGO = (): ParametroApi[] => [
  p({ nome: 'dossie.ttlMinutos', rotulo: 'Expiração do dossiê (min)', grupo: 'DOSSIE' }),
  p({ nome: 'dossie.threads', rotulo: 'Gerações simultâneas', grupo: 'DOSSIE', valor: '2', padrao: '2', minimo: 1, maximo: 8, classeDeAplicacao: 'EXIGE_REINICIO' }),
  p({ nome: 'dossie.sumario', rotulo: 'Sumário', grupo: 'DOSSIE', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null }),
  p({ nome: 'dossie.origem.JUNTADA', rotulo: 'Origem: Juntadas', grupo: 'DOSSIE', tipo: 'BOOLEANO', valor: 'true', padrao: 'true', minimo: null, maximo: null, classeDeAplicacao: 'IMEDIATO' }),
  p({ nome: 'conversao.planilhaModo', rotulo: 'Planilhas', grupo: 'CONVERSAO', tipo: 'OPCAO', valor: 'LARGURA', padrao: 'LARGURA', minimo: null, maximo: null, opcoes: ['LARGURA', 'PAGINA_POR_ABA'] }),
  p({ nome: 'visualizacao.ttlMinutos', rotulo: 'Vida do cache (min)', grupo: 'VISUALIZACAO', valor: '10', padrao: '10', minimo: 1, classeDeAplicacao: 'IMEDIATO' }),
  p({ nome: 'conexoes.downloadTimeoutSegundos', rotulo: 'Tempo limite de download (s)', grupo: 'CONEXOES', valor: '120', padrao: '120', minimo: 10, maximo: 300 }),
  p({ nome: 'auditoria.retencaoDocumentosAnos', rotulo: 'Retenção: documentos', grupo: 'AUDITORIA', tipo: 'OPCAO', valor: '5', padrao: '5', minimo: null, maximo: null, opcoes: ['1', '2', '5', '10'], classeDeAplicacao: 'IMEDIATO' }),
];

interface Controle {
  parametros: ParametroApi[];
  puts: { alteracoes: Record<string, unknown> }[];
  deletes: string[];
  testes: string[];
  /** Resposta do teste por conexão; padrão: sucesso em 42 ms. */
  testePor: Record<string, object>;
  corpos: string[];
}

async function abrir(page: Page, rota = '/configuracoes'): Promise<Controle> {
  const c: Controle = { parametros: CATALOGO(), puts: [], deletes: [], testes: [], testePor: {}, corpos: [] };
  await page.addInitScript(() => sessionStorage.setItem('rastro.token', 'token-de-teste'));
  await page.route('**/api/**', async (route: Route) => {
    const url = new URL(route.request().url());
    const caminho = url.pathname;
    const metodo = route.request().method();
    if (caminho === '/api/auth/me') return route.fulfill({ json: ADMIN });
    if (caminho === '/api/configuracoes/usuarios') {
      return route.fulfill({ json: { conteudo: [], pagina: 0, tamanho: 0, totalElementos: 0, totalPaginas: 0 } });
    }
    if (caminho === '/api/configuracoes/parametros' && metodo === 'GET') return route.fulfill({ json: { parametros: c.parametros } });
    if (caminho === '/api/configuracoes/parametros' && metodo === 'PUT') {
      const corpo = route.request().postDataJSON() as { alteracoes: Record<string, unknown> };
      c.puts.push(corpo);
      const fora = c.parametros.find((q) => q.nome in corpo.alteracoes && q.tipo === 'INTEIRO' && Number(corpo.alteracoes[q.nome]) > (q.maximo ?? Infinity));
      if (fora) {
        return route.fulfill({
          status: 400,
          json: { status: 400, errorCode: 'PARAMETRO_INVALIDO', campos: [{ campo: fora.nome, mensagem: `Deve ficar entre ${fora.minimo} e ${fora.maximo}.` }] },
        });
      }
      for (const q of c.parametros) {
        if (q.nome in corpo.alteracoes) {
          q.valor = String(corpo.alteracoes[q.nome]);
          q.sobreposto = q.valor !== q.padrao;
          q.pendenteDeReinicio = q.classeDeAplicacao === 'EXIGE_REINICIO' && q.sobreposto;
        }
      }
      return route.fulfill({ json: { parametros: c.parametros } });
    }
    if (caminho.startsWith('/api/configuracoes/parametros/') && metodo === 'DELETE') {
      const nome = decodeURIComponent(caminho.split('/').pop()!);
      c.deletes.push(nome);
      const q = c.parametros.find((x) => x.nome === nome)!;
      q.valor = q.padrao;
      q.sobreposto = false;
      q.pendenteDeReinicio = false;
      return route.fulfill({ json: { parametros: c.parametros } });
    }
    if (caminho === '/api/configuracoes/conexoes') {
      return route.fulfill({
        json: {
          conexoes: [
            { nome: 'siga', rotulo: 'Banco do SIGA', estado: 'CONFIGURADA', horasAteExpirar: null, tokenVencido: false },
            { nome: 'arquivos', rotulo: 'API de arquivos', estado: 'CONFIGURADA', horasAteExpirar: 36, tokenVencido: false },
            { nome: 'scorp', rotulo: 'SCORP', estado: 'NAO_CONFIGURADA', horasAteExpirar: null, tokenVencido: false },
            { nome: 'taxas', rotulo: 'Taxas', estado: 'DESATIVADA', horasAteExpirar: null, tokenVencido: false },
          ],
          tempoLimiteDoTesteSegundos: 15,
        },
      });
    }
    const teste = /^\/api\/configuracoes\/conexoes\/([^/]+)\/teste$/.exec(caminho);
    if (teste && metodo === 'POST') {
      c.testes.push(teste[1]);
      const corpo = c.testePor[teste[1]] ?? {
        nome: teste[1],
        sucesso: true,
        classificacao: 'OK',
        codigoHttp: 200,
        tempoMs: 42,
        tempoLimiteSegundos: 15,
        mensagem: 'Serviço funcional: respondeu HTTP 200.',
        horasAteExpirar: null,
        tokenVencido: false,
      };
      c.corpos.push(JSON.stringify(corpo));
      return route.fulfill({ json: corpo });
    }
    return route.fulfill({ status: 404, json: { status: 404, errorCode: 'NAO_ENCONTRADO' } });
  });
  await page.goto(rota);
  return c;
}

test.use({ viewport: { width: 1280, height: 900 } });

const aba = (page: Page, nome: string) => page.getByRole('tab', { name: nome }).click();
const campo = (page: Page, nome: string) => page.locator(`[data-param="${nome}"]`);

test('abas de parâmetros: campos, faixa, padrão do ambiente e etiquetas de classe de aplicação', async ({ page }) => {
  await abrir(page);
  await aba(page, 'Dossiê');

  await expect(campo(page, 'dossie.ttlMinutos')).toContainText('Faixa: 5 a 240');
  await expect(campo(page, 'dossie.ttlMinutos')).toContainText('Padrão do ambiente: 30');
  await expect(campo(page, 'dossie.ttlMinutos').locator('.tag').first()).toHaveText('Vale na próxima geração');
  await expect(campo(page, 'dossie.threads').locator('.tag').first()).toHaveText('Exige reinício');
  await expect(campo(page, 'dossie.origem.JUNTADA').locator('.tag').first()).toHaveText('Vale já');
  await expect(page.locator('.cfg-fixo')).toContainText('Ordem cronológica: fixa (RN03)');

  await aba(page, 'Conversão');
  await expect(page.getByLabel('Uma página por aba')).toBeVisible();
  await aba(page, 'Visualização');
  await expect(campo(page, 'visualizacao.ttlMinutos').locator('.tag').first()).toHaveText('Vale já');
  await aba(page, 'Auditoria');
  await expect(campo(page, 'auditoria.retencaoDocumentosAnos').getByRole('combobox')).toHaveValue('5');
});

test('editar não grava; salvar envia um único PUT com tudo e mostra o resumo e o que exige reinício', async ({ page }) => {
  const c = await abrir(page);
  await aba(page, 'Dossiê');

  await campo(page, 'dossie.ttlMinutos').getByRole('spinbutton').fill('45');
  await campo(page, 'dossie.threads').getByRole('spinbutton').fill('4');
  await aba(page, 'Conversão');
  await page.getByLabel('Uma página por aba').check();

  await expect(page.locator('.cfg-barra')).toContainText('3 alterações pendentes');
  expect(c.puts).toEqual([]);
  await page.getByRole('button', { name: 'Salvar alterações' }).click();

  await expect(page.locator('.cfg-resumo')).toContainText('3 alterações salvas');
  await expect(page.locator('.cfg-resumo')).toContainText('Planilhas: Ajustar à largura → Uma página por aba');
  await expect(page.locator('.cfg-resumo')).toContainText('Exigem reinício: Gerações simultâneas');
  await expect(page.locator('.cfg-resumo')).toContainText('variável de ambiente');
  await expect(page.locator('.cfg-barra')).toHaveCount(0);
  expect(c.puts).toEqual([{ alteracoes: { 'dossie.ttlMinutos': 45, 'dossie.threads': 4, 'conversao.planilhaModo': 'PAGINA_POR_ABA' } }]);
  await aba(page, 'Dossiê');
  await expect(campo(page, 'dossie.threads')).toContainText('Pendente de reinício');
});

test('erro de faixa marca o campo e mantém as pendências; descartar volta ao vigente', async ({ page }) => {
  await abrir(page);
  await aba(page, 'Dossiê');
  await campo(page, 'dossie.ttlMinutos').getByRole('spinbutton').fill('999');
  await campo(page, 'dossie.sumario').getByRole('checkbox').uncheck();

  await page.getByRole('button', { name: 'Salvar alterações' }).click();

  await expect(campo(page, 'dossie.ttlMinutos').locator('.cfg-campo-erro')).toContainText('Deve ficar entre 5 e 240.');
  await expect(page.locator('.cfg-barra')).toContainText('2 alterações pendentes');
  await page.getByRole('button', { name: 'Descartar' }).click();
  await expect(page.locator('.cfg-barra')).toHaveCount(0);
  await expect(campo(page, 'dossie.ttlMinutos').getByRole('spinbutton')).toHaveValue('30');
  await expect(campo(page, 'dossie.sumario').getByRole('checkbox')).toBeChecked();
});

test('Restaurar padrão chama o DELETE e volta ao valor do ambiente', async ({ page }) => {
  const c = await abrir(page);
  await aba(page, 'Visualização');
  await campo(page, 'visualizacao.ttlMinutos').getByRole('spinbutton').fill('20');
  await page.getByRole('button', { name: 'Salvar alterações' }).click();
  await expect(campo(page, 'visualizacao.ttlMinutos')).toContainText('Alterado nesta sessão');

  await campo(page, 'visualizacao.ttlMinutos').getByRole('button', { name: 'Restaurar padrão' }).click();

  await expect(campo(page, 'visualizacao.ttlMinutos').getByRole('spinbutton')).toHaveValue('10');
  expect(c.deletes).toEqual(['visualizacao.ttlMinutos']);
  await expect(campo(page, 'visualizacao.ttlMinutos').getByRole('button', { name: 'Restaurar padrão' })).toHaveCount(0);
});

test('sair da tela com pendência pede confirmação: cancelar fica, confirmar sai', async ({ page }) => {
  await abrir(page);
  await aba(page, 'Dossiê');
  await campo(page, 'dossie.ttlMinutos').getByRole('spinbutton').fill('45');
  const mensagens: string[] = [];
  let aceitar = false;
  page.on('dialog', (dialogo) => {
    mensagens.push(dialogo.message());
    void (aceitar ? dialogo.accept() : dialogo.dismiss());
  });

  await page.locator('.top .nav a', { hasText: 'Consulta' }).first().click();
  await expect(page).toHaveURL(/\/configuracoes$/);
  expect(mensagens[0]).toContain('alterações de parâmetros que ainda não foram salvas');

  aceitar = true;
  await page.locator('.top .nav a', { hasText: 'Consulta' }).first().click();
  await expect(page).toHaveURL(/\/consulta$/);
});

test('sem pendência, sair não pergunta nada', async ({ page }) => {
  await abrir(page);
  let perguntou = false;
  page.on('dialog', (d) => {
    perguntou = true;
    void d.dismiss();
  });

  await page.locator('.top .nav a', { hasText: 'Consulta' }).first().click();

  await expect(page).toHaveURL(/\/consulta$/);
  expect(perguntou).toBe(false);
});

test('conexões: teste com sucesso, falha e token vencido; nenhuma credencial na tela', async ({ page }) => {
  const c = await abrir(page);
  c.testePor['arquivos'] = {
    nome: 'arquivos',
    sucesso: false,
    classificacao: 'FALHA_DE_AUTENTICACAO',
    tempoMs: 310,
    tempoLimiteSegundos: 15,
    mensagem: 'Token vencido: capture um novo token, atualize o arquivo e teste de novo.',
    horasAteExpirar: 0,
    tokenVencido: true,
  };
  c.testePor['scorp'] = {
    nome: 'scorp',
    sucesso: false,
    classificacao: 'FALHA',
    tempoMs: 5,
    tempoLimiteSegundos: 15,
    mensagem: 'O SCORP não está configurado.',
    horasAteExpirar: null,
    tokenVencido: false,
  };
  await aba(page, 'Conexões');
  const conexao = (nome: string) => page.locator(`[data-conexao="${nome}"]`);

  await expect(conexao('arquivos')).toContainText('Token expira em 36 h');
  await expect(conexao('taxas').getByRole('button', { name: 'Testar conexão' })).toBeDisabled();

  await conexao('siga').getByRole('button', { name: 'Testar conexão' }).click();
  await expect(conexao('siga').locator('.cfg-teste.ok')).toContainText('42 ms (limite 15 s)');
  await expect(conexao('siga').locator('.cfg-teste.ok')).toContainText('OK');
  await expect(page.locator('.cfg-limite-teste')).toContainText('Tempo limite de cada chamada: 15 s');

  await conexao('scorp').getByRole('button', { name: 'Testar conexão' }).click();
  await expect(conexao('scorp').locator('.cfg-teste.falha')).toContainText('O SCORP não está configurado.');
  await expect(conexao('scorp').locator('.cfg-teste.falha')).toContainText('Falha');
  await expect(conexao('scorp').getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

  await conexao('arquivos').getByRole('button', { name: 'Testar conexão' }).click();
  await expect(conexao('arquivos').locator('.cfg-vencido')).toContainText('Token vencido');
  await expect(conexao('arquivos').locator('.cfg-teste.falha')).toContainText('Falha de autenticação');
  await expect(conexao('arquivos').locator('.cfg-token-vencido')).toContainText(
    'Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.',
  );
  await expect(conexao('arquivos').getByRole('button', { name: 'Tentar novamente' })).toBeVisible();

  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await expect(page.locator('.cfg-painel')).not.toContainText(/https?:\/\//);
  await expect(page.locator('.cfg-painel')).toContainText('não as exibe nem as edita');
  await expect(campo(page, 'conexoes.downloadTimeoutSegundos')).toContainText('Faixa: 10 a 300');
});

test('quadro do topo com a mesma altura em todas as abas', async ({ page }) => {
  await abrir(page);
  const altura = () => page.locator('.dash-hero').evaluate((e) => e.getBoundingClientRect().height);
  const inicial = await altura();

  for (const nome of ['Dossiê', 'Conversão', 'Visualização', 'Conexões', 'Auditoria']) {
    await aba(page, nome);
    expect(await altura()).toBe(inicial);
  }
  await aba(page, 'Dossiê');
  await campo(page, 'dossie.ttlMinutos').getByRole('spinbutton').fill('45');
  await expect(page.locator('.cfg-barra')).toBeVisible();
  expect(await altura()).toBe(inicial);
});

test.describe('celular', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('sem rolagem horizontal em nenhuma aba, nem com a barra de pendências', async ({ page }) => {
    await abrir(page);
    const rolagem = () => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);

    for (const nome of ['Dossiê', 'Conversão', 'Visualização', 'Conexões', 'Auditoria']) {
      await aba(page, nome);
      expect(await rolagem()).toBeLessThanOrEqual(0);
    }
    await aba(page, 'Dossiê');
    await campo(page, 'dossie.ttlMinutos').getByRole('spinbutton').fill('45');
    await expect(page.locator('.cfg-barra')).toBeVisible();
    expect(await rolagem()).toBeLessThanOrEqual(0);
  });
});

test('conexões: resposta 4xx esperada para a chamada de teste é "Alcançável com ressalva", não falha (S6, bloco 4)', async ({ page }) => {
  const c = await abrir(page);
  c.testePor['scorp'] = {
    nome: 'scorp',
    sucesso: true,
    classificacao: 'ALCANCAVEL_COM_RESSALVA',
    codigoHttp: 400,
    tempoMs: 180,
    tempoLimiteSegundos: 15,
    mensagem: 'Serviço alcançável: respondeu HTTP 400 à chamada de teste, que usa um identificador de pessoa fictício.',
    horasAteExpirar: null,
    tokenVencido: false,
  };
  await aba(page, 'Conexões');
  const scorp = page.locator('[data-conexao="scorp"]');

  await scorp.getByRole('button', { name: 'Testar conexão' }).click();

  const resultado = scorp.locator('[data-classificacao="ALCANCAVEL_COM_RESSALVA"]');
  await expect(resultado).toContainText('Alcançável com ressalva');
  await expect(resultado).toContainText('HTTP 400');
  await expect(resultado).toContainText('identificador de pessoa fictício');
  await expect(resultado).toHaveAttribute('role', 'status');
  await expect(scorp.locator('.cfg-teste.falha')).toHaveCount(0);
});
