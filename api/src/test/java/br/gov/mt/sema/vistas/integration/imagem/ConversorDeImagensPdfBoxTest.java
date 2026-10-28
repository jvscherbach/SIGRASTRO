package br.gov.mt.sema.vistas.integration.imagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import javax.imageio.ImageIO;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.vistas.documento.domain.DetectorDeTipoReal;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import br.gov.mt.sema.vistas.testsupport.ParametrosDeConversaoDeTeste;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Imagens para PDF. Todas as imagens são geradas aqui, por código. */
class ConversorDeImagensPdfBoxTest {

    private static final TipoDetectado PNG = new TipoDetectado(TipoReal.IMAGEM, "png");
    private static final TipoDetectado JPG = new TipoDetectado(TipoReal.IMAGEM, "jpg");
    private static final int VERMELHO = 0xFF0000;
    private static final int AZUL = 0x0000FF;

    @TempDir
    Path pasta;

    private final ConversorDeImagensPdfBox conversor = new ConversorDeImagensPdfBox(ParametrosDeConversaoDeTeste.ligada());

    // --- geração das imagens

    /** Imagem azul com o quadrante superior esquerdo vermelho. */
    private static BufferedImage comMarca(int largura, int altura, int tipo) {
        BufferedImage imagem = new BufferedImage(largura, altura, tipo);
        Graphics2D g = imagem.createGraphics();
        g.setColor(new Color(AZUL));
        g.fillRect(0, 0, largura, altura);
        g.setColor(new Color(VERMELHO));
        g.fillRect(0, 0, largura / 2, altura / 2);
        g.dispose();
        return imagem;
    }

    private Path gravar(String nome, BufferedImage imagem, String formato) throws IOException {
        Path arquivo = pasta.resolve(nome);
        assertThat(ImageIO.write(imagem, formato, arquivo.toFile())).isTrue();
        return arquivo;
    }

