package br.gov.mt.sema.vistas.documento.application.port;

/** Parâmetros de negócio dos documentos pessoais do SCORP (S4); no protótipo, variáveis de ambiente em {@code config}. */
public interface ParametrosDosDocumentosPessoais {

    /**
     * {@code SCORP_SIGILO_PADRAO}: o SCORP não informa sigilo. {@code false} (padrão) segue a Vista do SIGA e trata
     * todos como não sigilosos (R25); {@code true} trata todos como sigilosos. Decisão pendente com a SEMA (P30).
     */
    boolean sigiloPadrao();
}
