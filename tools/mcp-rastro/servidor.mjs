// Servidor MCP do Rastro (S6, bloco 7), por stdio: consultas SOMENTE LEITURA ao Rastro local em modo de demonstração.
// stdout é só do protocolo MCP; avisos vão para stderr, sem dado sensível. Nada aqui cria, altera, apaga, gera dossiê
// ou baixa documento. Configuração pelo ambiente do processo: RASTRO_MCP_URL, RASTRO_MCP_USUARIO, RASTRO_MCP_SENHA
// (sem valor padrão no repositório) e RASTRO_MCP_TIMEOUT_MS.

import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { z } from 'zod';

import { ClienteRastro, ErroDoRastro } from './cliente-rastro.mjs';
import { buscarRequerimento, estadoDoServico, indiceDosDocs, listarDocumentos } from './ferramentas.mjs';
import { mascararTexto } from './mascara.mjs';

const RAIZ = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const SO_LEITURA = { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false };

function log(mensagem) {
  process.stderr.write(`[rastro-mcp] ${mensagem}\n`);
}

/** Resultado de ferramenta: texto, ou erro com mensagem segura (mascarada também, por garantia). */
async function responder(acao) {
  try {
    return { content: [{ type: 'text', text: await acao() }] };
  } catch (erro) {
    const mensagem = erro instanceof ErroDoRastro ? erro.message : 'Falha inesperada no servidor MCP do Rastro.';
    if (!(erro instanceof ErroDoRastro)) {
      log(`falha inesperada (${erro?.name ?? 'Erro'})`);
    }
    return { isError: true, content: [{ type: 'text', text: mascararTexto(mensagem).texto }] };
  }
}

/**
 * Monta o servidor. `cliente` pode ser injetado (testes); senão é criado do ambiente. Uma URL recusada (host remoto)
 * não derruba o servidor: toda ferramenta responde com a recusa.
 */
export function criarServidor({ cliente, ambiente = process.env, readme = path.join(RAIZ, 'docs', 'README.md') } = {}) {
  let problema = null;
  let rastro = cliente;
  if (!rastro) {
    try {
      rastro = new ClienteRastro({
        url: ambiente.RASTRO_MCP_URL,
        usuario: ambiente.RASTRO_MCP_USUARIO,
        senha: ambiente.RASTRO_MCP_SENHA,
        tempoLimiteMs: Number(ambiente.RASTRO_MCP_TIMEOUT_MS) || undefined,
      });
    } catch (erro) {
      problema = erro instanceof ErroDoRastro ? erro : new ErroDoRastro('Configuração do servidor MCP inválida.');
      log(problema.message);
    }
  }
  const comCliente = (acao) => responder(async () => {
    if (problema) throw problema;
    return acao(rastro);
  });

  const servidor = new McpServer({ name: 'rastro', version: '0.1.0' });

  servidor.registerTool(
    'buscar_requerimento',
    {
      title: 'Buscar requerimento ou processo',
      description:
        'Busca um requerimento ou processo do Rastro local (modo de demonstração) pelo número N/AAAA. Devolve número, ' +
        'situação, tipo e datas; nunca CPF/CNPJ do interessado. Somente leitura.',
      inputSchema: {
        tipo: z.enum(['REQUERIMENTO', 'PROCESSO']).describe('REQUERIMENTO ou PROCESSO'),
        numero: z.string().regex(/^\d{1,10}\/\d{4}$/, 'Use o formato N/AAAA').describe('Número no formato N/AAAA'),
      },
      annotations: SO_LEITURA,
    },
    ({ tipo, numero }) => comCliente((c) => buscarRequerimento(c, { tipo, numero })),
  );

  servidor.registerTool(
    'listar_documentos',
    {
      title: 'Listar documentos do requerimento',
      description:
        'Lista os documentos de um requerimento do Rastro local (modo de demonstração): título, nome do arquivo, tipo, ' +
        'data, situação (vigente, substituído, indisponível) e se é sigiloso. Sem chave do arquivo e sem conteúdo. ' +
        'Somente leitura.',
      inputSchema: {
        idRequerimento: z.number().int().positive().describe('id do requerimento (vem de buscar_requerimento)'),
      },
      annotations: SO_LEITURA,
    },
    ({ idRequerimento }) => comCliente((c) => listarDocumentos(c, { idRequerimento })),
  );

  servidor.registerTool(
    'estado_do_servico',
    {
      title: 'Estado do Rastro local',
      description: 'Diz se o Rastro local está no ar e em que modo (o servidor MCP só opera em demonstração).',
      inputSchema: {},
      annotations: SO_LEITURA,
    },
    () => comCliente((c) => estadoDoServico(c)),
  );

  servidor.registerResource(
    'indice-dos-docs',
    'rastro://docs/indice',
    {
      title: 'Índice da documentação do Rastro',
      description: 'Títulos e caminhos de docs/ (lidos de docs/README.md). Somente leitura.',
      mimeType: 'text/plain',
    },
    async (uri) => ({ contents: [{ uri: uri.href, mimeType: 'text/plain', text: await indiceDosDocs(readme) }] }),
  );

  return servidor;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const servidor = criarServidor();
  servidor.connect(new StdioServerTransport()).then(
    () => log('pronto (somente leitura, só Rastro local em demonstração)'),
    (erro) => {
      log(`não foi possível iniciar (${erro?.name ?? 'Erro'})`);
      process.exit(1);
    },
  );
}
