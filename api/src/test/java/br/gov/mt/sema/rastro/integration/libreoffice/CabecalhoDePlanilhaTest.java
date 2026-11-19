package br.gov.mt.sema.rastro.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Texto do cabeçalho das planilhas convertidas (S3.1). Não exige o LibreOffice: roda sempre. */
class CabecalhoDePlanilhaTest {

    @Test
    void limpaControleEspacosEMantemAcentosEMarcacaoComoTexto() {
        assertThat(CabecalhoDePlanilha.limpar("  Relatório\u0007 de\n\tvazão​ <b>&{x}  ", 200))
                .isEqualTo("Relatório de vazão <b>&{x}");
        assertThat(CabecalhoDePlanilha.limpar(null, 200)).isEmpty();
        assertThat(CabecalhoDePlanilha.limpar(" \u0000 \r\n", 200)).isEmpty();
    }

    @Test
    void cortaComReticenciasSemPartirCaractereComposto() {
        assertThat(CabecalhoDePlanilha.cortar("abcdef", 6)).isEqualTo("abcdef");
        assertThat(CabecalhoDePlanilha.cortar("abcdefg", 6)).isEqualTo("abcde…");
        assertThat(CabecalhoDePlanilha.cortar("ab cdefg", 4)).isEqualTo("ab…");
        // Emoji (par substituto) conta como um caractere e não é partido ao meio.
        String comEmoji = "📄📄📄";
        assertThat(CabecalhoDePlanilha.cortar(comEmoji, 2)).isEqualTo("📄…");
    }

    @Test
    void comNomeDaAbaPreservaAAbaECortaOTitulo() {
        assertThat(CabecalhoDePlanilha.texto("Documento", null, 100)).isEqualTo("Documento");
        assertThat(CabecalhoDePlanilha.texto("Documento", "Dados", 100)).isEqualTo("Documento — Dados");
        assertThat(CabecalhoDePlanilha.texto("Documento muito longo", "Dados", 16)).isEqualTo("Documen… — Dados");
        assertThat(CabecalhoDePlanilha.texto("Documento", "  ", 100)).isEqualTo("Documento");
    }
}
