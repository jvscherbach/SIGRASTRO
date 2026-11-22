package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Porta do mapa de eventos do plano B (arquivo {@code rastro/eventos-historico.yaml}, validado na subida contra o
 * catálogo de etapas): evento de {@code HISTORICO_REQUERIMENTO} → etapa simplificada do modelo, ou marco de data. Nunca
 * lança: evento fora do mapa é {@link Desconhecido} (ignorado e contado).
 */
public interface MapaDeEventosDoHistorico {

    Resolucao resolver(String chaveDoModelo, String evento);

    sealed interface Resolucao permits NaEtapa, Marco, Desconhecido {}

    /** O evento indica que o requerimento estava nesta etapa naquela data. */
    record NaEtapa(String idDaEtapa) implements Resolucao {}

    /** O evento é um marco de data sem etapa própria (protocolo, conclusão). */
    record Marco(HistoricoAproximado.TipoDeMarco tipo) implements Resolucao {}

    record Desconhecido() implements Resolucao {}
}
