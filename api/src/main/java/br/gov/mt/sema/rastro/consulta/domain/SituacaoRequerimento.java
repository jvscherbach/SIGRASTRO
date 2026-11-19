package br.gov.mt.sema.rastro.consulta.domain;

/** Situações do requerimento no SIGA (Apêndice A de docs/04-modelo-de-dados.md). */
public enum SituacaoRequerimento {
    EM_CADASTRO("Em cadastro"),
    EM_CORRECAO_CADASTRO("Em correção de cadastro"),
    AGUARDANDO_CONFERENCIA("Aguardando conferência"),
    EM_CONFERENCIA("Em conferência"),
    CHECKLIST_REPROVADO("Checklist reprovado"),
    ENCAMINHADO_PARA_ARRECADACAO("Encaminhado para arrecadação"),
    CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE("Cancelado por solicitação do cadastrante"),
    CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO("Cancelado por expiração de prazo de cadastro"),
    CANCELADO_POR_FALTA_DE_PAGAMENTO("Cancelado por falta de pagamento"),
    AGUARDANDO_EMISSAO_DE_TAXAS("Aguardando emissão de taxas"),
    AGUARDANDO_PAGAMENTO_DE_TAXAS("Aguardando pagamento de taxas"),
    AGUARDANDO_ASSINATURA_LAC("Aguardando assinatura LAC"),
    AGUARDANDO_ATRIBUICAO("Aguardando atribuição"),
    AGUARDANDO_ANALISE("Aguardando análise"),
    AGUARDANDO_ELABORACAO("Aguardando elaboração"),
    AGUARDANDO_ACEITE_ATRIBUICAO("Aguardando aceite de atribuição"),
    EM_ANALISE("Em análise"),
    AGUARDANDO_PUBLICACAO("Aguardando publicação"),
    AGUARDANDO_CHECKLIST_DOCUMENTOS("Aguardando checklist de documentos"),
    ARQUIVADO_DEFINITIVAMENTE("Arquivado definitivamente"),
    ARQUIVADO_TEMPORARIAMENTE("Arquivado temporariamente"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado"),
    AGUARDANDO_REVISAO("Aguardando revisão"),
    EM_REVISAO("Em revisão"),
    REQUERIMENTO_REVISADO("Requerimento revisado"),
    AGUARDANDO_CORRECAO("Aguardando correção"),
    EM_CORRECAO("Em correção"),
    REQUERIMENTO_CORRIGIDO("Requerimento corrigido"),
    /** Valor que o SIGA passou a usar e que ainda não consta da lista acima. */
    DESCONHECIDA("Situação não reconhecida");

    private final String descricao;

    SituacaoRequerimento(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    public static SituacaoRequerimento de(String codigo) {
        if (codigo != null) {
            for (SituacaoRequerimento s : values()) {
                if (s != DESCONHECIDA && s.name().equals(codigo.trim())) {
                    return s;
                }
            }
        }
        return DESCONHECIDA;
    }
}
