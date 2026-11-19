// Guarda 1 (PreToolUse, Bash e PowerShell): transforma as regras permanentes do CLAUDE.md em bloqueio automático.
// Postura (S6.1, D81): NA DÚVIDA, BLOQUEIA. Um comando que pode ler ou imprimir segredo e que esta análise estática não
// consegue provar inofensivo é bloqueado, com a regra e o jeito permitido. Falso bloqueio é preferível a vazamento.
//
// Bloqueia (código 2):
//  - git que não seja `git diff` (inclusive com -C, composto, subshell, `bash -c`, `pwsh -c`) e qualquer `gh`;
//  - apagar volumes (`down -v`, `docker volume rm|prune`, `docker system prune --volumes`);
//  - arquivo de segredo (`.env`, `.env.local`, `.env.*` menos `*.example`, `mapa-local.csv`, `.secrets/`,
//    `/run/secrets*`): citado por nome, por variação de nome (aspas, barra invertida, `$'..'`, chaves), por curinga
//    que o alcance, por caminho montado em tempo de execução (variável, `$(..)`, `xargs`), por busca, cópia ou pacote
//    recursivo a partir da raiz ou de pasta que contém segredo, e por comando montado dinamicamente (`eval`,
//    `sh -c "$X"`, `| sh`, `source <(..)`, `iex`, `-EncodedCommand`);
//  - despejo de ambiente: `printenv`, `env`, `set`, `export -p`, `declare -p`, `compgen -e`, `/proc/*/environ`,
//    `ps e`, `Get-ChildItem env:`, `$env:X` lido, `[Environment]::GetEnvironmentVariable(s)`, `docker inspect`,
//    `docker compose config` com valores, `docker|kubectl exec ... env`, interpretador inline (`node -e`,
//    `python -c`, `perl -e`, `awk`, heredoc...) que leia ambiente, arquivo ou rode processo, e eco de variável cujo
//    nome pareça segredo.
// Permite só a forma "vazia ou preenchida", sem o valor. Erro interno não trava a sessão (comum.mjs).
// O texto do bloqueio nunca repete o comando nem valor algum: só a regra e o exemplo do jeito permitido.

import fs from 'node:fs';
import path from 'node:path';

import { caminhoDeSegredo, principal } from './comum.mjs';

// --- vocabulário

const SHELLS = new Set(['bash', 'sh', 'zsh', 'dash', 'ash', 'ksh', 'fish', 'csh', 'tcsh', 'busybox']);
const POWERSHELL = new Set(['powershell', 'pwsh']);
/** Interpretadores e a opção que traz o código inline (`null`: o programa é o primeiro argumento, como no awk). */
const INTERPRETADORES = new Map([
  ['node', ['-e', '--eval', '-p', '--print']], ['nodejs', ['-e', '--eval', '-p', '--print']],
  ['python', ['-c']], ['python3', ['-c']], ['python2', ['-c']], ['py', ['-c']], ['pypy', ['-c']], ['pypy3', ['-c']],
  ['perl', ['-e', '-E']], ['ruby', ['-e']], ['php', ['-r']], ['bun', ['-e', '--eval', '-p', '--print']],
  ['deno', ['eval']], ['lua', ['-e']], ['rscript', ['-e']], ['osascript', ['-e']], ['jshell', []],
  ['awk', null], ['gawk', null], ['mawk', null], ['nawk', null],
]);
/** Comandos que mostram, copiam, enviam ou empacotam conteúdo de arquivo. */
const LEITORES = new Set([
  'cat', 'tac', 'nl', 'head', 'tail', 'less', 'more', 'most', 'bat', 'batcat', 'sort', 'uniq', 'cut', 'paste', 'od',
  'xxd', 'hexdump', 'hd', 'strings', 'base64', 'base32', 'basenc', 'awk', 'gawk', 'mawk', 'nawk', 'sed', 'tr',
  'column', 'fold', 'fmt', 'tee', 'dd', 'cp', 'mv', 'ln', 'install', 'rsync', 'scp', 'sftp', 'diff', 'diff3', 'cmp',
  'comm', 'join', 'iconv', 'expand', 'unexpand', 'pr', 'look', 'split', 'csplit', 'shuf', 'jq', 'yq', 'xmllint',
  'vi', 'vim', 'nvim', 'view', 'nano', 'emacs', 'ed', 'ex', 'source', '.', 'grep', 'egrep', 'fgrep', 'zgrep', 'rg',
  'ag', 'ack', 'ack-grep', 'ugrep', 'ug', 'pt', 'sift', 'findstr', 'type', 'get-content', 'gc', 'select-string', 'sls',
  'import-csv', 'ipcsv', 'get-item', 'gi', 'copy-item', 'cpi', 'copy', 'move-item', 'mi', 'move', 'xcopy', 'robocopy',
  'format-hex', 'fhx', 'tar', 'zip', 'unzip', '7z', '7za', 'gzip', 'gunzip', 'zcat', 'bzip2', 'bzcat', 'xz', 'xzcat',
  'zstd', 'openssl', 'gpg', 'curl', 'wget', 'nc', 'ncat', 'socat', 'compress-archive', 'invoke-webrequest', 'iwr',
  'invoke-restmethod', 'irm', 'certutil', 'bitsadmin',
]);
/** Leitores que percorrem pastas por si (busca recursiva sempre ligada). */
const BUSCA_SEMPRE_RECURSIVA = new Set(['rg', 'ag', 'ack', 'ack-grep', 'pt', 'sift']);
const GREPS = new Set(['grep', 'egrep', 'fgrep', 'zgrep', 'ugrep', 'ug']);
/** Empacotam ou copiam pastas inteiras. */
const PACOTES = new Set(['tar', 'zip', '7z', '7za', 'compress-archive', 'jar']);
const COPIADORES = new Set(['cp', 'rsync', 'scp', 'xcopy', 'robocopy', 'copy-item', 'cpi', 'copy']);
/** Só listam nomes, nunca conteúdo: o curinga que alcança o segredo não é leitura. */
const SO_NOMES = new Set(['ls', 'dir', 'get-childitem', 'gci', 'find', 'du', 'stat', 'file', 'wc', 'test', '[', '[[']);
const LISTAGEM_PS = new Set(['get-childitem', 'gci', 'dir', 'ls', 'get-item', 'gi', 'get-content', 'gc', 'type', 'cat']);
/** Palavras de controle do shell que antecedem o comando de verdade. */
const CONTROLE = new Set(['if', 'then', 'else', 'elif', 'fi', 'do', 'done', 'while', 'until', 'for', 'in', 'case',
  'esac', '!', 'function', 'select', 'time', 'coproc']);
