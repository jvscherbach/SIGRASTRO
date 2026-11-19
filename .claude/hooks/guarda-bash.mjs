// Guarda 1 (PreToolUse, Bash e PowerShell): transforma as regras permanentes do CLAUDE.md em bloqueio automático.
//
// Bloqueia (código 2, mensagem em português com a regra e o que fazer):
//  - qualquer `git` que não seja `git diff` (inclusive com -C, em comando composto, subshell, `bash -c`, `pwsh -c`)
//    e qualquer `gh`;
//  - `docker compose ... down -v/--volumes`, `docker volume rm|remove|prune`, `docker system prune --volumes`;
//  - leitura de arquivo de segredo por qualquer comando: `.env`, `.env.local`, `.env.*` (menos `*.example`) e
//    `mapa-local.csv`;
//  - dump de ambiente: `printenv`, `env` sem comando, `set` sem argumentos, `export -p`, `Get-ChildItem env:`,
//    `docker ... exec ... env|printenv`, `docker inspect` sem `--format`, `docker compose config` com valores, e
//    `echo`/`printf` de variáveis das integrações.
// Todo o resto passa. Erro interno não trava a sessão (comum.mjs).

import { caminhoDeSegredo, principal } from './comum.mjs';

const SHELLS = new Set(['bash', 'sh', 'zsh', 'dash', 'ash', 'ksh']);
const POWERSHELL = new Set(['powershell', 'pwsh']);
const IMPRESSAO = new Set(['echo', 'printf', 'print', 'write-output', 'write-host', 'write-information', 'out-host']);
const LISTAGEM_PS = new Set(['get-childitem', 'gci', 'dir', 'ls', 'get-item', 'gi', 'get-content', 'gc', 'type', 'cat']);
/** Variáveis que levam a sistemas externos (CLAUDE.md, compose.verificacao.yaml) e a senha do MCP. */
const VARIAVEL_SENSIVEL = /\$(?:\{|env:)?(SIGA_DB_|LICENCIAMENTO_API_|SCORP_API_|SCORP_TOKEN|VISTAS_DB_|RASTRO_DB_|RASTRO_MCP_SENHA)/i;
/** Opções globais do git que levam um valor separado. */
const GIT_OPCAO_COM_VALOR = new Set(['-c', '-C', '--git-dir', '--work-tree', '--namespace', '--exec-path', '--config-env']);

const REGRA_GIT =
  'Bloqueado pela guarda do projeto (regra: só `git diff` é permitido; nada de commit, push, branch, stash, add, ' +
  'checkout, reset, gh etc.). Quem commita é o responsável. Use `git diff` ou `git diff --stat` para mostrar a mudança.';
const REGRA_GH = 'Bloqueado pela guarda do projeto (regra: nenhum comando `gh`). Operações no GitHub ficam com o responsável.';
const REGRA_VOLUME =
  'Bloqueado pela guarda do projeto (regra: nunca apagar volumes; nada de `docker compose down -v`, ' +
  '`docker volume rm/prune` nem `docker system prune --volumes`). Use `docker compose ... down` sem `-v`.';
const REGRA_SEGREDO =
  'Bloqueado pela guarda do projeto (regra: nunca abrir, imprimir ou copiar `.env`, `.env.local`, `.env.*` nem ' +
  '`mapa-local.csv`). Use o `.env.local.example` para ver os nomes das variáveis; para saber se uma variável está ' +
  'preenchida, confira dentro do container sem imprimir o valor: ' +
  '[ -z "$VAR" ] && echo vazia || echo preenchida';
const REGRA_AMBIENTE =
  'Bloqueado pela guarda do projeto (regra: não despejar variáveis de ambiente, que podem trazer credenciais). O jeito ' +
  'permitido é só "vazia ou preenchida", sem o valor: [ -z "$VAR" ] && echo vazia || echo preenchida';
const REGRA_COMPOSE_CONFIG =
  'Bloqueado pela guarda do projeto (regra: `docker compose config` resolve o .env.local e imprime os valores). Use ' +
  '`docker compose ... config --services` (só nomes) ou `--env-file .env.local.example`.';

// --- análise do comando

/**
 * Divide o texto em trechos de comando: separa em `&&`, `||`, `;`, `|`, `&`, quebra de linha, parênteses, chaves e
 * crase, fora de aspas. Substituições `$( ... )` e crases (mesmo dentro de aspas duplas) viram trechos próprios.
 */
export function trechos(texto) {
  const resultado = [];
  for (const interno of substituicoes(texto)) {
    resultado.push(...trechos(interno));
  }
  let atual = '';
  let aspas = null;
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (aspas) {
      if (c === '\\' && aspas === '"' && i + 1 < texto.length) {
        atual += c + texto[++i];
        continue;
      }
      if (c === aspas) {
        aspas = null;
      }
      atual += c;
      continue;
    }
    if (c === '"' || c === "'") {
      aspas = c;
      atual += c;
      continue;
    }
    if (c === '\\' && i + 1 < texto.length && texto[i + 1] !== '\n' && texto[i + 1] !== '\r') {
      atual += c + texto[++i];
      continue;
    }
    if ('&|;\n\r(){}`'.includes(c)) {
      if (atual.trim()) {
        resultado.push(atual.trim());
      }
      atual = '';
      continue;
    }
    atual += c;
  }
  if (atual.trim()) {
    resultado.push(atual.trim());
  }
  return resultado;
}

