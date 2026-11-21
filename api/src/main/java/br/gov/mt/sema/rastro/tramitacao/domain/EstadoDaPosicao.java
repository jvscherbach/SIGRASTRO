package br.gov.mt.sema.rastro.tramitacao.domain;

/** Estado da posição devolvida (contrato do bloco 6). {@code INDISPONIVEL} não é erro: o Rastro degrada. */
public enum EstadoDaPosicao {
    ATIVO,
    ENCERRADO,
    SEM_INSTANCIA,
    NAO_SUPORTADO,
    INDISPONIVEL
}