const TESTES = new Set(['[', '[[', 'test']);
/** Nome de variável que parece segredo (só o nome, nunca o valor). */
const NOME_SENSIVEL = /(SENHA|PASSWORD|PASSWD|PASS\b|TOKEN|SECRET|SEGREDO|KEY|CHAVE|COOKIE|CREDENCIAL|CREDENTIAL|AUTH|DB_|SIGA_DB|LICENCIAMENTO_API|SCORP_|RASTRO_MCP_SENHA|_API_)/i;
/** Código inline de interpretador que lê ambiente, lê arquivo, lista pasta ou roda processo. */
const CODIGO_ARRISCADO = new RegExp([
  'env', 'environ', 'getenv', '/proc', // ambiente
  'readFile', 'createReadStream', 'openSync', '\\bopen\\s*\\(', 'read_text', 'read_bytes', '\\bFile\\.', '\\bIO\\.',
  'file_get_contents', 'fopen', 'readlines', 'slurp', 'ReadAll', 'Get-Content', '\\bfs\\b', 'pathlib', 'Path\\(',
  'readdir', 'listdir', 'scandir', '\\bwalk\\b', 'glob', 'getline', // arquivos
  'child_process', 'execSync', 'spawn', 'subprocess', 'os\\.system', 'popen', '\\bsystem\\s*\\(', '\\bexec\\b',
  '`', 'Start-Process', 'Invoke-Expression', '\\biex\\b', 'require\\s*\\(', '\\bimport\\b', // processos e módulos
].join('|'), 'i');
/** Executam outro comando que vem nos argumentos (o comando é analisado; montado na execução, bloqueia). */
const EXECUTORES = new Set(['watch', 'script', 'flock', 'strace', 'ltrace', 'su', 'runuser', 'chroot', 'nsenter',
  'ssh', 'npx', 'cross-env', 'dotenv', 'dotenvx', 'gdb', 'valgrind', 'entr', 'unshare', 'setsid', 'firejail',
  'pnpm', 'yarn', 'bunx', 'pipx', 'uvx', 'start-job', 'invoke-command', 'icm', 'start-threadjob']);
/** Cmdlets que filtram ou passam adiante o que vem pelo cano (procura-se a origem antes deles). */
const FILTROS_PS = new Set(['?', 'where-object', 'where', 'select-object', 'select', 'sort-object', 'sort',
  'group-object', 'tee-object', 'out-null']);
/** Cmdlets que listam arquivos: o objeto que eles passam pelo cano é o arquivo, que o leitor seguinte abre. */
const LISTAGENS_PS = new Set(['get-childitem', 'gci', 'dir', 'ls', 'get-item', 'gi', 'resolve-path', 'rvpa']);
/** Leitores do PowerShell que, sem caminho, abrem os arquivos que chegam pelo cano. */
const LEITORES_DO_CANO_PS = new Set(['get-content', 'gc', 'cat', 'type', 'select-string', 'sls', 'import-csv',
  'ipcsv', 'copy-item', 'cpi', 'move-item', 'mi', 'format-hex', 'fhx', 'compress-archive']);
const GIT_OPCAO_COM_VALOR = new Set(['-c', '-C', '--git-dir', '--work-tree', '--namespace', '--exec-path', '--config-env']);
/** Amostras de nome que um curinga não pode alcançar. */
const AMOSTRAS_DE_SEGREDO = ['.env', '.env.local', '.env.producao', '.env.x', 'mapa-local.csv'];
const LIMITE_DA_VARREDURA = 40_000;

// --- mensagens (regra + jeito permitido; nunca o comando nem valor)

const COMO_VER_VARIAVEL =
  'Para saber se uma variável está preenchida, sem o valor: no Bash `[ -z "$VAR" ] && echo vazia || echo preenchida`; ' +
  "no PowerShell `if ([string]::IsNullOrEmpty($env:VAR)) { 'vazia' } else { 'preenchida' }`; dentro do container " +
  "`docker compose -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml exec -T api sh -c '[ -z \"$VAR\" ] && echo vazia || echo preenchida'`.";
export const REGRA_GIT =
  'Bloqueado pela guarda do projeto (regra: só `git diff` é permitido; nada de commit, push, branch, stash, add, ' +
  'checkout, reset, gh etc.). Quem commita é o responsável. Use `git diff` ou `git diff --stat` para mostrar a mudança.';
export const REGRA_GH =
  'Bloqueado pela guarda do projeto (regra: nenhum comando `gh`). Operações no GitHub ficam com o responsável.';
export const REGRA_VOLUME =
  'Bloqueado pela guarda do projeto (regra: nunca apagar volumes; nada de `docker compose down -v`, ' +
  '`docker volume rm/prune` nem `docker system prune --volumes`). Use `docker compose ... down` sem `-v`.';
export const REGRA_SEGREDO =
  'Bloqueado pela guarda do projeto (regra: nunca abrir, imprimir, copiar ou enviar `.env`, `.env.local`, `.env.*`, ' +
  '`mapa-local.csv` nem os arquivos de token montados, nem por variação do nome). Os nomes das variáveis estão no ' +
  '`.env.local.example` (ex.: `cat .env.local.example`). ' + COMO_VER_VARIAVEL;
export const REGRA_CURINGA =
  'Bloqueado pela guarda do projeto (regra: curinga que pode alcançar arquivo de segredo, como `.e*`, `.*` ou `*`). ' +
  'Use o nome exato ou um curinga que não alcance o segredo, ex.: `cat docs/*.md` ou `cat .env.local.example`.';
export const REGRA_BUSCA =
  'Bloqueado pela guarda do projeto (regra: busca, cópia ou pacote recursivo a partir da raiz, de pasta que contém ' +
  'arquivo de segredo ou de alvo que não dá para conferir). Aponte para pastas de código, ex.: ' +
  '`grep -rn TERMO api/src docs front/src` ou `rg TERMO docs`; para empacotar, use a pasta exata (`tar czf x.tgz api/src`).';
export const REGRA_DINAMICO =
  'Bloqueado pela guarda do projeto (regra: comando ou caminho montado em tempo de execução que pode ler segredo: ' +
  'variável ou `$(...)` como caminho de leitura, `xargs` com leitor, `eval`, `sh -c` com variável, `| sh`, ' +
  '`source <(...)`, `iex`, `-EncodedCommand`). Escreva o comando com o caminho literal de um arquivo do projeto, ' +
  'ex.: `cat docs/README.md`, ou use a ferramenta Read.';
export const REGRA_AMBIENTE =
  'Bloqueado pela guarda do projeto (regra: não despejar variáveis de ambiente nem imprimir o valor de variável que ' +
  'pode ser credencial). ' + COMO_VER_VARIAVEL;
export const REGRA_INTERPRETADOR =
  'Bloqueado pela guarda do projeto (regra: código inline de interpretador, como `node -e`, `python -c`, `perl -e`, ' +
  '`awk` ou heredoc, que lê ambiente ou arquivo, lista pasta, carrega módulo ou roda processo, não dá para conferir). ' +
  'Grave o código num arquivo do projeto e rode `node arquivo.mjs`, ou use a ferramenta Read. ' + COMO_VER_VARIAVEL;
export const REGRA_COMPOSE_CONFIG =
  'Bloqueado pela guarda do projeto (regra: `docker compose config` resolve o `.env.local` e imprime os valores). ' +
  'Use `docker compose ... config --quiet` (só valida) ou `config --services` (só nomes), por exemplo ' +
  '`docker compose --env-file .env.local.example -f compose.yaml -f compose.local.yaml -f compose.verificacao.yaml config --services`.';

// --- leitura do comando

/**
 * Divide o texto em trechos de comando, fora de aspas: `&&`, `||`, `;`, `|`, `&`, quebra de linha, parênteses, chaves
 * e crase. `$( ... )` e crases (mesmo dentro de aspas duplas) também viram trechos próprios. Cada trecho sabe se veio
 * depois de um cano (`|`) e se é seguido de parêntese (argumento montado, comum no PowerShell).
 */
