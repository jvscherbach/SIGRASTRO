package br.gov.mt.sema.rastro.financeiro.application.port;

/**
 * Parâmetros da leitura do financeiro (S9, bloco 2). Ligados em {@code config}; lidos a cada consulta (o bloco 10 os leva à
 * tela de Configurações, com efeito na próxima leitura).
 */
public interface ParametrosDoFinanceiro {

    /** {@code RASTRO_FINANCEIRO_LIMITE_LANCAMENTOS}: lançamentos lidos por requerimento; passando disso, aviso. */
    int limiteDeLancamentos();

    /** {@code RASTRO_FINANCEIRO_TIMEOUT_SEGUNDOS}: tempo limite de cada consulta a {@code LANCAMENTO_TAXA}. */
    int consultaTimeoutSegundos();
}
