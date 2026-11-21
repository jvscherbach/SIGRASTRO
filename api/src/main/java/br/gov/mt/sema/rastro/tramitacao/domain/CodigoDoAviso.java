package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Códigos estáveis dos avisos da posição (S7, bloco 5; docs/15, "Avisos"). O texto e a gravidade ficam no arquivo
 * {@code rastro/confronto.yaml}; o código é o contrato com o front e a auditoria.
 */
public enum CodigoDoAviso {
    /** Hídrico: fluxo encerrado por taxa não paga, situação ainda "aguardando pagamento" (bug de origem). */
    FLUXO_ENCERRADO_SEM_STATUS(true),
    /** Situação CONCLUIDO com a instância em "Verificação da revisão" (arquivamento). */
    CONCLUIDO_ANTES_DA_VERIFICACAO(true),
    /** Situação do Licenciamento fora das aceitas para a etapa (ou o final) do fluxo. */
    SITUACAO_NAO_CONFERE(false),
    /** LAC: expirado na correção com a situação do expirado no cadastro. */
    EXPIRADO_NA_CORRECAO_LAC(true),
    VARIAS_INSTANCIAS(false),
    EM_SUPORTE(false),
    ETAPA_NAO_MAPEADA(false),
    SEM_INSTANCIA(false);

    private final boolean divergenciaConhecida;

    CodigoDoAviso(boolean divergenciaConhecida) {
        this.divergenciaConhecida = divergenciaConhecida;
    }

    /** Pode aparecer na lista {@code conhecidas} da tabela de compatibilidade. */
    public boolean divergenciaConhecida() {
        return divergenciaConhecida;
    }

    /** O aviso que cada marca da leitura gera. */
    public static CodigoDoAviso da(MarcaDaPosicao marca) {
        return switch (marca) {
            case VARIAS_INSTANCIAS -> VARIAS_INSTANCIAS;
            case EM_SUPORTE -> EM_SUPORTE;
            case ETAPA_NAO_MAPEADA -> ETAPA_NAO_MAPEADA;
            case SEM_INSTANCIA -> SEM_INSTANCIA;
        };
    }
}
