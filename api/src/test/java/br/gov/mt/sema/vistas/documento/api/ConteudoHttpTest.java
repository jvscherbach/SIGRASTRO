package br.gov.mt.sema.vistas.documento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import br.gov.mt.sema.vistas.documento.application.CacheDeVisualizacao;
import br.gov.mt.sema.vistas.documento.application.port.ConversorDeDocumentos;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaVisualizacao;
import br.gov.mt.sema.vistas.documento.application.port.ResultadoDaConversao;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException;
import br.gov.mt.sema.vistas.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.vistas.testsupport.PdfsDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Contrato de GET /api/documentos/{origem}/{id}/conteudo (S3, docs/07). Arquivos e conversor de documentos falsos;
 * imagens pelo conversor real (PDFBox). Uma conversão por vez e fila zero, para provocar o 429.
 */
@TestPropertySource(properties = {"vistas.visualizacao.conversoes-simultaneas=1", "vistas.visualizacao.fila-de-conversoes=0"})
class ConteudoHttpTest extends BaseHttpDeDocumentos {

    private static final String CHAVE_PLANILHA = "CHAVE-INTERNA-PLANILHA";
    private static final String CHAVE_DOCX = "CHAVE-INTERNA-DOCX";
    private static final String CHAVE_IMAGEM = "CHAVE-INTERNA-IMAGEM";
    private static final String CHAVE_HTML = "CHAVE-INTERNA-HTML";
    private static final String CHAVE_ESTRANHO = "CHAVE-INTERNA-ESTRANHO";
    private static final String CHAVE_NOME_HOSTIL = "CHAVE-INTERNA-NOME";

    @MockitoBean
    ConversorDeDocumentos conversor;

    @Autowired
    CacheDeVisualizacao cache;

    @Autowired
    ParametrosDaVisualizacao parametros;

    /** Todas as respostas do teste, para conferir que nenhuma traz chave ou token. */
    private final List<MockHttpServletResponse> respostas = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        cache.esvaziar();
        respostas.clear();
        List<Documento> lista = new ArrayList<>(listaPadrao());
        LocalDateTime d = LocalDateTime.of(2025, 5, 1, 10, 0);
        lista.add(doc("804340", CHAVE_DOCX, "Memorial", "memorial.docx", d));
        lista.add(doc("804341", CHAVE_IMAGEM, "Foto", "foto.png", d.plusDays(1)));
        lista.add(doc("804342", CHAVE_HTML, "Página", "pagina.html", d.plusDays(2)));
        lista.add(doc("804343", CHAVE_ESTRANHO, "Estranho", "estranho.pdf", d.plusDays(3)));
        lista.add(doc("804344", CHAVE_NOME_HOSTIL, "Hostil", "../../etc/x\r\nSet-Cookie: a=b\"; .pdf", d.plusDays(4)));
        when(documentos.listar(101)).thenReturn(lista);

        conteudos.put(CHAVE_PLANILHA, zip("xl/workbook.xml"));
        conteudos.put(CHAVE_DOCX, zip("word/document.xml"));
        conteudos.put(CHAVE_IMAGEM, png());
        conteudos.put(CHAVE_HTML, "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        conteudos.put(CHAVE_ESTRANHO, "apenas texto, sem assinatura".getBytes(StandardCharsets.UTF_8));

        when(conversor.disponivel()).thenReturn(true);
        doAnswer(chamada -> {
            Path destino = chamada.getArgument(2);
            Files.write(destino, PdfsDeTeste.bytes(2, "convertido"));
            return new ResultadoDaConversao(2, 0);
        }).when(conversor).converter(any(Path.class), any(), any(Path.class));
    }

    // --- acesso ---

    @Test
    void matrizDePerfisDoDocumentoComum() throws Exception {
        for (String usuario : new String[] {"operador.demo", "gestor.demo", "admin.demo"}) {
            pedir(usuario, "DOCUMENTO_PROCESSO", "804328", 101, "pdf").andExpect(status().isOk());
        }
    }

    @Test
    void matrizDePerfisDoDocumentoSigiloso() throws Exception {
        pedir("operador.demo", "DOCUMENTO_PROCESSO", "804329", 101, "pdf")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENTO_SIGILOSO"));
        pedir("operador.demo", "DOCUMENTO_PROCESSO", "804329", 101, "original")
                .andExpect(status().isForbidden());
        verify(arquivos, never()).baixar(anyString(), any(Path.class));

        for (String usuario : new String[] {"gestor.demo", "admin.demo"}) {
            pedir(usuario, "DOCUMENTO_PROCESSO", "804329", 101, "pdf").andExpect(status().isOk());
        }
    }

