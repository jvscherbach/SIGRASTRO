package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Evento de fim do modelo ({@code END_ACT_ID_} de uma instância encerrada).
 *
 * @param id         identificador estável (ex.: {@code concluido})
 * @param rotulo     texto ao usuário (ex.: "Concluído", "Cancelado pelo cadastrante")
 * @param idTecnico  id do evento de fim no BPMN (ex.: {@code Event_1wiuc48})
 */
public record FinalDoFluxo(String id, String rotulo, String idTecnico) {

    public TipoDeEtapa tipo() {
        return TipoDeEtapa.FINAL;
    }
}
