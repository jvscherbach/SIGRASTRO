package br.gov.mt.sema.rastro.configuracao.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Um parâmetro editável: nome estável, faixa e quando vale.
 *
 * @param propriedade propriedade do Spring de onde vem o padrão do ambiente
 * @param padrao valor quando o ambiente não define a propriedade
 * @param minimo só em {@link Tipo#INTEIRO}
 * @param maximo só em {@link Tipo#INTEIRO}
 * @param opcoes só em {@link Tipo#OPCAO}
 */
public record DefinicaoDeParametro(String nome, String rotulo, String grupo, String propriedade, String padrao, Tipo tipo,
        int minimo, int maximo, List<String> opcoes, ClasseDeAplicacao classe) {

    public enum Tipo {
        INTEIRO,
        BOOLEANO,
        OPCAO
    }

    static DefinicaoDeParametro inteiro(String nome, String rotulo, String grupo, String propriedade, int padrao, int minimo,
            int maximo, ClasseDeAplicacao classe) {
        return new DefinicaoDeParametro(nome, rotulo, grupo, propriedade, String.valueOf(padrao), Tipo.INTEIRO, minimo, maximo,
                List.of(), classe);
    }

    static DefinicaoDeParametro booleano(String nome, String rotulo, String grupo, String propriedade, boolean padrao,
            ClasseDeAplicacao classe) {
        return new DefinicaoDeParametro(nome, rotulo, grupo, propriedade, String.valueOf(padrao), Tipo.BOOLEANO, 0, 0,
                List.of(), classe);
    }

    static DefinicaoDeParametro opcao(String nome, String rotulo, String grupo, String propriedade, String padrao,
            List<String> opcoes, ClasseDeAplicacao classe) {
        return new DefinicaoDeParametro(nome, rotulo, grupo, propriedade, padrao, Tipo.OPCAO, 0, 0, opcoes, classe);
    }

    /** O valor canônico, se estiver dentro da faixa. */
    public Optional<String> aceitar(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        String limpo = valor.strip();
        return switch (tipo) {
            case INTEIRO -> inteiro(limpo);
            case BOOLEANO -> "true".equalsIgnoreCase(limpo) || "false".equalsIgnoreCase(limpo)
                    ? Optional.of(limpo.toLowerCase(Locale.ROOT)) : Optional.empty();
            case OPCAO -> opcoes.stream().filter(limpo::equalsIgnoreCase).findFirst();
        };
    }

    public String faixa() {
        return switch (tipo) {
            case INTEIRO -> "Use um número inteiro de " + minimo + " a " + maximo + ".";
            case BOOLEANO -> "Use true ou false.";
            case OPCAO -> "Use " + String.join(", ", opcoes) + ".";
        };
    }

    private Optional<String> inteiro(String valor) {
        try {
            int numero = Integer.parseInt(valor);
            return numero >= minimo && numero <= maximo ? Optional.of(String.valueOf(numero)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
