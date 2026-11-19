/**
 * Chaves do armazenamento do navegador (R1, bloco 6; D75). O prefixo passou de `vistas.` para `rastro.`; a chave
 * antiga é lida como reserva por esta versão, para ninguém ser deslogado nem perder os recentes na troca.
 */
export const PREFIXO = 'rastro.';
export const PREFIXO_ANTIGO = 'vistas.';

/**
 * Lê `PREFIXO + nome`. Sem ela, usa `PREFIXO_ANTIGO + nome`, copia o valor para a chave nova e apaga a antiga.
 * Com as duas, vale a nova e a antiga é apagada. Pode lançar (armazenamento bloqueado): quem chama trata.
 */
export function lerComMigracao(armazenamento: Storage, nome: string): string | null {
  const chave = PREFIXO + nome;
  const chaveAntiga = PREFIXO_ANTIGO + nome;
  const atual = armazenamento.getItem(chave);
  const antigo = armazenamento.getItem(chaveAntiga);
  if (antigo === null) {
    return atual;
  }
  if (atual === null) {
    try {
      armazenamento.setItem(chave, antigo);
    } catch {
      // cota cheia: devolve o antigo e não apaga, para tentar de novo na próxima leitura.
      return antigo;
    }
  }
  armazenamento.removeItem(chaveAntiga);
  return atual ?? antigo;
}

/** Apaga a chave nova e a antiga. Pode lançar: quem chama trata. */
export function removerComAntiga(armazenamento: Storage, nome: string): void {
  armazenamento.removeItem(PREFIXO + nome);
  armazenamento.removeItem(PREFIXO_ANTIGO + nome);
}
