package br.gov.mt.sema.rastro.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.rastro.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Conversor;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Montador;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.rastro.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.rastro.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.StatusDossie;
import br.gov.mt.sema.rastro.testsupport.ParametrosDeConversaoDeTeste;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Office detectado pelo conteúdo quando o tipo não foi informado (V6, bloco 1): planilha e texto antigos sem extensão
 * no nome são convertidos em vez de falhar. Arquivos sintéticos gerados aqui; conversores falsos.
 */
class OfficeSemTipoNoPipelineTest {

    @TempDir
    Path pasta;

    private Arquivos arquivos;
    private Montador montador;
    private Conversor conversor;
    private Parametros parametros;
    private RelogioControlado relogio;

    @BeforeEach
    void preparar() throws IOException {
        arquivos = new Arquivos();
        montador = new Montador();
        conversor = new Conversor();
        parametros = new Parametros(pasta);
        relogio = new RelogioControlado();
        arquivos.comportamentos.put("K-XLS", destino -> escrever(destino, xls()));
        arquivos.comportamentos.put("K-DOC", destino -> escrever(destino, doc()));
        arquivos.comportamentos.put("K-XLSX", destino -> escrever(destino, ooxml("xl/workbook.xml")));
        arquivos.comportamentos.put("K-DOCX", destino -> escrever(destino, ooxml("word/document.xml")));
        arquivos.comportamentos.put("K-ZIP", destino -> escrever(destino, ooxml("pasta/qualquer.txt")));
        arquivos.comportamentos.put("K-XLS-TRUNCADO", destino -> escrever(destino, Arrays.copyOf(xls(), 600)));
        arquivos.comportamentos.put("K-LIXO", destino -> escrever(destino, "lixo qualquer".getBytes()));
    }

    private GeradorDeDossie gerador() {
        PreparadorDePdf preparador = new PreparadorDePdf(arquivo -> {
            if (!Files.exists(arquivo) || !conteudo(arquivo).startsWith("paginas=")) {
                // Como o ValidadorPdfBox real: o que não é PDF falha com o tipo detectado.
                throw new FalhaNoDocumentoException("o arquivo não é um PDF (tipo detectado: documento do Office antigo (doc, xls))");
            }
            return Integer.parseInt(conteudo(arquivo).substring("paginas=".length()));
        }, conversor, conversor, ParametrosDeConversaoDeTeste.ligada());
        return new GeradorDeDossie(arquivos, preparador, montador, parametros, d -> Long.MAX_VALUE, relogio, Fakes.eventos());
    }

    private PedidoDeDossie pedido(ItemDoPedido... itens) {
        return Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30, itens);
    }

    @Test
    void xlsSemExtensaoESemTipoConverteESaiComoConvertido() {
        PedidoDeDossie pedido = pedido(item("1", "Pivôs R1", "K-PDF", "", 1), item("2", "Poços R2", "K-XLS", "", 2));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).containsExactly("xls");
        List<DocumentoParaMontagem> montados = montador.documentosMontados.get(0);
        assertThat(montados).extracting(DocumentoParaMontagem::titulo).containsExactly("Pivôs R1", "Poços R2");
        assertThat(montados).extracting(DocumentoParaMontagem::convertidoDe).containsExactly(null, "XLS");
        assertThat(montados.get(1).nota()).isEqualTo("convertido de XLS");
        assertThat(pedido.itens().get(1).convertidoDe()).isEqualTo("XLS");
    }

    @Test
    void docXlsxEDocxSemTipoTambemConvertem() {
        PedidoDeDossie pedido = pedido(item("1", "Texto antigo", "K-DOC", "", 1), item("2", "Planilha nova", "K-XLSX", "", 2),
                item("3", "Texto novo", "K-DOCX", "", 3));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).containsExactly("doc", "xlsx", "docx");
    }

    @Test
    void extensaoQueNaoEDeOfficeNoNomeTambemUsaOConteudo() {
        // "Poços R1_V1.1": a "extensão" do nome é "1".
        PedidoDeDossie pedido = pedido(item("1", "Poços R1_V1.1", "K-XLS", "1", 1));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).containsExactly("xls");
    }

    @Test
    void pdfValidoSemTipoPassaSemConversao() {
        PedidoDeDossie pedido = pedido(item("1", "Requerimento", "K-PDF", "", 1));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).isEmpty();
        assertThat(montador.documentosMontados.get(0)).extracting(DocumentoParaMontagem::convertidoDe).containsExactly((String) null);
    }

    @Test
    void desconhecidoEZipSemTipoContinuamFalhandoComAMensagemAtual() {
        PedidoDeDossie pedido = pedido(item("1", "Lixo", "K-LIXO", "", 1), item("2", "Pacote", "K-ZIP", "", 2));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.falhas()).extracting(ItemDoPedido::id).containsExactly("1", "2");
        assertThat(pedido.falhas().get(1).motivo()).isEqualTo("arquivo compactado (ZIP): não entra no dossiê");
        assertThat(conversor.convertidos).isEmpty();
    }

    @Test
    void xlsCorrompidoVaiParaNaoObtidosENaoEntregaSemDecisao() {
        // (a) estrutura ilegível: não dá para afirmar o tipo, falha como antes.
        // (b) estrutura de planilha, mas o conversor recusa o conteúdo.
        conversor.falhas.put("xls", Motivo.CORROMPIDO);
        PedidoDeDossie pedido = pedido(item("1", "Requerimento", "K-PDF", "", 1), item("2", "Truncado", "K-XLS-TRUNCADO", "", 2),
                item("3", "Planilha ruim", "K-XLS", "", 3));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.falhas()).extracting(ItemDoPedido::id).containsExactly("2", "3");
        assertThat(pedido.falhas().get(0).motivo()).startsWith("o arquivo não é um PDF");
        assertThat(montador.documentosMontados).isEmpty();
    }

    @Test
    void tipoInformadoComoPdfComConteudoOfficeContinuaFalhando() {
        // Comportamento preservado: extensão declarada "pdf" não é revista, o arquivo vai à validação de PDF e falha.
        PedidoDeDossie pedido = pedido(item("1", "Declarado PDF", "K-XLS", "pdf", 1));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.falhas().get(0).motivo()).startsWith("o arquivo não é um PDF (tipo detectado:");
        assertThat(conversor.convertidos).isEmpty();
    }

    @Test
    void nomeComExtensaoXlsContinuaSaindoIgual() {
        PedidoDeDossie pedido = pedido(item("1", "Planilha antiga", "K-XLS", "xls", 1));

        gerador().processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).containsExactly("xls");
        assertThat(pedido.itens().get(0).convertidoDe()).isEqualTo("XLS");
    }

    // ---- apoio ----

    private static ItemDoPedido item(String id, String titulo, String chave, String extensao, int dia) {
        return new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", id, titulo,
                LocalDateTime.of(2025, 4, dia, 10, 0), chave, dia - 1, extensao);
    }

    static byte[] xls() {
        try (HSSFWorkbook livro = new HSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            livro.createSheet("Dados").createRow(0).createCell(0).setCellValue("valor fictício");
            livro.write(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] doc() {
        try (POIFSFileSystem fs = new POIFSFileSystem(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            fs.createDocument(new java.io.ByteArrayInputStream(new byte[1024]), "WordDocument");
            fs.writeFilesystem(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] ooxml(String entrada) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entrada));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static String conteudo(Path arquivo) {
        try {
            byte[] bytes = Files.readAllBytes(arquivo);
            return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void escrever(Path destino, byte[] conteudo) {
        try {
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
