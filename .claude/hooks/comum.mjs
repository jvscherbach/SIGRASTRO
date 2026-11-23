// Base comum das guardas do Rastro (S6, bloco 6). Node ESM, sem dependências.
//
// Contrato com o Claude Code (hooks "command"): a entrada chega em JSON pela entrada padrão; sair com código 2 bloqueia
// a ação e devolve o stderr ao Claude; qualquer outro código diferente de zero é um erro NÃO bloqueante (a ação segue).
//
// Falha segura: erro interno da própria guarda (JSON inválido, exceção) nunca trava a sessão. Escreve um aviso curto
// em stderr e sai com código 1 (não bloqueante). Só o que casa com uma regra bloqueia (código 2).

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

export const BLOQUEAR = 2;
export const ERRO_NAO_BLOQUEANTE = 1;

/** Lê toda a entrada padrão como texto. */
export async function lerEntrada() {
  const partes = [];
  for await (const parte of process.stdin) {
    partes.push(parte);
  }
  return Buffer.concat(partes).toString('utf8');
}

/**
 * Executa uma guarda: `avaliar(entrada)` recebe o JSON do hook e devolve `null` (deixa passar) ou o texto do bloqueio.
 * Pode ser assíncrona. Qualquer exceção vira aviso não bloqueante.
 */
export async function executarGuarda(nome, avaliar) {
  let entrada;
  try {
    const texto = await lerEntrada();
    entrada = JSON.parse(texto);
    if (entrada === null || typeof entrada !== 'object' || Array.isArray(entrada)) {
      throw new Error('entrada não é um objeto JSON');
    }
  } catch (erro) {
    avisar(nome, `entrada inválida (${resumo(erro)})`);
    return ERRO_NAO_BLOQUEANTE;
  }
  try {
    const bloqueio = await avaliar(entrada);
    if (bloqueio) {
      process.stderr.write(`${bloqueio}\n`);
      return BLOQUEAR;
    }
    return 0;
  } catch (erro) {
    avisar(nome, `erro interno (${resumo(erro)})`);
    return ERRO_NAO_BLOQUEANTE;
  }
}

function avisar(nome, motivo) {
  process.stderr.write(`[${nome}] aviso: ${motivo}; a ação segue sem esta guarda.\n`);
}

/** Só o tipo e a primeira linha: nunca o conteúdo da entrada (pode ter dado sensível). */
function resumo(erro) {
  const nome = erro && erro.name ? erro.name : 'Erro';
  return nome;
}

/** Verdadeiro quando o módulo foi chamado diretamente (`node arquivo.mjs`), e não importado pelos testes. */
export function chamadoDiretamente(urlDoModulo) {
  return Boolean(process.argv[1]) && urlDoModulo === pathToFileURL(process.argv[1]).href;
}

/** Executa a guarda e encerra com o código certo. */
export function principal(urlDoModulo, nome, avaliar) {
  if (!chamadoDiretamente(urlDoModulo)) {
    return;
  }
  executarGuarda(nome, avaliar).then(
    (codigo) => process.exit(codigo),
    () => process.exit(ERRO_NAO_BLOQUEANTE),
  );
}

// --- arquivos de segredo (usado pelas duas guardas de leitura)

/** Nomes de arquivo de credencial conhecidos (S6.1): nunca são lidos, nem fora do projeto. */
export const ARQUIVOS_DE_CREDENCIAL = new Set([
  '.git-credentials', '.netrc', '_netrc', '.pgpass', '.npmrc', '.pypirc', '.credentials.json', 'id_rsa', 'id_dsa',
  'id_ecdsa', 'id_ed25519',
]);

/**
 * `.env`, `.env.local` e `.env.<qualquer coisa>` que não termine em `.example`, `mapa-local.csv` e os arquivos de token
 * montados (`.secrets/` do projeto e `/run/secrets*` do container, compose.local.yaml). Recebe um caminho ou um trecho
 * de comando (com aspas, `=`, `<`, `@` de envio, separadores de Windows ou Unix).
 */
