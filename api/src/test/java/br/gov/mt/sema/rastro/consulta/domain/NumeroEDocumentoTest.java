package br.gov.mt.sema.rastro.consulta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NumeroEDocumentoTest {

    @ParameterizedTest
    @ValueSource(strings = {"3331/2025", "1/2025", "999999/1999", " 12/2024 "})
    void numeroNoFormatoNAAAAEValido(String texto) {
        assertThat(NumeroRequerimento.de(texto).texto()).isEqualTo(texto.trim());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3331", "3331/25", "3331-2025", "abc/2025", "1234567/2025", "/2025", "3331/20255", "3331/2025/1"})
    void numeroForaDoFormatoEhRejeitadoComOCampo(String texto) {
        assertThatThrownBy(() -> NumeroRequerimento.de(texto))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("numero"));
    }

    @Test
    void zerosAEsquerdaSaoNormalizados() {
        assertThat(NumeroRequerimento.de("0012/2024").texto()).isEqualTo("12/2024");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123.456.789-09", "12345678909", "12.345.678/0001-90", "12345678000190"})
    void cpfOuCnpjGuardaSoOsDigitos(String texto) {
        assertThat(CpfCnpj.de(texto).digitos()).isEqualTo(texto.replaceAll("\\D", ""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "1234567890", "123456789012", "123456789012345", "abcdefghijk"})
    void cpfOuCnpjComTamanhoErradoEhRejeitado(String texto) {
        assertThatThrownBy(() -> CpfCnpj.de(texto))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campo()).isEqualTo("cpfCnpj"));
    }
}
