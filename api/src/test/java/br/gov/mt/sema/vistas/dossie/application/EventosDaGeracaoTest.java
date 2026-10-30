package br.gov.mt.sema.vistas.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.NovoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Espaco;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Montador;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.vistas.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Validador;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Eventos da thread de geração nos casos que o teste HTTP não alcança: montagem que falha e cancelamento. */
class EventosDaGeracaoTest {

    @TempDir
    Path pasta;

    private final List<NovoEvento> registrados = new ArrayList<>();
    private final Arquivos arquivos = new Arquivos();

    private GeradorDeDossie gerador(MontadorDoDossie montador) {
        return new GeradorDeDossie(arquivos, Fakes.preparador(new Validador()), montador, new Parametros(pasta), new Espaco(),
                new RelogioControlado(),
                new EventosDoDossie(registrados::add, new ContextoDoEvento(Fakes.semUsuario(), Fakes.semRequisicao())));
    }

    private PedidoDeDossie pedido() {
        return Fakes.pedido("gestor.demo", LocalDateTime.of(2026, 10, 21, 10, 0), 30, Fakes.item("1", "Documento", "CHAVE-1"));
    }

    @Test
    void falha_na_montagem_registra_uma_falha_sem_documento_e_nenhum_dossie() {
        PedidoDeDossie pedido = pedido();

        gerador(new Montador() {
            @Override
            public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
                    OpcoesDeMontagem opcoes, Path destino) {
                throw new FalhaNoDocumentoException("página inválida", 0);
            }
        }).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(registrados).extracting(NovoEvento::tipo).containsExactly(TipoDeEvento.FALHA);
        assertThat(registrados.get(0).documentos()).isEmpty();
        assertThat(registrados.get(0).detalhe()).contains("página inválida");
        assertThat(registrados.get(0).usuario().login()).isEqualTo("gestor.demo");
    }

    @Test
    void cancelado_durante_a_montagem_nao_registra_dossie() {
        PedidoDeDossie pedido = pedido();

        gerador(new Montador() {
            @Override
            public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
                    OpcoesDeMontagem opcoes, Path destino) {
                pedido.cancelar();
                return super.montar(capa, documentos, inventario, opcoes, destino);
            }
        }).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(registrados).isEmpty();
    }

    @Test
    void falha_de_documento_depois_do_cancelamento_nao_e_falha_do_documento() {
        PedidoDeDossie pedido = pedido();
        arquivos.comportamentos.put("CHAVE-1", destino -> {
            pedido.cancelar();
            throw new FalhaNoDocumentoException("interrompido", 0);
        });

        gerador(new Montador()).processar(pedido);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(registrados).isEmpty();
    }
}
