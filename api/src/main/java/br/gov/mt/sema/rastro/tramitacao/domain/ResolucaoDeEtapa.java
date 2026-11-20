package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Para onde um id técnico do Camunda leva no catálogo. Nunca lança: id desconhecido é {@link NaoMapeado}, modelo desconhecido
 * é {@link NaoSuportado} (docs/15, "Catálogo de etapas").
 */
public sealed interface ResolucaoDeEtapa {

    /** Rótulo mostrado quando o id técnico não está no catálogo. O id técnico só aparece para o Administrador (bloco 6). */
    String ROTULO_NAO_MAPEADA = "Etapa não mapeada";

    /** Rótulo mostrado quando o modelo (Trifásico, LAS...) ainda não está no catálogo. */
    String ROTULO_NAO_SUPORTADO = "Fluxo ainda não suportado pelo Rastro";

    /** O id técnico cai numa etapa do modelo. */
    record NaEtapa(Etapa etapa) implements ResolucaoDeEtapa {}

    /** O id técnico é um evento de fim do modelo. */
    record Encerrado(FinalDoFluxo fim) implements ResolucaoDeEtapa {}

    /** O id técnico é conhecido e nunca vira etapa (suporte, ruído, ramo inativo). */
    record Ignorado(IdIgnorado ignorado) implements ResolucaoDeEtapa {}

    /**
     * O id técnico não está no catálogo, ou está numa etapa cuja condição não vale para o percurso. Não é erro de execução.
     *
     * @param idTecnico id como veio do Camunda (só para o Administrador)
     */
    record NaoMapeado(String idTecnico) implements ResolucaoDeEtapa {}

    /** A chave do modelo (ou a instância do requerimento) não está no catálogo. */
    record NaoSuportado(String chave) implements ResolucaoDeEtapa {}
}
