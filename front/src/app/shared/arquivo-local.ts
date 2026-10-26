/** Entrega de um Blob ao usuário sem colocar nada (e muito menos o token) numa URL. */

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
