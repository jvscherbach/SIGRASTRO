package br.gov.mt.sema.vistas.dossie.domain;

import java.time.LocalDateTime;

/**
 * Documento da lista que não entrou no PDF (S2.2, seção "Documentos não incluídos"). {@code posicao} é a posição na
 * lista cronológica do requerimento: a seção segue a mesma ordem do sumário. Nunca carrega chave de arquivo.
 */
public record DocumentoNaoIncluido(int posicao, String origem, String id, String titulo, LocalDateTime data,
        String motivo) {

    public static final String NAO_SELECIONADO = "não selecionado pelo usuário";
    public static final String INDISPONIVEL = "indisponível (chave do arquivo inválida)";
    public static final String NAO_PDF = "não é PDF: aguardando conversão";
    public static final String SIGILOSO = "sigiloso: o perfil de quem gerou não tem acesso";
    public static final String REMOVIDO = "removido pelo usuário";

    /** Removido na decisão: o motivo da falha, quando houve, acompanha. */
    public static String removido(String motivoDaFalha) {
        return motivoDaFalha == null || motivoDaFalha.isBlank() ? REMOVIDO + " (não obtido)"
                : REMOVIDO + " após falha: " + motivoDaFalha;
    }
}
