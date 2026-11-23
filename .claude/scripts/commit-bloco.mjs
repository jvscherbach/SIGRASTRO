// commit-bloco (A1, bloco 1): o ÚNICO caminho de commit do modo automático. Node ESM, sem dependências.
//
// Chamada exata (a guarda-bash só deixa passar esta forma):
//   node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt
//
// Nove verificações, nesta ordem; a primeira que falhar encerra com código diferente de zero e mensagem clara, sem
// alterar nada (nem o índice):
//   1. argumentos exatos;            2. raiz do repositório e branch `auto/*`;   3. identidade do Git configurada;
//   4. marca de testes do bloco;     5. mensagem limpa e no padrão;              6. nenhum caminho proibido na árvore;
//   7. varredura de dados no que vai entrar (mesmo detector da guarda varrer-dados);
//   8. só então: add de tudo e commit com a mensagem limpa (arquivo temporário, nunca `-m`), sem pular ganchos
//      e sem emendar commit anterior;
//   9. apaga a mensagem e a marca do bloco.
//
// O Git é chamado por execFile com vetor de argumentos (nunca shell). A identidade é a do Git do responsável: o script
// nunca define autor, nunca passa `-c` e remove do ambiente do Git as variáveis que trocariam autor, repositório ou
// configuração. Ele só cria commit local: enviar ao remoto é sempre do responsável, à mão.

