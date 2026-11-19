package br.gov.mt.sema.rastro.dossie.domain;

/** Decisão do usuário sobre um pedido em PENDENTE_DECISAO (S2.2). Nenhuma das duas reinicia o que já foi obtido. */
public enum AcaoDeContinuacao {
    /** Refaz só os itens com problema (em falha ou não obtidos). */
    TENTAR_NOVAMENTE,
    /** Marca os itens com problema como REMOVIDO e vai para a montagem. */
    REMOVER_E_CONTINUAR
}