export function trechosDetalhados(texto) {
  const resultado = [];
  for (const interno of substituicoes(texto)) {
    for (const t of trechosDetalhados(interno)) resultado.push({ ...t, substituicao: true });
  }
  let atual = '';
  let aspas = null;
  let depoisDeCano = false;
  const fechar = (separador, proximo) => {
    if (atual.trim()) {
      resultado.push({ texto: atual.trim(), depoisDeCano, seguidoDeParentese: separador === '(' });
    } else if (separador === '(' && resultado.length && !depoisDeCano) {
      // "Get-Content (" sem palavra própria: o anterior é que é seguido de parêntese
    }
    depoisDeCano = separador === '|' && proximo !== '|';
    atual = '';
  };
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (aspas) {
      if (c === '\\' && aspas === '"' && i + 1 < texto.length) {
        atual += c + texto[++i];
        continue;
      }
      if (c === aspas) aspas = null;
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
    // `${NOME}` é uma palavra só (expansão de parâmetro): partir nas chaves fazia `echo ${TOKEN}` virar `echo $` e `TOKEN`,
    // e a variável sensível deixava de ser vista (S6.1, bloco 3: achado do corpus de vazamento).
    if (c === '$' && texto[i + 1] === '{') {
      const fim = texto.indexOf('}', i + 2);
      if (fim !== -1) {
        atual += texto.slice(i, fim + 1);
        i = fim;
        continue;
      }
    }
    if ('&|;\n\r(){}`'.includes(c)) {
      if (c === '|' && texto[i + 1] === '|') {
        fechar('||');
        i++;
        depoisDeCano = false;
        continue;
      }
      if (c === '&' && texto[i + 1] === '&') {
        fechar('&&');
        i++;
        continue;
      }
      fechar(c, texto[i + 1]);
      continue;
    }
    atual += c;
  }
  fechar('fim');
  return resultado;
}

/** Compatível com os testes antigos: só os textos. */
export function trechos(texto) {
  return trechosDetalhados(texto).filter((t) => !t.substituicao).map((t) => t.texto);
}

