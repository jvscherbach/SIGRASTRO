package br.gov.mt.sema.rastro.acesso.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CpfCnpjDoUsuarioTest {

    @ParameterizedTest
    @ValueSource(strings = {"11144477735", "11222333000181"})
    void digitos_verificadores_corretos(String digitos) {
        assertThat(CpfCnpjDoUsuario.valido(digitos)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11144477736", "11222333000182", "11111111111", "00000000000000", "123", ""})
    void recusa_o_resto(String digitos) {
        assertThat(CpfCnpjDoUsuario.valido(digitos)).isFalse();
    }

    @Test
    void mascara_nunca_traz_o_numero_inteiro() {
        assertThat(CpfCnpjDoUsuario.mascarar("111.444.777-35")).isEqualTo("***.444.777-**");
        assertThat(CpfCnpjDoUsuario.mascarar("11.222.333/0001-81")).isEqualTo("**.222.333/****-**");
        assertThat(CpfCnpjDoUsuario.mascarar(null)).isNull();
        assertThat(CpfCnpjDoUsuario.mascarar("123")).isNull();
    }
}
