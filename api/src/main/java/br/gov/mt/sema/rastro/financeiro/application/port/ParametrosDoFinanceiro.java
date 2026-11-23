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

    /**
     * {@code RASTRO_MOSTRAR_VALORES} (S9, bloco 6): valor, desconto e UPF saem nas respostas. Desligado, não saem em
     * nenhuma resposta, para nenhum perfil; o número do DAR continua. Dinheiro público de pessoas e empresas: a regra
     * definitiva é da SEMA (docs/10). O padrão aqui serve aos dublês de teste; a aplicação lê do ambiente.
     */
    default boolean mostrarValores() {
        return true;
    }
}