    @Test
    void operadorDeOutroInteressadoRecebe403SemLerNadaDoRepositorio() throws Exception {
        pedir("operador.demo", "DOCUMENTO_PROCESSO", "804328", 202, "pdf")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
        verify(documentos, never()).listar(202);
        verify(arquivos, never()).baixar(anyString(), any(Path.class));
    }

    @Test
    void origemEIdQueNaoPertencemAoRequerimentoSao404() throws Exception {
        // 900118 existe, mas na origem COMPLEMENTACAO: os ids se repetem entre origens.
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "900118", 101, "pdf")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "999999", 101, "pdf").andExpect(status().isNotFound());
        pedir("gestor.demo", "ORIGEM_INVENTADA", "804328", 101, "pdf").andExpect(status().isNotFound());
        // documento do requerimento 101 pedido como se fosse do 202
        when(documentos.listar(202)).thenReturn(List.of());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 202, "pdf").andExpect(status().isNotFound());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 404, "pdf").andExpect(status().isNotFound());
        // indisponível (chave "0")
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804331", 101, "pdf").andExpect(status().isNotFound());
        verify(arquivos, never()).baixar(anyString(), any(Path.class));
    }

    @Test
    void semTokenEh401EParametrosInvalidosSao400() throws Exception {
        mvc.perform(get("/api/documentos/DOCUMENTO_PROCESSO/804328/conteudo?requerimento=101"))
                .andExpect(status().isUnauthorized());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 101, "docx")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        guardar(mvc.perform(get("/api/documentos/DOCUMENTO_PROCESSO/804328/conteudo")
                .header("Authorization", bearer("gestor.demo"))))
                .andExpect(status().isBadRequest());
    }

    // --- formatos ---

    @Test
    void pdfDeUmPdfVemComoEstaInline() throws Exception {
        byte[] pdf = PdfsDeTeste.bytes(3, "original em PDF");
        conteudos.put(CHAVE_1, pdf);
        MockHttpServletResponse resposta = pedir("operador.demo", "DOCUMENTO_PROCESSO", "804328", 101, "pdf")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("X-Vistas-Convertido-De"))
                .andReturn().getResponse();

        assertThat(resposta.getHeader("Content-Disposition")).startsWith("inline").contains("requerimento.pdf");
        assertThat(resposta.getContentAsByteArray()).isEqualTo(pdf);
        verify(conversor, never()).converter(any(), any(), any());
    }

    @Test
    void pdfDeXlsxEDocxConverteComOConversorDoDossie() throws Exception {
        MockHttpServletResponse planilha = pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Vistas-Convertido-De", "XLSX"))
                .andReturn().getResponse();
        assertThat(planilha.getHeader("Content-Disposition")).startsWith("inline").contains("calculo.pdf");
        assertThat(new String(planilha.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804340", 101, "pdf")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Vistas-Convertido-De", "DOCX"));

        verify(conversor).converter(any(Path.class),
                eq(new br.gov.mt.sema.vistas.documento.domain.TipoDetectado(
                        br.gov.mt.sema.vistas.documento.domain.TipoReal.PLANILHA, "xlsx")), any(Path.class));
        verify(conversor, times(2)).converter(any(), any(), any());
    }

    @Test
    void pdfDeImagemUsaOConversorDeImagens() throws Exception {
        MockHttpServletResponse resposta = pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804341", 101, "pdf")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("X-Vistas-Convertido-De", "PNG"))
                .andReturn().getResponse();
        assertThat(new String(resposta.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        verify(conversor, never()).converter(any(), any(), any());
    }

    @Test
    void originalDeHtmlVaiComoDownloadNuncaInline() throws Exception {
        MockHttpServletResponse resposta = pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804342", 101, "original")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        assertThat(resposta.getContentType()).startsWith("text/html");
        assertThat(resposta.getHeader("Content-Disposition")).startsWith("attachment").contains("pagina.html");
    }

    @Test
    void originalDePdfEImagemPodemSerExibidos() throws Exception {
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804341", 101, "original")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "original")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void tipoSemConversaoEh415() throws Exception {
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804343", 101, "pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errorCode").value("FORMATO_NAO_SUPORTADO"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("não tem conversão")));
        // compactado com nome de PDF
        conteudos.put(CHAVE_ESTRANHO, zip("dados/qualquer.txt"));
        cache.esvaziar();
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804343", 101, "pdf")
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void nomeHostilViraNomeSeguro() throws Exception {
        String disposicao = pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804344", 101, "original")
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Content-Disposition");
        assertThat(disposicao).doesNotContain("\r", "\n", "../", "etc/");
        assertThat(disposicao).contains("filename=");
    }

    // --- falhas ---

    @Test
    void falhasDeObtencaoViramProblemDetailComCodigo() throws Exception {
        doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(eq(CHAVE_1), any(Path.class));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 101, "pdf")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));

        doThrow(new FalhaNoDocumentoException("tempo limite excedido ao obter o arquivo"))
                .when(arquivos).baixar(eq(CHAVE_2), any(Path.class));
        pedir("gestor.demo", "COMPLEMENTACAO", "900118", 101, "pdf")
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.errorCode").value("TEMPO_LIMITE_EXCEDIDO"));

        doThrow(new CredencialArquivosRecusadaException()).when(arquivos).baixar(eq(CHAVE_SIGILOSA), any(Path.class));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804329", 101, "pdf")
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.errorCode").value("CREDENCIAL_ARQUIVOS_RECUSADA"));

        doThrow(new FalhaNoDocumentoException("repositório de arquivos indisponível"))
                .when(arquivos).baixar(eq(CHAVE_DOCX), any(Path.class));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804340", 101, "pdf")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL"));
        // Falha não deixa nada no cache.
        assertThat(arquivosNoCache()).isEmpty();
    }

    @Test
    void falhasDeConversaoViramProblemDetailComCodigo() throws Exception {
        doThrow(new FalhaDeConversaoException(Motivo.PROTEGIDO_POR_SENHA)).when(conversor).converter(any(), any(), any());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENTO_PROTEGIDO_POR_SENHA"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("protegido por senha")));

        doThrow(new FalhaDeConversaoException(Motivo.CORROMPIDO)).when(conversor).converter(any(), any(), any());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf")
                .andExpect(jsonPath("$.errorCode").value("DOCUMENTO_CORROMPIDO"));

        doThrow(new FalhaDeConversaoException(Motivo.TEMPO_LIMITE)).when(conversor).converter(any(), any(), any());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf")
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.errorCode").value("TEMPO_LIMITE_EXCEDIDO"));

        when(conversor.disponivel()).thenReturn(false);
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("CONVERSOR_INDISPONIVEL"));
        // O original obtido fica no cache: a nova tentativa não baixa de novo.
        verify(arquivos, times(1)).baixar(eq(CHAVE_PLANILHA), any(Path.class));
    }

    // --- cache ---

    @Test
    void segundaChamadaUsaOCacheMasConfereOAcessoDeNovo() throws Exception {
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf").andExpect(status().isOk());
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf").andExpect(status().isOk());
        verify(arquivos, times(1)).baixar(eq(CHAVE_PLANILHA), any(Path.class));
        verify(conversor, times(1)).converter(any(), any(), any());
        verify(documentos, times(2)).listar(101);

        // Sigiloso já em cache pelo Gestor: o Operador continua barrado.
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804329", 101, "pdf").andExpect(status().isOk());
        pedir("operador.demo", "DOCUMENTO_PROCESSO", "804329", 101, "pdf")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("DOCUMENTO_SIGILOSO"));
        // E o operador de outro interessado não chega ao cache.
        pedir("operador.demo", "DOCUMENTO_PROCESSO", "804330", 202, "pdf")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
    }

    @Test
    void depoisDoTtlBaixaEConverteDeNovo() throws Exception {
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf").andExpect(status().isOk());
        FileTime antigo = FileTime.from(Instant.now().minusSeconds(parametros.ttlMinutos() * 60L + 5));
        for (Path arquivo : arquivosNoCache()) {
            Files.setLastModifiedTime(arquivo, antigo);
        }
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804330", 101, "pdf").andExpect(status().isOk());
        verify(arquivos, times(2)).baixar(eq(CHAVE_PLANILHA), any(Path.class));
        verify(conversor, times(2)).converter(any(), any(), any());
    }

    @Test
    void filaCheiaDa429EPdfNaoEntraNaFila() throws Exception {
        // As duas pastas não podem cair na mesma faixa de trava (senão a segunda esperaria a primeira, sem 429).
        assertThat(Math.floorMod(pasta("804330").hashCode(), 64)).isNotEqualTo(Math.floorMod(pasta("804340").hashCode(), 64));
        CountDownLatch convertendo = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        doAnswer(chamada -> {
            convertendo.countDown();
            liberar.await(30, TimeUnit.SECONDS);
            Files.write((Path) chamada.getArgument(2), PdfsDeTeste.bytes(1, "convertido"));
            return new ResultadoDaConversao(1, 0);
        }).when(conversor).converter(any(), any(), any());
        String autorizacao = bearer("gestor.demo");
        AtomicReference<Integer> statusDaPrimeira = new AtomicReference<>();
        Thread primeira = new Thread(() -> {
            try {
                statusDaPrimeira.set(mvc.perform(get("/api/documentos/DOCUMENTO_PROCESSO/804330/conteudo?requerimento=101")
                        .header("Authorization", autorizacao)).andReturn().getResponse().getStatus());
            } catch (Exception e) {
                statusDaPrimeira.set(-1);
            }
        });
        primeira.start();
        try {
            assertThat(convertendo.await(30, TimeUnit.SECONDS)).isTrue();
            pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804340", 101, "pdf")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.errorCode").value("MUITAS_CONVERSOES"));
        } finally {
            liberar.countDown();
            primeira.join(30_000);
        }
        assertThat(statusDaPrimeira.get()).isEqualTo(200);
        // PDF não passa pela fila de conversões.
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 101, "pdf").andExpect(status().isOk());
    }

    // --- RNF09 ---

    @Test
    void nenhumaRespostaTrazAChaveDoArquivoNemOToken() throws Exception {
        for (String usuario : new String[] {"gestor.demo", "operador.demo"}) {
            for (String[] doc : new String[][] {{"DOCUMENTO_PROCESSO", "804328"}, {"DOCUMENTO_PROCESSO", "804329"},
                    {"DOCUMENTO_PROCESSO", "804330"}, {"DOCUMENTO_PROCESSO", "804341"}, {"DOCUMENTO_PROCESSO", "804342"},
                    {"DOCUMENTO_PROCESSO", "804343"}, {"DOCUMENTO_PROCESSO", "804331"}, {"COMPLEMENTACAO", "900118"}}) {
                for (String formato : new String[] {"pdf", "original"}) {
                    pedir(usuario, doc[0], doc[1], 101, formato);
                }
            }
        }
        doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(anyString(), any(Path.class));
        cache.esvaziar();
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 101, "pdf");
        doThrow(new CredencialArquivosRecusadaException()).when(arquivos).baixar(anyString(), any(Path.class));
        pedir("gestor.demo", "DOCUMENTO_PROCESSO", "804328", 101, "pdf");

        assertThat(respostas).hasSizeGreaterThan(30);
        for (MockHttpServletResponse resposta : respostas) {
            StringBuilder tudo = new StringBuilder(new String(resposta.getContentAsByteArray(), StandardCharsets.ISO_8859_1));
            for (String nome : resposta.getHeaderNames()) {
                tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
            }
            for (String segredo : SEGREDOS) {
                assertThat(tudo.toString()).doesNotContain(segredo);
            }
        }
    }

    // --- apoio ---

    private ResultActions pedir(String usuario, String origem, String id, long requerimento, String formato)
            throws Exception {
        return guardar(mvc.perform(get("/api/documentos/{origem}/{id}/conteudo", origem, id)
                .param("requerimento", String.valueOf(requerimento)).param("formato", formato)
                .header("Authorization", bearer(usuario))));
    }

    private ResultActions guardar(ResultActions resultado) {
        respostas.add(resultado.andReturn().getResponse());
        return resultado;
    }

    private String pasta(String id) {
        return "DOCUMENTO_PROCESSO-" + HexFormat.of().formatHex(id.getBytes(StandardCharsets.UTF_8));
    }

    private List<Path> arquivosNoCache() throws Exception {
        if (!Files.isDirectory(parametros.diretorio())) {
            return List.of();
        }
        try (Stream<Path> todos = Files.walk(parametros.diretorio())) {
            return todos.filter(Files::isRegularFile).toList();
        }
    }

    private static Documento doc(String id, String chave, String titulo, String nome, LocalDateTime data) {
        return Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, id, 101, chave, titulo, nome, data, null, 0);
    }

    private static byte[] zip(String entrada) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entrada));
            zip.write("<x/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] png() throws Exception {
        BufferedImage imagem = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", bytes);
        return bytes.toByteArray();
    }
}
