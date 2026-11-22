package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Valor de uma variável da <b>lista branca</b> ({@link #PERMITIDAS}) lida do histórico: {@code ACT_HI_DETAIL} (valor da
 * época, em ordem) ou {@code ACT_HI_VARINST} (valor final). As variáveis do Camunda podem guardar CPF e nome; só estas
 * seis são lidas, e um valor que pareça CPF (11 dígitos) ou passe de {@value #TAMANHO_MAXIMO} caracteres é descartado
 * aqui (fica vazio), por segurança.
 *
 * @param texto  {@code TEXT_}
 * @param numero {@code LONG_} (os booleanos do Camunda ficam aqui, 0 ou 1; o que cada valor significa é do bloco 3, P40)
 */
public record VariavelBruta(String instancia, String nome, String tipo, String texto, Long numero,
        LocalDateTime quando) {

    /** Lista branca do prompt da S8 (bloco 3): nenhuma guarda dado pessoal. */
    public static final Set<String> PERMITIDAS = Set.of("decisaoValidacaoParecerTecnico", "revisarRequerimento",
            "decisaoVerificarRevisao", "isRevisaoAnalise", "isRevisaoParecer", "statusConferencia");

    public static final int TAMANHO_MAXIMO = 100;

    private static final Pattern ONZE_DIGITOS = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");

    public VariavelBruta {
        Objects.requireNonNull(instancia, "instancia");
        Objects.requireNonNull(nome, "nome");
        if (!PERMITIDAS.contains(nome)) {
            throw new IllegalArgumentException("variável fora da lista branca: " + nome);
        }
        if (texto != null && (texto.length() > TAMANHO_MAXIMO || ONZE_DIGITOS.matcher(texto).find())) {
            texto = null;
        }
        if (numero != null && ONZE_DIGITOS.matcher(Long.toString(numero)).find()) {
            numero = null;
        }
    }

    /** O valor como texto: {@code TEXT_} se houver, senão {@code LONG_}. */
    public Optional<String> valor() {
        if (texto != null) {
            return Optional.of(texto);
        }
        return Optional.ofNullable(numero).map(Object::toString);
    }
}
