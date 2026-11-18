package br.gov.mt.sema.vistas.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Conversor;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Montador;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.vistas.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Validador;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.vistas.dossie.domain.AcaoDeContinuacao;
import br.gov.mt.sema.vistas.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import br.gov.mt.sema.vistas.testsupport.ParametrosDeConversaoDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Conversão no pipeline do dossiê (S3, bloco 5): escolha pelo tipo real, falha de conversão no fluxo da S2.2, retomada
 * sem converter nem baixar de novo, cancelamento no meio, conversão desligada e conversor indisponível. Conversores
 * falsos (a conversão real tem os próprios testes em integration.*).
 */
class ConversaoNoPipelineTest {

    @TempDir
    Path pasta;

    private Arquivos arquivos;
    private Montador montador;
    private Parametros parametros;
    private RelogioControlado relogio;
    private Conversor conversor;

    @BeforeEach
    void preparar() {
        arquivos = new Arquivos();
        montador = new Montador();
        parametros = new Parametros(pasta);
        relogio = new RelogioControlado();
        conversor = new Conversor();
        arquivos.comportamentos.put("K-XLSX", destino -> escrever(destino, ooxml("xl/workbook.xml")));
        arquivos.comportamentos.put("K-DOCX", destino -> escrever(destino, ooxml("word/document.xml")));
        arquivos.comportamentos.put("K-PNG", destino -> escrever(destino, PNG));
        arquivos.comportamentos.put("K-ZIP", destino -> escrever(destino, ooxml("pasta/qualquer.txt")));
    }

    private GeradorDeDossie gerador(boolean conversaoLigada) {
        PreparadorDePdf preparador = new PreparadorDePdf(new Validador(), conversor, conversor,
                conversaoLigada ? ParametrosDeConversaoDeTeste.ligada() : ParametrosDeConversaoDeTeste.desligada());
        return new GeradorDeDossie(arquivos, preparador, montador, parametros, d -> Long.MAX_VALUE, relogio, Fakes.eventos());
    }

