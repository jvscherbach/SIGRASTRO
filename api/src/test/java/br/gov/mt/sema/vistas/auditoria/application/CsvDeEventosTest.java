package br.gov.mt.sema.vistas.auditoria.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvDeEventosTest {

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@x", "\tx", "\rx", "  =1+1"})
    void formula_recebe_apostrofo(String valor) {
        assertThat(CsvDeEventos.celula(valor)).startsWith("\"'");
    }

    @Test
    void texto_comum_nao_muda() {
        assertThat(CsvDeEventos.celula("Licença 2825/2025")).isEqualTo("\"Licença 2825/2025\"");
        assertThat(CsvDeEventos.celula(null)).isEqualTo("\"\"");
        assertThat(CsvDeEventos.celula("")).isEqualTo("\"\"");
    }

    @Test
    void aspas_duplicadas_e_sem_quebra_de_linha() {
        assertThat(CsvDeEventos.celula("a\"b;c\r\nd")).isEqualTo("\"a\"\"b;c d\"");
    }
}