/** Conteúdo de `$( ... )` e de crases, inclusive dentro de aspas duplas (fora de aspas simples). */
function substituicoes(texto) {
  const achados = [];
  let simples = false;
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (c === "'" ) {
      simples = !simples;
      continue;
    }
    if (simples) {
      continue;
    }
    if (c === '$' && texto[i + 1] === '(') {
      let profundidade = 1;
      let j = i + 2;
      while (j < texto.length && profundidade > 0) {
        if (texto[j] === '(') profundidade++;
        if (texto[j] === ')') profundidade--;
        j++;
      }
      achados.push(texto.slice(i + 2, j - 1));
      i = j - 1;
    } else if (c === '`') {
      const fim = texto.indexOf('`', i + 1);
      if (fim > i) {
        achados.push(texto.slice(i + 1, fim));
        i = fim;
      }
    }
  }
  return achados;
}

/**
 * Palavras do trecho, sem as aspas. A barra invertida fica como está (caminhos do Windows, `D:\p\.env.local`), menos
 * dentro de aspas duplas antes de `"`, `\`, `$` ou crase, como no bash.
 */
export function palavras(trecho) {
  const resultado = [];
  let atual = '';
  let tem = false;
  let aspas = null;
  for (let i = 0; i < trecho.length; i++) {
    const c = trecho[i];
    if (aspas) {
      if (c === '\\' && aspas === '"' && '"\\$`'.includes(trecho[i + 1] ?? 'x')) {
        atual += trecho[++i];
        continue;
      }
      if (c === aspas) {
        aspas = null;
        continue;
      }
      atual += c;
      continue;
    }
    if (c === '"' || c === "'") {
      aspas = c;
      tem = true;
      continue;
    }
    if (/\s/.test(c)) {
      if (tem || atual) {
        resultado.push(atual);
      }
      atual = '';
      tem = false;
      continue;
    }
    atual += c;
    tem = true;
  }
  if (tem || atual) {
    resultado.push(atual);
  }
  return resultado;
}

/** Nome do programa sem caminho nem extensão, em minúsculas (`C:\\Git\\bin\\GIT.EXE` → `git`). */
export function programa(palavra) {
  const nome = (palavra ?? '').split(/[\\/]/).pop().toLowerCase();
  return nome.replace(/\.(exe|cmd|bat|ps1|com)$/, '');
}

