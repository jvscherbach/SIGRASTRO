package br.gov.mt.sema.rastro.tramitacao.domain;

/**
 * Fatos da leitura que viram avisos no bloco 5 (o texto ao usuário fica no catálogo de avisos, não aqui). Nenhuma marca
 * muda a posição.
 */
public enum MarcaDaPosicao {
    /** Mais de uma instância raiz para o requerimento: vale a mais recente por início. */
    VARIAS_INSTANCIAS,
    /** Tarefa "Suporte" ativa: a posição é a última etapa real percorrida. */
    EM_SUPORTE,
    /** Algum ponto ativo (ou o final) com id técnico fora do catálogo. */
    ETAPA_NAO_MAPEADA,
    /** Requerimento sem instância no Camunda (legado; plano B na S8). */
    SEM_INSTANCIA
}
