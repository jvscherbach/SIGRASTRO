package br.gov.mt.sema.rastro.caixa.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Estado de posse de um requerimento para um analista (S11): com o analista (e em qual etapa do catálogo), com o interessado
 * (correção que continua sendo dele) ou fora da posse.
 */
public record EstadoDePosse(Tipo tipo, Optional<String> etapa) {

    public enum Tipo { COM_ANALISTA, COM_INTERESSADO, FORA }

    public static final EstadoDePosse COM_INTERESSADO = new EstadoDePosse(Tipo.COM_INTERESSADO, Optional.empty());
    public static final EstadoDePosse FORA = new EstadoDePosse(Tipo.FORA, Optional.empty());

    public EstadoDePosse {
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(etapa);
        if ((tipo == Tipo.COM_ANALISTA) != etapa.isPresent()) {
            throw new IllegalArgumentException("só COM_ANALISTA tem etapa");
        }
    }

    /** Na posse do analista, na etapa de id estável {@code etapa} do catálogo. */
    public static EstadoDePosse comAnalista(String etapa) {
        return new EstadoDePosse(Tipo.COM_ANALISTA, Optional.of(etapa));
    }

    public boolean comAnalista() {
        return tipo == Tipo.COM_ANALISTA;
    }
}
