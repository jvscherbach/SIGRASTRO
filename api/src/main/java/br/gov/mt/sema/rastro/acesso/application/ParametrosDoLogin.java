package br.gov.mt.sema.rastro.acesso.application;

/**
 * Parâmetros do login (L1). Todos são de classe "reinício": valem a partir da subida do serviço. Os valores e as faixas
 * vêm do ambiente ({@code config.ParametrosDoLoginDoAmbiente}).
 */
public interface ParametrosDoLogin {

    /** Custo do PBKDF2 para hashes novos. */
    int iteracoes();

    /** Senhas erradas seguidas, para o mesmo login e a mesma origem, que bloqueiam a entrada. */
    int tentativas();

    /** Falhas mais antigas que isto (minutos) deixam de contar. */
    int janelaMinutos();

    /** Duração do bloqueio, em minutos. */
    int bloqueioMinutos();

    /** Falhas, somando todos os logins, que bloqueiam uma origem dentro da janela. */
    int limitePorOrigem();
}
