package br.gov.mt.sema.vistas.integration.libreoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Conversor LibreOffice. Aqui, só o que não exige o LibreOffice (roda sempre); a conversão real fica em
 * {@link ConversaoComSofficeTest} e {@link ProcessosDoConversorTest}, com os apoios comuns desta classe.
 */
class ConversorLibreOfficeTest {

    static final File OFFICE_HOME = ConversorLibreOffice.localizar("");
    static final String SEM_SOFFICE = "LibreOffice (soffice) não instalado nesta máquina: testes de conversão pulados";

    /**
     * Parâmetros de teste. Os do pool (timeout, processos, tarefas) valem a partir da subida; os de planilha e os
     * limites são lidos a cada conversão e podem mudar entre os testes de um mesmo conversor.
     */
    static final class Parametros implements ParametrosDaConversao {

        private final boolean habilitada;
        private final int timeoutSegundos;
        private final int processos;
        private final int tarefasPorProcesso;
        PlanilhaModo planilhaModo = PlanilhaModo.LARGURA;
        boolean abasOcultas;
        int planilhaEscalaMinima = 40;
        int paginasMaximas = 2000;

        Parametros(boolean habilitada, int timeoutSegundos, int processos, int tarefasPorProcesso) {
            this.habilitada = habilitada;
            this.timeoutSegundos = timeoutSegundos;
            this.processos = processos;
            this.tarefasPorProcesso = tarefasPorProcesso;
        }

        static Parametros padrao() {
            return new Parametros(true, 60, 1, 50);
        }

        @Override
        public boolean habilitada() {
            return habilitada;
        }

        @Override
        public int timeoutSegundos() {
            return timeoutSegundos;
        }

        @Override
        public int processos() {
            return processos;
        }

        @Override
        public int tarefasPorProcesso() {
            return tarefasPorProcesso;
        }

        @Override
        public PlanilhaModo planilhaModo() {
            return planilhaModo;
        }

        @Override
        public boolean abasOcultas() {
            return abasOcultas;
        }

        @Override
        public int imagemMaxMegapixels() {
            return 100;
        }

        @Override
        public int planilhaEscalaMinima() {
            return planilhaEscalaMinima;
        }

        @Override
        public int paginasMaximas() {
            return paginasMaximas;
        }

        @Override
        public int saidaMaxMb() {
            return 50;
        }
    }

    static ConversorLibreOffice conversor(Parametros parametros, File officeHome, Path base) {
        return new ConversorLibreOffice(parametros, officeHome, base, Clock.systemUTC());
    }

    @TempDir
    Path pasta;

    // ---- sem LibreOffice: rodam sempre ----

    @Test
    void semSofficeFicaIndisponivelEFalhaComMotivoProprio() throws IOException {
        assertThat(ConversorLibreOffice.localizar(pasta.resolve("nao-existe").toString())).isNull();
        ConversorLibreOffice semSoffice = conversor(Parametros.padrao(), null, pasta);
        Path origem = Files.write(pasta.resolve("a.docx"), new byte[] {1});

        assertThat(semSoffice.disponivel()).isFalse();
        assertThatThrownBy(() -> semSoffice.converter(origem, new TipoDetectado(TipoReal.TEXTO, "docx"),
                pasta.resolve("saida.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(Motivo.CONVERSOR_INDISPONIVEL));
        semSoffice.destroy();
    }

    @Test
    void desligadoPelaChaveDeSegurancaFicaIndisponivel() {
        ConversorLibreOffice desligado = conversor(new Parametros(false, 60, 1, 50), new File("."), pasta);
        assertThat(desligado.disponivel()).isFalse();
    }

    @Test
    void imagemPdfEDesconhecidoNaoPassamPeloLibreOffice() throws IOException {
        ConversorLibreOffice semSoffice = conversor(Parametros.padrao(), null, pasta);
        Path origem = Files.write(pasta.resolve("a.bin"), new byte[] {1});
        for (TipoDetectado tipo : List.of(new TipoDetectado(TipoReal.IMAGEM, "png"),
                new TipoDetectado(TipoReal.PDF, "pdf"), new TipoDetectado(TipoReal.DESCONHECIDO, null),
                new TipoDetectado(TipoReal.COMPACTADO, "zip"), new TipoDetectado(TipoReal.TEXTO, "txt"))) {
            assertThatThrownBy(() -> semSoffice.converter(origem, tipo, pasta.resolve("saida.pdf")))
                    .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                            f -> assertThat(f.motivo()).isEqualTo(Motivo.FORMATO_NAO_SUPORTADO));
        }
    }

    @Test
    void reconheceOfficeCriptografadoSemLerOArquivoInteiro() throws Exception {
        Path criptografado = Files.write(pasta.resolve("c.docx"), DocumentosDeTeste.docxCriptografado("x"));
        Path simples = Files.write(pasta.resolve("s.docx"), DocumentosDeTeste.docx("x"));
        Path lixo = Files.write(pasta.resolve("l.doc"), DocumentosDeTeste.zipCorrompido());
        Path vazio = Files.write(pasta.resolve("v.doc"), new byte[0]);

        assertThat(DocumentoCriptografado.ooxmlCriptografado(criptografado)).isTrue();
        assertThat(DocumentoCriptografado.ooxmlCriptografado(simples)).isFalse();
        assertThat(DocumentoCriptografado.ooxmlCriptografado(lixo)).isFalse();
        assertThat(DocumentoCriptografado.ooxmlCriptografado(vazio)).isFalse();
    }

    @Test
    void mensagensDeFalhaSemCaminhoNemNomeDeArquivo() {
        for (Motivo motivo : Motivo.values()) {
            assertThat(new FalhaDeConversaoException(motivo).getMessage())
                    .doesNotContain("/").doesNotContain("\\").doesNotContain("entrada").doesNotContain("soffice");
        }
    }

    // ---- apoio ----

    static Path converter(Path pasta, ConversorLibreOffice alvo, String extensao, byte[] conteudo, TipoReal tipo)
            throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        Path destino = pedido.resolve("saida.pdf");
        alvo.converter(origem, new TipoDetectado(tipo, extensao), destino);
        return destino;
    }

    static void assertFalha(Path pasta, ConversorLibreOffice alvo, String extensao, byte[] conteudo, Motivo esperado)
            throws IOException {
        Path pedido = Files.createTempDirectory(pasta, "pedido");
        Path origem = Files.write(pedido.resolve("origem." + extensao), conteudo);
        Path destino = pedido.resolve("saida.pdf");
        assertThatThrownBy(() -> alvo.converter(origem, new TipoDetectado(TipoReal.TEXTO, extensao), destino))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        f -> assertThat(f.motivo()).isEqualTo(esperado));
        try (Stream<Path> itens = Files.list(pedido)) {
            assertThat(itens.map(p -> p.getFileName().toString())).as("nada além da origem")
                    .containsExactly("origem." + extensao);
        }
    }

    static String texto(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(documento).replaceAll("\\s+", " ");
        }
    }

    static PDRectangle pagina(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            return documento.getPage(0).getMediaBox();
        }
    }
}