/** Pula atribuições (`VAR=x`) e invólucros (`sudo`, `env VAR=x`, `nohup`, `time`, `xargs`, `&`...). */
function inicioDoComando(ps) {
  let i = 0;
  while (i < ps.length) {
    const p = ps[i];
    const nome = programa(p);
    if (/^[A-Za-z_][A-Za-z0-9_]*=/.test(p)) {
      i++;
    } else if (['sudo', 'command', 'exec', 'nohup', 'time', 'builtin', 'noglob', '&', '.'].includes(nome)) {
      i++;
      while (i < ps.length && ps[i].startsWith('-')) i++;
    } else if (nome === 'nice' || nome === 'timeout') {
      i++;
      while (i < ps.length && ps[i].startsWith('-')) i++;
      if (nome === 'timeout' && i < ps.length) i++; // duração
      if (nome === 'nice' && i < ps.length && /^-?\d+$/.test(ps[i])) i++;
    } else if (nome === 'xargs') {
      i++;
      while (i < ps.length && ps[i].startsWith('-')) {
        if (['-I', '-n', '-L', '-P', '-d', '-E', '-s'].includes(ps[i])) i++;
        i++;
      }
    } else if (nome === 'env' && i + 1 < ps.length) {
      // `env` com comando é invólucro; sozinho (ou só com opções) é dump, tratado em avaliarTrecho.
      let j = i + 1;
      while (j < ps.length && (ps[j].startsWith('-') || /^[A-Za-z_][A-Za-z0-9_]*=/.test(ps[j]))) {
        if (ps[j] === '-u' || ps[j] === '-C' || ps[j] === '-S') j++;
        j++;
      }
      if (j >= ps.length) {
        return i; // env sem comando
      }
      i = j;
    } else {
      return i;
    }
  }
  return i;
}

function avaliarGit(args) {
  let i = 0;
  while (i < args.length && args[i].startsWith('-')) {
    const opcao = args[i];
    if (GIT_OPCAO_COM_VALOR.has(opcao)) {
      i += 2;
    } else {
      i += 1;
    }
  }
  const subcomando = (args[i] ?? '').toLowerCase();
  if (subcomando !== 'diff') {
    return REGRA_GIT;
  }
  // `git diff --output=arquivo` grava arquivo: fora do "só olhar".
  if (args.slice(i + 1).some((a) => /^--output(=|$)/i.test(a))) {
    return REGRA_GIT;
  }
  return null;
}

function avaliarDocker(nome, ps) {
  const minusculas = ps.map((p) => p.toLowerCase());
  const tem = (palavra) => minusculas.includes(palavra);
  const compose = nome === 'docker-compose' || tem('compose');
  const depoisDe = (palavra) => minusculas.slice(minusculas.indexOf(palavra) + 1);

  if (compose && tem('down')) {
    const opcoes = depoisDe('down');
    if (opcoes.some((o) => o === '--volumes' || o === '--volumes=true' || /^-[a-z]*v[a-z]*$/.test(o))) {
      return REGRA_VOLUME;
    }
  }
  if (tem('volume') && ['rm', 'remove', 'prune'].some((s) => depoisDe('volume').includes(s))) {
    return REGRA_VOLUME;
  }
  if (tem('system') && depoisDe('system').includes('prune') && minusculas.some((o) => o.startsWith('--volumes'))) {
    return REGRA_VOLUME;
  }
  if (compose && tem('config')) {
    const seguros = ['--services', '--volumes', '--profiles', '--images', '--networks', '-q', '--quiet', '--hash'];
    if (!depoisDe('config').some((o) => seguros.includes(o.split('=')[0]))) {
      return REGRA_COMPOSE_CONFIG;
    }
  }
  if (tem('exec') || tem('run')) {
    const depois = depoisDe(tem('exec') ? 'exec' : 'run');
    if (depois.some((o) => o === 'env' || o === 'printenv' || o === 'set')) {
      return REGRA_AMBIENTE;
    }
  }
  if (tem('inspect') && !minusculas.some((o) => o === '-f' || o.startsWith('--format'))) {
    return REGRA_AMBIENTE;
  }
  return null;
}

