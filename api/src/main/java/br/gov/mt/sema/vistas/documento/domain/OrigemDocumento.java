package br.gov.mt.sema.vistas.documento.domain;

/**
 * Origens de documento do requerimento: as seis tabelas da seção "Arquivos do Processo" da Vista do SIGA e, desde a
 * S4, os documentos pessoais do SCORP, na ordem de precedência da deduplicação (RN05) e do empate na ordem cronológica
 * (SCORP por último). As demais tabelas de arquivo que a Vista não lista ficam em docs/04; a doença grave nunca é
 * origem (RN02). Todas têm fim da vigência (RN01); o SCORP não informa sigilo (padrão por configuração, P30).
 */
public enum OrigemDocumento {

    DOCUMENTO_PROCESSO("Documento do processo"),
    DOCUMENTO_REQUERIMENTO("Documento do requerimento"),
    COMPLEMENTACAO("Complementação"),
    IMOVEL("Imóvel"),
    REPRESENTANTE_LEGAL("Representante legal"),
    RESPONSAVEL_TECNICO("Responsável técnico"),
    /** Documentos pessoais do interessado, dos representantes legais e dos responsáveis técnicos (API do SCORP). */
    SCORP("Pessoal");

    private final String descricao;

    OrigemDocumento(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /** Chave da propriedade {@code vistas.documentos.origens.<chave>} que liga ou desliga a origem. */
    public String propriedade() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Origem lida por SQL no banco do SIGA (as seis da Vista); o SCORP vem por API. */
    public boolean doSiga() {
        return this != SCORP;
    }

    public static OrigemDocumento de(String codigo) {
        for (OrigemDocumento origem : values()) {
            if (origem.name().equals(codigo)) {
                return origem;
            }
        }
        return null;
    }
}
