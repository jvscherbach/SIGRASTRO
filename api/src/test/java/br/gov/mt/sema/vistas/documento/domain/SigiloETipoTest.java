package br.gov.mt.sema.vistas.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SigiloETipoTest {

    // --- sigilo (RN06): normalização tolerante

    @ParameterizedTest
    @ValueSource(strings = {"S", "s", "SIM", "sim", "Y", "YES", "T", "TRUE", "true", "1", " S "})
    void textoAfirmativoESigiloso(String valor) {
        assertThat(Sigilo.ehSigiloso(valor)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "n", "NAO", "não", "NÃO", "NO", "F", "FALSE", "false", "0", " N "})
    void textoNegativoNaoESigiloso(String valor) {
        assertThat(Sigilo.ehSigiloso(valor)).isFalse();
    }

    @Test
    void numeroDiferenteDeZeroESigilosoEZeroNao() {
        assertThat(Sigilo.ehSigiloso(1)).isTrue();
        assertThat(Sigilo.ehSigiloso(2L)).isTrue();
        assertThat(Sigilo.ehSigiloso(new BigDecimal("1"))).isTrue();
        assertThat(Sigilo.ehSigiloso(new BigDecimal("-1"))).isTrue();
        assertThat(Sigilo.ehSigiloso(0)).isFalse();
        assertThat(Sigilo.ehSigiloso(new BigDecimal("0"))).isFalse();
        assertThat(Sigilo.ehSigiloso(new BigDecimal("0.0"))).isFalse();
    }

    @Test
    void booleanoSegueOValor() {
        assertThat(Sigilo.ehSigiloso(Boolean.TRUE)).isTrue();
        assertThat(Sigilo.ehSigiloso(Boolean.FALSE)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nuloEVazioNaoSaoSigilosos(String valor) {
        assertThat(Sigilo.ehSigiloso(valor)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TALVEZ", "X", "2", "restrito", "?"})
    void valorDesconhecidoNaoNuloENaDuvidaESigiloso(String valor) {
        assertThat(Sigilo.ehSigiloso(valor)).isTrue();
    }

    // --- tipo de arquivo pela extensão

    @Test
    void tipoPelaExtensao() {
        assertThat(TipoArquivo.daExtensao("pdf")).isEqualTo(TipoArquivo.PDF);
        for (String e : new String[] {"xls", "xlsx", "ods"}) {
            assertThat(TipoArquivo.daExtensao(e)).isEqualTo(TipoArquivo.PLANILHA);
        }
        for (String e : new String[] {"doc", "docx", "odt", "rtf", "html", "txt"}) {
            assertThat(TipoArquivo.daExtensao(e)).isEqualTo(TipoArquivo.TEXTO);
        }
        for (String e : new String[] {"png", "jpg", "jpeg"}) {
            assertThat(TipoArquivo.daExtensao(e)).isEqualTo(TipoArquivo.IMAGEM);
        }
        for (String e : new String[] {"shp", "kml", "kmz"}) {
            assertThat(TipoArquivo.daExtensao(e)).isEqualTo(TipoArquivo.GEOMETRIA);
        }
        for (String e : new String[] {"zip", "rar", "7z"}) {
            assertThat(TipoArquivo.daExtensao(e)).isEqualTo(TipoArquivo.COMPACTADO);
        }
        assertThat(TipoArquivo.daExtensao("dwg")).isEqualTo(TipoArquivo.DESCONHECIDO);
        assertThat(TipoArquivo.daExtensao("")).isEqualTo(TipoArquivo.DESCONHECIDO);
        assertThat(TipoArquivo.daExtensao(null)).isEqualTo(TipoArquivo.DESCONHECIDO);
    }

    @Test
    void extensaoDoNomeEEmMinusculaESemPonto() {
        assertThat(TipoArquivo.extensaoDe("Relatório FINAL.PDF")).isEqualTo("pdf");
        assertThat(TipoArquivo.extensaoDe("a.b.c.XLSX")).isEqualTo("xlsx");
        assertThat(TipoArquivo.extensaoDe("sem-extensao")).isEmpty();
        assertThat(TipoArquivo.extensaoDe("termina-com-ponto.")).isEmpty();
        assertThat(TipoArquivo.extensaoDe(null)).isEmpty();
    }

    // --- situação (RN01)

    @Test
    void chaveZeroNulaOuVaziaEIndisponivelMesmoComVigenciaAberta() {
        assertThat(SituacaoDocumento.calcular("0", null)).isEqualTo(SituacaoDocumento.INDISPONIVEL);
        assertThat(SituacaoDocumento.calcular(" 0 ", null)).isEqualTo(SituacaoDocumento.INDISPONIVEL);
        assertThat(SituacaoDocumento.calcular(null, null)).isEqualTo(SituacaoDocumento.INDISPONIVEL);
        assertThat(SituacaoDocumento.calcular("", null)).isEqualTo(SituacaoDocumento.INDISPONIVEL);
    }

    @Test
    void indisponivelTemPrecedenciaSobreSubstituido() {
        assertThat(SituacaoDocumento.calcular("0", LocalDateTime.now())).isEqualTo(SituacaoDocumento.INDISPONIVEL);
    }

    @Test
    void fimDaVigenciaPreenchidoESubstituidoESemFimEVigente() {
        assertThat(SituacaoDocumento.calcular("K1", LocalDateTime.now())).isEqualTo(SituacaoDocumento.SUBSTITUIDO);
        assertThat(SituacaoDocumento.calcular("K1", null)).isEqualTo(SituacaoDocumento.VIGENTE);
    }

    @Test
    void todaOrigemLeOFimDaVigenciaEOSigiloDaLinha() {
        for (OrigemDocumento origem : OrigemDocumento.values()) {
            Documento d = Documento.deLinha(origem, "1", 10, "K1", null, "a.pdf", null, LocalDateTime.now(), 1);

            assertThat(d.situacao()).isEqualTo(SituacaoDocumento.SUBSTITUIDO);
            assertThat(d.sigiloso()).isTrue();
        }
    }

    @Test
    void sigiloForaDeZeroEUmENaDuvidaESigiloso() {
        Documento d = Documento.deLinha(OrigemDocumento.REPRESENTANTE_LEGAL, "1", 10, "K1", null, "a.pdf", null, null, "TALVEZ");

        assertThat(d.sigiloso()).isTrue();
    }
}
