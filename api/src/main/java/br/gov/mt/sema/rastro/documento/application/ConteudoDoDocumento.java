package br.gov.mt.sema.rastro.documento.application;

import java.io.InputStream;

/**
 * Conteúdo pronto para a resposta do modal. O arquivo já está aberto ({@code corpo}): a limpeza do cache pode
 * apagá-lo do disco sem cortar a resposta. Quem recebe fecha o fluxo.
 *
 * @param tipoDeConteudo {@code Content-Type} pelo tipo real
 * @param nomeDoArquivo nome seguro, sem caminho nem caractere de controle
 * @param anexo entregar como download ({@code attachment}): tudo o que não é PDF nem imagem
 * @param convertidoDe formato de origem (ex.: "XLSX") quando o PDF veio de uma conversão; nulo nos demais casos
 */
public record ConteudoDoDocumento(InputStream corpo, long tamanho, String tipoDeConteudo, String nomeDoArquivo,
        boolean anexo, String convertidoDe) {
}
