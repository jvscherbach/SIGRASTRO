// Guarda 2 (PreToolUse; Read, Edit, Write, MultiEdit, NotebookEdit, Grep, Glob): nenhuma ferramenta de arquivo abre,
// altera, procura ou lista arquivo de segredo: `.env`, `.env.local`, `.env.*` (menos `*.example`), `mapa-local.csv`,
// `.secrets/`, `/run/secrets*`, `.rastro/` (segredos fora do projeto) e arquivos de credencial do usuário.
//
// S6.1, bloco 2: o caminho é NORMALIZADO antes da conferência (barras invertidas, `..`, `./`, `~`, maiúsculas no
// Windows, caminho POSIX do Windows `/c/...`, e o destino de link simbólico), e Grep/Glob sobre uma pasta são
// conferidos no disco:
//  - Grep respeita o `.gitignore` (documentação oficial do Claude Code; prova em docs/claude-code/hooks.md). Sobre pasta
//    que contém segredo, só passa se TODOS os segredos dela estiverem ignorados pelo `.gitignore` da raiz (conferido
//    aqui, com a regra de "último padrão vence"); um `glob` que alcance o segredo bloqueia.
//  - Glob NÃO respeita o `.gitignore` (lista nomes ignorados). Sobre a raiz ou pasta que contém segredo, só passa se o
//    padrão não alcança nome de segredo (ex.: `**/*.java` passa, `**/*` e `**/.*` não).
// `.env.local.example` e `.env.example` passam. Erro interno não trava a sessão (comum.mjs).

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import { caminhoDeSegredo, principal } from './comum.mjs';

const FERRAMENTAS = new Set(['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit', 'Grep', 'Glob']);
const AMOSTRAS = ['.env', '.env.local', '.env.producao', 'mapa-local.csv'];
const LIMITE_DA_VARREDURA = 40_000;

export const REGRA =
  'Bloqueado pela guarda do projeto (regra: nunca abrir, alterar, procurar ou listar `.env`, `.env.local`, `.env.*`, ' +
  '`mapa-local.csv`, os arquivos de token montados, a pasta `.rastro` nem credenciais do usuário). Os nomes das ' +
  'variáveis estão no `.env.local.example` (use Read nele); para saber se uma variável está preenchida, confira dentro ' +
  'do container sem imprimir o valor: [ -z "$VAR" ] && echo vazia || echo preenchida';
export const REGRA_PASTA =
  'Bloqueado pela guarda do projeto (regra: busca ou listagem sobre pasta que contém arquivo de segredo, com padrão ' +
  'que pode alcançá-lo ou com segredo fora do `.gitignore`). Restrinja a pasta ou o padrão, ex.: Grep com ' +
  '`path: "api/src"` ou `glob: "**/*.java"`; Glob com `pattern: "docs/**/*.md"`.';

