package br.gov.mt.sema.rastro.configuracao.domain;

/** Quando uma alteração de parâmetro passa a valer. */
public enum ClasseDeAplicacao {

    /** Na próxima leitura. */
    IMEDIATO,
    /** Em pedidos criados depois; os em andamento seguem com o retrato tirado na criação. */
    PROXIMA_GERACAO,
    /** Lido só na subida do serviço: fica pendente e, como as sobreposições somem ao reiniciar, exige a variável de ambiente. */
    EXIGE_REINICIO
}
