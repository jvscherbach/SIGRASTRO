package br.gov.mt.sema.rastro.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomesNoZipTest {

    @Test
    void nomeComumFicaComoEsta() {
        assertThat(new NomesNoZip().reservar("Área de reserva.kml", "kml")).isEqualTo("Área de reserva.kml");
    }

    @Test
    void colisaoGanhaSufixoNumericoSemDiferenciarMaiusculas() {
        NomesNoZip nomes = new NomesNoZip();

        assertThat(nomes.reservar("area.kml", "kml")).isEqualTo("area.kml");
        assertThat(nomes.reservar("area.kml", "kml")).isEqualTo("area-2.kml");
        assertThat(nomes.reservar("AREA.KML", "kml")).isEqualTo("AREA-3.kml");
        assertThat(nomes.reservar("area-2.kml", "kml")).isEqualTo("area-2-2.kml");
    }

    @Test
    void semCaminhoNemSubidaDeDiretorio() {
        NomesNoZip nomes = new NomesNoZip();

        assertThat(nomes.reservar("../../etc/x.kml", "kml")).isEqualTo("x.kml");
        assertThat(nomes.reservar("C:\\Windows\\sistema.shp", "shp")).isEqualTo("sistema.shp");
        assertThat(nomes.reservar("..", "kml")).isEqualTo("geometria.kml");
        assertThat(nomes.reservar("a..b.kml", "kml")).isEqualTo("a.b.kml");
        assertThat(nomes.reservar("/", "kmz")).isEqualTo("geometria.kmz");
    }

    @Test
    void semCaractereDeControleFormatacaoOuReservado() {
        NomesNoZip nomes = new NomesNoZip();

        assertThat(nomes.reservar("a\u0000b\r\nSet-Cookie: x.kml", "kml")).isEqualTo("abSet-Cookie_ x.kml");
        // U+202E inverte a direção do texto ("lmk.exe" pareceria outra coisa)
        assertThat(nomes.reservar("\u202Eexe.kml", "kml")).isEqualTo("exe.kml");
        assertThat(nomes.reservar("a<b>c|d?e*f\".kml", "kml")).isEqualTo("a_b_c_d_e_f_.kml");
        assertThat(nomes.reservar(null, "kml")).isEqualTo("geometria.kml");
    }

    @Test
    void nomeLongoECortadoPreservandoAExtensao() {
        String nome = new NomesNoZip().reservar("x".repeat(300) + ".shp", "shp");

        assertThat(nome).hasSize(NomesNoZip.MAXIMO).endsWith(".shp").startsWith("xxx");
    }

    @Test
    void colisaoDeNomeNoLimiteContinuaNoLimite() {
        NomesNoZip nomes = new NomesNoZip();
        String longo = "y".repeat(300) + ".kml";

        nomes.reservar(longo, "kml");
        String segundo = nomes.reservar(longo, "kml");

        assertThat(segundo).hasSize(NomesNoZip.MAXIMO).endsWith("-2.kml");
    }
}