    /** Insere um segmento EXIF (big-endian) com a orientação logo após o SOI do JPEG. */
    private Path jpegComOrientacao(String nome, BufferedImage imagem, int orientacao) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", jpeg);
        byte[] bytes = jpeg.toByteArray();
        byte[] exif = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 34,
                'E', 'x', 'i', 'f', 0, 0,
                'M', 'M', 0x00, 0x2A, 0, 0, 0, 8,
                0x00, 0x01,
                0x01, 0x12, 0x00, 0x03, 0, 0, 0, 1, 0, (byte) orientacao, 0, 0,
                0, 0, 0, 0};
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(bytes, 0, 2);
        saida.write(exif);
        saida.write(bytes, 2, bytes.length - 2);
        Path arquivo = pasta.resolve(nome);
        Files.write(arquivo, saida.toByteArray());
        return arquivo;
    }

    private Path converter(Path origem, TipoDetectado tipo) {
        Path destino = pasta.resolve("saida-" + origem.getFileName() + ".pdf");
        ResultadoDaConversao resultado = conversor.converter(origem, tipo, destino);
        assertThat(resultado.paginas()).isEqualTo(1);
        assertThat(resultado.abasEmPaginaUnica()).isZero();
        return destino;
    }

    // --- verificação do PDF

    private static BufferedImage renderizar(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            return new PDFRenderer(documento).renderImageWithDPI(0, 72);
        }
    }

    private static PDPage pagina(Path pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf.toFile())) {
            return documento.getPage(0);
        }
    }

    private static boolean ehVermelho(int rgb) {
        return ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 90 && (rgb & 0xFF) < 90;
    }

    /** Centro do vermelho, de 0 a 1 em cada eixo (0,0 = canto superior esquerdo da página). */
    private static double[] centroDoVermelho(BufferedImage pagina) {
        long somaX = 0;
        long somaY = 0;
        long total = 0;
        for (int y = 0; y < pagina.getHeight(); y++) {
            for (int x = 0; x < pagina.getWidth(); x++) {
                if (ehVermelho(pagina.getRGB(x, y))) {
                    somaX += x;
                    somaY += y;
                    total++;
                }
            }
        }
        assertThat(total).as("pixels vermelhos").isGreaterThan(50);
        return new double[] {(double) somaX / total / pagina.getWidth(), (double) somaY / total / pagina.getHeight()};
    }

    /** Caixa dos pixels que não são brancos: {minX, minY, maxX, maxY}. */
    private static int[] caixaDoConteudo(BufferedImage pagina) {
        int[] caixa = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int y = 0; y < pagina.getHeight(); y++) {
            for (int x = 0; x < pagina.getWidth(); x++) {
                if ((pagina.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    caixa[0] = Math.min(caixa[0], x);
                    caixa[1] = Math.min(caixa[1], y);
                    caixa[2] = Math.max(caixa[2], x);
                    caixa[3] = Math.max(caixa[3], y);
                }
            }
        }
        return caixa;
    }

    // --- testes

    @Test
    void pngPequenoViraUmaPaginaA4EmPaisagemQuandoMaisLargoQueAlto() throws IOException {
        Path pdf = converter(gravar("foto.png", comMarca(200, 100, BufferedImage.TYPE_INT_RGB), "png"), PNG);

        PDPage pagina = pagina(pdf);
        assertThat(pagina.getMediaBox().getWidth()).isGreaterThan(pagina.getMediaBox().getHeight());
        assertThat(pagina.getMediaBox().getHeight()).isCloseTo(595.28f, org.assertj.core.data.Offset.offset(0.5f));
        double[] centro = centroDoVermelho(renderizar(pdf));
        assertThat(centro[0]).isLessThan(0.5);
        assertThat(centro[1]).isLessThan(0.5);
    }

    @Test
    void jpgPequenoEmRetratoLevaOsBytesOriginais() throws IOException {
        Path jpg = gravar("foto.jpg", comMarca(100, 200, BufferedImage.TYPE_INT_RGB), "jpg");
        Path pdf = converter(jpg, JPG);

        PDPage pagina = pagina(pdf);
        assertThat(pagina.getMediaBox().getHeight()).isGreaterThan(pagina.getMediaBox().getWidth());
        // O JPEG não é recompressado: os bytes originais estão dentro do PDF.
        byte[] original = Files.readAllBytes(jpg);
        byte[] saida = Files.readAllBytes(pdf);
        assertThat(indiceDe(saida, Arrays.copyOfRange(original, 0, 64))).isGreaterThanOrEqualTo(0);
    }

    private static int indiceDe(byte[] palheiro, byte[] agulha) {
        for (int i = 0; i + agulha.length <= palheiro.length; i++) {
            if (Arrays.equals(palheiro, i, i + agulha.length, agulha, 0, agulha.length)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void jpgComExifGiradoApareceNaOrientacaoCerta() throws IOException {
        BufferedImage base = comMarca(200, 100, BufferedImage.TYPE_INT_RGB);

        // Orientação 1: paisagem, marca no canto superior esquerdo.
        double[] normal = centroDoVermelho(renderizar(converter(jpegComOrientacao("o1.jpg", base, 1), JPG)));
        assertThat(normal[0]).isLessThan(0.5);
        assertThat(normal[1]).isLessThan(0.5);

        // Orientação 6 (girar 90° horário): vira retrato e a marca vai para o canto superior direito.
        Path pdf6 = converter(jpegComOrientacao("o6.jpg", base, 6), JPG);
        assertThat(pagina(pdf6).getMediaBox().getHeight()).isGreaterThan(pagina(pdf6).getMediaBox().getWidth());
        double[] girado = centroDoVermelho(renderizar(pdf6));
        assertThat(girado[0]).isGreaterThan(0.5);
        assertThat(girado[1]).isLessThan(0.5);

        // Orientação 3 (180°): paisagem, marca no canto inferior direito.
        double[] invertido = centroDoVermelho(renderizar(converter(jpegComOrientacao("o3.jpg", base, 3), JPG)));
        assertThat(invertido[0]).isGreaterThan(0.5);
        assertThat(invertido[1]).isGreaterThan(0.5);

        // Orientação 8 (girar 90° anti-horário): retrato, marca no canto inferior esquerdo.
        double[] antiHorario = centroDoVermelho(renderizar(converter(jpegComOrientacao("o8.jpg", base, 8), JPG)));
        assertThat(antiHorario[0]).isLessThan(0.5);
        assertThat(antiHorario[1]).isGreaterThan(0.5);
    }

    @Test
    void exifForaDeFaixaOuTruncadoNaoGira() throws IOException {
        BufferedImage base = comMarca(200, 100, BufferedImage.TYPE_INT_RGB);
        Path invalido = jpegComOrientacao("o9.jpg", base, 9);
        assertThat(OrientacaoExif.de(invalido)).isEqualTo(1);
        assertThat(OrientacaoExif.de(gravar("sem-exif.jpg", base, "jpg"))).isEqualTo(1);
        Path truncado = pasta.resolve("truncado.jpg");
        Files.write(truncado, Arrays.copyOf(Files.readAllBytes(jpegComOrientacao("o6b.jpg", base, 6)), 20));
        assertThat(OrientacaoExif.de(truncado)).isEqualTo(1);
        assertThat(OrientacaoExif.de(pasta.resolve("nao-existe.jpg"))).isEqualTo(1);
    }

    @Test
    void pngTransparenteFicaSobreFundoBrancoENaoPreto() throws IOException {
        BufferedImage transparente = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB); // todos os pixels 0x00000000
        Path pdf = converter(gravar("transparente.png", transparente, "png"), PNG);

        BufferedImage pagina = renderizar(pdf);
        assertThat(caixaDoConteudo(pagina)[2]).as("nenhum pixel não branco").isEqualTo(-1);
    }

    @Test
    void pngMeioTransparenteMantemOQueEOpacoEClareiaOResto() throws IOException {
        BufferedImage imagem = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(new Color(255, 0, 0, 255));
        g.fillRect(0, 0, 50, 100); // metade esquerda opaca; a direita fica transparente
        g.dispose();
        Path pdf = converter(gravar("meio.png", imagem, "png"), PNG);

        BufferedImage pagina = renderizar(pdf);
        int[] caixa = caixaDoConteudo(pagina);
        assertThat(caixa[2]).isGreaterThan(-1);
        int meioY = (caixa[1] + caixa[3]) / 2;
        assertThat(ehVermelho(pagina.getRGB(caixa[0] + 2, meioY))).isTrue();
        // só a metade opaca (24 de 48 px) é visível; a transparente ficou branca, não preta
        assertThat(caixa[2] - caixa[0] + 1).isBetween(22, 26);
        assertThat(pagina.getRGB(caixa[2] + 10, meioY) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void imagemMuitoLargaCabeNaPaginaSemDistorcer() throws IOException {
        BufferedImage larga = new BufferedImage(6000, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = larga.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 6000, 100);
        g.dispose();
        Path pdf = converter(gravar("larga.png", larga, "png"), PNG);

        PDPage pagina = pagina(pdf);
        assertThat(pagina.getMediaBox().getWidth()).isGreaterThan(pagina.getMediaBox().getHeight());
        int[] caixa = caixaDoConteudo(renderizar(pdf));
        double largura = caixa[2] - caixa[0] + 1;
        double altura = caixa[3] - caixa[1] + 1;
        assertThat(largura).isLessThanOrEqualTo(pagina.getMediaBox().getWidth() - 2 * ConversorDeImagensPdfBox.MARGEM + 2);
        assertThat(largura / altura).isBetween(60 * 0.9, 60 * 1.1);
    }

    @Test
    void imagemPequenaNaoEAmpliadaAlemDe150Dpi() throws IOException {
        Path pdf = converter(gravar("icone.png", comMarca(100, 100, BufferedImage.TYPE_INT_RGB), "png"), PNG);

        int[] caixa = caixaDoConteudo(renderizar(pdf));
        // 100 px a 150 dpi = 48 pontos (renderizado a 72 dpi: 48 px)
        assertThat(caixa[2] - caixa[0] + 1).isBetween(46, 50);
    }

    @Test
    void imagemAcimaDoLimiteDeMegapixelsEFalhaClaraSemDestino() throws IOException {
        ParametrosDaConversao limite = mock(ParametrosDaConversao.class);
        when(limite.imagemMaxMegapixels()).thenReturn(1);
        ConversorDeImagensPdfBox restrito = new ConversorDeImagensPdfBox(limite);
        Path origem = gravar("grande.png", new BufferedImage(2000, 1500, BufferedImage.TYPE_BYTE_GRAY), "png");
        Path destino = pasta.resolve("grande.pdf");

        assertThatThrownBy(() -> restrito.converter(origem, PNG, destino))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.IMAGEM_GRANDE_DEMAIS))
                .hasMessageContaining("limite");
        assertThat(destino).doesNotExist();
    }

    @Test
    void limiteEDecididoPelasDimensoesSemDecodificar() throws IOException {
        // PNG com cabeçalho válido de 100000 x 100000 (10.000 MP) e nenhum dado de pixels: só as dimensões bastam.
        byte[] png = pngSoComCabecalho(100_000, 100_000);
        Path origem = pasta.resolve("gigante.png");
        Files.write(origem, png);

        assertThatThrownBy(() -> conversor.converter(origem, PNG, pasta.resolve("gigante.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.IMAGEM_GRANDE_DEMAIS));
    }

    private static byte[] pngSoComCabecalho(int largura, int altura) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = new byte[13];
        escrever32(ihdr, 0, largura);
        escrever32(ihdr, 4, altura);
        ihdr[8] = 8; // profundidade
        ihdr[9] = 0; // tons de cinza
        byte[] tipo = "IHDR".getBytes(StandardCharsets.US_ASCII);
        byte[] bloco = new byte[4 + ihdr.length];
        System.arraycopy(tipo, 0, bloco, 0, 4);
        System.arraycopy(ihdr, 0, bloco, 4, ihdr.length);
        byte[] tamanho = new byte[4];
        escrever32(tamanho, 0, ihdr.length);
        saida.write(tamanho);
        saida.write(bloco);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bloco);
        byte[] soma = new byte[4];
        escrever32(soma, 0, (int) crc.getValue());
        saida.write(soma);
        return saida.toByteArray();
    }

    private static void escrever32(byte[] destino, int deslocamento, int valor) {
        destino[deslocamento] = (byte) (valor >>> 24);
        destino[deslocamento + 1] = (byte) (valor >>> 16);
        destino[deslocamento + 2] = (byte) (valor >>> 8);
        destino[deslocamento + 3] = (byte) valor;
    }

    @Test
    void arquivoCorrompidoEFalhaClaraSemDestinoNemParcial() throws IOException {
        byte[] lixo = new byte[2000];
        new Random(7).nextBytes(lixo);
        byte[] cabecalhoPng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(cabecalhoPng, 0, lixo, 0, cabecalhoPng.length);
        byte[] cabecalhoJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] lixoJpeg = new byte[2000];
        new Random(9).nextBytes(lixoJpeg);
        System.arraycopy(cabecalhoJpeg, 0, lixoJpeg, 0, cabecalhoJpeg.length);

        for (Path origem : new Path[] {Files.write(pasta.resolve("lixo.png"), lixo),
                Files.write(pasta.resolve("lixo.jpg"), lixoJpeg), Files.write(pasta.resolve("vazio.png"), new byte[0])}) {
            Path destino = pasta.resolve(origem.getFileName() + ".pdf");
            assertThatThrownBy(() -> conversor.converter(origem, PNG, destino))
                    .as(origem.getFileName().toString())
                    .isInstanceOfSatisfying(FalhaDeConversaoException.class, e -> assertThat(e.motivo())
                            .isIn(Motivo.CORROMPIDO, Motivo.FORMATO_NAO_SUPORTADO));
            assertThat(destino).doesNotExist();
            assertThat(pasta.resolve(destino.getFileName() + ".parcial")).doesNotExist();
        }
    }

    @Test
    void pngTruncadoEFalhaClara() throws IOException {
        Path inteiro = gravar("inteiro.png", comMarca(400, 400, BufferedImage.TYPE_INT_RGB), "png");
        byte[] bytes = Files.readAllBytes(inteiro);
        Path truncado = Files.write(pasta.resolve("truncado.png"), Arrays.copyOf(bytes, bytes.length / 2));
        Path destino = pasta.resolve("truncado.pdf");

        assertThatThrownBy(() -> conversor.converter(truncado, PNG, destino))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.CORROMPIDO));
        assertThat(destino).doesNotExist();
    }

    @Test
    void extensaoEnganosaValePeloConteudo() throws IOException {
        Path jpegComNomeDePng = Files.write(pasta.resolve("parece.png"),
                Files.readAllBytes(gravar("real.jpg", comMarca(100, 100, BufferedImage.TYPE_INT_RGB), "jpg")));
        TipoDetectado detectado = DetectorDeTipoReal.detectar(jpegComNomeDePng, "png");

        assertThat(detectado.tipo()).isEqualTo(TipoReal.IMAGEM);
        converter(jpegComNomeDePng, detectado);
        // mesmo com o tipo "png" informado, o conteúdo (JPEG) é que decide
        converter(jpegComNomeDePng, PNG);
    }

    @Test
    void tipoQueNaoEImagemOuFormatoDeImagemSemSuporteEFalha() throws IOException {
        Path png = gravar("a.png", comMarca(10, 10, BufferedImage.TYPE_INT_RGB), "png");
        assertThatThrownBy(() -> conversor.converter(png, new TipoDetectado(TipoReal.PDF, "pdf"), pasta.resolve("x.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.FORMATO_NAO_SUPORTADO));

        Path gif = gravar("a.gif", comMarca(10, 10, BufferedImage.TYPE_INT_RGB), "gif");
        assertThatThrownBy(() -> conversor.converter(gif, PNG, pasta.resolve("y.pdf")))
                .isInstanceOfSatisfying(FalhaDeConversaoException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.FORMATO_NAO_SUPORTADO));
    }

    @Test
    void origemNaoEAlterada() throws IOException {
        Path png = gravar("intacto.png", comMarca(50, 50, BufferedImage.TYPE_INT_RGB), "png");
        byte[] antes = Files.readAllBytes(png);

        converter(png, PNG);

        assertThat(Files.readAllBytes(png)).isEqualTo(antes);
    }
}
