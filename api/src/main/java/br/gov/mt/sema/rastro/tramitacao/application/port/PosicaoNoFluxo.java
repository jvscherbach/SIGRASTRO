package br.gov.mt.sema.rastro.tramitacao.application.port;

import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;

/**
 * Leitura do Camunda e do tipo de processo para a posição atual (S7, bloco 3). Implementada só em
 * {@code integration.siga} ({@code CamundaPosicaoJdbc}); todo SQL é {@code SELECT} por {@code PROC_INST_ID_}, com
 * limite de linhas e tempo limite (docs/15, "Leitura no Camunda"). Falha de banco sai como exceção: o caso de uso
 * transforma em {@code INDISPONIVEL}.
 */
public interface PosicaoNoFluxo {

    /** Tipo e situação do requerimento; vazio se o requerimento não existe. */
    Optional<RequerimentoNoFluxo> requerimento(long idRequerimento);

    /** Fatos do Camunda para o requerimento no modelo dado (a chave do modelo sempre filtra: regra técnica 2). */
    LeituraDoFluxo ler(long idRequerimento, String chaveDoModelo);

    /**
     * @param instancia valor de {@code TIPO_PROCESSO.INSTANCIA_REQUERIMENTO} (ex.: {@code RECURSO_HIDRICO})
     * @param situacao  {@code REQUERIMENTO.SITUACAO_PROCESSO}; só passa adiante para o confronto (bloco 5), nunca deriva a
     *                  etapa
     */
    record RequerimentoNoFluxo(String instancia, String situacao) {}
}
