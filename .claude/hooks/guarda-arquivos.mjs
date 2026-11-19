// Guarda 2 (PreToolUse; Read, Edit, Write, MultiEdit, NotebookEdit, Grep, Glob): nenhuma ferramenta de arquivo abre,
// altera, procura ou lista `.env`, `.env.local`, `.env.*` (menos `*.example`) nem `mapa-local.csv`.
// `.env.local.example` e `.env.example` passam. Erro interno não trava a sessão (comum.mjs).

import { caminhoDeSegredo, principal } from './comum.mjs';

const FERRAMENTAS = new Set(['Read', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit', 'Grep', 'Glob']);
const CAMPOS = ['file_path', 'notebook_path', 'path', 'pattern', 'glob'];

export const REGRA =
  'Bloqueado pela guarda do projeto (regra: nunca abrir, alterar ou procurar em `.env`, `.env.local`, `.env.*` nem ' +
  '`mapa-local.csv`). Os nomes das variáveis estão no `.env.local.example`; para saber se uma variável está ' +
  'preenchida, confira dentro do container sem imprimir o valor: [ -z "$VAR" ] && echo vazia || echo preenchida';

/** No Grep, `pattern` é uma expressão de busca, não um caminho: só `path` e `glob` contam. */
function camposDaFerramenta(ferramenta) {
  return ferramenta === 'Grep' ? ['path', 'glob'] : CAMPOS;
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
  for (const campo of camposDaFerramenta(ferramenta)) {
    const valor = dados[campo];
    if (typeof valor !== 'string') {
      continue;
    }
    // Um glob pode ter alternativas: "{.env.local,README.md}".
    const partes = valor.split(/[{},]/);
    if (partes.some((parte) => caminhoDeSegredo(parte.trim()))) {
      return REGRA;
    }
  }
  return null;
}

principal(import.meta.url, 'guarda-arquivos', avaliar);
