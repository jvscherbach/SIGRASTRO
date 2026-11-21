package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Por que uma etapa foi percorrida de novo (S8, bloco 3; tabela em docs/15, "Motivos da volta"). Só sai quando a regra
 * explícita de {@link MotivosDaVolta} casa: sem motivo determinável, a passagem fica sem motivo, nunca com um inventado.
 */
public enum MotivoDaVolta {
    REPROVADO_NA_CONFERENCIA("devolvido na conferência dos documentos"),
    DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE("devolvido na validação do parecer"),
    DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO("devolvido na validação do parecer"),
    TROCA_DE_ANALISTA("troca de analista"),
    DEVOLVIDO_NA_EMISSAO("devolvido na emissão do ato"),
    REVISAR_NA_VERIFICACAO("revisão pedida na verificação do processo"),
    RECUSA_NO_ACEITE("recusa no aceite pelo analista"),
    REDISTRIBUICAO("redistribuição"),
    CORRECAO_DO_REQUERIMENTO("depois da correção do requerimento");

    private final String rotulo;

    MotivoDaVolta(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto ao usuário, em minúsculas, para compor "2ª passagem: devolvido na validação do parecer". */
    public String rotulo() {
        return rotulo;
    }
}
