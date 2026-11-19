package br.gov.mt.sema.rastro.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Conversor;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Espaco;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Montador;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.rastro.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Validador;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.rastro.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.SituacaoItem;
import br.gov.mt.sema.rastro.dossie.domain.StatusDossie;
import br.gov.mt.sema.rastro.shared.application.exception.ArmazenamentoInsuficienteException;
import br.gov.mt.sema.rastro.testsupport.ParametrosDeConversaoDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Disco cheio na geração (S6, bloco 3). Simulação segura: o "sem espaço" é injetado pelas portas falsas (exceção com a
 * mensagem do Linux ou do Windows, ou espaço livre informado pelo teste). Nenhum teste enche disco de verdade.
 *
 * <p>Em todos os casos: FALHA com {@code ARMAZENAMENTO_INSUFICIENTE}, nada de arquivo parcial, um único evento de falha
 * sem caminho de arquivo, e nunca CONCLUIDO sem o PDF final gravado.
 */
class SemEspacoNaGeracaoTest {

    private static final String LINUX = "No space left on device";
    private static final String WINDOWS = "There is not enough space on the disk";

    @TempDir
    Path pasta;

    private Arquivos arquivos;
    private Montador montador;
    private Parametros parametros;
    private Espaco espaco;
    private RelogioControlado relogio;
    private Conversor conversor;
    private final List<NovoEvento> eventos = new ArrayList<>();
    private GeradorDeDossie gerador;

    @BeforeEach
    void preparar() {
        arquivos = new Arquivos();
        montador = new Montador();
        parametros = new Parametros(pasta);
        espaco = new Espaco();
        relogio = new RelogioControlado();
        conversor = new Conversor();
        PreparadorDePdf preparador = new PreparadorDePdf(new Validador(), conversor, conversor,
                ParametrosDeConversaoDeTeste.ligada());
        EventosDoDossie eventosDoDossie = new EventosDoDossie(eventos::add,
                new ContextoDoEvento(Fakes.semUsuario(), Fakes.semRequisicao()));
        gerador = new GeradorDeDossie(arquivos, preparador, montador, parametros, espaco, relogio, eventosDoDossie);
    }