export function caminhoDeSegredo(valor) {
  if (typeof valor !== 'string' || !valor) {
    return false;
  }
  const limpo = valor.replace(/^['"`]+|['"`;,)]+$/g, '').trim();
  const barras = limpo.replace(/\\/g, '/').toLowerCase();
  if (/(^|[/=:@<>])\.secrets(\/|$)/.test(barras) || /(^|[=:@<>/])\/?run\/secrets[^/]*(\/|$)/.test(barras)) {
    return true;
  }
  // Pasta dos segredos fora do projeto (S6.1, bloco 2; docs/claude-code/segredos.md): ~/.rastro/ e qualquer `.rastro/`.
  if (/(^|[/=:@<>~])\.rastro(\/|$)/.test(barras)) {
    return true;
  }
  // Credenciais do usuário fora do projeto (S6.1): chaves SSH, nuvem, Docker, Kubernetes, GPG e tokens de ferramentas.
  if (/(^|[/=:@<>~])\.(ssh|aws|gnupg|kube)(\/|$)/.test(barras) || /(^|\/)\.docker\/config\.json$/.test(barras)) {
    return true;
  }
  const ultimo = limpo.split(/[\\/=:<>@]/).pop() ?? '';
  const nome = ultimo.toLowerCase();
  if (nome === 'mapa-local.csv' || ARQUIVOS_DE_CREDENCIAL.has(nome)) {
    return true;
  }
  if (!nome.startsWith('.env')) {
    return false;
  }
  if (nome === '.env') {
    return true;
  }
  // Curingas (.env*, .env.*) alcançariam o .env.local.
  if (/^\.env[*?[]/.test(nome) || /^\.env\.[*?[]/.test(nome)) {
    return true;
  }
  if (!nome.startsWith('.env.')) {
    return false; // ".envrc", ".environment" etc. não são o arquivo de segredos do projeto
  }
  return !nome.endsWith('.example');
}

// --- pastas do Claude Code protegidas contra escrita (A1, bloco 2)

/**
 * Marcador da liberação de construção: o RESPONSÁVEL cria este arquivo à mão (vazio) para uma sessão principal poder
 * alterar as guardas, os scripts, os agentes, os comandos e as configurações do Claude Code, e o apaga ao terminar.
 * Fica em `~/.rastro/`, pasta que as guardas e o `permissions.deny` já proíbem o Claude de ler, criar ou listar.
 */
export const MARCADOR_DE_LIBERACAO = path.join(os.homedir(), '.rastro', 'liberar-construcao-claude');

/**
 * A escrita em `.claude/` protegido está liberada? Só na sessão principal (a entrada do hook de um subagente traz
 * `agent_id`/`agent_type`, e subagente NUNCA é liberado) e só com o marcador presente.
 */
export function construcaoLiberada(entrada, marcador = MARCADOR_DE_LIBERACAO) {
  if (entrada && (entrada.agent_id || entrada.agent_type)) return false;
  try {
    return fs.statSync(marcador).isFile();
  } catch {
    return false;
  }
}

/** `.claude` (a pasta), `.claude/{scripts,hooks,agents,commands}/` e `.claude/settings{,.local}.json`, em qualquer lugar. */
const CLAUDE_PROTEGIDO =
  /(^|[/=:@<>])\.claude(\/+(scripts|hooks|agents|commands)(\/|$)|\/+settings(\.local)?\.json$|\/*$)/;
/** Nome curto 8.3 do Windows (`CLAUDE~1`): na dúvida, protegido. */
const NOME_CURTO = /(^|[/=:@<>])claude~\d/;

function normalizarCaminho(valor) {
  const limpo = valor.replace(/^['"`]+|['"`;,)]+$/g, '').trim().replace(/\\/g, '/').toLowerCase();
  return limpo ? path.posix.normalize(limpo) : limpo;
}

/** O caminho (ou um trecho de comando como `x=caminho`, `>caminho`) é de uma pasta ou arquivo protegido do `.claude/`. */
export function caminhoClaudeProtegido(valor) {
  if (typeof valor !== 'string' || !valor) return false;
  const bruto = valor.replace(/\\/g, '/').toLowerCase();
  const normalizado = normalizarCaminho(valor);
  return [bruto, normalizado].some((t) => CLAUDE_PROTEGIDO.test(t) || NOME_CURTO.test(t));
}

/** Amostras do que está protegido: um curinga que alcance uma delas é tratado como protegido. */
const AMOSTRAS_PROTEGIDAS = [
  '.claude', '.claude/hooks', '.claude/hooks/guarda-bash.mjs', '.claude/scripts/commit-bloco.mjs',
  '.claude/agents/commitador.md', '.claude/commands/orquestrar.md', '.claude/settings.json',
  '.claude/settings.local.json',
];
export const AMOSTRA_DO_SCRIPT = '.claude/scripts/commit-bloco.mjs';

function regexDoCuringa(curinga) {
  let fonte = '';
  for (let i = 0; i < curinga.length; i++) {
    const c = curinga[i];
    // `**` atravessa pastas; `*` não, mas alcança nomes com ponto (na dúvida, como o `dotglob`).
    if (c === '*' && curinga[i + 1] === '*') {
      fonte += '.*';
      i++;
    } else if (c === '*') fonte += '[^/]*';
    else if (c === '?') fonte += '[^/]';
    else if (c === '[') {
      const fim = curinga.indexOf(']', i + 1);
      if (fim < 0) return null;
      fonte += `[${curinga.slice(i + 1, fim).replace(/^!/, '^').replace(/\\/g, '\\\\')}]`;
      i = fim;
    } else fonte += c.replace(/[.+^${}()|\\]/g, '\\$&');
  }
  try {
    return new RegExp(`^${fonte}$`, 'i');
  } catch {
    return null;
  }
}

/** Curinga (`*`, `?`, `[..]`) que pode alcançar uma amostra protegida (ou a dada), também a partir de qualquer pasta. */
export function curingaAlcancaClaude(valor, amostras = AMOSTRAS_PROTEGIDAS) {
  if (typeof valor !== 'string' || !/[*?[]/.test(valor)) return false;
  const partes = normalizarCaminho(valor).split('/');
  for (let k = 0; k < partes.length; k++) {
    const regex = regexDoCuringa(partes.slice(k).join('/'));
    if (!regex) return true; // não dá para conferir
    if (amostras.some((a) => regex.test(a))) return true;
  }
  return false;
}