import { execFile } from 'node:child_process';
import { mkdtemp, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

import { caminhoDeSegredo, chamadoDiretamente } from '../hooks/comum.mjs';
import { ignorado, varrerTexto } from '../hooks/varrer-dados.mjs';

const executar = promisify(execFile);

export const CAMINHO_MENSAGEM = '.claude/tmp/commit-msg.txt';
export const MARCAS_ACEITAS = new Set(['direcionados', 'completa', 'nao-se-aplica']);
export const TIPOS = ['feat', 'fix', 'refactor', 'test', 'docs', 'perf', 'chore', 'ci'];
const PRIMEIRA_LINHA = new RegExp(`^(?:${TIPOS.join('|')})(?:\\([a-z0-9][a-z0-9._/-]*\\))?: \\S.*$`);
const LIMITE_PRIMEIRA_LINHA = 100;

/** Erro de verificação: mensagem para o responsável, sem valor sensível. */
export class Recusa extends Error {
  constructor(etapa, texto) {
    super(`[commit-bloco] recusado na verificação ${etapa}: ${texto}`);
    this.name = 'Recusa';
    this.etapa = etapa;
  }
}

// --- 1. argumentos

/** Aceita exatamente `--bloco <1 a 3 dígitos> --mensagem .claude/tmp/commit-msg.txt`; devolve o número do bloco. */
export function lerArgumentos(argumentos) {
  if (
    argumentos.length !== 4 ||
    argumentos[0] !== '--bloco' ||
    !/^[1-9][0-9]{0,2}$/.test(argumentos[1]) ||
    argumentos[2] !== '--mensagem' ||
    argumentos[3] !== CAMINHO_MENSAGEM
  ) {
    throw new Recusa(
      1,
      'uso exato: node .claude/scripts/commit-bloco.mjs --bloco <N> --mensagem .claude/tmp/commit-msg.txt ' +
        '(N de 1 a 999, sem zero à esquerda; nenhum outro argumento ou caminho).',
    );
  }
  return Number(argumentos[1]);
}

// --- Git

/** Variáveis que mudariam autor, repositório, índice ou configuração do Git: nunca chegam ao Git chamado aqui. */
export function ambienteDoGit(base = process.env) {
  const limpo = {};
  for (const [nome, valor] of Object.entries(base)) {
    const maiusculo = nome.toUpperCase();
    if (
      maiusculo.startsWith('GIT_AUTHOR_') ||
      maiusculo.startsWith('GIT_COMMITTER_') ||
      maiusculo === 'GIT_DIR' ||
      maiusculo === 'GIT_WORK_TREE' ||
      maiusculo === 'GIT_INDEX_FILE' ||
      maiusculo === 'GIT_OBJECT_DIRECTORY' ||
      maiusculo === 'GIT_NAMESPACE' ||
      maiusculo === 'GIT_CONFIG_PARAMETERS' ||
      maiusculo === 'GIT_CONFIG_COUNT' ||
      maiusculo.startsWith('GIT_CONFIG_KEY_') ||
      maiusculo.startsWith('GIT_CONFIG_VALUE_')
    ) {
      continue;
    }
    limpo[nome] = valor;
  }
  return limpo;
}

function criarGit(cwd) {
  const env = ambienteDoGit();
  return async function git(argumentos, { aceitarFalha = false } = {}) {
    try {
      const { stdout } = await executar('git', argumentos, {
        cwd,
        env,
        windowsHide: true,
        maxBuffer: 256 * 1024 * 1024,
        encoding: 'utf8',
      });
      return { ok: true, stdout };
    } catch (erro) {
      if (aceitarFalha) {
        return { ok: false, stdout: erro.stdout ?? '', stderr: erro.stderr ?? '' };
      }
      throw erro;
    }
  };
}

async function mesmoCaminho(a, b) {
  const [ra, rb] = await Promise.all([realpath(a), realpath(b)]);
  const na = path.resolve(ra);
  const nb = path.resolve(rb);
  return process.platform === 'win32' ? na.toLowerCase() === nb.toLowerCase() : na === nb;
}

// --- 2. repositório e branch

async function verificarRepositorio(git, cwd) {
  const topo = await git(['rev-parse', '--show-toplevel'], { aceitarFalha: true });
  if (!topo.ok) {
    throw new Recusa(2, 'a pasta atual não é um repositório Git.');
  }
  if (!(await mesmoCaminho(topo.stdout.trim(), cwd))) {
    throw new Recusa(2, 'rode o script na raiz do repositório.');
  }
  const ramo = await git(['symbolic-ref', '--quiet', '--short', 'HEAD'], { aceitarFalha: true });
  if (!ramo.ok) {
    throw new Recusa(2, 'HEAD solto (fora de uma branch); o commit automático só acontece em branch auto/*.');
  }
  const nome = ramo.stdout.trim();
  if (!nome.startsWith('auto/') || nome.length <= 'auto/'.length) {
    throw new Recusa(
      2,
      `a branch atual é "${nome}"; o commit automático só acontece em branch auto/<nome>, criada pelo responsável ` +
        '(ex.: git switch -c auto/<nome>).',
    );
  }
  const cabeca = await git(['rev-parse', '--verify', '--quiet', 'HEAD'], { aceitarFalha: true });
  if (!cabeca.ok) {
    throw new Recusa(2, 'a branch ainda não tem commit; o responsável faz o primeiro.');
  }
  return nome;
}

// --- 3. identidade

async function verificarIdentidade(git) {
  const nome = await git(['config', '--get', 'user.name'], { aceitarFalha: true });
  const email = await git(['config', '--get', 'user.email'], { aceitarFalha: true });
  if (!nome.ok || !nome.stdout.trim() || !email.ok || !email.stdout.trim()) {
    throw new Recusa(
      3,
      'user.name e user.email do Git precisam estar configurados pelo responsável; o script nunca define identidade.',
    );
  }
}

// --- 4. marca de testes

export function validarMarca(conteudo, bloco) {
  let marca;
  try {
    marca = JSON.parse(conteudo);
  } catch {
    throw new Recusa(4, `.claude/tmp/bloco-${bloco}.json não é um JSON válido.`);
  }
  if (marca === null || typeof marca !== 'object' || Array.isArray(marca)) {
    throw new Recusa(4, `.claude/tmp/bloco-${bloco}.json não é um objeto JSON.`);
  }
  if (marca.bloco !== bloco) {
    throw new Recusa(4, `a marca de testes é de outro bloco (esperado ${bloco}).`);
  }
  if (!MARCAS_ACEITAS.has(marca.testes)) {
    throw new Recusa(
      4,
      '"testes" precisa ser "direcionados", "completa" ou "nao-se-aplica"; qualquer outro valor é recusado.',
    );
  }
  return marca;
}

async function verificarMarca(cwd, bloco) {
  let conteudo;
  try {
    conteudo = await readFile(path.join(cwd, '.claude', 'tmp', `bloco-${bloco}.json`), 'utf8');
  } catch {
    throw new Recusa(4, `falta .claude/tmp/bloco-${bloco}.json (a marca de que os testes do bloco passaram).`);
  }
  return validarMarca(conteudo, bloco);
}

// --- 5. mensagem

const LINHAS_DE_FERRAMENTA = [
  /^\s*co-authored-by\s*:/i,
  /^\s*claude-session\s*:/i,
  /^\s*signed-off-by\s*:.*(claude|anthropic)/i,
  /generated\s+with\s+\[?claude\s+code/i,
  /claude\.ai\/code/i,
  /noreply@anthropic\.com/i,
];

/** Remove linhas de coautoria e de ferramenta, espaços no fim das linhas e linhas vazias sobrando no fim. */
export function limparMensagem(texto) {
  const linhas = texto
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .filter((linha) => !LINHAS_DE_FERRAMENTA.some((padrao) => padrao.test(linha)))
    .map((linha) => linha.replace(/\s+$/, ''));
  while (linhas.length && linhas[linhas.length - 1] === '') {
    linhas.pop();
  }
  return linhas.join('\n');
}

/** Valida a mensagem já limpa; devolve o texto final (com quebra de linha no fim). */
export function validarMensagem(limpa) {
  if (!limpa.trim()) {
    throw new Recusa(5, 'a mensagem ficou vazia depois de remover as linhas de coautoria e de ferramenta.');
  }
  const [primeira, segunda] = limpa.split('\n');
  if (primeira.length > LIMITE_PRIMEIRA_LINHA) {
    throw new Recusa(5, `a primeira linha passa de ${LIMITE_PRIMEIRA_LINHA} caracteres.`);
  }
  if (!PRIMEIRA_LINHA.test(primeira)) {
    throw new Recusa(
      5,
      `a primeira linha não segue Conventional Commits (${TIPOS.join('|')}, escopo opcional em minúsculas, ": " e ` +
        'resumo; sem espaço no início).',
    );
  }
  if (segunda !== undefined && segunda !== '') {
    throw new Recusa(5, 'o corpo da mensagem deve vir depois de uma linha em branco.');
  }
  return `${limpa}\n`;
}

async function verificarMensagem(cwd) {
  let texto;
  try {
    texto = await readFile(path.join(cwd, CAMINHO_MENSAGEM), 'utf8');
  } catch {
    throw new Recusa(5, `falta ${CAMINHO_MENSAGEM}.`);
  }
  return validarMensagem(limparMensagem(texto));
}

// --- 6. caminhos proibidos

const SEGMENTOS_PROIBIDOS = new Set(['node_modules', 'dist', 'test-results', 'playwright-report', 'amostras-locais']);

/** Segredo, dado ou artefato que nunca entra num commit automático. */
export function caminhoProibido(caminho) {
  const barras = caminho.replace(/\\/g, '/').replace(/\/+$/, '');
  const partes = barras.split('/');
  const nome = (partes[partes.length - 1] || '').toLowerCase();
  if (caminhoDeSegredo(barras)) return true; // .env*, mapa-local.csv, .secrets/, .rastro/, credenciais (comum.mjs)
  if (partes.some((p) => SEGMENTOS_PROIBIDOS.has(p.toLowerCase()))) return true;
  if (nome.endsWith('.pem') || nome.endsWith('.key')) return true;
  const minusculo = barras.toLowerCase();
  return minusculo === '.claude/tmp' || minusculo.startsWith('.claude/tmp/');
}

/** `git status --porcelain=v1 -z`: [{estado, caminho}] (renomeação traz também a origem). */
export function lerStatus(saida) {
  const campos = saida.split('\0');
  const entradas = [];
  for (let i = 0; i < campos.length; i++) {
    const campo = campos[i];
    if (!campo) continue;
    const estado = campo.slice(0, 2);
    const caminho = campo.slice(3);
    let origem;
    if (estado[0] === 'R' || estado[0] === 'C') {
      origem = campos[i + 1];
      i++;
    }
    entradas.splice(entradas.length, 0, { estado, caminho, origem });
  }
  return entradas;
}

async function verificarCaminhos(git) {
  const status = await git(['status', '--porcelain=v1', '-z', '--untracked-files=all']);
  const entradas = lerStatus(status.stdout);
  if (!entradas.length) {
    throw new Recusa(6, 'nada para commitar (árvore de trabalho sem mudanças).');
  }
  const proibidos = entradas
    .flatMap((e) => (e.origem ? [e.caminho, e.origem] : [e.caminho]))
    .filter((c) => caminhoProibido(c));
  if (proibidos.length) {
    const lista = [...new Set(proibidos)].slice(0, 30).map((c) => `  - ${c}`).join('\n');
    throw new Recusa(
      6,
      `há caminhos de segredo, de dado ou de artefato na árvore (não rastreados ou alterados):\n${lista}\n` +
        'Remova-os ou ponha no .gitignore; o responsável decide.',
    );
  }
  return entradas;
}

// --- 7. varredura de dados

/**
 * Linhas acrescentadas de um diff unificado, com o número da linha no arquivo novo (pelos cabeçalhos `@@`):
 * `{texto, numeros}`, em que `numeros[i]` é a linha real de `texto.split('\n')[i]`.
 */
export function linhasAcrescentadas(diff) {
  const textos = [];
  const numeros = [];
  let atual = 0;
  let dentroDeTrecho = false; // antes do primeiro @@ ficam os cabeçalhos (`---`, `+++`); dentro, `+++x` é conteúdo
  for (const linha of diff.split(/\r?\n/)) {
    const cabecalho = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/.exec(linha);
    if (cabecalho) {
      atual = Number(cabecalho[1]);
      dentroDeTrecho = true;
      continue;
    }
    if (linha.startsWith('diff --git ')) {
      dentroDeTrecho = false;
      continue;
    }
    if (!dentroDeTrecho) continue;
    if (linha.startsWith('+')) {
      textos.splice(textos.length, 0, linha.slice(1));
      numeros.splice(numeros.length, 0, atual);
      atual++;
    } else if (linha.startsWith(' ')) {
      atual++;
    }
  }
  return { texto: textos.join('\n'), numeros };
}

async function verificarDados(git, cwd, entradas, mensagem) {
  const achados = [];
  const anotar = (arquivo, lista) => {
    for (const a of lista) achados.splice(achados.length, 0, `  - ${arquivo}:${a.linha} (${a.tipo})`);
  };
  for (const entrada of entradas) {
    const caminho = entrada.caminho;
    if (entrada.estado === '??') {
      if (ignorado(caminho)) continue;
      let bytes;
      try {
        bytes = await readFile(path.join(cwd, caminho));
      } catch {
        continue; // pasta vazia ou arquivo que sumiu entre o status e a leitura
      }
      if (bytes.subarray(0, 8192).includes(0)) continue; // binário
      anotar(caminho, varrerTexto(bytes.toString('utf8')));
      continue;
    }
    if (ignorado(caminho)) continue;
    const diff = await git(['diff', 'HEAD', '--no-color', '--no-ext-diff', '--unified=0', '--', caminho]);
    const { texto, numeros } = linhasAcrescentadas(diff.stdout);
    anotar(
      caminho,
      varrerTexto(texto).map((a) => ({ ...a, linha: numeros[a.linha - 1] ?? a.linha })),
    );
  }
  anotar('(mensagem de commit)', varrerTexto(mensagem));
  if (achados.length) {
    const mais = achados.length > 20 ? `\n  - e mais ${achados.length - 20} ocorrência(s)` : '';
    throw new Recusa(
      7,
      'possível dado pessoal ou segredo real no que seria commitado (o valor não é repetido):\n' +
        `${achados.slice(0, 20).join('\n')}${mais}`,
    );
  }
}

// --- 8 e 9. commit e limpeza

async function commitar(git, mensagem) {
  const pasta = await mkdtemp(path.join(os.tmpdir(), 'commit-bloco-'));
  const arquivo = path.join(pasta, 'mensagem.txt');
  try {
    await writeFile(arquivo, mensagem, 'utf8');
    await git(['add', '-A']);
    await git(['commit', '--quiet', '--cleanup=whitespace', '-F', arquivo]);
  } finally {
    await rm(pasta, { recursive: true, force: true });
  }
  const curto = (await git(['rev-parse', '--short', 'HEAD'])).stdout.trim();
  const resumo = (await git(['show', '--stat', '--oneline', '--no-color', 'HEAD'])).stdout;
  return { curto, resumo };
}

async function limparTemporarios(cwd, bloco) {
  await rm(path.join(cwd, CAMINHO_MENSAGEM), { force: true });
  await rm(path.join(cwd, '.claude', 'tmp', `bloco-${bloco}.json`), { force: true });
}

/** Executa as nove etapas na pasta `cwd`. Devolve `{bloco, branch, curto, resumo}` ou lança `Recusa`. */
export async function commitBloco(argumentos, cwd = process.cwd()) {
  const bloco = lerArgumentos(argumentos);
  const git = criarGit(cwd);
  const branch = await verificarRepositorio(git, cwd);
  await verificarIdentidade(git);
  await verificarMarca(cwd, bloco);
  const mensagem = await verificarMensagem(cwd);
  const entradas = await verificarCaminhos(git);
  await verificarDados(git, cwd, entradas, mensagem);
  const { curto, resumo } = await commitar(git, mensagem);
  await limparTemporarios(cwd, bloco);
  return { bloco, branch, curto, resumo };
}

async function principal() {
  try {
    const r = await commitBloco(process.argv.slice(2));
    process.stdout.write(`[commit-bloco] bloco ${r.bloco} commitado em ${r.branch}: ${r.curto}\n${r.resumo}`);
    return 0;
  } catch (erro) {
    if (erro instanceof Recusa) {
      process.stderr.write(`${erro.message}\n`);
      return 2;
    }
    // Falha do próprio Git (gancho recusou, índice travado etc.): só o tipo e o stderr do Git, que não traz conteúdo.
    const detalhe = erro && typeof erro.stderr === 'string' ? erro.stderr.trim() : (erro && erro.name) || 'Erro';
    process.stderr.write(`[commit-bloco] falha do Git: ${detalhe}\n`);
    return 1;
  }
}

if (chamadoDiretamente(import.meta.url)) {
  principal().then((codigo) => process.exit(codigo));
}
