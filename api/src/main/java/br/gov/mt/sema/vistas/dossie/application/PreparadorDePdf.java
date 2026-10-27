package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import br.gov.mt.sema.vistas.documento.application.port.ConversorDeDocumentos;
import br.gov.mt.sema.vistas.documento.application.port.ConversorDeImagens;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.vistas.documento.domain.DetectorDeTipoReal;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.ValidadorDePdf;
import org.springframework.stereotype.Service;

/**
 * Transforma o arquivo obtido no PDF que entra no dossiê (S3), pelo <b>tipo real</b> (conteúdo), não pelo nome:
 *
 * <ul>
 *   <li>PDF: vai como está, depois da validação de sempre;</li>
 *   <li>planilha, texto e HTML: conversor de documentos (LibreOffice); imagem: conversor de imagens (PDFBox);</li>
 *   <li>compactado: falha do documento (RN11);</li>
 *   <li>não reconhecido: vai à validação de PDF, que aceita PDF com bytes antes da assinatura e recusa o resto.</li>
 * </ul>
 *
 * <p>Falha de conversão é falha do documento (S2.2: coletada, decisão do usuário), com o motivo do conversor. Com
 * {@code CONVERSAO_HABILITADA=false}, vale a S2.2: o que não é PDF falha. O original não é alterado nem apagado
 * pela conversão: o gerador o guarda até ela dar certo, para a retomada não baixá-lo de novo. Quando já é PDF, o
 * original é movido para o destino.
 */
@Service
public class PreparadorDePdf {

    static final String CONVERSAO_DESLIGADA = "o arquivo não é um PDF (tipo detectado: %s) e a conversão está desligada";
    static final String COMPACTADO = "arquivo compactado (%s): não entra no dossiê";

    private final ValidadorDePdf validador;
    private final ConversorDeDocumentos documentos;
    private final ConversorDeImagens imagens;
    private final ParametrosDaConversao conversao;

    public PreparadorDePdf(ValidadorDePdf validador, ConversorDeDocumentos documentos, ConversorDeImagens imagens,
            ParametrosDaConversao conversao) {
        this.validador = validador;
        this.documentos = documentos;
        this.imagens = imagens;
        this.conversao = conversao;
    }

    /**
     * O PDF pronto para a montagem.
     *
     * @param convertidoDe formato de origem em maiúsculas (ex.: "XLSX"), ou nulo quando já era PDF
     */
    public record PdfPreparado(int paginas, String convertidoDe, int abasEmPaginaUnica) {
    }

    /**
     * Prepara {@code original} em {@code pdf}.
     *
     * @param extensaoDoNome extensão do nome do arquivo; só desempata XLS e DOC antigos (mesmo contêiner OLE2)
     * @throws FalhaNoDocumentoException com o motivo exibido ao usuário
     */
    public PdfPreparado preparar(Path original, String extensaoDoNome, Path pdf) {
        TipoDetectado tipo = DetectorDeTipoReal.detectar(original, extensaoDoNome);
        return switch (tipo.tipo()) {
            case PLANILHA, TEXTO -> converter(tipo, () -> documentos.converter(original, tipo, pdf));
            case IMAGEM -> converter(tipo, () -> imagens.converter(original, tipo, pdf));
            case COMPACTADO -> throw new FalhaNoDocumentoException(
                    COMPACTADO.formatted(tipo.extensaoSugerida().toUpperCase(Locale.ROOT)), 0);
            case PDF, DESCONHECIDO -> new PdfPreparado(validarComoPdf(original, pdf), null, 0);
        };
    }

    private PdfPreparado converter(TipoDetectado tipo, Conversao conversao) {
        if (!this.conversao.habilitada()) {
            throw new FalhaNoDocumentoException(CONVERSAO_DESLIGADA.formatted(nome(tipo)), 0);
        }
        try {
            ResultadoDaConversao resultado = conversao.executar();
            return new PdfPreparado(resultado.paginas(), tipo.extensaoSugerida().toUpperCase(Locale.ROOT),
                    resultado.abasEmPaginaUnica());
        } catch (FalhaDeConversaoException e) {
            throw new FalhaNoDocumentoException(e.getMessage(), 0);
        }
    }

    /** O original passa a ser o PDF (movido, sem cópia) e vai à validação de sempre (assinatura, senha, páginas). */
    private int validarComoPdf(Path original, Path pdf) {
        try {
            Files.move(original, pdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FalhaNoDocumentoException("não foi possível ler o arquivo obtido", 0);
        }
        return validador.validar(pdf);
    }

    /** Ex.: "planilha XLSX", "texto HTML", "imagem PNG". */
    private static String nome(TipoDetectado tipo) {
        String familia = switch (tipo.tipo()) {
            case PLANILHA -> "planilha";
            case IMAGEM -> "imagem";
            default -> "texto";
        };
        return familia + " " + tipo.extensaoSugerida().toUpperCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface Conversao {
        ResultadoDaConversao executar();
    }
}
