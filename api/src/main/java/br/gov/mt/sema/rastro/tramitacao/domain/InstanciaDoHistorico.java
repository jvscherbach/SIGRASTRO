package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Instância do Camunda lida para o caminho percorrido ({@code ACT_HI_PROCINST}).
 *
 * @param chave          {@code PROC_DEF_KEY_}
 * @param estado         {@code STATE_}, só diagnóstico
 * @param atividadeFinal {@code END_ACT_ID_}
 * @param idDaMae        {@code SUPER_PROCESS_INSTANCE_ID_}; vazio na raiz e na complementação
 * @param truncada       a instância tem mais atividades do que o limite de linhas: só as primeiras
 *                       ({@code lidas}) foram trazidas, na ordem de gravação
 * @param lidas          quantas atividades vieram
 */
public record InstanciaDoHistorico(String id, OrigemDaInstancia origem, String chave, LocalDateTime inicio,
        LocalDateTime fim, String atividadeFinal, String estado, String idDaMae, boolean truncada, int lidas) {

    public InstanciaDoHistorico {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origem, "origem");
    }

    public boolean encerrada() {
        return fim != null;
    }

    public InstanciaDoHistorico comLeitura(boolean truncada, int lidas) {
        return new InstanciaDoHistorico(id, origem, chave, inicio, fim, atividadeFinal, estado, idDaMae, truncada, lidas);
    }
}
