package br.gov.mt.sema.vistas.integration.imagem;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import br.gov.mt.sema.vistas.documento.application.port.ConversorDeImagens;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/**
 * Imagens PNG e JPEG para PDF, só com PDFBox (o LibreOffice não participa). Uma página por imagem, A4 como base, em
 * retrato ou paisagem conforme a proporção da imagem já com a orientação EXIF aplicada, margem de {@value #MARGEM}
 * pontos e sem distorção. A imagem é reduzida para caber, mas nunca ampliada além de 150 dpi (um scan A4 a 150 dpi
 * ocupa a página; um ícone não vira um borrão).
 *
 * <p>O formato é decidido pelo conteúdo (nome e extensão não valem). As dimensões são lidas antes de decodificar:
 * acima de {@code CONVERSAO_IMAGEM_MAX_MEGAPIXELS} a imagem é recusada sem ocupar memória. O JPEG vai para o PDF com
 * os bytes originais (sem recompressão); o PNG é composto sobre fundo branco, para que transparência não vire preto.
 */
@Component
class ConversorDeImagensPdfBox implements ConversorDeImagens {

    static final float MARGEM = 18f;
    /** Pontos por pixel: 72 pontos por polegada ÷ 150 dpi. */
    private static final float PONTOS_POR_PIXEL_MAXIMO = 72f / 150f;

    private final ParametrosDaConversao parametros;

    ConversorDeImagensPdfBox(ParametrosDaConversao parametros) {
        this.parametros = parametros;
    }

    @Override
    public ResultadoDaConversao converter(Path origem, TipoDetectado tipo, Path destinoPdf) {
        if (tipo == null || tipo.tipo() != TipoReal.IMAGEM) {
            throw new FalhaDeConversaoException(Motivo.FORMATO_NAO_SUPORTADO);
        }
        Path parcial = destinoPdf.resolveSibling(destinoPdf.getFileName() + ".parcial");
        try {
            gerar(origem, parcial);
            Files.move(parcial, destinoPdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            apagar(parcial);
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO, e);
        } catch (RuntimeException | Error e) {
            apagar(parcial);
            throw e;
        }
        return new ResultadoDaConversao(1, 0);
    }

    private void gerar(Path origem, Path destino) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(origem.toFile())) {
            if (entrada == null) {
                throw new FalhaDeConversaoException(Motivo.CORROMPIDO);
            }
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new FalhaDeConversaoException(Motivo.FORMATO_NAO_SUPORTADO);
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                String formato = leitor.getFormatName().toLowerCase(java.util.Locale.ROOT);
                boolean jpeg = formato.equals("jpeg") || formato.equals("jpg");
                if (!jpeg && !formato.equals("png")) {
                    throw new FalhaDeConversaoException(Motivo.FORMATO_NAO_SUPORTADO);
                }
                int largura = leitor.getWidth(0);
                int altura = leitor.getHeight(0);
                conferirLimite(largura, altura);
                int orientacao = jpeg ? OrientacaoExif.de(origem) : OrientacaoExif.SEM_ROTACAO;
                // No JPEG a decodificação só valida; o PDF leva os bytes originais.
                BufferedImage imagem = decodificar(leitor, jpeg);
                try (PDDocument documento = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
                    PDImageXObject pdImagem = jpeg
                            ? JPEGFactory.createFromByteArray(documento, Files.readAllBytes(origem))
                            : LosslessFactory.createFromImage(documento, sobreFundoBranco(imagem));
                    desenhar(documento, pdImagem, largura, altura, orientacao);
                    documento.save(destino.toFile());
                }
            } finally {
                leitor.dispose();
            }
        }
    }

    private void conferirLimite(int largura, int altura) {
        long megapixels = (long) largura * altura / 1_000_000L;
        if (largura <= 0 || altura <= 0) {
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO);
        }
        if (megapixels > parametros.imagemMaxMegapixels()) {
            throw new FalhaDeConversaoException(Motivo.IMAGEM_GRANDE_DEMAIS);
        }
    }

    /**
     * Decodifica para validar a imagem (e, no PNG, para usar os pixels). JPEG em CMYK ou outro espaço que o ImageIO
     * não entrega como imagem ainda é válido: vale se o raster for lido. Para o JPEG o resultado é descartado e o
     * arquivo original é que vai para o PDF.
     */
    private static BufferedImage decodificar(ImageReader leitor, boolean jpeg) {
        try {
            return leitor.read(0);
        } catch (IIOException e) {
            if (jpeg) {
                try {
                    leitor.readRaster(0, null);
                    return null;
                } catch (IOException | RuntimeException outra) {
                    throw new FalhaDeConversaoException(Motivo.CORROMPIDO, outra);
                }
            }
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO, e);
        } catch (IOException | RuntimeException e) {
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO, e);
        } catch (OutOfMemoryError e) {
            throw new FalhaDeConversaoException(Motivo.IMAGEM_GRANDE_DEMAIS, e);
        }
    }

    private static BufferedImage sobreFundoBranco(BufferedImage origem) {
        BufferedImage plano = new BufferedImage(origem.getWidth(), origem.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = plano.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, plano.getWidth(), plano.getHeight());
            g.drawImage(origem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return plano;
    }

    private static void desenhar(PDDocument documento, PDImageXObject imagem, int largura, int altura,
            int orientacao) throws IOException {
        boolean trocaEixos = orientacao >= 5;
        int exibidaLargura = trocaEixos ? altura : largura;
        int exibidaAltura = trocaEixos ? largura : altura;
        PDRectangle base = exibidaLargura > exibidaAltura
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;
        float areaLargura = base.getWidth() - 2 * MARGEM;
        float areaAltura = base.getHeight() - 2 * MARGEM;
        float escala = Math.min(Math.min(areaLargura / exibidaLargura, areaAltura / exibidaAltura),
                PONTOS_POR_PIXEL_MAXIMO);
        float caixaLargura = exibidaLargura * escala;
        float caixaAltura = exibidaAltura * escala;
        float origemX = (base.getWidth() - caixaLargura) / 2f;
        float origemY = (base.getHeight() - caixaAltura) / 2f;

        // (u, v) do espaço da imagem no PDF (origem embaixo) -> pixel (x, y) -> posição exibida -> página.
        AffineTransform paraPixel = new AffineTransform(1, 0, 0, -1, 0, altura);
        AffineTransform paraPagina = new AffineTransform(escala, 0, 0, -escala, origemX, origemY + caixaAltura);
        AffineTransform total = new AffineTransform();
        total.concatenate(paraPagina);
        total.concatenate(orientacaoExif(orientacao, largura, altura));
        total.concatenate(paraPixel);

        PDPage pagina = new PDPage(base);
        documento.addPage(pagina);
        try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
            conteudo.transform(new Matrix((float) total.getScaleX(), (float) total.getShearY(),
                    (float) total.getShearX(), (float) total.getScaleY(), (float) total.getTranslateX(),
                    (float) total.getTranslateY()));
            conteudo.drawImage(imagem, 0, 0, largura, altura);
        }
    }

    /** Pixel (x, y) da imagem gravada para a posição (x, y) com que ela deve ser vista (EXIF 1 a 8). */
    private static AffineTransform orientacaoExif(int orientacao, int w, int h) {
        return switch (orientacao) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorada) {
            // melhor esforço: o diretório do pedido é apagado ao fim
        }
    }
}
