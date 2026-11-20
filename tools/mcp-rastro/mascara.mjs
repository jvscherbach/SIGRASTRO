// Mascaramento da saída do servidor MCP (S6, bloco 7). Mesmo que a API devolva por engano, nada disto sai:
// CPF e CNPJ (com e sem máscara, válidos ou não), e-mail, token (JWT, Bearer), cookie e chave de arquivo.
// O valor vira `***` e a saída diz quantos itens foram mascarados.

export const MASCARA = '***';

/** Chaves de JSON cujo valor nunca sai, em qualquer nível (chave do arquivo, token, cookie, senha, documento). */
const CHAVE_SENSIVEL = /^(chave|chavedoarquivo|chave_arquivo|key|token|access_?token|refresh_?token|cookie|set-cookie|senha|password|secret|segredo|authorization|cpf|cnpj|cpfcnpj|cpf_cnpj|documento)$/i;

const PADROES = [
  // JWT (três partes base64url começando por eyJ)
  /eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{2,}/g,
  // Authorization: Bearer <valor>
  /\bBearer\s+[A-Za-z0-9._~+/=-]{8,}/gi,
  // Cookie: a=b; c=d  /  Set-Cookie
  /\b(?:Set-)?Cookie:\s*[^\r\n]+/gi,
  // e-mail
  /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g,
  // CNPJ com máscara (antes do CPF, que é mais curto)
  /\b\d{2}\.\d{3}\.\d{3}\/\d{4}-\d{2}\b/g,
  // CPF com máscara
  /\b\d{3}\.\d{3}\.\d{3}-\d{2}\b/g,
  // CPF ou CNPJ só com dígitos (11 ou 14 seguidos)
  /(?<!\d)(?:\d{14}|\d{11})(?!\d)/g,
  // token=, chave=, senha= em texto
  /\b(?:token|chave|senha|password|cookie)\s*[=:]\s*[^\s,;&"']+/gi,
];

/** Substitui no texto e devolve quantas ocorrências foram mascaradas. */
export function mascararTexto(texto) {
  let resultado = String(texto ?? '');
  let ocorrencias = 0;
  for (const padrao of PADROES) {
    resultado = resultado.replace(padrao, () => {
      ocorrencias++;
      return MASCARA;
    });
  }
  return { texto: resultado, ocorrencias };
}

/** Cópia do JSON com os valores de chaves sensíveis trocados por `***` (contados). */
export function limparObjeto(valor, contador = { ocorrencias: 0 }) {
  if (Array.isArray(valor)) {
    return valor.map((item) => limparObjeto(item, contador));
  }
  if (valor && typeof valor === 'object') {
    const limpo = {};
    for (const [chave, item] of Object.entries(valor)) {
      if (CHAVE_SENSIVEL.test(chave) && item !== null && item !== undefined && item !== '') {
        contador.ocorrencias++;
        limpo[chave] = MASCARA;
      } else {
        limpo[chave] = limparObjeto(item, contador);
      }
    }
    return limpo;
  }
  return valor;
}

/** Texto final da ferramenta: mascarado, com a contagem no fim (somada à da limpeza do JSON). */
export function saidaMascarada(texto, jaMascarados = 0) {
  const { texto: final, ocorrencias } = mascararTexto(texto);
  const total = ocorrencias + jaMascarados;
  return `${final}\n\nMascarados: ${total}`;
}
