package br.gov.mt.sema.rastro.documento.application.port;

import java.util.List;

import br.gov.mt.sema.rastro.documento.domain.Documento;

/**
 * Porta de leitura dos documentos de um requerimento, implementada por integration.siga. Devolve todos os registros
 * das origens ligadas, sem filtrar vigência e sem deduplicar; as regras da lista são do domínio.
 */
public interface DocumentosDoRequerimento {

    List<Documento> listar(long idRequerimento);
}
