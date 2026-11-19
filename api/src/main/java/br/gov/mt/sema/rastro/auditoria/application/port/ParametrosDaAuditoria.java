package br.gov.mt.sema.rastro.auditoria.application.port;

import java.util.Set;

import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;

/** Parâmetros da auditoria. No protótipo vêm de variáveis de ambiente (implementação em {@code config}). */
public interface ParametrosDaAuditoria {

    /** Faixas de retenção oferecidas (docs/12). */
    Set<Integer> RETENCOES_EM_ANOS = Set.of(1, 2, 5, 10);

    /** Teto de eventos em memória, somando os dois grupos; acima dele saem os mais antigos. */
    int maximoDeEventos();

    /** Limite de linhas do CSV; acima dele corta e avisa. */
    int maximoDeLinhasDoCsv();

    int retencaoDocumentosAnos();

    int retencaoAcessosAnos();

    default int retencaoEmAnos(GrupoDeEvento grupo) {
        return switch (grupo) {
            case DOCUMENTOS -> retencaoDocumentosAnos();
            case ACESSOS -> retencaoAcessosAnos();
        };
    }
}
