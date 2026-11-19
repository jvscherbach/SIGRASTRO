// Regras de segurança do servidor MCP (node --test), contra o Rastro SIMULADO local: só local, só demonstração, só GET,
// mascaramento da saída, erros sem URL/token/corpo, tempo limite, tamanho máximo e renovação da sessão.

import assert from 'node:assert/strict';
import http from 'node:http';
import { test } from 'node:test';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';

import { ClienteRastro, ErroDoRastro, validarUrl } from '../cliente-rastro.mjs';
import { buscarRequerimento, estadoDoServico, listarDocumentos } from '../ferramentas.mjs';
import { limparObjeto, mascararTexto } from '../mascara.mjs';
import { iniciarRastroSimulado } from '../rastro-simulado.mjs';
import { criarServidor } from '../servidor.mjs';

const SENHA = 'senha-de-teste';

async function comSimulado(opcoes, corpo) {
  const simulado = await iniciarRastroSimulado(opcoes);
  try {
    return await corpo(simulado);
  } finally {
    await simulado.fechar();
  }
}

const novoCliente = (url, extra = {}) => new ClienteRastro({ url, usuario: 'demo.mcp', senha: SENHA, ...extra });

test('só local: recusa qualquer host que não seja localhost, 127.0.0.1 ou host.docker.internal', () => {
  for (const url of ['http://localhost:8080', 'http://127.0.0.1:8099', 'http://host.docker.internal:8080', 'https://LOCALHOST']) {
    assert.doesNotThrow(() => validarUrl(url), url);
  }
  for (const url of ['http://servidor-real.invalid', 'http://10.0.0.5:8080', 'http://127.0.0.1.nip.io',
    'http://localhost.exemplo.invalid', 'ftp://localhost', 'file:///etc/passwd', 'http://usuario:senha@localhost',
    'http://localhost:8080/?x=1', 'nao-e-url']) {
    assert.throws(() => validarUrl(url), ErroDoRastro, url);
  }
});

test('host remoto não derruba o servidor: toda ferramenta responde com a recusa, sem chamar nada', async () => {
  const servidor = criarServidor({ ambiente: { RASTRO_MCP_URL: 'http://servidor-real.invalid', RASTRO_MCP_USUARIO: 'x', RASTRO_MCP_SENHA: 'y' } });
  const [doCliente, doServidor] = InMemoryTransport.createLinkedPair();
  const mcp = new Client({ name: 'teste', version: '0.0.0' });
  await Promise.all([servidor.connect(doServidor), mcp.connect(doCliente)]);
  try {
    for (const [name, args] of [['estado_do_servico', {}], ['buscar_requerimento', { tipo: 'PROCESSO', numero: '1/2025' }],
      ['listar_documentos', { idRequerimento: 1 }]]) {
      const resultado = await mcp.callTool({ name, arguments: args });
      assert.equal(resultado.isError, true, name);
      assert.match(resultado.content[0].text, /só fala com o Rastro local/);
      assert.doesNotMatch(resultado.content[0].text, /servidor-real/);
    }
  } finally {
    await mcp.close();
  }
});

test('só demonstração: fora do modo, consultas recusadas com a orientação e nenhum dado pedido', async () => {
  await comSimulado({ modo: 'padrao' }, async (simulado) => {
    const cliente = novoCliente(simulado.url);
    await assert.rejects(buscarRequerimento(cliente, { tipo: 'PROCESSO', numero: '2825/2025' }), /não está em modo de demonstração/);
    await assert.rejects(listarDocumentos(cliente, { idRequerimento: 101 }), /compose\.verificacao\.yaml/);
    const estado = await estadoDoServico(cliente);
    assert.match(estado, /Modo: padrao/);
    assert.match(estado, /não está em modo de demonstração/);
    assert.ok(!simulado.pedidos.some((p) => p.caminho === '/api/busca' || p.caminho.endsWith('/documentos')),
      'nenhuma consulta de dados fora da demonstração');
  });
});

test('só GET: além do login, nenhuma ferramenta usa outro método', async () => {
  await comSimulado({}, async (simulado) => {
    const cliente = novoCliente(simulado.url);
    await estadoDoServico(cliente);
    await buscarRequerimento(cliente, { tipo: 'PROCESSO', numero: '2825/2025' });
    await listarDocumentos(cliente, { idRequerimento: 101 });
    const outros = simulado.pedidos.filter((p) => !(p.metodo === 'POST' && p.caminho === '/api/auth/login'));
    assert.ok(outros.length >= 5);
    assert.deepEqual([...new Set(outros.map((p) => p.metodo))], ['GET']);
    assert.ok(!simulado.pedidos.some((p) => /dossies|conteudo|geometrias|arquivo/.test(p.caminho)), 'nada de dossiê nem download');
  });
});

