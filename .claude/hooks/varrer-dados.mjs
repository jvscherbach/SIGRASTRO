// Guarda 3 (PostToolUse; Write, Edit, MultiEdit): depois de gravar um arquivo de texto, procura dado pessoal ou
// segredo real: CPF e CNPJ com dígito verificador válido (com e sem máscara), token (JWT `eyJ...`, `Bearer <longo>`) e
// chave privada. Achado: código 2, com a lista de arquivo e linha e o tipo, SEM repetir o valor.
//
// A gravação já aconteceu (PostToolUse não desfaz): o código 2 devolve o aviso ao Claude, que deve trocar o valor.
// Ignora arquivos acima de 2 MB, binários, `node_modules`, `dist`, `target`, `.git` e a própria `.claude/hooks/`.
// Erro interno não trava a sessão (comum.mjs).

import { readFile, stat } from 'node:fs/promises';
import path from 'node:path';

import { principal } from './comum.mjs';

const FERRAMENTAS = new Set(['Write', 'Edit', 'MultiEdit']);
const LIMITE_BYTES = 2 * 1024 * 1024;
const PASTAS_IGNORADAS = ['node_modules', 'dist', 'target', '.git', '.angular'];
const EXTENSOES_BINARIAS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.pdf', '.zip', '.gz', '.jar', '.class', '.xlsx', '.xls',
  '.docx', '.doc', '.odt', '.ods', '.woff', '.woff2', '.ttf', '.otf', '.kmz', '.shp', '.dbf', '.bin', '.exe',
]);

/**
 * Valores fictícios documentados (docs/claude-code/hooks.md), só dígitos. O CNPJ de exemplo do projeto e três números
 * didáticos clássicos, válidos por cálculo, que os testes de validação e máscara já usam. CPF inválido de propósito
 * (ex.: 000.000.000-01) nem chega aqui: não passa no dígito verificador.
 */
export const FICTICIOS = new Set(['12345678000190', '11144477735', '11222333000181', '12345678909']);

const CPF_COM_MASCARA = /(?<![\d.])\d{3}\.\d{3}\.\d{3}-\d{2}(?![\d-])/g;
const CNPJ_COM_MASCARA = /(?<![\d.])\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}(?![\d-])/g;
/** 11 ou 14 dígitos seguidos, também dentro de nome de arquivo (`contrato-<digitos>.pdf`), mas não no meio de um hash. */
const SO_DIGITOS = /(?<![\dA-Za-z])\d{11}(?:\d{3})?(?![\dA-Za-z])/g;
const JWT = /eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}/;
/** Valor com cara de token aleatório: 24+ caracteres, com letra e dígito (não "Bearer token-que-nao-existe"). */
const BEARER = /\bBearer\s+(?=[A-Za-z0-9._~+/-]*\d)(?=[A-Za-z0-9._~+/-]*[A-Za-z])[A-Za-z0-9._~+/-]{24,}=*/;
const CHAVE_PRIVADA = /-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----/;

export function cpfValido(valor) {
  const d = valor.replace(/\D/g, '');
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) {
    return false;
  }
  const digito = (n) => {
    let soma = 0;
    for (let i = 0; i < n; i++) soma += Number(d[i]) * (n + 1 - i);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  return digito(9) === Number(d[9]) && digito(10) === Number(d[10]);
}

export function cnpjValido(valor) {
  const d = valor.replace(/\D/g, '');
  if (d.length !== 14 || /^(\d)\1{13}$/.test(d)) {
    return false;
  }
  const digito = (n) => {
    let soma = 0;
    let peso = n - 7;
    for (let i = 0; i < n; i++) {
      soma += Number(d[i]) * peso--;
      if (peso < 2) peso = 9;
    }
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  return digito(12) === Number(d[12]) && digito(13) === Number(d[13]);
}

function documentoReal(valor) {
  const digitos = valor.replace(/\D/g, '');
  if (FICTICIOS.has(digitos)) {
    return null;
  }
  if (digitos.length === 11 && cpfValido(digitos)) return 'CPF válido';
  if (digitos.length === 14 && cnpjValido(digitos)) return 'CNPJ válido';
  return null;
}

/** Achados de um texto: `[{linha, tipo}]`, sem o valor. */
export function varrerTexto(texto) {
  const achados = [];
  const linhas = texto.split(/\r?\n/);
  linhas.forEach((conteudo, indice) => {
    const tipos = new Set();
    for (const expressao of [CPF_COM_MASCARA, CNPJ_COM_MASCARA, SO_DIGITOS]) {
      for (const encontrado of conteudo.matchAll(expressao)) {
        const tipo = documentoReal(encontrado[0]);
        if (tipo) tipos.add(tipo);
      }
    }
    if (JWT.test(conteudo)) tipos.add('token JWT');
    if (BEARER.test(conteudo)) tipos.add('token Bearer');
    if (CHAVE_PRIVADA.test(conteudo)) tipos.add('chave privada');
    for (const tipo of tipos) {
      achados.push({ linha: indice + 1, tipo });
    }
  });
  return achados;
}

export function ignorado(caminho) {
  const partes = path.normalize(caminho).split(/[\\/]+/);
  if (partes.some((p) => PASTAS_IGNORADAS.includes(p))) {
    return true;
  }
  for (let i = 0; i < partes.length - 1; i++) {
    if (partes[i] === '.claude' && partes[i + 1] === 'hooks') {
      return true;
    }
  }
  return EXTENSOES_BINARIAS.has(path.extname(caminho).toLowerCase());
}

export function mensagem(arquivo, achados) {
  const lista = achados.slice(0, 20).map((a) => `  - ${arquivo}:${a.linha} (${a.tipo})`).join('\n');
  const mais = achados.length > 20 ? `\n  - e mais ${achados.length - 20} ocorrência(s)` : '';
  return (
    'Possível dado pessoal ou segredo real gravado (regra do projeto: nenhum CPF, CNPJ, nome, token ou chave real ' +
    'em código, teste, doc ou log). O valor não é repetido aqui:\n' + lista + mais + '\n' +
    'Troque por <CPF_DE_TESTE>/<CNPJ_DE_TESTE> ou por dado evidentemente fictício (CPF inválido de propósito, como ' +
    '000.000.000-01; CNPJ 12.345.678/0001-90). Em teste, gere o valor por cálculo na hora, nunca como literal; ' +
    'token, gere no próprio teste.'
  );
}

export async function avaliar(entrada) {
  if (!FERRAMENTAS.has(entrada.tool_name)) {
    return null;
  }
  const informado = entrada.tool_input && entrada.tool_input.file_path;
  if (typeof informado !== 'string' || !informado) {
    throw new TypeError('tool_input.file_path ausente');
  }
  const base = typeof entrada.cwd === 'string' && entrada.cwd ? entrada.cwd : process.cwd();
  const caminho = path.resolve(base, informado);
  if (ignorado(caminho)) {
    return null;
  }
  const info = await stat(caminho);
  if (!info.isFile() || info.size > LIMITE_BYTES) {
    return null;
  }
  const bytes = await readFile(caminho);
  if (bytes.subarray(0, 8192).includes(0)) {
    return null; // binário
  }
  const achados = varrerTexto(bytes.toString('utf8'));
  if (!achados.length) {
    return null;
  }
  const exibido = path.relative(process.env.CLAUDE_PROJECT_DIR || base, caminho) || path.basename(caminho);
  return mensagem(exibido.split(path.sep).join('/'), achados);
}

principal(import.meta.url, 'varrer-dados', avaliar);
