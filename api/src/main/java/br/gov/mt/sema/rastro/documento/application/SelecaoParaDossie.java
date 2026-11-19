package br.gov.mt.sema.rastro.documento.application;

import java.util.List;

import br.gov.mt.sema.rastro.documento.domain.Documento;

/**
 * Resultado da seleção para o dossiê (S2.2): os escolhidos, em ordem cronológica e com a chave resolvida, e os demais
 * documentos da lista que o usuário viu, com o motivo de não entrarem. {@code listados} é o total da lista
 * (escolhidos + não selecionados): o dossiê registra o que não foi incluído e "N incluídos de M listados".
 */
public record SelecaoParaDossie(List<Documento> escolhidos, List<NaoSelecionado> naoSelecionados, int listados) {

    public SelecaoParaDossie {
        escolhidos = List.copyOf(escolhidos);
        naoSelecionados = List.copyOf(naoSelecionados);
    }

    /**
     * Documento listado que ficou fora da seleção. {@code titulo} já vem mascarado para sigiloso sem permissão; a chave
     * do arquivo nunca é usada a partir daqui.
     */
    public record NaoSelecionado(Documento documento, String titulo, Motivo motivo) {
    }

    public enum Motivo {
        /** Selecionável, mas o usuário o desmarcou. */
        NAO_SELECIONADO,
        /** Chave nula, vazia ou "0". */
        INDISPONIVEL,
        /** Extensão conhecida que não é PDF: aguarda a conversão (S3). */
        NAO_PDF,
        /** Sigiloso e o perfil não o acessa. */
        SIGILOSO
    }
}
