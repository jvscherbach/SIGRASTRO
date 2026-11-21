package br.gov.mt.sema.rastro.tramitacao.application.port;

/**
 * Parâmetros da tramitação (S7). Ligados em {@code config}; lidos a cada consulta, então uma mudança vale na próxima
 * leitura (classe "imediato", como na S5). O bloco 10 os leva para a tela de Configurações.
 */
public interface ParametrosDoRastro {

    /** {@code RASTRO_MOSTRAR_NOME_RESPONSAVEL}: desligado, só a área e o papel, sem nome (P32). */
    boolean mostrarNomeDoResponsavel();

    /** {@code RASTRO_MAX_NOMES_POR_EQUIPE}: participantes listados; o resto vira "e mais N". */
    int maximoDeNomesPorEquipe();

    /** {@code RASTRO_POSICAO_CACHE_SEGUNDOS}: validade da posição em cache; 0 desliga o cache. */
    int cacheDaPosicaoSegundos();

    /**
     * {@code RASTRO_POSICOES_SIMULTANEAS} (S7, bloco 10): quantas posições a tela pede ao mesmo tempo (lista da busca e
     * outros requerimentos do processo). O front lê pela rota de parâmetros de exibição.
     */
    int posicoesSimultaneas();

    /** {@code RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS} (S7, bloco 10): tempo limite de cada consulta ao Camunda, lido a cada consulta. */
    int camundaTimeoutSegundos();
}
