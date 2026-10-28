package br.gov.mt.sema.vistas.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.acesso.application.UsuarioAtual;
import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.consulta.application.ObterRequerimento;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.documento.application.ListarDocumentos;
import br.gov.mt.sema.vistas.documento.application.ReferenciaDeDocumento;
import br.gov.mt.sema.vistas.documento.application.SelecionarDocumentosParaDossie;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.dossie.application.CriarDossie;
import br.gov.mt.sema.vistas.dossie.application.GeradorDeDossie;
import br.gov.mt.sema.vistas.dossie.application.PreparadorDePdf;
import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.application.port.ValidadorDePdf;
import br.gov.mt.sema.vistas.testsupport.ParametrosDeConversaoDeTeste;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Critério de aceite da S2: nenhuma conexão do SIGA fica aberta durante o download e a montagem. O fluxo é o real
 * (consulta, lista, seleção, criação do pedido) sobre o Oracle de teste; só a API de arquivos é simulada, e é ela
 * quem confere o pool do SIGA no instante do "download".
 */
@Testcontainers(disabledWithoutDocker = true)
class ConexaoDoSigaLiberadaTest {

    private static HikariDataSource dataSource;

    @TempDir
    Path pasta;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    @Test
    void duranteODownloadESuaMontagemOPoolDoSigaFicaSemConexaoAtiva() throws IOException {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                return new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
            }

            @Override
            public String token() {
                return "token";
            }
        };
        RequerimentosDoSigaJdbc requerimentos = new RequerimentosDoSigaJdbc(jdbc);
        PoliticaDeAcesso politica = new PoliticaDeAcesso(atual);
        ObterRequerimento obterRequerimento = new ObterRequerimento(requerimentos,
                new ObjetoEPrioridadeDoSigaJdbc(jdbc), new TarefaAtualDoSigaJdbc(jdbc), politica);
        VerificarAcessoAoRequerimento acesso = new VerificarAcessoAoRequerimento(requerimentos, politica);
        DocumentosDoSigaJdbc documentos = new DocumentosDoSigaJdbc(jdbc, EnumSet.allOf(OrigemDocumento.class));
        SelecionarDocumentosParaDossie selecao = new SelecionarDocumentosParaDossie(acesso,
                new ListarDocumentos(acesso, documentos, politica, ParametrosDeConversaoDeTeste.ligada()), politica,
                ParametrosDeConversaoDeTeste.ligada());

        Map<String, Integer> conexoesAtivasVistasNoDownload = new ConcurrentHashMap<>();
        AtomicInteger downloads = new AtomicInteger();
        ArquivosDoRepositorio arquivos = (chave, destino) -> {
            conexoesAtivasVistasNoDownload.put(chave, dataSource.getHikariPoolMXBean().getActiveConnections());
            downloads.incrementAndGet();
            gravar(destino, "pdf");
            return 1;
        };
        ValidadorDePdf validador = arquivo -> {
            conexoesAtivasVistasNoDownload.put("validacao-" + arquivo.getFileName(), dataSource.getHikariPoolMXBean().getActiveConnections());
            return 1;
        };
        MontadorDoDossie montador = (capa, docs, inventario, opcoes, destino) -> {
            conexoesAtivasVistasNoDownload.put("montagem", dataSource.getHikariPoolMXBean().getActiveConnections());
            gravar(destino, "dossie");
            return docs.size() + 1;
        };
        Map<String, PedidoDeDossie> pedidos = new ConcurrentHashMap<>();
        RepositorioDePedidos repositorio = new RepositorioDePedidos() {
            @Override
            public void salvar(PedidoDeDossie pedido) {
                pedidos.put(pedido.id(), pedido);
            }

            @Override
            public Optional<PedidoDeDossie> buscar(String id) {
                return Optional.ofNullable(pedidos.get(id));
            }

            @Override
            public List<PedidoDeDossie> todos() {
                return new ArrayList<>(pedidos.values());
            }

            @Override
            public void remover(String id) {
                pedidos.remove(id);
            }
        };
        ParametrosDoDossie parametros = new ParametrosDoDossie() {
            @Override
            public int ttlMinutos() {
                return 30;
            }

            @Override
            public int threads() {
                return 1;
            }

            @Override
            public int downloadTotalMaximoSegundos() {
                return 900;
            }

            @Override
            public long espacoMinimoMb() {
                return 0;
            }

            @Override
            public int downloadTimeoutSegundos() {
                return 60;
            }

            @Override
            public boolean sumario() {
                return true;
            }

            @Override
            public boolean marcadores() {
                return true;
            }

            @Override
            public boolean numeracao() {
                return true;
            }

            @Override
            public Path diretorio() {
                return pasta;
            }
        };
        java.time.Clock relogio = java.time.Clock.systemDefaultZone();
        GeradorDeDossie gerador = new GeradorDeDossie(arquivos,
                new PreparadorDePdf(validador, null, null, ParametrosDeConversaoDeTeste.ligada()), montador, parametros,
                d -> Long.MAX_VALUE, relogio);
        CriarDossie criar = new CriarDossie(obterRequerimento, selecao, atual, repositorio, Runnable::run, gerador,
                parametros, relogio);

        String id = criar.executar(101, List.of(new ReferenciaDeDocumento("DOCUMENTO_PROCESSO", "1"),
                new ReferenciaDeDocumento("DOCUMENTO_REQUERIMENTO", "13"), new ReferenciaDeDocumento("COMPLEMENTACAO", "21")));

        assertThat(pedidos.get(id).status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(downloads.get()).isEqualTo(3);
        assertThat(conexoesAtivasVistasNoDownload).isNotEmpty().containsKeys("montagem", "K-DP-1", "K-DR-13", "K-CO-21");
        assertThat(conexoesAtivasVistasNoDownload.values()).as("conexões ativas do SIGA durante download, validação e montagem")
                .containsOnly(0);
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private static void gravar(Path destino, String conteudo) {
        try {
            Files.writeString(destino, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
