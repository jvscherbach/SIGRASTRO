package br.gov.mt.sema.vistas.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Montador;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.vistas.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Validador;
import br.gov.mt.sema.vistas.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.SituacaoItem;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** RN09 / RNF04: completo ou nenhum. Portas falsas, sem SIGA e sem rede. */
class GeradorDeDossieTest {

    @TempDir
    Path pasta;

    private Arquivos arquivos;
    private Montador montador;
    private Parametros parametros;
    private RelogioControlado relogio;
    private GeradorDeDossie gerador;

    @BeforeEach
    void preparar() {
        arquivos = new Arquivos();
        montador = new Montador();
        parametros = new Parametros(pasta);
        relogio = new RelogioControlado();
        gerador = new GeradorDeDossie(arquivos, new Validador(), montador, parametros, relogio);
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

    @Test
    void sucessoConcluiMontaNaOrdemEDeixaSoOArquivoFinal() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K2", Arquivos.paginas(4));

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(pedido.processados()).isEqualTo(3);
        assertThat(pedido.paginas()).isEqualTo(1 + 1 + 4 + 1);
        assertThat(pedido.erro()).isNull();
        assertThat(pedido.itens()).allMatch(i -> i.situacao() == SituacaoItem.OK);
        assertThat(arquivos.baixadas).containsExactly("K1", "K2", "K3");
        assertThat(montador.titulosMontados).containsExactly(java.util.List.of("Requerimento", "Oficio", "Laudo"));
        assertThat(pedido.arquivo()).exists().hasFileName("dossie.pdf");
        assertThat(arquivosNaPasta()).as("só o PDF final permanece").isEqualTo(1);
        assertThat(pedido.tamanhoBytes()).isEqualTo(Files.size(pedido.arquivo()));
    }

    @Test
    void validadeContaDoMomentoDaConclusao() {
        PedidoDeDossie pedido = pedidoDeTres();
        relogio.avancarMinutos(5);

        gerador.processar(pedido);

        assertThat(pedido.expiraEm()).isEqualTo(LocalDateTime.now(relogio).plusMinutes(30));
    }

    @Test
    void falhaNoMeioInterrompeDescartaTudoENaoProcessaOsSeguintes() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K2", Arquivos.falhaDoDocumento("tempo limite excedido ao obter o arquivo"));

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro()).isEqualTo(new ErroDoDossie("DOCUMENTO_PROCESSO", "2", "Oficio",
                "tempo limite excedido ao obter o arquivo", null));
        assertThat(pedido.itens()).extracting(i -> i.situacao())
                .containsExactly(SituacaoItem.OK, SituacaoItem.FALHA, SituacaoItem.AGUARDANDO);
        assertThat(pedido.processados()).isEqualTo(1);
        assertThat(arquivos.baixadas).as("o terceiro não é processado").containsExactly("K1", "K2");
        assertThat(montador.titulosMontados).isEmpty();
        assertThat(pedido.arquivo()).isNull();
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
        assertThat(arquivosNaPasta()).isZero();
    }

    @Test
    void documentoInvalidoDepoisDeObtidoTambemInterrompe() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K1", destino -> Fakes.gravar(destino, "invalido:o documento está protegido por senha"));

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro().motivo()).isEqualTo("o documento está protegido por senha");
        assertThat(pedido.erro().id()).isEqualTo("1");
        assertThat(arquivos.baixadas).containsExactly("K1");
        assertThat(arquivosNaPasta()).isZero();
    }

    @Test
    void credencialRecusadaInterrompeTudoComOCodigoPropriosemOToken() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.comportamentos.put("K1", destino -> {
            throw new CredencialArquivosRecusadaException();
        });

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro().codigo()).isEqualTo("CREDENCIAL_ARQUIVOS_RECUSADA");
        assertThat(pedido.erro().motivo()).isEqualTo("Token da API de arquivos expirado ou recusado: capture um novo token.");
        assertThat(arquivos.baixadas).containsExactly("K1");
        assertThat(arquivosNaPasta()).isZero();
    }

    @Test
    void erroInesperadoNaMontagemTambemNaoEntregaNada() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        montador.falha = new IllegalStateException("detalhe interno que não deve vazar");

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro().motivo()).isEqualTo("falha inesperada ao gerar o dossiê");
        assertThat(pedido.arquivo()).isNull();
        assertThat(arquivosNaPasta()).isZero();
    }

    @Test
    void pedidoCanceladoAntesDeComecarNaoBaixaNada() {
        PedidoDeDossie pedido = pedidoDeTres();
        pedido.cancelar();

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(arquivos.baixadas).isEmpty();
    }

    @Test
    void cancelamentoDuranteAGeracaoParaNoProximoDocumentoELimpa() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.aoBaixar = chave -> {
            if (chave.equals("K1")) {
                pedido.cancelar();
            }
        };

        gerador.processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(arquivos.baixadas).containsExactly("K1");
        assertThat(montador.titulosMontados).isEmpty();
        assertThat(arquivosNaPasta()).isZero();
        assertThat(pedido.arquivo()).isNull();
    }

    @Test
    void cancelamentoDepoisDoUltimoDocumentoNaoMontaNada() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        arquivos.aoBaixar = chave -> {
            if (chave.equals("K3")) {
                pedido.cancelar();
            }
        };

        gerador.processar(pedido);

        // O último documento foi obtido e o cancelamento chegou antes da montagem: nada é montado nem entregue.
        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(arquivosNaPasta()).isZero();
    }

    @Test
    void pedidoJaProcessadoNaoReexecuta() {
        PedidoDeDossie pedido = pedidoDeTres();
        gerador.processar(pedido);
        arquivos.baixadas.clear();

        gerador.processar(pedido);

        assertThat(arquivos.baixadas).isEmpty();
    }

    @Test
    void opcoesDeSumarioMarcadoresENumeracaoChegamAoMontador() {
        parametros.sumario = false;
        parametros.numeracao = false;
        Montador capturador = new Montador() {
            @Override
            public int montar(br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa capa, java.util.List<DocumentoParaMontagem> documentos,
                    OpcoesDeMontagem opcoes, Path destino) {
                assertThat(opcoes).isEqualTo(new OpcoesDeMontagem(false, true, false));
                return super.montar(capa, documentos, opcoes, destino);
            }
        };
        PedidoDeDossie pedido = pedidoDeTres();

        new GeradorDeDossie(arquivos, new Validador(), capturador, parametros, relogio).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
    }

    @Test
    void cancelamentoDuranteAMontagemDescartaOArquivoPronto() throws IOException {
        PedidoDeDossie pedido = pedidoDeTres();
        Montador cancelador = new Montador() {
            @Override
            public int montar(br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa capa, java.util.List<DocumentoParaMontagem> documentos,
                    OpcoesDeMontagem opcoes, Path destino) {
                int paginas = super.montar(capa, documentos, opcoes, destino);
                pedido.cancelar();
                return paginas;
            }
        };

        new GeradorDeDossie(arquivos, new Validador(), cancelador, parametros, relogio).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(pedido.arquivo()).isNull();
        assertThat(arquivosNaPasta()).isZero();
    }
}
