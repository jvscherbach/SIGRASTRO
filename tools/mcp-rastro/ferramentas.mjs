// Ferramentas do servidor MCP do Rastro (S6, bloco 7): todas só leitura, saída em texto curto e mascarado.
// Antes de qualquer consulta de dados, confere que o Rastro se declara em modo de demonstração (GET /api/estado).

import { readFile } from 'node:fs/promises';

import { ErroDoRastro } from './cliente-rastro.mjs';
import { limparObjeto, saidaMascarada } from './mascara.mjs';

export const MODO_DEMONSTRACAO = 'demonstracao';
const MAXIMO_DE_DOCUMENTOS = 200;
const SITUACAO = { VIGENTE: 'vigente', SUBSTITUIDO: 'substituído', INDISPONIVEL: 'indisponível' };

export const ORIENTACAO_DEMONSTRACAO =
  'Recusado: o Rastro local não está em modo de demonstração. O servidor MCP só opera com o ambiente de verificação, ' +
  'sem nenhum sistema externo: docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml up -d ' +
  '(docs/claude-code/mcp.md).';

/** "2025-03-31T10:12:00" → "31/03/2025 10:12" (o Rastro já devolve hora de Cuiabá; data pura fica só a data). */
export function formatarData(valor) {
  if (typeof valor !== 'string') return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(valor);
  if (!m) return '—';
  return m[4] ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}` : `${m[3]}/${m[2]}/${m[1]}`;
}

/** Estado do serviço; lança se não estiver em demonstração. */
async function exigirDemonstracao(cliente) {
  const estado = await cliente.get('/api/estado');
  if (!estado || estado.modo !== MODO_DEMONSTRACAO) {
    throw new ErroDoRastro(ORIENTACAO_DEMONSTRACAO);
  }
  return estado;
}

export async function estadoDoServico(cliente) {
  const estado = limparObjeto(await cliente.get('/api/estado'));
  const modo = typeof estado?.modo === 'string' ? estado.modo : 'desconhecido';
  const linhas = [
    `Rastro local: ${estado?.status === 'UP' ? 'no ar' : 'situação desconhecida'}`,
    `Modo: ${modo}`,
    modo === MODO_DEMONSTRACAO
      ? 'O servidor MCP opera: consultas somente leitura, com dados de demonstração.'
      : ORIENTACAO_DEMONSTRACAO,
  ];
  return saidaMascarada(linhas.join('\n'));
}

export async function buscarRequerimento(cliente, { tipo, numero }) {
  await exigirDemonstracao(cliente);
  const contador = { ocorrencias: 0 };
  const pagina = limparObjeto(await cliente.get('/api/busca', { tipo, numero }), contador);
  const itens = Array.isArray(pagina?.conteudo) ? pagina.conteudo : [];
  if (!itens.length) {
    return saidaMascarada(`Nenhum ${tipo === 'PROCESSO' ? 'processo' : 'requerimento'} ${numero} encontrado.`, contador.ocorrencias);
  }
  const blocos = itens.slice(0, 20).map((r) => {
    const identificacao = r.identificacao ? `${r.identificacao.rotulo ?? ''} ${r.identificacao.numero ?? ''}`.trim() : '';
    return [
      `${identificacao || 'Requerimento'} (id ${r.id})`,
      `  Requerimento: ${r.numeroRequerimento ?? '—'} · Processo: ${r.numeroProcesso ?? '—'}`,
      `  Situação: ${r.situacao?.descricao ?? r.situacao?.codigo ?? '—'}`,
      `  Tipo: ${r.tipoProcesso ?? '—'}`,
      `  Abertura: ${formatarData(r.abertura)}`,
    ].join('\n');
  });
  const total = typeof pagina.totalElementos === 'number' ? pagina.totalElementos : itens.length;
  return saidaMascarada(`${total} resultado(s):\n\n${blocos.join('\n\n')}`, contador.ocorrencias);
}

export async function listarDocumentos(cliente, { idRequerimento }) {
  await exigirDemonstracao(cliente);
  const contador = { ocorrencias: 0 };
  const lista = limparObjeto(await cliente.get(`/api/requerimentos/${encodeURIComponent(String(idRequerimento))}/documentos`), contador);
  const itens = Array.isArray(lista?.itens) ? lista.itens : [];
  if (!itens.length) {
    return saidaMascarada(`Nenhum documento listado para o requerimento ${idRequerimento}.`, contador.ocorrencias);
  }
  const linhas = itens.slice(0, MAXIMO_DE_DOCUMENTOS).map((d, i) =>
    [
      `${i + 1}. ${d.titulo ?? '—'}`,
      `   arquivo: ${d.nomeArquivo ?? '—'} · tipo: ${d.tipoArquivo ?? d.extensao ?? '—'} · origem: ${d.origemDescricao ?? '—'}`,
      `   data: ${formatarData(d.data)} · situação: ${SITUACAO[d.situacao] ?? '—'} · sigiloso: ${d.sigiloso ? 'sim' : 'não'}`,
    ].join('\n'),
  );
  const resto = itens.length > MAXIMO_DE_DOCUMENTOS ? `\n(e mais ${itens.length - MAXIMO_DE_DOCUMENTOS})` : '';
  return saidaMascarada(`${itens.length} documento(s) do requerimento ${idRequerimento}:\n\n${linhas.join('\n')}${resto}`,
    contador.ocorrencias);
}

/** Índice de docs/ a partir da tabela de docs/README.md: título e caminho, só leitura. */
export async function indiceDosDocs(caminhoDoReadme) {
  const texto = await readFile(caminhoDoReadme, 'utf8');
  const linhas = [];
  for (const m of texto.matchAll(/^\|\s*\[([^\]]+)\]\(([^)\s]+)\)\s*\|/gm)) {
    linhas.push(`- ${m[1]}: docs/${m[2].replace(/^\.\//, '')}`);
  }
  return saidaMascarada(`Índice de docs/ (${linhas.length} itens):\n${linhas.join('\n')}`);
}