/** Conteúdo de `$( ... )` e de crases, inclusive dentro de aspas duplas (fora de aspas simples). */
function substituicoes(texto) {
  const achados = [];
  let simples = false;
  for (let i = 0; i < texto.length; i++) {
    const c = texto[i];
    if (c === "'") {
      simples = !simples;
      continue;
    }
    if (simples) continue;
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

/** `\xHH`, `\NNN`, `\n`... de `$'...'` (ANSI-C do bash). */
function decodificarAnsiC(corpo) {
  return corpo.replace(/\\(x[0-9a-fA-F]{1,2}|[0-7]{1,3}|.)/g, (_, e) => {
    if (e[0] === 'x') return String.fromCharCode(parseInt(e.slice(1), 16));
    if (/^[0-7]+$/.test(e)) return String.fromCharCode(parseInt(e, 8));
    return { n: '\n', t: '\t', r: '\r', a: '', b: '', e: '', f: '', v: '' }[e] ?? e;
  });
}

/** `$` seguido disto expande (nome, `{`, `(`, parâmetro especial); `$` no fim ou antes de aspa é literal (`"fim$"`). */
function expansao(proximo) {
  return proximo !== undefined && /[A-Za-z_{(0-9@*#?!$]/.test(proximo);
}

/**
 * Palavras do trecho com o que importa para a guarda: `texto` sem aspas; `expande` (há `$` ou crase fora de aspas
 * simples: o valor só existe na execução); `curinga` (há `*`, `?` ou `[` fora de aspas). A barra invertida fica como
 * está (caminhos do Windows), menos dentro de aspas duplas antes de `"`, `\`, `$` ou crase, como no bash.
 */
export function palavrasDetalhadas(trecho) {
  const resultado = [];
  let atual = { texto: '', expande: false, curinga: false, existe: false };
  let aspas = null;
  const fechar = () => {
    if (atual.existe || atual.texto) resultado.push(atual);
    atual = { texto: '', expande: false, curinga: false, existe: false };
  };
  for (let i = 0; i < trecho.length; i++) {
    const c = trecho[i];
    if (aspas === "$'") {
      let j = i;
      let corpo = '';
      while (j < trecho.length && trecho[j] !== "'") {
        if (trecho[j] === '\\' && j + 1 < trecho.length) {
          corpo += trecho[j] + trecho[j + 1];
          j += 2;
        } else {
          corpo += trecho[j++];
        }
      }
      atual.texto += decodificarAnsiC(corpo);
      i = j;
      aspas = null;
      continue;
    }
    if (aspas) {
      if (c === '\\' && aspas === '"' && '"\\$`'.includes(trecho[i + 1] ?? 'x')) {
        atual.texto += trecho[++i];
        continue;
      }
      if (c === aspas) {
        aspas = null;
        continue;
      }
      if (aspas === '"' && ((c === '$' && expansao(trecho[i + 1])) || c === '`')) atual.expande = true;
      atual.texto += c;
      continue;
    }
    if (c === '$' && trecho[i + 1] === "'") {
      aspas = "$'";
      atual.existe = true;
      i++;
      continue;
    }
    if (c === '"' || c === "'") {
      aspas = c;
      atual.existe = true;
      continue;
    }
    if (/\s/.test(c)) {
      fechar();
      continue;
    }
    if ((c === '$' && expansao(trecho[i + 1])) || c === '`') atual.expande = true;
    if (c === '*' || c === '?' || c === '[') atual.curinga = true;
    if (c === '%' && /^%[A-Za-z_][A-Za-z0-9_]*%/.test(trecho.slice(i))) atual.expande = true; // cmd: %VAR%
    atual.texto += c;
    atual.existe = true;
  }
  fechar();
  return resultado;
}

/** Compatível com os testes antigos: só os textos. */
export function palavras(trecho) {
  return palavrasDetalhadas(trecho).map((p) => p.texto);
}

/** Nome do programa sem caminho nem extensão, em minúsculas (`C:\\Git\\bin\\GIT.EXE` → `git`). */
export function programa(palavra) {
  const nome = (palavra ?? '').split(/[\\/]/).pop().toLowerCase();
  return nome.replace(/\.(exe|cmd|bat|ps1|com)$/, '');
}

/** Variações de uma palavra para procurar o nome do segredo: como está e sem barras invertidas de escape. */
function variacoes(texto) {
  const sem = texto.replace(/\\(.)/g, '$1');
  return sem === texto ? [texto] : [texto, sem];
}

/** A palavra cita um arquivo de segredo (inclusive `x=.env.local`, `@.env.local`, `<.env.local`, `.env\.local`). */
function citaSegredo(texto) {
  return variacoes(texto).some((v) => v.split(/\s+/).some(caminhoDeSegredo));
}

/** Curinga (`*`, `?`, `[..]`) do último segmento que alcança um nome de segredo. */
export function curingaAlcancaSegredo(texto) {
  const ultimo = texto.split(/[\\/]/).pop() ?? '';
  if (!/[*?[]/.test(ultimo)) return false;
  let fonte = '';
  for (let i = 0; i < ultimo.length; i++) {
    const c = ultimo[i];
    if (c === '*') fonte += '.*';
    else if (c === '?') fonte += '.';
    else if (c === '[') {
      const fim = ultimo.indexOf(']', i + 1);
      if (fim < 0) return true; // classe sem fim: na dúvida
      fonte += `[${ultimo.slice(i + 1, fim).replace(/^!/, '^').replace(/\\/g, '\\\\')}]`;
      i = fim;
    } else fonte += c.replace(/[.+^${}()|\\]/g, '\\$&');
  }
  let regex;
  try {
    regex = new RegExp(`^${fonte}$`, 'i');
  } catch {
    return true;
  }
  return AMOSTRAS_DE_SEGREDO.some((amostra) => regex.test(amostra));
}

/** Expande chaves simples (`.e{nv,xx}.local`) do texto bruto e diz se alguma alternativa é segredo. */
function chavesAlcancamSegredo(texto) {
  for (const m of texto.matchAll(/[^\s'"]*\{[^{}\s]*,[^{}\s]*\}[^\s'"|;&]*/g)) {
    const palavra = m[0];
    const abre = palavra.indexOf('{');
    const fecha = palavra.indexOf('}', abre);
    const antes = palavra.slice(0, abre);
    const depois = palavra.slice(fecha + 1);
    for (const alt of palavra.slice(abre + 1, fecha).split(',')) {
      const candidato = antes + alt + depois;
      if (citaSegredo(candidato) || curingaAlcancaSegredo(candidato)) return true;
    }
  }
  return false;
}

/** Variáveis lidas numa palavra (`$X`, `${X}`, `$env:X`, `${env:X}`, `%X%`). */
function variaveisLidas(texto) {
  const nomes = [];
  for (const m of texto.matchAll(/\$\{?(?:env:)?([A-Za-z_][A-Za-z0-9_]*)/gi)) nomes.push(m[1]);
  for (const m of texto.matchAll(/%([A-Za-z_][A-Za-z0-9_]*)%/g)) nomes.push(m[1]);
  return nomes;
}

// --- sistema de arquivos (alvo de busca e cópia recursiva)

function raizDoProjeto(contexto) {
  return path.resolve(contexto.raiz || contexto.cwd);
}

/** `.`, `./`, `..`, `*`, `/`, `~`, raiz de disco, ou caminho que é a pasta atual, a raiz do projeto ou acima delas. */
export function alvoNaRaiz(alvo, contexto) {
  const t = alvo.trim();
  if (/^(\.{1,2}([\\/]\.{1,2})*[\\/]?|\*|[\\/]|~[\\/]?|[a-zA-Z]:[\\/]?|\$HOME|\$env:USERPROFILE)$/.test(t)) return true;
  const resolvido = path.resolve(contexto.cwd, t.replace(/^~(?=[\\/]|$)/, process.env.HOME || process.env.USERPROFILE || '~'));
  const acima = (base) => {
    const relativo = path.relative(resolvido, base);
    return relativo === '' || (!relativo.startsWith('..') && !path.isAbsolute(relativo));
  };
  return acima(path.resolve(contexto.cwd)) || acima(raizDoProjeto(contexto));
}

/**
 * Procura arquivo de segredo dentro do alvo (sem seguir links de pasta). Devolve 'sim', 'nao' ou 'incerto' (pasta
 * grande demais para conferir: tratada como 'sim').
 */
export function contemSegredo(alvo, contexto) {
  const inicio = path.resolve(contexto.cwd, alvo);
  let info;
  try {
    info = fs.lstatSync(inicio);
  } catch {
    return 'nao'; // não existe: não há o que ler
  }
  if (!info.isDirectory()) return caminhoDeSegredo(inicio) ? 'sim' : 'nao';
  const pilha = [inicio];
  let vistos = 0;
  while (pilha.length) {
    const pasta = pilha.pop();
    let entradas;
    try {
      entradas = fs.readdirSync(pasta, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entradas) {
      if (++vistos > LIMITE_DA_VARREDURA) return 'incerto';
      const completo = path.join(pasta, e.name);
      if (caminhoDeSegredo(completo)) return 'sim';
      if (e.isDirectory() && !e.isSymbolicLink()) pilha.push(completo);
    }
  }
  return 'nao';
}

function alvoInseguro(palavra, contexto) {
  if (palavra.expande || palavra.curinga) return true; // não dá para conferir
  if (alvoNaRaiz(palavra.texto, contexto)) return true;
  return contemSegredo(palavra.texto, contexto) !== 'nao';
}

// --- regras por programa

/** Pula atribuições (`VAR=x`), palavras de controle e invólucros (`sudo`, `env VAR=x`, `nohup`, `xargs`, `&`...). */
function inicioDoComando(ps) {
  let i = 0;
  while (i < ps.length) {
    const p = ps[i].texto;
    const nome = programa(p);
    if (/^[A-Za-z_][A-Za-z0-9_]*\+?=/.test(p)) {
      i++;
    } else if (CONTROLE.has(nome)) {
      i++;
    } else if (['sudo', 'command', 'exec', 'nohup', 'builtin', 'noglob', '&', 'doas', 'stdbuf', 'unbuffer'].includes(nome)) {
      i++;
      while (i < ps.length && ps[i].texto.startsWith('-')) i++;
    } else if (nome === 'xargs' || nome === 'parallel') {
      // O programa de verdade vem depois das opções; a checagem de "xargs com leitor" é feita no texto inteiro.
      i++;
      while (i < ps.length && (ps[i].texto.startsWith('-') || ps[i].texto === '{}' || ps[i].texto === ':::')) {
        if (['-I', '-n', '-L', '-P', '-d', '-E', '-s', '-a', '--arg-file', '-j'].includes(ps[i].texto)) i++;
        i++;
      }
    } else if (nome === 'nice' || nome === 'timeout' || nome === 'ionice' || nome === 'taskset') {
      i++;
      while (i < ps.length && ps[i].texto.startsWith('-')) i++;
      if ((nome === 'timeout' || nome === 'taskset') && i < ps.length) i++;
      if (nome === 'nice' && i < ps.length && /^-?\d+$/.test(ps[i].texto)) i++;
    } else if (nome === 'env' && i + 1 < ps.length) {
      let j = i + 1;
      while (j < ps.length && (ps[j].texto.startsWith('-') || /^[A-Za-z_][A-Za-z0-9_]*=/.test(ps[j].texto))) {
        if (['-u', '-C', '-S'].includes(ps[j].texto)) j++;
        j++;
      }
      if (j >= ps.length) return i; // env sem comando: despejo
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
    i += GIT_OPCAO_COM_VALOR.has(args[i]) ? 2 : 1;
  }
  if ((args[i] ?? '').toLowerCase() !== 'diff') return REGRA_GIT;
  if (args.slice(i + 1).some((a) => /^--output(=|$)/i.test(a))) return REGRA_GIT;
  return null;
}

function avaliarDocker(nome, ps) {
  const minusculas = ps.map((p) => p.toLowerCase());
  const tem = (palavra) => minusculas.includes(palavra);
  const compose = nome === 'docker-compose' || tem('compose');
  const depoisDe = (palavra) => minusculas.slice(minusculas.indexOf(palavra) + 1);

  if (compose && tem('down')) {
    if (depoisDe('down').some((o) => o === '--volumes' || o === '--volumes=true' || /^-[a-z]*v[a-z]*$/.test(o))) {
      return REGRA_VOLUME;
    }
  }
  if (tem('volume') && ['rm', 'remove', 'prune'].some((s) => depoisDe('volume').includes(s))) return REGRA_VOLUME;
  if (tem('system') && depoisDe('system').includes('prune') && minusculas.some((o) => o.startsWith('--volumes'))) {
    return REGRA_VOLUME;
  }
  if (compose && tem('config')) {
    const opcoes = depoisDe('config').map((o) => o.split('=')[0]);
    const seguras = ['--services', '--volumes', '--profiles', '--images', '--networks', '-q', '--quiet', '--hash'];
    const imprimem = ['--environment', '--variables', '--no-interpolate', '--format', '--output', '-o'];
    if (!opcoes.some((o) => seguras.includes(o)) || opcoes.some((o) => imprimem.includes(o))) return REGRA_COMPOSE_CONFIG;
  }
  if (tem('exec') || tem('run')) {
    const depois = depoisDe(tem('exec') ? 'exec' : 'run');
    if (depois.some((o) => ['env', 'printenv', 'set', 'export', 'declare'].includes(o))) return REGRA_AMBIENTE;
  }
  if (tem('inspect')) {
    const k = minusculas.findIndex((o) => o === '-f' || o.startsWith('--format'));
    const formato = k < 0 ? '' : minusculas[k].includes('=') ? minusculas[k].split('=').slice(1).join('=') : (minusculas[k + 1] ?? '');
    if (k < 0 || /env|config|json\s*\.\s*}}|^\{\{\s*\.\s*\}\}$/.test(formato)) return REGRA_AMBIENTE;
  }
  return null;
}

/** Código inline (ou script por heredoc/here-string) de interpretador. */
function codigoArriscado(codigo) {
  return CODIGO_ARRISCADO.test(codigo) || citaSegredo(codigo);
}

/** Interpretador sem script em arquivo: código inline, `-`, ou nada (lê da entrada). */
function avaliarInterpretador(nome, args, depoisDeCano) {
  const opcoes = INTERPRETADORES.get(nome);
  const textos = args.map((a) => a.texto);
  if (opcoes === null) {
    // awk: o programa é o primeiro argumento que não é opção (ou -f arquivo)
    let i = 0;
    while (i < textos.length && textos[i].startsWith('-')) {
      if (['-f', '--file'].includes(textos[i])) return null;
      i += ['-F', '-v', '--assign', '--field-separator'].includes(textos[i]) ? 2 : 1;
    }
    const programaAwk = textos[i];
    if (programaAwk === undefined) return depoisDeCano ? REGRA_DINAMICO : null;
    return codigoArriscado(programaAwk) ? REGRA_INTERPRETADOR : null;
  }
  // Sem diferenciar caixa: na dúvida, `-E` também conta como código inline.
  const minusculas = opcoes.map((o) => o.toLowerCase());
  const k = textos.findIndex((t) => minusculas.includes(t.toLowerCase())
    || minusculas.some((o) => o.startsWith('--') && t.toLowerCase().startsWith(`${o}=`)));
  if (k >= 0) {
    const codigo = textos[k].includes('=') && textos[k].startsWith('--') ? textos[k].split('=').slice(1).join('=') : textos.slice(k + 1).join(' ');
    if (args.slice(k + 1).some((a) => a.expande) && !args.slice(k + 1).every((a) => !a.expande)) return REGRA_DINAMICO;
    return codigoArriscado(codigo) ? REGRA_INTERPRETADOR : null;
  }
  // `-` ou sem script: lê o código da entrada (cano, heredoc): não dá para conferir aqui.
  const temScript = textos.some((t) => !t.startsWith('-') && t !== '-');
  if (!temScript) return depoisDeCano || textos.includes('-') || nome === 'jshell' ? REGRA_DINAMICO : null;
  return null;
}

/** Leitores: curinga que alcança segredo, caminho montado na execução, busca/cópia/pacote recursivo inseguro. */
function avaliarLeitor(nome, args, contexto, trecho) {
  const operandos = args.filter((a) => !(a.texto.startsWith('-') && !a.expande && !a.curinga) || a.texto === '-');
  const ehPowerShell = /^(get-content|gc|select-string|sls|import-csv|ipcsv|get-item|gi|copy-item|cpi|move-item|mi|format-hex|fhx|compress-archive)$/.test(nome);
  const opcaoPs = (n) => args.findIndex((a) => a.texto.toLowerCase() === n);

  if (args.some((a) => a.curinga && curingaAlcancaSegredo(a.texto))) return REGRA_CURINGA;
  if (operandos.some((a) => a.expande)) return REGRA_DINAMICO;
  if (args.some((a) => /^-[^-]/.test(a.texto) && a.expande)) return REGRA_DINAMICO;
  if (trecho.seguidoDeParentese) return REGRA_DINAMICO;
  if (/@\$|@`/.test(args.map((a) => a.texto).join(' '))) return REGRA_DINAMICO;

  // Busca recursiva
  let recursiva = BUSCA_SEMPRE_RECURSIVA.has(nome);
  if (GREPS.has(nome)) {
    recursiva = args.some((a) => /^-[a-zA-Z]*[rR][a-zA-Z]*$/.test(a.texto) || /^--(recursive|dereference-recursive)$/.test(a.texto)
      || /^--directories=recurse$/.test(a.texto)) || /\s-d\s+recurse\b/.test(trecho.texto);
  }
  if (nome === 'findstr') recursiva = args.some((a) => /^\/[a-z]*s[a-z]*$/i.test(a.texto));
  if (recursiva) {
    if (nome === 'rg' && args.some((a) => a.texto === '--files')) return null; // só nomes
    // Alvos: operandos depois do padrão (o padrão vem por -e/-f ou é o primeiro operando).
    const padraoPorOpcao = args.some((a) => /^(-e|-f|--regexp|--file)(=|$)/.test(a.texto) || /^-[a-zA-Z]*e$/.test(a.texto));
    const alvos = nome === 'findstr' ? [] : padraoPorOpcao ? operandos : operandos.slice(1);
    if (!alvos.length) return REGRA_BUSCA; // sem alvo: começa na pasta atual
    if (alvos.some((a) => alvoInseguro(a, contexto))) return REGRA_BUSCA;
    return null;
  }

  // Pacote e cópia de pasta inteira
  const copiaRecursiva = COPIADORES.has(nome) && (nome === 'rsync' || nome === 'robocopy'
    || args.some((a) => /^-[a-zA-Z]*[rRa][a-zA-Z]*$/.test(a.texto) || /^--(recursive|archive)$/.test(a.texto)
      || /^(-recurse|\/[se])$/i.test(a.texto)));
  if (PACOTES.has(nome) || copiaRecursiva) {
    const alvos = ehPowerShell && opcaoPs('-path') >= 0 ? [args[opcaoPs('-path') + 1]].filter(Boolean) : operandos;
    if (alvos.some((a) => alvoInseguro(a, contexto))) return REGRA_BUSCA;
  }
  return null;
}

/** Regras de um único trecho; devolve a mensagem de bloqueio ou nulo. */
function avaliarTrecho(trecho, contexto, profundidade) {
  const ps = palavrasDetalhadas(trecho.texto);
  if (!ps.length) return null;
  const textos = ps.map((p) => p.texto);

  // 1) Arquivo de segredo citado, em qualquer posição e com qualquer variação de escrita.
  if (ps.some((p) => citaSegredo(p.texto)) || chavesAlcancamSegredo(trecho.texto)) return REGRA_SEGREDO;
  // 2) Ambiente do processo por /proc e por ps com "e" (mostra o ambiente de cada processo).
  if (textos.some((t) => /\/proc\/[^\s]*environ|\/proc\/(\*|self|\d+)\//i.test(t))) return REGRA_AMBIENTE;
  // 3) .NET de ambiente e de leitura de arquivo, no PowerShell.
  if (textos.some((t) => /\[(system\.)?environment\]::(getenvironmentvariable|expandenvironmentvariables)/i.test(t))) {
    return REGRA_AMBIENTE;
  }
  if (textos.some((t) => /\[(system\.)?io\.(file|streamreader|filestream|directory)\]::/i.test(t))) return REGRA_DINAMICO;

  // 4) Scripts dentro de outros comandos, em qualquer posição (ex.: docker exec ... sh -c '...').
  for (let i = 0; i < ps.length; i++) {
    const nome = programa(ps[i].texto);
    const seguinte = ps[i + 1];
    if (SHELLS.has(nome) && seguinte && /^-[a-z]*c[a-z]*$/i.test(seguinte.texto)) {
      const script = ps[i + 2];
      if (!script || script.expande) return REGRA_DINAMICO;
      const interno = avaliarTexto(script.texto, contexto, profundidade + 1);
      if (interno) return interno;
    }
    if (POWERSHELL.has(nome)) {
      if (ps.slice(i + 1).some((p) => /^-(e|ec|en|enc|encodedcommand|encodedarguments)$/i.test(p.texto))) return REGRA_DINAMICO;
      const k = ps.findIndex((p, j) => j > i && /^-(c|command|commandwithargs|cwa)$/i.test(p.texto));
      if (k > 0) {
        const resto = ps.slice(k + 1);
        if (!resto.length || (resto.length === 1 && resto[0].texto === '-')) return REGRA_DINAMICO;
        const interno = avaliarTexto(resto.map((p) => p.texto).join(' '), contexto, profundidade + 1, true);
        if (interno) return interno;
      }
    }
    if (nome === 'cmd') {
      const k = ps.findIndex((p, j) => j > i && /^\/[ck]$/i.test(p.texto));
      if (k > 0) {
        const resto = ps.slice(k + 1);
        if (resto.some((p) => p.expande)) return REGRA_DINAMICO;
        const interno = avaliarTexto(resto.map((p) => p.texto).join(' '), contexto, profundidade + 1);
        if (interno) return interno;
      }
    }
  }

  const inicio = inicioDoComando(ps);
  if (inicio >= ps.length) return null;
  const primeira = ps[inicio];
  const nome = programa(primeira.texto);
  const args = ps.slice(inicio + 1);
  const argsTexto = args.map((a) => a.texto);

  // 5) Variável ou expressão no lugar do programa: executa ou imprime um valor que não dá para conferir.
  const atribuicaoPs = /^\$[\w:{}]+$/.test(primeira.texto) && (argsTexto[0] === '=' || /^[+\-*/]?=/.test(argsTexto[0] ?? ''));
  if (atribuicaoPs) {
    // `$x = <expr>`: só a expressão à direita conta (lida como outro comando).
    const direita = args.slice(1).map((a) => a.texto).join(' ');
    if (/^\$env:/i.test(primeira.texto) && !args.slice(1).some((a) => a.expande)) return null; // grava variável
    return direita ? avaliarTexto(direita, contexto, profundidade + 1, contexto.powershell) : null;
  }
  if (primeira.expande && !/^\[/.test(primeira.texto)) {
    const nomes = variaveisLidas(primeira.texto);
    if (/\$\{?env:/i.test(primeira.texto) || nomes.some((n) => NOME_SENSIVEL.test(n))) return REGRA_AMBIENTE;
    return REGRA_DINAMICO;
  }

  // 6) Variável que parece segredo, qualquer `$env:` lida ou indireção `${!X}`, fora de um teste "vazia ou preenchida".
  if (ps.some((p) => p.expande && /\$\{!/.test(p.texto))) return REGRA_AMBIENTE;
  if (!TESTES.has(nome)) {
    for (const p of ps) {
      if (!p.expande) continue;
      if (/\$\{?env:/i.test(p.texto)) return REGRA_AMBIENTE;
      if (variaveisLidas(p.texto).some((n) => NOME_SENSIVEL.test(n))) return REGRA_AMBIENTE;
    }
  }

  if (nome === 'git') return avaliarGit(argsTexto);
  if (nome === 'gh') return REGRA_GH;
  if (['start-process', 'saps', 'start'].includes(nome) && argsTexto.some((a) => ['git', 'gh'].includes(programa(a)))) {
    return REGRA_GIT;
  }
  if (nome === 'docker' || nome === 'docker-compose' || nome === 'podman') {
    const docker = avaliarDocker(nome, argsTexto.length ? [primeira.texto, ...argsTexto] : [primeira.texto]);
    if (docker) return docker;
  }
  if (nome === 'kubectl' || nome === 'oc') {
    const k = argsTexto.indexOf('exec');
    if (k >= 0 && argsTexto.slice(k).some((a) => ['env', 'printenv', 'set'].includes(a.toLowerCase()))) return REGRA_AMBIENTE;
  }

  // 7) Despejo de ambiente.
  if (nome === 'printenv') return REGRA_AMBIENTE;
  if (nome === 'env' && argsTexto.every((a) => a.startsWith('-'))) return REGRA_AMBIENTE;
  // `set` sem argumento lista tudo; `set PREFIXO` (cmd) lista as que começam assim. Só opções (`set -e`) passam.
  if (nome === 'set') {
    if (argsTexto.length === 0 || argsTexto.every((a) => /^-[a-z]*[px][a-z]*$/i.test(a))) return REGRA_AMBIENTE;
    for (let k = 0; k < argsTexto.length; k++) {
      if (/^[-+][a-z]*o$/i.test(argsTexto[k])) {
        k++; // `-o pipefail`, `-euo pipefail`: o seguinte é nome de opção
      } else if (!/^[-+]/.test(argsTexto[k])) {
        return REGRA_AMBIENTE;
      }
    }
  }
  // `env`/`printenv` passados como argumento a outro programa que os executa (`npm run env`, `npx cross-env env`).
  if (!LEITORES.has(nome) && !SO_NOMES.has(nome) && !['echo', 'printf'].includes(nome)
      && argsTexto.some((a) => a === 'env' || a === 'printenv')) {
    return REGRA_AMBIENTE;
  }
  if (['export', 'declare', 'typeset', 'readonly', 'local'].includes(nome)
      && (argsTexto.length === 0 || argsTexto.some((a) => /^-[a-z]*p[a-z]*$/i.test(a))
        || argsTexto.every((a) => /^-[a-z]*[px][a-z]*$/i.test(a)))) {
    return REGRA_AMBIENTE;
  }
  if (nome === 'compgen' && argsTexto.some((a) => /^-[a-z]*[ev][a-z]*$/i.test(a) || /^(export|variable)$/i.test(a))) {
    return REGRA_AMBIENTE;
  }
  if (nome === 'ps' && argsTexto.some((a) => /^[a-z]*e[a-z]*$/i.test(a))) return REGRA_AMBIENTE;
  if (LISTAGEM_PS.has(nome) && argsTexto.some((a) => /^(env|variable):/i.test(a))) return REGRA_AMBIENTE;
  if (nome === 'get-variable' || nome === 'gv') return REGRA_AMBIENTE;

  // 8) Montagem dinâmica.
  if (nome === 'eval') {
    if (args.some((a) => a.expande)) return REGRA_DINAMICO;
    return avaliarTexto(argsTexto.join(' '), contexto, profundidade + 1);
  }
  if (nome === 'iex' || nome === 'invoke-expression') {
    const resto = args.filter((a) => !/^-command$/i.test(a.texto));
    if (!resto.length || resto.some((a) => a.expande) || trecho.depoisDeCano) return REGRA_DINAMICO;
    return avaliarTexto(resto.map((a) => a.texto).join(' '), contexto, profundidade + 1, true);
  }
  if (EXECUTORES.has(nome)) {
    if (args.some((a) => a.expande)) return REGRA_DINAMICO;
    const resto = args.filter((a) => !a.texto.startsWith('-')).map((a) => a.texto).join(' ');
    const interno = resto ? avaliarTexto(resto, contexto, profundidade + 1) : null;
    if (interno) return interno;
  }
  if ((nome === 'source' || nome === '.') && (!args.length || args.some((a) => a.texto.startsWith('<') || a.expande
      || /^\/dev\/(stdin|fd)/.test(a.texto)))) {
    return REGRA_DINAMICO;
  }
  if (SHELLS.has(nome) || POWERSHELL.has(nome) || nome === 'cmd') {
    const script = args.find((a) => !a.texto.startsWith('-') && !/^\/[a-z]$/i.test(a.texto));
    if (args.some((a) => a.texto === '-s' || a.texto === '-' || a.texto.startsWith('<'))) return REGRA_DINAMICO;
    if (!script && trecho.depoisDeCano) return REGRA_DINAMICO;
    if (script && script.expande) return REGRA_DINAMICO;
  }
  if (INTERPRETADORES.has(nome)) {
    const interpretador = avaliarInterpretador(nome, args, trecho.depoisDeCano);
    if (interpretador) return interpretador;
  }

  // 9) Leitores de conteúdo e curingas que alcançam o segredo.
  if (LEITORES.has(nome) || /^\[(system\.)?io\./i.test(primeira.texto)) {
    const leitor = avaliarLeitor(nome, args, contexto, trecho);
    if (leitor) return leitor;
  } else if (!SO_NOMES.has(nome) && args.some((a) => a.curinga && curingaAlcancaSegredo(a.texto))) {
    return REGRA_CURINGA;
  }
  return null;
}

/** Corpo de heredocs (`<<FIM`, `<<'FIM'`, `<<-FIM`) e here-strings do PowerShell (`@' '@`, `@" "@`). */
function separarDocumentosEmbutidos(texto) {
  const embutidos = [];
  const linhas = texto.split(/\r?\n/);
  const restantes = [];
  for (let i = 0; i < linhas.length; i++) {
    const linha = linhas[i];
    const heredoc = /<<(-?)\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\2/.exec(linha);
    if (heredoc && !/<<</.test(linha.slice(heredoc.index, heredoc.index + 3))) {
      const fim = heredoc[3];
      const corpo = [];
      let j = i + 1;
      while (j < linhas.length && (heredoc[1] ? linhas[j].trim() : linhas[j]) !== fim) corpo.push(linhas[j++]);
      embutidos.push({ linha, corpo: corpo.join('\n'), expande: !heredoc[2], tipo: 'heredoc' });
      restantes.push(linha);
      i = j;
      continue;
    }
    const aqui = /@(['"])\s*$/.exec(linha);
    if (aqui) {
      const aspas = aqui[1];
      const corpo = [];
      let j = i + 1;
      while (j < linhas.length && !linhas[j].startsWith(`${aspas}@`)) corpo.push(linhas[j++]);
      const fechamento = linhas[j] ?? '';
      embutidos.push({ linha: linha + fechamento.slice(2), corpo: corpo.join('\n'), expande: aspas === '"', tipo: 'herestring' });
      restantes.push(`${linha.slice(0, aqui.index)}'texto'${fechamento.slice(2)}`);
      i = j;
      continue;
    }
    restantes.push(linha);
  }
  return { texto: restantes.join('\n'), embutidos };
}

function avaliarEmbutido(doc, contexto, profundidade) {
  const linha = doc.linha;
  // O programa que recebe o documento é o do ÚLTIMO trecho antes do `<<` (ex.: `cd x && node - <<FIM` → node).
  const antes = doc.tipo === 'heredoc' ? linha.slice(0, linha.search(/<<-?\s*['"]?[A-Za-z_]/)) : linha;
  const pedacos = trechos(antes);
  const ps = palavrasDetalhadas(pedacos[pedacos.length - 1] ?? '');
  const nome = programa(ps[inicioDoComando(ps)]?.texto ?? '');
  // O valor expandido aparece na saída: variável que parece segredo ou $env: no corpo de um documento que expande.
  if (doc.expande) {
    if (/\$\{?env:/i.test(doc.corpo) || variaveisLidas(doc.corpo).some((n) => NOME_SENSIVEL.test(n))) return REGRA_AMBIENTE;
    for (const interno of substituicoes(doc.corpo)) {
      const r = avaliarTexto(interno, contexto, profundidade + 1);
      if (r) return r;
    }
  }
  if (doc.tipo === 'herestring' && /\|\s*(iex|invoke-expression|pwsh|powershell|cmd)\b/i.test(linha)) {
    return avaliarTexto(doc.corpo, contexto, profundidade + 1, true) ?? (doc.expande ? REGRA_DINAMICO : null);
  }
  if (SHELLS.has(nome) || POWERSHELL.has(nome) || nome === 'source' || nome === '.') {
    return avaliarTexto(doc.corpo, contexto, profundidade + 1, POWERSHELL.has(nome)) ?? (doc.expande ? REGRA_DINAMICO : null);
  }
  if (INTERPRETADORES.has(nome)) return codigoArriscado(doc.corpo) ? REGRA_INTERPRETADOR : null;
  return null;
}

/** Permite só a forma documentada de conferir variável sem o valor; troca por um marcador neutro antes da análise. */
function neutralizarConferencias(texto) {
  return texto.replace(
    /if\s*\(\s*(-not\s*)?\(?\s*\[string\]::IsNull(Or)?(Empty|WhiteSpace)\(\s*\$env:[A-Za-z_][A-Za-z0-9_]*\s*\)\s*\)?\s*\)\s*\{\s*'[^'$`]*'\s*\}\s*else\s*\{\s*'[^'$`]*'\s*\}/gi,
    "'conferencia'",
  );
}

/**
 * Avalia um comando inteiro (Bash ou PowerShell).
 * @param {object} [contexto] `cwd` (pasta onde o comando roda) e `raiz` (raiz do projeto)
 */
export function avaliarTexto(texto, contexto = {}, profundidade = 0, powershell = false) {
  if (typeof texto !== 'string' || !texto.trim()) return null;
  if (profundidade > 6) return REGRA_DINAMICO; // aninhamento demais: não dá para conferir
  const ctx = {
    cwd: contexto.cwd || process.env.CLAUDE_PROJECT_DIR || process.cwd(),
    raiz: contexto.raiz || process.env.CLAUDE_PROJECT_DIR || contexto.cwd || process.cwd(),
    powershell: powershell || contexto.powershell,
  };
  const { texto: semEmbutidos, embutidos } = separarDocumentosEmbutidos(neutralizarConferencias(texto));
  // Antes de dividir em trechos (que separa nas chaves): expansão de chaves, `${env:X}` lida e xargs/parallel com
  // leitor ou shell.
  if (chavesAlcancamSegredo(semEmbutidos)) return REGRA_SEGREDO;
  if (/\$\{!/.test(semEmbutidos)) return REGRA_AMBIENTE; // indireção do bash: lê qualquer variável pelo nome
  for (const m of semEmbutidos.matchAll(/\$\{env:[^}]*\}(\s*=(?!=))?/gi)) {
    if (!m[1]) return REGRA_AMBIENTE; // leitura (atribuição `${env:X} = ...` passa)
  }
  for (const m of semEmbutidos.matchAll(/(?:^|[\s|;&(])(?:xargs|parallel)\b([^|;&\n]*)/gi)) {
    for (const p of palavrasDetalhadas(m[1].replace(/[{}]/g, ' '))) {
      const alvo = programa(p.texto);
      if (LEITORES.has(alvo) || SHELLS.has(alvo) || POWERSHELL.has(alvo) || INTERPRETADORES.has(alvo)) return REGRA_DINAMICO;
    }
  }
  for (const doc of embutidos) {
    const r = avaliarEmbutido(doc, ctx, profundidade);
    if (r) return r;
  }
  // Here-string do bash (`<<< "..."`) para interpretador ou shell: o texto é código.
  for (const m of semEmbutidos.matchAll(/([^\s|;&]+)[^|;&\n]*<<<\s*("[^"]*"|'[^']*'|\S+)/g)) {
    const nome = programa(m[1]);
    const corpo = m[2].replace(/^['"]|['"]$/g, '');
    if (SHELLS.has(nome) || POWERSHELL.has(nome)) {
      if (m[2].startsWith('"') && /\$/.test(corpo)) return REGRA_DINAMICO;
      const r = avaliarTexto(corpo, ctx, profundidade + 1);
      if (r) return r;
    }
    if (INTERPRETADORES.has(nome) && codigoArriscado(corpo)) return REGRA_INTERPRETADOR;
  }
  for (const trecho of trechosDetalhados(semEmbutidos)) {
    const bloqueio = avaliarTrecho(trecho, ctx, profundidade);
    if (bloqueio) return bloqueio;
  }
  // Cano a partir de listagem recursiva para leitor (PowerShell e find): `gci -Recurse | sls`, `find . | xargs`.
  const partes = trechosDetalhados(semEmbutidos).filter((t) => !t.substituicao);
  for (let i = 0; i + 1 < partes.length; i++) {
    if (!partes[i + 1].depoisDeCano) continue;
    const esquerda = palavrasDetalhadas(partes[i].texto);
    const direita = palavrasDetalhadas(partes[i + 1].texto);
    const nomeE = programa(esquerda[inicioDoComando(esquerda)]?.texto ?? '');
    const nomeD = programa(direita[inicioDoComando(direita)]?.texto ?? '');
    const recursiva = esquerda.some((p) => /^-(recurse|r|rec|depth)$/i.test(p.texto)) || nomeE === 'find';
    if (['get-childitem', 'gci', 'dir', 'ls', 'find'].includes(nomeE) && recursiva
        && (LEITORES.has(nomeD) || ['foreach-object', '%', 'xargs'].includes(nomeD))) {
      const alvos = esquerda.slice(inicioDoComando(esquerda) + 1)
        .filter((p) => !p.texto.startsWith('-') || p.expande);
      const k = esquerda.findIndex((p) => /^-(path|literalpath)$/i.test(p.texto));
      const escolhidos = k >= 0 ? [esquerda[k + 1]].filter(Boolean) : nomeE === 'find' ? alvos.slice(0, 1) : alvos.filter((p) => !/^\d+$/.test(p.texto));
      if (!escolhidos.length || escolhidos.some((p) => alvoInseguro(p, ctx))) return REGRA_BUSCA;
    }
  }
  // PowerShell: leitor sem caminho abre os ARQUIVOS que chegam pelo cano (`gci | gc`, `ls -Force | ? .. | gc`). A
  // origem (antes dos filtros) precisa ser uma listagem com pasta explícita e segura; texto de outro programa
  // (`npm test | Select-String FAIL`) passa.
  for (let i = 1; i < partes.length; i++) {
    if (!partes[i].depoisDeCano) continue;
    const direita = palavrasDetalhadas(partes[i].texto);
    const nomeD = programa(direita[inicioDoComando(direita)]?.texto ?? '');
    if (!LEITORES_DO_CANO_PS.has(nomeD) || (!ctx.powershell && ['cat', 'type'].includes(nomeD))) continue;
    const argsD = direita.slice(inicioDoComando(direita) + 1);
    const temCaminho = argsD.some((a, k) => /^-(path|literalpath)$/i.test(a.texto) && argsD[k + 1])
      || argsD.filter((a) => !a.texto.startsWith('-')).length > (['select-string', 'sls'].includes(nomeD) ? 1 : 0);
    if (temCaminho) continue;
    let j = i - 1;
    let origem = null;
    while (j >= 0) {
      const ps = palavrasDetalhadas(partes[j].texto);
      const nomeE = programa(ps[inicioDoComando(ps)]?.texto ?? '');
      if (FILTROS_PS.has(nomeE) && partes[j].depoisDeCano) {
        j--;
        continue;
      }
      origem = { nome: nomeE, ps };
      break;
    }
    if (!origem || !LISTAGENS_PS.has(origem.nome)) continue;
    const argsE = origem.ps.slice(inicioDoComando(origem.ps) + 1);
    const k = argsE.findIndex((a) => /^-(path|literalpath)$/i.test(a.texto));
    const alvos = k >= 0 ? [argsE[k + 1]].filter(Boolean) : argsE.filter((a) => !a.texto.startsWith('-'));
    if (!alvos.length || alvos.some((a) => alvoInseguro(a, ctx))) return REGRA_DINAMICO;
  }
  // find com -exec de leitor percorre a pasta inteira.
  for (const trecho of partes) {
    const ps = palavrasDetalhadas(trecho.texto);
    const i = inicioDoComando(ps);
    if (programa(ps[i]?.texto ?? '') !== 'find') continue;
    const k = ps.findIndex((p) => /^-(exec|execdir|ok|okdir)$/.test(p.texto));
    if (k < 0) continue;
    const executado = programa(ps[k + 1]?.texto ?? '');
    if (LEITORES.has(executado) || SHELLS.has(executado) || INTERPRETADORES.has(executado)) {
      const alvos = [];
      for (let j = i + 1; j < ps.length && !/^[-(!]/.test(ps[j].texto); j++) alvos.push(ps[j]);
      if (!alvos.length || alvos.some((p) => alvoInseguro(p, ctx))) return REGRA_BUSCA;
    }
  }
  return null;
}

/** Entrada do hook: só olha `tool_input.command` das ferramentas de shell. */
export function avaliar(entrada) {
  const ferramenta = entrada.tool_name;
  if (ferramenta !== 'Bash' && ferramenta !== 'PowerShell') return null;
  const comando = entrada.tool_input && entrada.tool_input.command;
  if (typeof comando !== 'string') throw new TypeError('tool_input.command ausente');
  const cwd = typeof entrada.cwd === 'string' && entrada.cwd ? entrada.cwd : undefined;
  return avaliarTexto(comando, { cwd }, 0, ferramenta === 'PowerShell');
}

principal(import.meta.url, 'guarda-bash', avaliar);
