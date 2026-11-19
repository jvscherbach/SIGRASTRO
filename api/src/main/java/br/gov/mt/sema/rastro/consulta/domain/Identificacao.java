package br.gov.mt.sema.rastro.consulta.domain;

/**
 * RN04: com processo, a identificação é o número do processo; o número do requerimento só aparece
 * enquanto ele não foi protocolado.
 */
public record Identificacao(String rotulo, String numero, boolean protocolado) {

    public static Identificacao de(String numeroRequerimento, String numeroProcesso) {
        if (numeroProcesso != null && !numeroProcesso.isBlank()) {
            return new Identificacao("Processo", numeroProcesso.trim(), true);
        }
        return new Identificacao("Requerimento", numeroRequerimento, false);
    }
}
