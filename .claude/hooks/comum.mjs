// Base comum das guardas do Rastro (S6, bloco 6). Node ESM, sem dependências.
//
// Contrato com o Claude Code (hooks "command"): a entrada chega em JSON pela entrada padrão; sair com código 2 bloqueia
// a ação e devolve o stderr ao Claude; qualquer outro código diferente de zero é um erro NÃO bloqueante (a ação segue).
//
// Falha segura: erro interno da própria guarda (JSON inválido, exceção) nunca trava a sessão. Escreve um aviso curto
// em stderr e sai com código 1 (não bloqueante). Só o que casa com uma regra bloqueia (código 2).

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

/**
 * `.env`, `.env.local` e `.env.<qualquer coisa>` que não termine em `.example`, e `mapa-local.csv`. Recebe um caminho
 * ou um trecho de comando (com aspas, `=`, `<`, separadores de Windows ou Unix).
 */
export function caminhoDeSegredo(valor) {
  if (typeof valor !== 'string' || !valor) {
    return false;
  }
  const limpo = valor.replace(/^['"`]+|['"`;,)]+$/g, '').trim();
  const ultimo = limpo.split(/[\\/=:<>]/).pop() ?? '';
  const nome = ultimo.toLowerCase();
  if (nome === 'mapa-local.csv') {
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
