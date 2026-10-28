package br.gov.mt.sema.vistas.documento.api.dto;

import java.time.LocalDateTime;

import br.gov.mt.sema.vistas.documento.application.DocumentoListado;
import br.gov.mt.sema.vistas.documento.domain.Documento;

/**
 * Item da seção "Geometrias" (RN10, S4): os campos de identificação e situação de {@link DocumentoResponse}, sem os de
 * seleção (geometria não entra no dossiê). {@code tamanho} em bytes, nulo enquanto desconhecido (o SIGA não informa;
 * só se sabe ao obter o arquivo). Sem campo para a chave do arquivo (RNF09).
 */
public record GeometriaResponse(String origem, String id, long idRequerimento, String titulo, String nomeArquivo,
        String origemDescricao, LocalDateTime data, String situacao, boolean sigiloso, String extensao,
        String tipoArquivo, Long tamanho) {

    public static GeometriaResponse de(DocumentoListado item) {
        Documento d = item.documento();
        return new GeometriaResponse(d.origem().name(), d.id(), d.idRequerimento(), item.titulo(), item.nomeArquivo(),
                d.descricaoDaOrigem(), d.data(), d.situacao().name(), d.sigiloso(), item.extensao(),
                d.tipoArquivo().name(), null);
    }
}