test('mascaramento: CPF/CNPJ, e-mail, token, cookie e chave nunca saem, mesmo que a API os devolva', async () => {
  await comSimulado({ vazar: true }, async (simulado) => {
    const cliente = novoCliente(simulado.url);
    const busca = await buscarRequerimento(cliente, { tipo: 'PROCESSO', numero: '2825/2025' });
    const docs = await listarDocumentos(cliente, { idRequerimento: 101 });
    for (const saida of [busca, docs]) {
      assert.doesNotMatch(saida, /\d{3}\.\d{3}\.\d{3}-\d{2}/);
      assert.doesNotMatch(saida, /\d{11,14}/);
      assert.doesNotMatch(saida, /@/);
      assert.doesNotMatch(saida, /eyJ/);
      assert.doesNotMatch(saida, /Bearer|Cookie|sessao=|CHAVE-INTERNA/);
    }
    // Os títulos com CPF injetado aparecem mascarados, e a contagem aparece no fim.
    assert.match(docs, /1\. Requerimento assinado \*\*\*/);
    const total = Number(/Mascarados: (\d+)/.exec(docs)[1]);
    assert.ok(total >= 4, `contagem: ${total}`);
  });
});

test('máscara do texto e limpeza do JSON, peça por peça', () => {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const jwt = `${b64({ a: 1 })}.${b64({ b: 2 })}.assinatura`;
  const cpf = ['000', '000', '000'].join('.') + '-01';
  const { texto, ocorrencias } = mascararTexto(`a ${cpf} b ${'1'.repeat(11)} c ${jwt} d x@y.invalid e Bearer ${'k'.repeat(12)}`);
  assert.equal(ocorrencias, 5);
  assert.ok(!texto.includes(cpf) && !texto.includes(jwt));
  const contador = { ocorrencias: 0 };
  const limpo = limparObjeto({ chave: 'K1', interno: { token: 't', nome: 'ok' }, lista: [{ cpfCnpj: 'x' }] }, contador);
  assert.deepEqual(limpo, { chave: '***', interno: { token: '***', nome: 'ok' }, lista: [{ cpfCnpj: '***' }] });
  assert.equal(contador.ocorrencias, 3);
});

test('login: entra uma vez, guarda o token só em memória e renova ao vencer ou ao receber 401', async () => {
  await comSimulado({ validadeMs: 30_000 }, async (simulado) => {
    const cliente = novoCliente(simulado.url);
    await cliente.get('/api/estado');
    await cliente.get('/api/estado');
    assert.equal(simulado.sessoes(), 2, 'validade de 30 s já está na margem de renovação: entra de novo');

    await comSimulado({}, async (outro) => {
      const c = novoCliente(outro.url);
      await c.get('/api/estado');
      await c.get('/api/estado');
      assert.equal(outro.sessoes(), 1, 'sessão longa: um login só');
      outro.invalidarSessoes();
      await c.get('/api/estado');
      assert.equal(outro.sessoes(), 2, '401: entra de novo uma vez e repete o GET');
    });
  });
});

test('sem usuário e senha no ambiente, orienta e não chama nada', async () => {
  await comSimulado({}, async (simulado) => {
    const cliente = new ClienteRastro({ url: simulado.url });
    await assert.rejects(cliente.get('/api/estado'), /RASTRO_MCP_USUARIO e RASTRO_MCP_SENHA/);
    assert.equal(simulado.pedidos.length, 0);
  });
});

test('erros sem URL, sem token, sem senha e sem corpo bruto', async () => {
  await comSimulado({}, async (simulado) => {
    const cliente = novoCliente(simulado.url);
    const erro = await buscarRequerimento(cliente, { tipo: 'PROCESSO', numero: '9/2020' }).catch((e) => e);
    assert.ok(erro instanceof ErroDoRastro);
    for (const proibido of [simulado.url, '127.0.0.1', SENHA, 'sessao-simulada', 'Nada para', '9/2020']) {
      assert.ok(!erro.message.includes(proibido), `não pode trazer: ${proibido}`);
    }
  });
  const fora = novoCliente('http://127.0.0.1:9');
  const erro = await fora.get('/api/estado').catch((e) => e);
  assert.equal(erro.message, 'Rastro local inacessível. Suba o ambiente de demonstração (docs/claude-code/mcp.md).');
});

test('tempo limite: resposta lenta vira erro claro', async () => {
  await comSimulado({ atrasoMs: 1_500 }, async (simulado) => {
    const cliente = novoCliente(simulado.url, { tempoLimiteMs: 300 });
    await assert.rejects(cliente.get('/api/estado'), /Tempo limite \(0 s\)|Tempo limite/);
  });
});

test('tamanho máximo: resposta grande demais é recusada', async () => {
  await comSimulado({ tamanhoExtra: 50_000 }, async (simulado) => {
    const cliente = novoCliente(simulado.url, { tamanhoMaximo: 10_000 });
    await assert.rejects(buscarRequerimento(cliente, { tipo: 'PROCESSO', numero: '2825/2025' }), /grande demais/);
  });
});

test('redirecionamento é recusado (poderia levar a outro host)', async () => {
  const servidor = http.createServer((req, res) => {
    res.writeHead(302, { Location: 'http://servidor-real.invalid/' });
    res.end();
  });
  await new Promise((r) => servidor.listen(0, '127.0.0.1', r));
  try {
    const cliente = novoCliente(`http://127.0.0.1:${servidor.address().port}`);
    await assert.rejects(cliente.entrar(), /redirecionamento \(HTTP 302\); recusado/);
  } finally {
    await new Promise((r) => servidor.close(r));
  }
});
