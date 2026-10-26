package br.gov.mt.sema.vistas.dossie.infrastructure.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.DocumentoNaoIncluido;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Uma montagem de dossiê com PDFBox 3 (uma instância por geração: as fontes não são compartilhadas entre threads) (docs/06). Páginas dos documentos entram por {@code importPage} (tamanho, rotação,
 * anotações e links preservados; nada é forçado para A4, RNF06). Tudo vive em arquivos temporários: os documentos de
 * origem são abertos com cache em disco e ficam abertos até o PDF final ser salvo (o PDFBox compartilha os objetos
 * entre origem e destino), mas só um dossiê por thread de geração. Medido com 20 PDFs de ~48 MB: pico de heap de
 * ~130 MB, inclusive com -Xmx256m, sem precisar montar em lotes (docs/10, R26; DesempenhoDaMontagemTest).
 *
 * Capa e sumário são páginas A4 desenhadas aqui; o sumário tem paginação própria (linhas fixas por página), então a
 * página inicial de cada documento é determinística e conhecida antes de importar qualquer página.
 */

class MontagemPdfBox implements MontadorDoDossie {

    private static final Logger log = LoggerFactory.getLogger(MontadorPdfBox.class);

    static final int LINHAS_POR_PAGINA_DO_SUMARIO = 38;
    /** Cada documento não incluído ocupa duas linhas (dados e motivo). */
    static final int ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS = 22;
    static final String TITULO_DOS_NAO_INCLUIDOS = "Documentos não incluídos";
    private static final float TAMANHO_DO_RODAPE = 9f;
    private static final float MARGEM_DO_RODAPE = 20f;
    private static final float ALTURA_DA_LINHA = 16f;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final String SEM_DATA = "sem data";
    static final String NOTA_SEM_DATA =
            "Documentos \"sem data\" não têm data registrada na origem e aparecem ao final, por origem e id.";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Override
    public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
            OpcoesDeMontagem opcoes, Path destino) {
        List<PDDocument> abertos = new ArrayList<>();
        try (PDDocument saida = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            PDPage paginaDaCapa = paginaA4(saida);
            desenharCapa(saida, paginaDaCapa, capa, documentos.size(), inventario.listados());

            int paginasDoSumario = opcoes.sumario() ? Math.max(1, ceilDiv(documentos.size(), LINHAS_POR_PAGINA_DO_SUMARIO)) : 0;
            List<PDPage> paginasDoSumarioA4 = new ArrayList<>();
            for (int i = 0; i < paginasDoSumario; i++) {
                paginasDoSumarioA4.add(paginaA4(saida));
            }
            // "Documentos não incluídos" logo após o sumário (omitida se vazia), com paginação própria.
            List<DocumentoNaoIncluido> fora = inventario.naoIncluidos();
            int paginasDosNaoIncluidos = ceilDiv(fora.size(), ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS);
            List<PDPage> paginasDosNaoIncluidosA4 = new ArrayList<>();
            for (int i = 0; i < paginasDosNaoIncluidos; i++) {
                paginasDosNaoIncluidosA4.add(paginaA4(saida));
            }
            // Número (1 = capa) da primeira página de cada documento.
            int[] inicio = new int[documentos.size()];
            int proxima = 2 + paginasDoSumario + paginasDosNaoIncluidos;
            for (int i = 0; i < documentos.size(); i++) {
                inicio[i] = proxima;
                proxima += documentos.get(i).paginas();
            }

            for (DocumentoParaMontagem documento : documentos) {
                importar(saida, documento, abertos);
            }

            if (opcoes.sumario()) {
                desenharSumario(saida, paginasDoSumarioA4, documentos, inicio);
            }
            if (!fora.isEmpty()) {
                desenharNaoIncluidos(saida, paginasDosNaoIncluidosA4, fora);
            }
            if (opcoes.marcadores()) {
                criarMarcadores(saida, documentos, inicio, paginasDoSumario > 0,
                        fora.isEmpty() ? null : paginasDosNaoIncluidosA4.get(0));
            }
            if (opcoes.numeracao()) {
                numerar(saida);
            }
            saida.save(destino.toFile());
            return saida.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao montar o PDF do dossiê", e);
        } finally {
            for (PDDocument aberto : abertos) {
                try {
                    aberto.close();
                } catch (IOException e) {
                    log.debug("Falha ao fechar um documento de origem: {}", e.getMessage());
                }
            }
        }
    }

    private PDPage paginaA4(PDDocument saida) {
        PDPage pagina = new PDPage(PDRectangle.A4);
        saida.addPage(pagina);
        return pagina;
    }

    // --- documentos

    private void importar(PDDocument saida, DocumentoParaMontagem documento, List<PDDocument> abertos) throws IOException {
        PDDocument origem;
        try {
            origem = Loader.loadPDF(documento.arquivo().toFile(), "", null, null, IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException e) {
            throw new FalhaNoDocumentoException("o arquivo está corrompido", e);
        }
        abertos.add(origem);
        achatarFormulario(origem);
        int importadas = 0;
        for (PDPage pagina : origem.getPages()) {
            PDPage nova = saida.importPage(pagina);
            // importPage não herda do nó pai: copia explicitamente o que a página usa (como o PDFMergerUtility).
            nova.setMediaBox(pagina.getMediaBox());
            nova.setCropBox(pagina.getCropBox());
            nova.setRotation(pagina.getRotation());
            nova.setResources(pagina.getResources());
            soltarReferenciaAPaginaDeOrigem(nova);
            importadas++;
        }
        if (importadas != documento.paginas()) {
            throw new IllegalStateException("O documento mudou de tamanho entre a validação e a montagem");
        }
    }

    /** Formulários interativos são achatados para não colidir nomes de campo entre documentos; falha não interrompe. */
    private void achatarFormulario(PDDocument origem) {
        try {
            PDAcroForm formulario = origem.getDocumentCatalog().getAcroForm();
            if (formulario != null) {
                formulario.flatten();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Formulário interativo não pôde ser achatado; os campos ficam como anotações: {}", e.getMessage());
        }
    }

    /** O /P de uma anotação aponta para a página da origem; mantê-lo arrastaria a página antiga para o PDF final. */
    private void soltarReferenciaAPaginaDeOrigem(PDPage pagina) {
        try {
            for (PDAnnotation anotacao : pagina.getAnnotations()) {
                anotacao.getCOSObject().removeItem(COSName.P);
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Anotações não tratadas: {}", e.getMessage());
        }
    }

    // --- capa

    private void desenharCapa(PDDocument saida, PDPage pagina, DadosDaCapa capa, int incluidos, int listados)
            throws IOException {
        float x = 60f;
        float largura = pagina.getMediaBox().getWidth() - 2 * x;
        try (PDPageContentStream c = new PDPageContentStream(saida, pagina)) {
            float y = 760f;
            y = texto(c, "Vistas do Processo", negrito, 10f, x, y, 18f);
            y -= 6f;
            for (String linha : TextoPdf.quebrar(negrito, capa.titulo(), 22f, largura)) {
                y = texto(c, linha, negrito, 22f, x, y, 28f);
            }
            y -= 8f;
            c.setLineWidth(1f);
            c.moveTo(x, y);
            c.lineTo(x + largura, y);
            c.stroke();
            y -= 26f;

            y = campo(c, "Interessado", capa.interessado(), x, y, largura);
            y = campo(c, "CPF/CNPJ", capa.cpfCnpj(), x, y, largura);
            y = campo(c, "Tipo de processo", capa.tipoProcesso(), x, y, largura);
            y = campo(c, "Situação", capa.situacao(), x, y, largura);
            y = campo(c, "Data de abertura", capa.abertura() == null ? null : capa.abertura().format(DATA), x, y, largura);
            y = campo(c, "Documentos neste dossiê", incluidosDeListados(incluidos, listados), x, y, largura);
            y -= 6f;
            y = lista(c, "Objetivos", capa.objetivos(), x, y, largura);
            y = lista(c, "Finalidades e atividades", capa.atividades(), x, y, largura);

            String geracao = "Gerado em " + capa.geradoEm().format(DATA_HORA) + " por " + (capa.usuario() == null ? "-" : capa.usuario());
            texto(c, TextoPdf.cortar(normal, geracao, 9f, largura), normal, 9f, x, 50f, 0f);
        }
    }

    private float campo(PDPageContentStream c, String rotulo, String valor, float x, float y, float largura) throws IOException {
        y = texto(c, rotulo, normal, 9f, x, y, 12f);
        List<String> linhas = TextoPdf.quebrar(negrito, valor == null || valor.isBlank() ? "-" : valor, 12f, largura);
        for (String linha : linhas.subList(0, Math.min(2, linhas.size()))) {
            y = texto(c, linha, negrito, 12f, x, y, 16f);
        }
        return y - 6f;
    }

    private float lista(PDPageContentStream c, String titulo, List<String> itens, float x, float y, float largura) throws IOException {
        if (itens.isEmpty() || y < 140f) {
            return y;
        }
        y = texto(c, titulo, normal, 9f, x, y, 13f);
        int maximo = 7;
        for (int i = 0; i < Math.min(maximo, itens.size()); i++) {
            if (y < 90f) {
                break;
            }
            y = texto(c, TextoPdf.cortar(negrito, "- " + itens.get(i), 11f, largura), negrito, 11f, x, y, 15f);
        }
        if (itens.size() > maximo) {
            y = texto(c, "... e mais " + (itens.size() - maximo), normal, 10f, x, y, 15f);
        }
        return y - 8f;
    }

    /** Escreve uma linha na posição (x, y) e devolve o y da linha seguinte. */
    private float texto(PDPageContentStream c, String linha, PDType1Font fonte, float tamanho, float x, float y,
            float avanco) throws IOException {
        c.beginText();
        c.setFont(fonte, tamanho);
        c.newLineAtOffset(x, y);
        c.showText(TextoPdf.seguro(fonte, linha));
        c.endText();
        return y - avanco;
    }

    // --- sumário

    private void desenharSumario(PDDocument saida, List<PDPage> paginas, List<DocumentoParaMontagem> documentos,
            int[] inicio) throws IOException {
        float colData = 50f;
        float colTitulo = 120f;
        float colOrigem = 365f;
        float colPaginaDireita = 545f;
        for (int p = 0; p < paginas.size(); p++) {
            PDPage pagina = paginas.get(p);
            try (PDPageContentStream c = new PDPageContentStream(saida, pagina)) {
                float y = 790f;
                y = texto(c, "Sumário", negrito, 16f, colData, y, 26f);
                texto(c, "Data", negrito, 9f, colData, y, 0f);
                texto(c, "Documento", negrito, 9f, colTitulo, y, 0f);
                texto(c, "Origem", negrito, 9f, colOrigem, y, 0f);
                String cabecalhoPagina = "Pág.";
                texto(c, cabecalhoPagina, negrito, 9f, colPaginaDireita - TextoPdf.largura(negrito, cabecalhoPagina, 9f), y, 0f);
                y -= 20f;
                int primeiro = p * LINHAS_POR_PAGINA_DO_SUMARIO;
                int ultimo = Math.min(documentos.size(), primeiro + LINHAS_POR_PAGINA_DO_SUMARIO);
                for (int i = primeiro; i < ultimo; i++) {
                    DocumentoParaMontagem d = documentos.get(i);
                    LocalDateTime data = d.data();
                    texto(c, data == null ? SEM_DATA : data.format(DATA), normal, 9f, colData, y, 0f);
                    texto(c, TextoPdf.cortar(normal, d.titulo(), 9f, colOrigem - colTitulo - 8f), normal, 9f, colTitulo, y, 0f);
                    texto(c, TextoPdf.cortar(normal, d.origem(), 9f, colPaginaDireita - 40f - colOrigem), normal, 9f, colOrigem, y, 0f);
                    String numero = String.valueOf(inicio[i]);
                    texto(c, numero, normal, 9f, colPaginaDireita - TextoPdf.largura(normal, numero, 9f), y, 0f);
                    y -= ALTURA_DA_LINHA;
                }
                // A nota fica no rodapé da última página do sumário: não ocupa linha, então a paginação não muda.
                if (p == paginas.size() - 1 && documentos.stream().anyMatch(d -> d.data() == null)) {
                    texto(c, NOTA_SEM_DATA, normal, 8f, colData, 60f, 0f);
                }
            }
            adicionarLinks(saida, pagina, primeiro(p), Math.min(documentos.size(), primeiro(p) + LINHAS_POR_PAGINA_DO_SUMARIO), inicio);
        }
    }

    private static int primeiro(int paginaDoSumario) {
        return paginaDoSumario * LINHAS_POR_PAGINA_DO_SUMARIO;
    }

    /** Cada linha do sumário leva à primeira página do documento. */
    private void adicionarLinks(PDDocument saida, PDPage pagina, int primeiro, int ultimo, int[] inicio) throws IOException {
        float y = 790f - 26f - 20f;
        for (int i = primeiro; i < ultimo; i++) {
            PDPageFitDestination destino = new PDPageFitDestination();
            destino.setPage(saida.getPage(inicio[i] - 1));
            PDActionGoTo acao = new PDActionGoTo();
            acao.setDestination(destino);
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(50f, y - 3f, 495f, ALTURA_DA_LINHA));
            PDBorderStyleDictionary borda = new PDBorderStyleDictionary();
            borda.setWidth(0);
            link.setBorderStyle(borda);
            link.setAction(acao);
            pagina.getAnnotations().add(link);
            y -= ALTURA_DA_LINHA;
        }
    }

    // --- documentos não incluídos (S2.2)

    /** "N documentos incluídos de M listados"; sem inventário (M menor que N), M = N. */
    static String incluidosDeListados(int incluidos, int listados) {
        int total = Math.max(incluidos, listados);
        return incluidos + (incluidos == 1 ? " documento incluído" : " documentos incluídos") + " de " + total
                + (total == 1 ? " listado" : " listados");
    }

    /**
     * Uma entrada por documento fora do dossiê, em duas linhas (data, título, origem e id; depois o motivo), com
     * {@link #ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS} por página: o número de páginas é conhecido antes de desenhar.
     */
    private void desenharNaoIncluidos(PDDocument saida, List<PDPage> paginas, List<DocumentoNaoIncluido> fora)
            throws IOException {
        float colData = 50f;
        float colTitulo = 120f;
        float colOrigem = 365f;
        float colId = 545f;
        for (int p = 0; p < paginas.size(); p++) {
            try (PDPageContentStream c = new PDPageContentStream(saida, paginas.get(p))) {
                float y = 790f;
                y = texto(c, TITULO_DOS_NAO_INCLUIDOS, negrito, 16f, colData, y, 20f);
                y = texto(c, "Documentos listados no requerimento que não fazem parte deste dossiê, e o motivo.",
                        normal, 9f, colData, y, 20f);
                texto(c, "Data", negrito, 9f, colData, y, 0f);
                texto(c, "Documento", negrito, 9f, colTitulo, y, 0f);
                texto(c, "Origem", negrito, 9f, colOrigem, y, 0f);
                texto(c, "Id", negrito, 9f, colId - TextoPdf.largura(negrito, "Id", 9f), y, 0f);
                y -= 20f;
                int primeiro = p * ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS;
                int ultimo = Math.min(fora.size(), primeiro + ITENS_POR_PAGINA_DOS_NAO_INCLUIDOS);
                for (int i = primeiro; i < ultimo; i++) {
                    DocumentoNaoIncluido d = fora.get(i);
                    texto(c, d.data() == null ? SEM_DATA : d.data().format(DATA), normal, 9f, colData, y, 0f);
                    texto(c, TextoPdf.cortar(normal, d.titulo(), 9f, colOrigem - colTitulo - 8f), normal, 9f, colTitulo, y, 0f);
                    String id = TextoPdf.cortar(normal, d.id(), 9f, 60f);
                    texto(c, TextoPdf.cortar(normal, d.origem(), 9f, colId - 70f - colOrigem), normal, 9f, colOrigem, y, 0f);
                    texto(c, id, normal, 9f, colId - TextoPdf.largura(normal, id, 9f), y, 0f);
                    y -= 12f;
                    texto(c, TextoPdf.cortar(normal, "Motivo: " + d.motivo(), 8f, colId - colTitulo), normal, 8f,
                            colTitulo, y, 0f);
                    y -= 16f;
                }
            }
        }
    }

    // --- marcadores

    private void criarMarcadores(PDDocument saida, List<DocumentoParaMontagem> documentos, int[] inicio, boolean comSumario,
            PDPage naoIncluidos) throws IOException {
        PDDocumentOutline contorno = new PDDocumentOutline();
        saida.getDocumentCatalog().setDocumentOutline(contorno);
        contorno.addLast(item("Capa", saida.getPage(0)));
        if (comSumario) {
            contorno.addLast(item("Sumário", saida.getPage(1)));
        }
        if (naoIncluidos != null) {
            contorno.addLast(item(TITULO_DOS_NAO_INCLUIDOS, naoIncluidos));
        }
        for (int i = 0; i < documentos.size(); i++) {
            contorno.addLast(item(documentos.get(i).titulo(), saida.getPage(inicio[i] - 1)));
        }
        contorno.openNode();
    }

    private PDOutlineItem item(String titulo, PDPage pagina) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(titulo == null || titulo.isBlank() ? "Documento" : titulo);
        PDPageFitDestination destino = new PDPageFitDestination();
        destino.setPage(pagina);
        item.setDestination(destino);
        return item;
    }

    // --- numeração

    /** "Página X de Y" a partir da primeira página após a capa; X é o número físico (a capa é a página 1, sem número). */
    private void numerar(PDDocument saida) throws IOException {
        int total = saida.getNumberOfPages();
        for (int i = 1; i < total; i++) {
            rodape(saida, saida.getPage(i), "Página " + (i + 1) + " de " + total);
        }
    }

    /**
     * O rodapé fica no centro da borda inferior <em>como a página é exibida</em>: a caixa usada é a de recorte e a
     * posição e o sentido do texto acompanham a rotação, em vez de uma coordenada fixa.
     */
    void rodape(PDDocument saida, PDPage pagina, String texto) throws IOException {
        PDRectangle caixa = pagina.getCropBox();
        float x0 = caixa.getLowerLeftX();
        float y0 = caixa.getLowerLeftY();
        float x1 = caixa.getUpperRightX();
        float y1 = caixa.getUpperRightY();
        float cx = (x0 + x1) / 2f;
        float cy = (y0 + y1) / 2f;
        String seguro = TextoPdf.seguro(normal, texto);
        float largura = TextoPdf.largura(normal, seguro, TAMANHO_DO_RODAPE);
        int rotacao = ((pagina.getRotation() % 360) + 360) % 360;
        Matrix matriz = switch (rotacao) {
            case 90 -> Matrix.getRotateInstance(Math.toRadians(90), x1 - MARGEM_DO_RODAPE, cy - largura / 2f);
            case 180 -> Matrix.getRotateInstance(Math.toRadians(180), cx + largura / 2f, y1 - MARGEM_DO_RODAPE);
            case 270 -> Matrix.getRotateInstance(Math.toRadians(270), x0 + MARGEM_DO_RODAPE, cy + largura / 2f);
            default -> Matrix.getTranslateInstance(cx - largura / 2f, y0 + MARGEM_DO_RODAPE);
        };
        try (PDPageContentStream c = new PDPageContentStream(saida, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
            c.beginText();
            c.setFont(normal, TAMANHO_DO_RODAPE);
            c.setTextMatrix(matriz);
            c.showText(seguro);
            c.endText();
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
