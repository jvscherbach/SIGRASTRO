package br.gov.mt.sema.rastro.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** S6, bloco 1: o nome mostrado é o do arquivo; o título (tipo/descrição) fica à parte; cabeçalho sem extensão. */
class NomeEExibicaoDoDocumentoTest {

    private static Documento doc(String titulo, String arquivo) {
        return Documento.deLinha(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "7", 1, "K", titulo, arquivo,
                LocalDateTime.of(2025, 1, 1, 10, 0), null, null);
    }

    @Test
    void nomeEOArquivoETituloFicaComoTipo() {
        Documento d = doc("Teste de bombeamento.", "Laudo vazão.xlsx");

        assertThat(d.nome()).isEqualTo("Laudo vazão.xlsx");
        assertThat(d.titulo()).isEqualTo("Teste de bombeamento.");
    }

    @Test
    void semArquivoONomeEOTituloESemAmbosEODescritivoDaOrigem() {
        assertThat(doc("Só título", null).nome()).isEqualTo("Só título");
        assertThat(doc(null, "  ").nome()).isEqualTo("Documento do requerimento 7");
    }

    @Test
    void cabecalhoDaPlanilhaUsaONomeDoArquivoSemExtensaoENaoOTipo() {
        assertThat(doc("Teste de bombeamento.", "Teste de bombeamento 2025.xlsx").nomeParaCabecalho())
                .isEqualTo("Teste de bombeamento 2025");
        assertThat(doc("Tipo do documento", "laudo.xlsx").nomeParaCabecalho()).isEqualTo("laudo");
    }

    @Test
    void cabecalhoSemArquivoOuSoComExtensaoCaiNoTitulo() {
        assertThat(doc("Tipo", null).nomeParaCabecalho()).isEqualTo("Tipo");
        assertThat(doc("Tipo", ".xlsx").nomeParaCabecalho()).isEqualTo("Tipo");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "Teste de bombeamento.xlsx|Teste de bombeamento",
            "relatório.final.v2.xlsx|relatório.final.v2",
            "Relatório v1.2 final|Relatório v1.2 final",
            "sem extensão|sem extensão",
            "Termina com ponto.|Termina com ponto",
            ".xlsx|''",
            "  espaço.ods  |espaço",
            "NULL|''"})
    void semExtensao(String entrada, String esperado) {
        assertThat(TipoArquivo.semExtensao(entrada)).isEqualTo("''".equals(esperado) ? "" : esperado);
    }
}