    private PedidoDeDossie pedidoDeTres() {
        return Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                Fakes.item("1", "Requerimento", "K1"), Fakes.item("2", "Oficio", "K2"), Fakes.item("3", "Laudo", "K3"));
    }

    private long arquivosNaPasta() throws IOException {
        try (Stream<Path> todos = Files.walk(pasta)) {
            return todos.filter(Files::isRegularFile).count();
        }
    }

    /** Situação final comum a todos os cenários de disco cheio. */
    private void assertFalhaPorEspaco(PedidoDeDossie pedido) throws IOException {
        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro().codigo()).isEqualTo(ErroDoDossie.ARMAZENAMENTO_INSUFICIENTE);
        assertThat(pedido.erro().motivo()).isEqualTo(
                "Sem espaço em disco para gerar o dossiê. Libere espaço e tente novamente.");
        assertThat(pedido.arquivo()).isNull();
        assertThat(pedido.arquivoGerado()).isFalse();
        assertThat(arquivosNaPasta()).as("nada parcial fica no disco").isZero();
        assertThat(pasta.resolve(pedido.id())).as("o diretório do pedido é apagado").doesNotExist();
        assertThat(eventos).as("um único evento, do pedido, e não um por documento").singleElement()
                .satisfies(evento -> {
                    assertThat(evento.tipo()).isEqualTo(TipoDeEvento.FALHA);
                    assertThat(evento.detalhe()).startsWith("ARMAZENAMENTO_INSUFICIENTE")
                            .doesNotContain(pasta.toString()).doesNotContain("dossie.pdf").doesNotContain(".original");
                    assertThat(evento.documentos()).isNullOrEmpty();
                });
        assertThat(montador.titulosMontados).as("nada é montado").isEmpty();
    }

    @Test
    void verificacaoAntesDoDownloadParaNoPrimeiroDocumentoSemBaixarNada() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        parametros.espacoMinimoMb = 500;
        espaco.livre = 500L * 1024 * 1024 - 1;

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
        assertThat(arquivos.baixadas).isEmpty();
        assertThat(pedido.itens()).extracting(ItemDoPedido::situacao)
                .containsExactly(SituacaoItem.FALHA, SituacaoItem.AGUARDANDO, SituacaoItem.AGUARDANDO);
        assertThat(pedido.itens().get(0).motivo()).isEqualTo(GeradorDeDossie.ESPACO_INSUFICIENTE);
    }

    @Test
    void falhaNaEscritaDoDownloadParaAObtencaoEApagaOParcial() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K2", destino -> {
            escrever(destino, "parcial".getBytes());
            // O adapter traduz o ENOSPC da escrita; aqui ele chega já traduzido.
            throw new ArmazenamentoInsuficienteException();
        });

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
        assertThat(arquivos.baixadas).as("o terceiro nem é tentado").containsExactly("K1", "K2");
        assertThat(pedido.itens().get(1).situacao()).isEqualTo(SituacaoItem.FALHA);
    }

    @Test
    void falhaNaObtencaoComCausaEncadeadaDoLinuxTambemValeSemOAdapterTraduzir() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K1", destino -> {
            throw new UncheckedIOException(new FileSystemException(destino.toString(), null, LINUX));
        });

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
    }

    @Test
    void falhaNaConversaoComMensagemDoWindowsViraFalhaDoPedidoENaoDoDocumento() throws IOException {
        arquivos.comportamentos.put("K-XLSX", destino -> escrever(destino, ooxml("xl/workbook.xml")));
        conversor.aoConverter = () -> {
            throw new FalhaDeConversaoException(FalhaDeConversaoException.Motivo.CORROMPIDO, new IOException(WINDOWS));
        };
        PedidoDeDossie pedido = Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                Fakes.item("1", "Requerimento", "K1"),
                new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", "2", "Planilha",
                        LocalDateTime.of(2025, 4, 2, 10, 0), "K-XLSX", 1, "xlsx"));

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
    }

    @Test
    void falhaOpacaDoConversorComOVolumePraticamenteCheioViraFaltaDeEspaco() throws IOException {
        arquivos.comportamentos.put("K-XLSX", destino -> escrever(destino, ooxml("xl/workbook.xml")));
        conversor.aoConverter = () -> {
            espaco.livre = GeradorDeDossie.LIMIAR_DE_DISCO_CHEIO - 1;
            throw new FalhaDeConversaoException(FalhaDeConversaoException.Motivo.CORROMPIDO);
        };
        PedidoDeDossie pedido = Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", "1", "Planilha",
                        LocalDateTime.of(2025, 4, 1, 10, 0), "K-XLSX", 0, "xlsx"));
        parametros.espacoMinimoMb = 0;

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
    }

    @Test
    void documentoCorrompidoComEspacoSobrandoContinuaFalhaDoDocumentoAguardandoDecisao() {
        arquivos.comportamentos.put("K-XLSX", destino -> escrever(destino, ooxml("xl/workbook.xml")));
        conversor.falhas.put("xlsx", FalhaDeConversaoException.Motivo.CORROMPIDO);
        PedidoDeDossie pedido = Fakes.pedido("gestor.demo", LocalDateTime.now(relogio), 30,
                Fakes.item("1", "Requerimento", "K1"),
                new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", "2", "Planilha",
                        LocalDateTime.of(2025, 4, 2, 10, 0), "K-XLSX", 1, "xlsx"));

        gerador.processar(pedido);

        assertThat(pedido.status()).as("só disco cheio vira FALHA (RN09 vale para o resto)")
                .isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.erro()).isNull();
    }

    @Test
    void falhaNaMontagemPorFaltaDeEspacoApagaTudoEFalha() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        montador.falha = new IllegalStateException("Falha ao montar o PDF do dossiê",
                new FileSystemException("/tmp/dossies/x/dossie.pdf", null, LINUX));

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
        assertThat(arquivos.baixadas).hasSize(3);
    }

    @Test
    void montagemQueNaoGravaOArquivoFinalNuncaConclui() {
        PedidoDeDossie pedido = pedidoDeTres();
        Montador semGravar = new Montador() {
            @Override
            public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
                    OpcoesDeMontagem opcoes, Path destino) {
                return 3;
            }
        };
        GeradorDeDossie comMontadorQueNaoGrava = new GeradorDeDossie(arquivos, Fakes.preparador(new Validador()),
                semGravar, parametros, espaco, relogio, Fakes.eventos());

        comMontadorQueNaoGrava.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.erro().motivo()).isEqualTo(GeradorDeDossie.FINAL_NAO_GRAVADO);
        assertThat(pedido.arquivo()).isNull();
    }

    @Test
    void montagemQueGravaArquivoVazioNuncaConcluiEOApaga() {
        PedidoDeDossie pedido = pedidoDeTres();
        Montador vazio = new Montador() {
            @Override
            public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
                    OpcoesDeMontagem opcoes, Path destino) {
                escrever(destino, new byte[0]);
                return 3;
            }
        };
        GeradorDeDossie comMontadorVazio = new GeradorDeDossie(arquivos, Fakes.preparador(new Validador()), vazio,
                parametros, espaco, relogio, Fakes.eventos());

        comMontadorVazio.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.erro().motivo()).isEqualTo(GeradorDeDossie.FINAL_NAO_GRAVADO);
        assertThat(pasta.resolve(pedido.id()).resolve("dossie.pdf")).doesNotExist();
    }

    @Test
    void concluidoSoComArquivoFinalExistenteEComTamanho() {
        PedidoDeDossie pedido = pedidoDeTres();

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(pedido.arquivo()).exists().isRegularFile();
        assertThat(pedido.tamanhoBytes()).isPositive();
    }

    @Test
    void semMedidaDeEspacoAVerificacaoNaoBloqueiaNemDerrubaAGeracao() {
        PedidoDeDossie pedido = pedidoDeTres();
        GeradorDeDossie semMedida = new GeradorDeDossie(arquivos, Fakes.preparador(new Validador()), montador,
                parametros, diretorio -> {
                    throw new UncheckedIOException(new IOException("sem acesso ao volume"));
                }, relogio, Fakes.eventos());

        semMedida.exigirEspacoParaNovoPedido();
        semMedida.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
    }

    @Test
    void falhaDeDocumentoComumNaoViraFaltaDeEspaco() {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K2", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.itens().get(1).motivo()).isEqualTo("arquivo não encontrado no repositório");
        assertThat(pedido.itens().get(2).situacao()).as("a obtenção continua").isEqualTo(SituacaoItem.OK);
    }

    @Test
    void falhaNoDocumentoComCausaDeDiscoCheioTambemEncerra() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K1", destino -> {
            throw new FalhaNoDocumentoException("não foi possível gravar o arquivo temporário",
                    new IOException(LINUX));
        });

        gerador.processar(pedido);

        assertFalhaPorEspaco(pedido);
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
}
