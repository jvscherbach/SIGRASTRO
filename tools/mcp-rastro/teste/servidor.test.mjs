// Protocolo MCP de ponta a ponta (node --test): cliente MCP em memória → servidor do Rastro → Rastro SIMULADO local
// (dados fictícios). Nenhum serviço real é chamado.

import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, before, test } from 'node:test';

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';

import { ClienteRastro } from '../cliente-rastro.mjs';
import { iniciarRastroSimulado } from '../rastro-simulado.mjs';
import { criarServidor } from '../servidor.mjs';

const README = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', 'docs', 'README.md');

let simulado;
let cliente;

async function conectar(opcoes) {
  const servidor = criarServidor({ readme: README, ...opcoes });
  const [doCliente, doServidor] = InMemoryTransport.createLinkedPair();
  const mcp = new Client({ name: 'teste', version: '0.0.0' });
  await Promise.all([servidor.connect(doServidor), mcp.connect(doCliente)]);
  return mcp;
}

const texto = (resultado) => resultado.content.map((c) => c.text).join('\n');

before(async () => {
  simulado = await iniciarRastroSimulado();
  cliente = await conectar({
    cliente: new ClienteRastro({ url: simulado.url, usuario: 'demo.mcp', senha: 'senha-de-teste' }),
  });
});

after(async () => {
  await cliente?.close();
  await simulado?.fechar();
});

test('lista as três ferramentas, todas marcadas como somente leitura', async () => {
  const { tools } = await cliente.listTools();
  assert.deepEqual(tools.map((t) => t.name).sort(), ['buscar_requerimento', 'estado_do_servico', 'listar_documentos']);
  for (const ferramenta of tools) {
    assert.equal(ferramenta.annotations?.readOnlyHint, true, ferramenta.name);
    assert.equal(ferramenta.annotations?.destructiveHint, false, ferramenta.name);
  }
});

test('estado_do_servico diz que está no ar e em demonstração', async () => {
  const resultado = await cliente.callTool({ name: 'estado_do_servico', arguments: {} });
  assert.ok(!resultado.isError);
  assert.match(texto(resultado), /Rastro local: no ar/);
  assert.match(texto(resultado), /Modo: demonstracao/);
});

test('buscar_requerimento devolve número, situação, tipo e data, sem documento do interessado', async () => {
  const resultado = await cliente.callTool({ name: 'buscar_requerimento', arguments: { tipo: 'PROCESSO', numero: '2825/2025' } });
  const saida = texto(resultado);
  assert.ok(!resultado.isError, saida);
  assert.match(saida, /Processo 2825\/2025 \(id 101\)/);
  assert.match(saida, /Requerimento: 3331\/2025/);
  assert.match(saida, /Situação: Em análise/);
  assert.match(saida, /Tipo: Uso de Recursos Hídricos/);
  assert.match(saida, /Abertura: 31\/03\/2025 10:12/);
  assert.match(saida, /Mascarados: 0/);
});

test('buscar_requerimento valida o formato do número', async () => {
  const resultado = await cliente.callTool({ name: 'buscar_requerimento', arguments: { tipo: 'PROCESSO', numero: 'abc' } });
  assert.equal(resultado.isError, true);
});

test('número inexistente responde com o erro do Rastro, sem URL nem corpo', async () => {
  const resultado = await cliente.callTool({ name: 'buscar_requerimento', arguments: { tipo: 'REQUERIMENTO', numero: '1/2020' } });
  assert.equal(resultado.isError, true);
  assert.equal(texto(resultado), 'O Rastro respondeu HTTP 404 NAO_ENCONTRADO (não encontrado).');
});

test('listar_documentos traz título, arquivo, tipo, data, situação e sigilo; nada de chave nem conteúdo', async () => {
  const resultado = await cliente.callTool({ name: 'listar_documentos', arguments: { idRequerimento: 101 } });
  const saida = texto(resultado);
  assert.ok(!resultado.isError, saida);
  assert.match(saida, /4 documento\(s\) do requerimento 101/);
  assert.match(saida, /1\. Requerimento assinado\n {3}arquivo: requerimento\.pdf · tipo: PDF · origem: Documento do processo\n {3}data: 01\/04\/2025 10:00 · situação: vigente · sigiloso: não/);
  assert.match(saida, /situação: substituído/);
  assert.match(saida, /situação: indisponível · sigiloso: sim/);
  assert.doesNotMatch(saida, /CHAVE|chave/);
});

test('recurso rastro://docs/indice lista títulos e caminhos de docs/', async () => {
  const { resources } = await cliente.listResources();
  assert.deepEqual(resources.map((r) => r.uri), ['rastro://docs/indice']);
  const lido = await cliente.readResource({ uri: 'rastro://docs/indice' });
  const conteudo = lido.contents[0].text;
  assert.match(conteudo, /- 01 — Visão geral: docs\/01-visao-geral\.md/);
  assert.match(conteudo, /- Ferramentas do Claude Code: docs\/claude-code\/README\.md/);
});

test('stdio de verdade: o processo do servidor fala só o protocolo no stdout e responde pelo ambiente', async () => {
  const { StdioClientTransport } = await import('@modelcontextprotocol/sdk/client/stdio.js');
  const transporte = new StdioClientTransport({
    command: process.execPath,
    args: [path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'servidor.mjs')],
    env: { ...process.env, RASTRO_MCP_URL: simulado.url, RASTRO_MCP_USUARIO: 'operador', RASTRO_MCP_SENHA: 'senha-de-teste-123' },
    stderr: 'pipe',
  });
  const mcp = new Client({ name: 'teste-stdio', version: '0.0.0' });
  await mcp.connect(transporte);
  try {
    const resultado = await mcp.callTool({ name: 'buscar_requerimento', arguments: { tipo: 'PROCESSO', numero: '2825/2025' } });
    assert.match(texto(resultado), /Processo 2825\/2025 \(id 101\)/);
  } finally {
    await mcp.close();
  }
});
