package br.gov.mt.sema.vistas.documento.application;

/** Documento referenciado pelo cliente: origem e id, nunca a chave do arquivo (RNF09). */
public record ReferenciaDeDocumento(String origem, String id) {
}
