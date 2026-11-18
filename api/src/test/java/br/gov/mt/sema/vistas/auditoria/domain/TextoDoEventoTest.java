package br.gov.mt.sema.vistas.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** CPF/CNPJ colado a separador, achado pela varredura da S5 (R58). Números de exemplo, fictícios. */
class TextoDoEventoTest {

    @ParameterizedTest
    @ValueSource(strings = {"contrato-11144477735.pdf", "contrato_11144477735.pdf", "contrato.11144477735.pdf",
            "contrato-11222333000181.pdf", "contrato-111.444.777-35.pdf", "contrato.11.222.333/0001-81.pdf"})
    void documento_colado_a_separador_sai_do_nome_do_arquivo(String nome) {
        assertThat(TextoDoEvento.limpar(nome, 200)).contains(TextoDoEvento.REMOVIDO).doesNotContain("444", "222");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dossie-processo-2825-2025.pdf", "Processo 2825/2025", "oficio-123456.pdf"})
    void numero_que_nao_e_documento_fica(String texto) {
        assertThat(TextoDoEvento.limpar(texto, 200)).isEqualTo(texto);
    }
}