    /** PDF (1 página), XLSX, DOCX e PNG, nesta ordem cronológica. */
    private PedidoDeDossie pedidoMisto() {
        return Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                item("1", "Requerimento", "K-PDF", "pdf", 1),
                item("2", "Planilha de custos", "K-XLSX", "xlsx", 2),
                item("3", "Memorial", "K-DOCX", "docx", 3),
                item("4", "Foto do imóvel", "K-PNG", "png", 4));
    }

    @Test
    void dossieMistoConverteNaObtencaoEMontaNaOrdemCronologicaComOFormatoDeOrigem() {
        conversor.abasEmPaginaUnica = 1;
        PedidoDeDossie pedido = pedidoMisto();

        gerador(true).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        List<DocumentoParaMontagem> montados = montador.documentosMontados.get(0);
        assertThat(montados).extracting(DocumentoParaMontagem::titulo)
                .containsExactly("Requerimento", "Planilha de custos", "Memorial", "Foto do imóvel");
        assertThat(montados).extracting(DocumentoParaMontagem::convertidoDe).containsExactly(null, "XLSX", "DOCX", "PNG");
        // Página inicial conhecida antes da montagem: as páginas vêm da conversão.
        assertThat(montados).extracting(DocumentoParaMontagem::paginas).containsExactly(1, 2, 2, 2);
        assertThat(montados.get(1).nota()).isEqualTo("convertido de XLSX*");
        assertThat(montados.get(1).abasEmPaginaUnica()).isEqualTo(1);
        assertThat(conversor.convertidos).containsExactly("xlsx", "docx", "png");
        // S3.1: o conversor de documentos recebe o título do sumário (cabeçalho da planilha); a imagem não.
        assertThat(conversor.titulos).containsExactly("Planilha de custos", "Memorial");
        assertThat(arquivosNaPasta()).containsExactly("dossie.pdf");
    }

    @Test
    void falhaDeConversaoEFalhaDoDocumentoETentarNovamenteNaoBaixaNemConverteDeNovoOQueJaDeuCerto() {
        conversor.falhas.put("xlsx", Motivo.TEMPO_LIMITE);
        PedidoDeDossie pedido = pedidoMisto();
        GeradorDeDossie gerador = gerador(true);

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.falhas()).extracting(ItemDoPedido::id).containsExactly("2");
        assertThat(pedido.falhas().get(0).motivo()).isEqualTo("tempo limite da conversão excedido");
        assertThat(arquivosNaPasta()).contains("0002.original").doesNotContain("0002.pdf");

        conversor.falhas.clear();
        assertThat(pedido.decidir(AcaoDeContinuacao.TENTAR_NOVAMENTE, LocalDateTime.now(relogio)))
                .isEqualTo(PedidoDeDossie.Decisao.ACEITA);
        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(Collections.frequency(arquivos.baixadas, "K-XLSX")).as("original guardado: sem novo download").isEqualTo(1);
        assertThat(pedido.itens().get(1).tentativas()).isZero();
        assertThat(Collections.frequency(conversor.convertidos, "docx")).as("convertido não é refeito").isEqualTo(1);
        assertThat(Collections.frequency(conversor.convertidos, "png")).isEqualTo(1);
        assertThat(Collections.frequency(conversor.convertidos, "xlsx")).isEqualTo(2);
        assertThat(arquivosNaPasta()).containsExactly("dossie.pdf");
    }

    @Test
    void removerEContinuarNaoConverteDeNovoEApagaOOriginalGuardado() {
        conversor.falhas.put("docx", Motivo.PROTEGIDO_POR_SENHA);
        PedidoDeDossie pedido = pedidoMisto();
        GeradorDeDossie gerador = gerador(true);
        gerador.processar(pedido);
        assertThat(pedido.falhas().get(0).motivo()).isEqualTo("documento protegido por senha");

        pedido.decidir(AcaoDeContinuacao.REMOVER_E_CONTINUAR, LocalDateTime.now(relogio));
        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(conversor.convertidos).containsExactly("xlsx", "docx", "png");
        assertThat(montador.documentosMontados.get(0)).extracting(DocumentoParaMontagem::titulo)
                .containsExactly("Requerimento", "Planilha de custos", "Foto do imóvel");
        assertThat(pedido.naoIncluidos()).singleElement()
                .satisfies(n -> assertThat(n.motivo()).isEqualTo("removido pelo usuário após falha: documento protegido por senha"));
        assertThat(arquivosNaPasta()).containsExactly("dossie.pdf");
    }

    @Test
    void cancelarNoMeioDaConversaoInterrompeEApagaOsTemporarios() throws Exception {
        CountDownLatch convertendo = new CountDownLatch(1);
        conversor.aoConverter = () -> {
            convertendo.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FalhaDeConversaoException(Motivo.INTERROMPIDA);
            }
        };
        PedidoDeDossie pedido = pedidoMisto();
        GeradorDeDossie gerador = gerador(true);
        Thread execucao = new Thread(() -> gerador.processar(pedido));
        execucao.start();
        assertThat(convertendo.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(pedido.cancelar()).isFalse(); // em andamento: só marca
        gerador.interromperConversao(pedido.id());
        execucao.join(10_000);

        assertThat(execucao.isAlive()).as("a conversão não esperou os 60 s").isFalse();
        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(conversor.convertidos).containsExactly("xlsx");
        assertThat(Files.exists(ArquivosTemporarios.pastaDo(parametros, pedido))).isFalse();
        assertThat(montador.documentosMontados).isEmpty();
    }

    @Test
    void conversaoDesligadaVoltaAoComportamentoDaS22() {
        PedidoDeDossie pedido = pedidoMisto();

        gerador(false).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.falhas()).extracting(ItemDoPedido::motivo).containsExactly(
                "o arquivo não é um PDF (tipo detectado: planilha XLSX) e a conversão está desligada",
                "o arquivo não é um PDF (tipo detectado: texto DOCX) e a conversão está desligada",
                "o arquivo não é um PDF (tipo detectado: imagem PNG) e a conversão está desligada");
        assertThat(conversor.convertidos).isEmpty();
    }

    @Test
    void conversorIndisponivelEFalhaDoDocumentoComMotivoProprio() {
        conversor.disponivel = false;
        PedidoDeDossie pedido = Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                item("1", "Planilha", "K-XLSX", "xlsx", 1));

        gerador(true).processar(pedido);

        assertThat(pedido.falhas()).extracting(ItemDoPedido::motivo)
                .containsExactly("conversor de documentos indisponível");
    }

    @Test
    void compactadoETipoPeloConteudoNaoPeloNome() {
        // Um ZIP com nome de planilha e uma planilha com nome de PDF: vale o conteúdo.
        PedidoDeDossie pedido = Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                item("1", "Arquivo", "K-ZIP", "xlsx", 1), item("2", "Enganoso", "K-XLSX", "pdf", 2));

        gerador(true).processar(pedido);

        assertThat(pedido.falhas()).extracting(ItemDoPedido::motivo)
                .containsExactly("arquivo compactado (ZIP): não entra no dossiê");
        assertThat(pedido.itens().get(1).convertidoDe()).isEqualTo("XLSX");
        assertThat(conversor.convertidos).containsExactly("xlsx");
    }

    // ---- apoio ----

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private static ItemDoPedido item(String id, String titulo, String chave, String extensao, int dia) {
        return new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", id, titulo,
                LocalDateTime.of(2025, 4, dia, 10, 0), chave, dia - 1, extensao);
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

    private static void escrever(Path destino, byte[] conteudo) {
        try {
            Files.write(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> arquivosNaPasta() {
        try (Stream<Path> todos = Files.walk(pasta)) {
            return todos.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