/** Regras de um único trecho; devolve a mensagem de bloqueio ou nulo. `profundidade` evita recursão sem fim. */
function avaliarTrecho(trecho, profundidade) {
  const ps = palavras(trecho);
  if (!ps.length) {
    return null;
  }
  // Arquivo de segredo em qualquer posição (cat, type, Get-Content, cp, grep, ls, redirecionamento...).
  if (ps.some((p) => p.split(/\s+/).some(caminhoDeSegredo))) {
    return REGRA_SEGREDO;
  }
  // Script dentro de `sh -c`, `pwsh -Command`, `iex`, em qualquer posição (ex.: docker exec ... sh -c '...').
  for (let i = 0; i < ps.length - 1; i++) {
    const nome = programa(ps[i]);
    if (SHELLS.has(nome) && /^-[a-z]*c[a-z]*$/i.test(ps[i + 1]) && ps[i + 2] !== undefined) {
      const interno = avaliarTexto(ps[i + 2], profundidade + 1);
      if (interno) return interno;
    }
    if (POWERSHELL.has(nome)) {
      const k = ps.findIndex((p, j) => j > i && /^-(c|command)$/i.test(p));
      if (k > 0) {
        const interno = avaliarTexto(ps.slice(k + 1).join(' '), profundidade + 1);
        if (interno) return interno;
      }
    }
    if (nome === 'iex' || nome === 'invoke-expression') {
      const interno = avaliarTexto(ps.slice(i + 1).filter((p) => !/^-command$/i.test(p)).join(' '), profundidade + 1);
      if (interno) return interno;
    }
  }

  const inicio = inicioDoComando(ps);
  if (inicio >= ps.length) {
    return null;
  }
  const nome = programa(ps[inicio]);
  const args = ps.slice(inicio + 1);

  if (nome === 'git') {
    return avaliarGit(args);
  }
  if (nome === 'gh') {
    return REGRA_GH;
  }
  if ((nome === 'start-process' || nome === 'saps' || nome === 'start') && args.some((a) => ['git', 'gh'].includes(programa(a)))) {
    return REGRA_GIT;
  }
  if (nome === 'docker' || nome === 'docker-compose' || nome === 'podman') {
    const docker = avaliarDocker(nome, ps.slice(inicio));
    if (docker) return docker;
  }
  if (nome === 'printenv') {
    return REGRA_AMBIENTE;
  }
  if (nome === 'env' && args.every((a) => a.startsWith('-'))) {
    return REGRA_AMBIENTE;
  }
  if ((nome === 'set' || nome === 'export' || nome === 'declare' || nome === 'typeset') &&
      (args.length === 0 || args.every((a) => /^-[a-z]*[px][a-z]*$/i.test(a)))) {
    return REGRA_AMBIENTE;
  }
  if (LISTAGEM_PS.has(nome) && args.some((a) => /^env:/i.test(a))) {
    return REGRA_AMBIENTE;
  }
  if (ps.some((p) => /\[(system\.)?environment\]::getenvironmentvariables/i.test(p))) {
    return REGRA_AMBIENTE;
  }
  if (IMPRESSAO.has(nome) && args.some((a) => VARIAVEL_SENSIVEL.test(a))) {
    return REGRA_AMBIENTE;
  }
  return null;
}

/** Avalia um comando inteiro (Bash ou PowerShell). */
export function avaliarTexto(texto, profundidade = 0) {
  if (typeof texto !== 'string' || !texto.trim() || profundidade > 5) {
    return null;
  }
  for (const trecho of trechos(texto)) {
    const bloqueio = avaliarTrecho(trecho, profundidade);
    if (bloqueio) {
      return bloqueio;
    }
  }
  return null;
}

/** Entrada do hook: só olha `tool_input.command` das ferramentas de shell. */
export function avaliar(entrada) {
  const ferramenta = entrada.tool_name;
  if (ferramenta !== 'Bash' && ferramenta !== 'PowerShell') {
    return null;
  }
  const comando = entrada.tool_input && entrada.tool_input.command;
  if (typeof comando !== 'string') {
    throw new TypeError('tool_input.command ausente');
  }
  return avaliarTexto(comando);
}

principal(import.meta.url, 'guarda-bash', avaliar);
