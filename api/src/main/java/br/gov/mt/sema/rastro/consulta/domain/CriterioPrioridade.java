package br.gov.mt.sema.rastro.consulta.domain;

/**
 * Critério de prioridade (docs/04, P01). As descrições vêm do enum do código do SIGA; a equivalência com o
 * domínio oficial é NÃO CONFIRMADA. Código desconhecido aparece como "Critério N".
 */
public record CriterioPrioridade(int codigo, String descricao) {

    public static CriterioPrioridade de(int codigo) {
        return new CriterioPrioridade(codigo, descricaoDe(codigo));
    }

    private static String descricaoDe(int codigo) {
        return switch (codigo) {
            case 1 -> "Decisão Administrativa";
            case 2 -> "Pessoa com idade igual ou superior a 80 anos";
            case 3 -> "Pessoa com idade igual ou superior a 60 anos";
            case 4 -> "Pessoa com deficiência física ou mental";
            case 5 -> "Pessoa com doença grave";
            case 6 -> "Trata de embargo ou suspensão";
            case 7 -> "Pré-requisito para licenciamento";
            case 8 -> "Utilidade pública";
            case 9 -> "Risco de grave prejuízo";
            default -> "Critério " + codigo;
        };
    }
}
