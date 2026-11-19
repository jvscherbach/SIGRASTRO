package br.gov.mt.sema.rastro.documento.api.dto;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.documento.application.DocumentoListado;
import br.gov.mt.sema.rastro.documento.domain.Documento;

/** Item da lista de documentos. Não há campo para a chave do arquivo (RNF09). */
public record DocumentoResponse(String origem, String id, long idRequerimento, String titulo, String nomeArquivo,
        String origemDescricao, LocalDateTime data, String situacao, boolean sigiloso, boolean acessivel,
        String extensao, String tipoArquivo, boolean selecionavelPorPadrao, String bloqueio) {

    public static DocumentoResponse de(DocumentoListado item) {
        Documento d = item.documento();
        return new DocumentoResponse(d.origem().name(), d.id(), d.idRequerimento(), item.titulo(), item.nomeArquivo(),
                d.descricaoDaOrigem(), d.data(), d.situacao().name(), d.sigiloso(), item.acessivel(), item.extensao(),
                item.acessivel() ? d.tipoArquivo().name() : null, item.selecionavelPorPadrao(),
                item.bloqueio() == null ? null : item.bloqueio().name());
    }
}
