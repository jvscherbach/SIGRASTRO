/** Entrega do arquivo ao usuário: por Blob (o token de sessão nunca vai para uma URL) ou direto, com ticket (D49). */

/** Nome do arquivo no Content-Disposition (RFC 6266/5987); vazio se o cabeçalho não traz um. */
export function nomeDoContentDisposition(cabecalho: string | null | undefined): string | null {
  if (!cabecalho) {
    return null;
  }
  const estendido = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/i.exec(cabecalho);
  if (estendido) {
    try {
      return decodeURIComponent(estendido[1].trim());
    } catch {
      // cai para o filename simples
    }
  }
  const simples = /filename\s*=\s*"([^"]+)"/i.exec(cabecalho) ?? /filename\s*=\s*([^;]+)/i.exec(cabecalho);
  return simples ? simples[1].trim() : null;
}

/**
 * Confere o PDF recebido antes de entregá-lo ao navegador (S6, bloco 3): não vazio, do tamanho anunciado pelo
 * servidor (Content-Length, quando veio) e começando por `%PDF-`. Um corpo truncado nunca vira "download concluído".
 */
export async function pdfRecebidoInteiro(blob: Blob | null | undefined, tamanhoAnunciado: string | null): Promise<boolean> {
  if (!blob || blob.size === 0) {
    return false;
  }
  const esperado = tamanhoAnunciado === null ? NaN : Number(tamanhoAnunciado);
  if (Number.isFinite(esperado) && esperado !== blob.size) {
    return false;
  }
  try {
    const inicio = new Uint8Array(await lerBytes(blob.slice(0, 5)));
    return String.fromCharCode(...inicio) === '%PDF-';
  } catch {
    return false;
  }
}

function lerBytes(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') {
    return blob.arrayBuffer();
  }
  return new Promise((resolver, rejeitar) => {
    const leitor = new FileReader();
    leitor.onload = () => resolver(leitor.result as ArrayBuffer);
    leitor.onerror = () => rejeitar(leitor.error);
    leitor.readAsArrayBuffer(blob);
  });
}

/** Baixa o Blob com o nome dado e revoga a URL de objeto logo depois. */
export function baixarBlob(blob: Blob, nome: string): void {
  const url = URL.createObjectURL(blob);
  const ancora = document.createElement('a');
  ancora.href = url;
  ancora.download = nome;
  ancora.rel = 'noopener';
  document.body.appendChild(ancora);
  ancora.click();
  ancora.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/**
 * Download direto pelo navegador, em streaming (dossiê grande, D49): a URL leva só um ticket de uso único e curta
 * validade, nunca o token de sessão. Sem o atributo download: a resposta é attachment (a página não navega) e o nome
 * vem do Content-Disposition.
 */
export function baixarDireto(url: string): void {
  const ancora = document.createElement('a');
  ancora.href = url;
  ancora.rel = 'noopener noreferrer';
  document.body.appendChild(ancora);
  ancora.click();
  ancora.remove();
}