/** Caminho absoluto normalizado: `~`, barras, `..`, caminho POSIX do Windows (`/c/x` → `C:\x`). */
export function normalizar(valor, cwd) {
  let texto = valor.trim().replace(/^['"]|['"]$/g, '');
  if (texto === '~' || /^~[\\/]/.test(texto)) texto = path.join(os.homedir(), texto.slice(1));
  if (process.platform === 'win32' && /^\/[a-zA-Z]\//.test(texto)) texto = `${texto[1]}:${texto.slice(2)}`;
  if (process.platform === 'win32' && /^\/\/[a-zA-Z]\//.test(texto)) texto = `${texto[2]}:${texto.slice(3)}`;
  return path.resolve(cwd, texto);
}

/** O caminho (como escrito, normalizado ou o destino real de um link) é de segredo. */
export function caminhoProibido(valor, cwd) {
  if (caminhoDeSegredo(valor)) return true;
  const absoluto = normalizar(valor, cwd);
  if (caminhoDeSegredo(absoluto)) return true;
  try {
    const real = fs.realpathSync.native(absoluto);
    if (real !== absoluto && caminhoDeSegredo(real)) return true;
  } catch {
    // não existe (ainda): vale o nome
  }
  return false;
}

/** Curinga do último segmento que alcança um nome de segredo. */
export function padraoAlcancaSegredo(padrao) {
  if (typeof padrao !== 'string' || !padrao) return false;
  for (const alternativa of expandirChaves(padrao)) {
    if (caminhoDeSegredo(alternativa)) return true;
    const ultimo = alternativa.split(/[\\/]/).pop() ?? '';
    if (!/[*?[]/.test(ultimo)) continue;
    let fonte = '';
    for (let i = 0; i < ultimo.length; i++) {
      const c = ultimo[i];
      if (c === '*') fonte += '.*';
      else if (c === '?') fonte += '.';
      else if (c === '[') {
        const fim = ultimo.indexOf(']', i + 1);
        if (fim < 0) return true;
        fonte += `[${ultimo.slice(i + 1, fim).replace(/^!/, '^')}]`;
        i = fim;
      } else fonte += c.replace(/[.+^${}()|\\]/g, '\\$&');
    }
    try {
      const regex = new RegExp(`^${fonte}$`, 'i');
      if (AMOSTRAS.some((a) => regex.test(a))) return true;
    } catch {
      return true;
    }
  }
  return false;
}

function expandirChaves(texto) {
  const m = /\{([^{}]*)\}/.exec(texto);
  if (!m) return [texto];
  return m[1].split(',').flatMap((alt) => expandirChaves(texto.slice(0, m.index) + alt + texto.slice(m.index + m[0].length)));
}

/**
 * Segredos dentro da pasta que o Grep alcançaria: não segue link de pasta e não entra em pasta ignorada pelo
 * `.gitignore` (o Grep também não entra). Devolve só os NÃO ignorados; `null` se grande demais para conferir.
 */
export function segredosAlcancaveis(pasta, raiz) {
  const achados = [];
  const pilha = [pasta];
  let vistos = 0;
  while (pilha.length) {
    const atual = pilha.pop();
    let entradas;
    try {
      entradas = fs.readdirSync(atual, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entradas) {
      if (++vistos > LIMITE_DA_VARREDURA) return null;
      const completo = path.join(atual, e.name);
      if (e.name === '.git') continue;
      if (caminhoDeSegredo(completo)) {
        // Pasta de segredo (`.secrets/`): ignorada se o que está dentro dela é ignorado (padrão `pasta/`).
        const alvo = e.isDirectory() ? path.join(completo, 'x') : completo;
        if (!ignoradoPeloGit(alvo, raiz)) achados.push(completo);
        continue;
      }
      if (e.isDirectory() && !e.isSymbolicLink() && !ignoradoPeloGit(path.join(completo, 'x'), raiz)) pilha.push(completo);
    }
  }
  return achados;
}

/**
 * O `.gitignore` da raiz ignora o arquivo? Implementa o necessário do formato: comentários, `!` (o último padrão que
 * casa vence), barra final de pasta, barra inicial ou no meio (ancorado), `*`, `?`, `**`. Sem `.gitignore`: não ignora.
 */
const CACHE_DO_GITIGNORE = new Map();

export function ignoradoPeloGit(arquivo, raiz) {
  let linhas = CACHE_DO_GITIGNORE.get(raiz);
  if (!linhas) {
    try {
      linhas = fs.readFileSync(path.join(raiz, '.gitignore'), 'utf8').split(/\r?\n/);
    } catch {
      linhas = [];
    }
    CACHE_DO_GITIGNORE.set(raiz, linhas);
  }
  const relativo = path.relative(raiz, arquivo).split(path.sep).join('/');
  if (relativo.startsWith('..')) return false;
  const partes = relativo.split('/');
  let ignorado = false;
  for (const bruta of linhas) {
    let linha = bruta.trim();
    if (!linha || linha.startsWith('#')) continue;
    const negado = linha.startsWith('!');
    if (negado) linha = linha.slice(1);
    const soPasta = linha.endsWith('/');
    if (soPasta) linha = linha.slice(0, -1);
    const ancorado = linha.includes('/');
    linha = linha.replace(/^\//, '');
    const regex = new RegExp(`^${linha.split('**').map((p) => p.replace(/[.+^${}()|\\]/g, '\\$&').replace(/\*/g, '[^/]*').replace(/\?/g, '[^/]')).join('.*')}$`, 'i');
    let casa = false;
    if (ancorado) {
      // Casa o caminho inteiro ou uma pasta antepassada.
      for (let i = 1; i <= partes.length; i++) {
        const prefixo = partes.slice(0, i).join('/');
        if (regex.test(prefixo) && (!soPasta || i < partes.length)) casa = true;
      }
    } else {
      partes.forEach((parte, i) => {
        if (regex.test(parte) && (!soPasta || i < partes.length - 1)) casa = true;
      });
    }
    if (casa) ignorado = !negado;
  }
  return ignorado;
}

function raizDoProjeto(cwd) {
  return path.resolve(process.env.CLAUDE_PROJECT_DIR || cwd);
}

/** Grep/Glob sobre pasta: confere o que há dentro e o padrão. */
function avaliarBuscaEmPasta(ferramenta, dados, cwd) {
  const pasta = normalizar(typeof dados.path === 'string' && dados.path ? dados.path : '.', cwd);
  let info;
  try {
    info = fs.statSync(pasta);
  } catch {
    return null; // não existe: nada a procurar
  }
  if (!info.isDirectory()) return null; // arquivo: já conferido pelo nome
  const padrao = ferramenta === 'Glob' ? dados.pattern : dados.glob;
  if (padraoAlcancaSegredo(padrao)) return REGRA;
  // Glob só lista nomes e o padrão já foi conferido; um padrão que não alcança nome de segredo não lista segredo.
  if (ferramenta === 'Glob') return padrao ? null : REGRA_PASTA;
  // Grep com `glob` que não alcança segredo: o segredo não entra na busca.
  if (typeof padrao === 'string' && padrao) return null;
  // Grep sem `glob`: só passa se nenhum segredo da pasta escapar do .gitignore (o Grep pula os ignorados).
  const segredos = segredosAlcancaveis(pasta, raizDoProjeto(cwd));
  if (segredos === null) return REGRA_PASTA; // grande demais para conferir: na dúvida, bloqueia
  return segredos.length ? REGRA_PASTA : null;
}

export function avaliar(entrada) {
  const ferramenta = entrada.tool_name;
  if (!FERRAMENTAS.has(ferramenta)) {
    return null;
  }
  const dados = entrada.tool_input;
  if (!dados || typeof dados !== 'object') {
    throw new TypeError('tool_input ausente');
  }
  const cwd = typeof entrada.cwd === 'string' && entrada.cwd ? entrada.cwd : process.env.CLAUDE_PROJECT_DIR || process.cwd();
  // No Grep, `pattern` é a expressão procurada (texto), não caminho: só `path` e `glob` contam.
  const campos = ferramenta === 'Grep' ? ['path', 'glob'] : ['file_path', 'notebook_path', 'path', 'pattern'];
  for (const campo of campos) {
    const valor = dados[campo];
    if (typeof valor !== 'string' || !valor) continue;
    const alternativas = expandirChaves(valor);
    if (alternativas.some((a) => caminhoProibido(a, cwd))) return REGRA;
    if ((campo === 'pattern' || campo === 'glob') && padraoAlcancaSegredo(valor) && /^\.|[\\/]\./.test(valor.split(/[\\/]/).pop() ?? '')) {
      return REGRA; // padrão que começa por ponto e alcança segredo (`.e*`, `**/.env*`)
    }
  }
  if (ferramenta === 'Grep' || ferramenta === 'Glob') {
    return avaliarBuscaEmPasta(ferramenta, dados, cwd);
  }
  return null;
}

principal(import.meta.url, 'guarda-arquivos', avaliar);
