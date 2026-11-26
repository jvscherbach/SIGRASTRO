package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS11.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.caixa.application.LerPosseEmLotes;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Leitura da posse dos analistas no Oracle fictício (S11, bloco 4), com o usuário de leitura: os candidatos e o detalhe de
 * cada cenário CX01 a CX17, a correção que continua sendo do analista (e a de quem nunca analisou), o lote de 1.200 em três
 * consultas de detalhe, o teto, nenhum CPF na resposta nem no SQL enviado e o banco fora do ar.
 *
 * <p>As fixtures das outras sprints também usam os "Servidor Exemplo 01 a 03": os testes só olham os ids 15001 a 15099
 * (cenários) e 16001 a 17200 (volume).
 */
@Testcontainers(disabledWithoutDocker = true)
class PosseDoAnalistaNoSigaJdbcTest {

    private static final Duration TEMPO = Duration.ofSeconds(60);
    private static final byte[] SAL = "sal-de-teste-0123456789".getBytes(StandardCharsets.UTF_8);

    private static final List<String> SQL_ENVIADO = new CopyOnWriteArrayList<>();
    private static HikariDataSource leitura;
    private static PosseDoAnalistaNoSigaJdbc posse;
    private static DefinicaoDaPosse definicao;
    private static AnalistaParaLeitura analista1;
    private static AnalistaParaLeitura analista2;
    private static AnalistaParaLeitura analista3;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.cenariosDaCaixa()).isNotEmpty();
        assertThat(SigaOracleTeste.volumeDaCaixa()).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        posse = new PosseDoAnalistaNoSigaJdbc(new NamedParameterJdbcTemplate(contando(leitura)));
        definicao = DefinicaoDaPosse.doCatalogo(CatalogoDeEtapasDeTeste.padrao());
        analista1 = analista(A1);
        analista2 = analista(A2);
        analista3 = analista(A3);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    @BeforeEach
    void zerar() {
        SQL_ENVIADO.clear();
    }

    private static AnalistaParaLeitura analista(String cpf) {
        IdentificadorDoResponsavel id = IdentificadorDoResponsavel.de(cpf).orElseThrow();
        return new AnalistaParaLeitura(ChaveDoResponsavel.de(id, SAL), id);
    }

    private static boolean doCenario(long id) {
        return id >= 15001 && id <= 15099;
    }

    private static boolean doVolume(long id) {
        return id >= VOLUME_PRIMEIRO && id < VOLUME_PRIMEIRO + VOLUME_TOTAL;
    }

    private static List<Long> candidatosDoCenario(AnalistaParaLeitura... analistas) {
        return posse.candidatos(List.of(analistas), definicao, 20_000, TEMPO).stream()
                .filter(PosseDoAnalistaNoSigaJdbcTest::doCenario).toList();
    }

    private static PosseLida detalhe(long id) {
        LeituraDaPosse lida = posse.ler(List.of(id), List.of(analista1, analista2, analista3), definicao, TEMPO);
        assertThat(lida.incompleta()).isFalse();
        return lida.requerimentos().stream().filter(p -> p.idRequerimento() == id).findFirst().orElse(null);
    }

    // ---- definição pelo catálogo ----

    @Test
    void aDefinicaoVemDoCatalogoSoComOHidrico() {
        assertThat(definicao.chavesDosModelos()).containsExactly("procOutorgaAguaSupSub");
        assertThat(definicao.atividadesDePosse()).containsExactlyInAnyOrder("taskAnalisarRequerimento",
                "taskRevisarParecerTecnico");
        assertThat(definicao.atividadesDeCorrecao()).contains("taskCorrecaoRequerimento");
    }

    // ---- candidatos ----

    @Test
    void osCandidatosDeCadaAnalistaSaoOsDaPosseEOsDaCorrecaoDele() {
        assertThat(candidatosDoCenario(analista1)).containsExactly(CX01, CX03, CX04, CX05, CX06, CX07, CX08, CX09, CX14,
                CX17);
        assertThat(candidatosDoCenario(analista2)).containsExactly(CX02, CX13);
        assertThat(candidatosDoCenario(analista3)).containsExactly(CX16);
        assertThat(candidatosDoCenario(analista1, analista2, analista3)).as("os que saíram da posse ou nunca foram")
                .doesNotContain(CX10, CX11, CX12, CX15);
    }

    @Test
    void oTetoCortaOsCandidatosEmOrdemDeId() {
        List<Long> tres = posse.candidatos(List.of(analista1, analista2, analista3), definicao, 3, TEMPO);
        assertThat(tres).hasSize(3).isSorted();
    }

    @Test
    void semAnalistaOuSemDefinicaoNaoConsulta() {
        assertThat(posse.candidatos(List.of(), definicao, 10, TEMPO)).isEmpty();
        assertThat(posse.candidatos(List.of(analista1), new DefinicaoDaPosse(java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of()), 10, TEMPO)).isEmpty();
        assertThat(posse.ler(List.of(), List.of(analista1), definicao, TEMPO)).isEqualTo(LeituraDaPosse.NENHUMA);
        assertThat(SQL_ENVIADO).isEmpty();
    }

    // ---- detalhe ----

    @Test
    void aAnaliseEmAndamentoTemATarefaAtivaDoAnalista() {
        PosseLida cx01 = detalhe(CX01);
        assertThat(cx01.chaveDoModelo()).isEqualTo("procOutorgaAguaSupSub");
        assertThat(cx01.tarefasAtivas()).singleElement().satisfies(t -> {
            assertThat(t.atividade()).isEqualTo("taskAnalisarRequerimento");
            assertThat(t.responsavel()).contains(analista1.chave());
            assertThat(t.inicio()).isEqualTo(a(20));
        });
        assertThat(cx01.ultimaDeAnalise()).as("a análise ainda não foi concluída").isEmpty();
        assertThat(cx01.interessado()).contains(NOME_DO_P1);
        assertThat(cx01.numero()).contains(CX01 + "/2028");
    }

    @Test
    void aRevisaoComOAnalistaEAMudancaDentroDaPosseTrazAUltimaAnalise() {
        PosseLida cx03 = detalhe(CX03);
        assertThat(cx03.tarefasAtivas()).singleElement().satisfies(t -> {
            assertThat(t.atividade()).isEqualTo("taskRevisarParecerTecnico");
            assertThat(t.responsavel()).contains(analista1.chave());
            assertThat(t.inicio()).isEqualTo(a(6));
        });
        assertThat(cx03.ultimaDeAnalise()).hasValueSatisfying(u -> {
            assertThat(u.atividade()).isEqualTo("taskAnalisarRequerimento");
            assertThat(u.responsavel()).contains(analista1.chave());
            assertThat(u.fim()).isEqualTo(a(6));
        });
        PosseLida cx02 = detalhe(CX02);
        assertThat(cx02.tarefasAtivas()).singleElement().satisfies(t -> assertThat(t.responsavel())
                .contains(analista2.chave()));
    }

    @Test
    void aCorrecaoContinuaSendoDoAnalistaQueFezAUltimaAnalise() {
        PosseLida cx04 = detalhe(CX04);
        assertThat(cx04.tarefasAtivas()).singleElement().satisfies(t -> {
            assertThat(t.atividade()).isEqualTo("taskCorrecaoRequerimento");
            assertThat(t.responsavel()).as("a tarefa é do interessado, não de um analista").isEmpty();
            assertThat(t.inicio()).isEqualTo(a(12).plusHours(1));
        });
        assertThat(cx04.ultimaDeAnalise()).hasValueSatisfying(u -> assertThat(u.responsavel())
                .contains(analista1.chave()));
        assertThat(detalhe(CX13).ultimaDeAnalise()).hasValueSatisfying(u -> assertThat(u.responsavel())
                .contains(analista2.chave()));
    }

    @Test
    void aCorrecaoSemNenhumaAnaliseAntesNaoTemUltimaAnalise() {
        PosseLida cx15 = detalhe(CX15);
        assertThat(cx15.tarefasAtivas()).singleElement().satisfies(t -> assertThat(t.atividade())
                .isEqualTo("taskCorrecaoRequerimento"));
        assertThat(cx15.ultimaDeAnalise()).isEmpty();
        assertThat(posse.candidatos(List.of(analista1, analista2, analista3), definicao, 20_000, TEMPO)).doesNotContain(CX15);
    }

    @Test
    void osRetornosVoltamParaAAnaliseOuARevisaoDoAnalista() {
        for (long id : List.of(CX05, CX07, CX08, CX09, CX17)) {
            assertThat(detalhe(id).tarefasAtivas()).as("CX " + id).singleElement().satisfies(t -> {
                assertThat(t.atividade()).isEqualTo("taskAnalisarRequerimento");
                assertThat(t.responsavel()).contains(analista1.chave());
            });
        }
        assertThat(detalhe(CX06).tarefasAtivas()).singleElement().satisfies(t -> assertThat(t.atividade())
                .isEqualTo("taskRevisarParecerTecnico"));
    }

    @Test
    void quemSaiuDaPosseNaoVoltaNoDetalhe() {
        LeituraDaPosse lida = posse.ler(List.of(CX10, CX11, CX12), List.of(analista1), definicao, TEMPO);
        assertThat(lida.requerimentos()).as("validação, emissão do ato e concluído").isEmpty();
    }

    @Test
    void oAnoAnteriorEntraSemRecorteDeAno() {
        assertThat(candidatosDoCenario(analista1)).contains(CX14);
        assertThat(detalhe(CX14).tarefasAtivas()).singleElement().satisfies(t -> assertThat(t.inicio())
                .isEqualTo(a(400)));
    }

    @Test
    void oInteressadoDeDoisRequerimentosVemPelosDois() {
        assertThat(detalhe(CX01).interessado()).contains(NOME_DO_P1);
        assertThat(detalhe(CX16).interessado()).contains(NOME_DO_P1);
        assertThat(detalhe(CX02).interessado()).contains(NOME_DO_P2);
        assertThat(detalhe(CX04).interessado()).contains(NOME_DO_P4);
    }

    @Test
    void maisDeQuinhentosIdsNoDetalheSaoRecusados() {
        List<Long> demais = LongStream.rangeClosed(1, 501).boxed().toList();
        assertThatThrownBy(() -> posse.ler(demais, List.of(analista1), definicao, TEMPO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SQL_ENVIADO).isEmpty();
    }

    // ---- volume: 1.200 em lotes ----

    @Test
    void milEDuzentosRequerimentosSaoLidosEmLotesDeQuinhentosSemLacoPorLinha() {
        List<AnalistaParaLeitura> todos = List.of(analista1, analista2, analista3);
        List<Long> candidatos = posse.candidatos(todos, definicao, 20_000, TEMPO);
        assertThat(candidatos.stream().filter(PosseDoAnalistaNoSigaJdbcTest::doVolume)).hasSize(VOLUME_TOTAL);
        SQL_ENVIADO.clear();

        LeituraDaPosse lida = new LerPosseEmLotes(posse, limite()).ler(todos, definicao, 20_000, TEMPO);

        long consultasDeDetalhe = SQL_ENVIADO.stream().filter(s -> s.contains("ULTIMA_ATIVIDADE")).count();
        assertThat(consultasDeDetalhe).as("uma consulta por lote de 500 candidatos")
                .isEqualTo((candidatos.size() + 499) / 500);
        assertThat(SQL_ENVIADO).as("1 de candidatos + os lotes de detalhe, nada por linha")
                .hasSize((int) consultasDeDetalhe + 1);
        List<PosseLida> doVolume = lida.requerimentos().stream().filter(p -> doVolume(p.idRequerimento())).toList();
        assertThat(doVolume).hasSize(VOLUME_TOTAL);
        List<AnalistaParaLeitura> ordem = List.of(analista1, analista2, analista3);
        for (PosseLida p : doVolume) {
            int i = (int) (p.idRequerimento() - VOLUME_PRIMEIRO);
            assertThat(p.tarefasAtivas()).singleElement().satisfies(t -> assertThat(t.responsavel())
                    .contains(ordem.get(i % 3).chave()));
            assertThat(p.interessado()).contains(NOME_DO_P_VOLUME);
        }
        Map<ChaveDoResponsavel, Long> porAnalista = doVolume.stream().collect(Collectors.groupingBy(
                p -> p.tarefasAtivas().getFirst().responsavel().orElseThrow(), Collectors.counting()));
        assertThat(porAnalista.values()).containsOnly((long) VOLUME_POR_ANALISTA);
    }

    // ---- CPF ----

    @Test
    void nenhumCpfNaRespostaNemNoSqlEnviado() {
        List<AnalistaParaLeitura> todos = List.of(analista1, analista2, analista3);
        List<Long> ids = CASOS.stream().map(Caso::id).toList();
        LeituraDaPosse lida = posse.ler(ids, todos, definicao, TEMPO);
        assertThat(lida.requerimentos()).isNotEmpty();
        String resposta = lida.toString();
        for (String cpf : List.of(A1, A2, A3, CamundaFicticioBuilder.CPF_DO_INTERESSADO,
                CamundaFicticioBuilder.CPF_SEM_SERVIDOR, CamundaFicticioBuilder.cpfDoServidor(4))) {
            assertThat(resposta).as("CPF " + cpf.length() + " dígitos").doesNotContain(cpf);
        }
        assertThat(resposta).doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
        assertThat(SQL_ENVIADO).isNotEmpty().allSatisfy(sql -> assertThat(sql).doesNotContain(A1).doesNotContain(A2)
                .doesNotContain(A3));
        // quem não é analista pedido aparece sem responsável (a tarefa do interessado em correção)
        assertThat(lida.requerimentos().stream().filter(p -> p.idRequerimento() == CX04).findFirst().orElseThrow()
                .tarefasAtivas().getFirst().responsavel()).isEqualTo(Optional.empty());
    }

    @Test
    void aChaveOpacaNaoRevelaOCpfEMudaComOSal() {
        ChaveDoResponsavel comUmSal = analista1.chave();
        ChaveDoResponsavel comOutroSal = ChaveDoResponsavel.de(analista1.identificador(),
                "outro-sal-de-teste-0123".getBytes(StandardCharsets.UTF_8));
        assertThat(comUmSal.valor()).hasSize(16).doesNotContain(A1);
        assertThat(comUmSal).isNotEqualTo(comOutroSal);
        assertThat(comUmSal).isEqualTo(analista(A1).chave());
        assertThat(analista1.identificador().toString()).doesNotContain(A1);
    }

    // ---- banco fora do ar ----

    @Test
    void bancoForaDoArFalhaSemResultadoParcial() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            PosseDoAnalistaNoSigaJdbc indisponivel = new PosseDoAnalistaNoSigaJdbc(new NamedParameterJdbcTemplate(fora));
            Duration curto = Duration.ofSeconds(1);
            assertThatThrownBy(() -> indisponivel.candidatos(List.of(analista1), definicao, 10, curto))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> indisponivel.ler(List.of(CX01), List.of(analista1), definicao, curto))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    // ---- apoio ----

    private static LimiteDeLeiturasDoCamunda limite() {
        return new LimiteDeLeiturasDoCamunda(new ParametrosDoRastro() {
            @Override
            public boolean mostrarNomeDoResponsavel() {
                return true;
            }

            @Override
            public int maximoDeNomesPorEquipe() {
                return 10;
            }

            @Override
            public int cacheDaPosicaoSegundos() {
                return 0;
            }

            @Override
            public int posicoesSimultaneas() {
                return 1;
            }

            @Override
            public int camundaTimeoutSegundos() {
                return 10;
            }
        });
    }

    /** Guarda cada SQL preparado (uma consulta). */
    private static DataSource contando(DataSource alvo) {
        ClassLoader cl = PosseDoAnalistaNoSigaJdbcTest.class.getClassLoader();
        return (DataSource) Proxy.newProxyInstance(cl, new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
            Object resultado = invocar(alvo, metodo, args);
            if (resultado instanceof Connection conexao) {
                return Proxy.newProxyInstance(cl, new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                        SQL_ENVIADO.add(a2 != null && a2.length > 0 ? String.valueOf(a2[0]) : "");
                    }
                    return invocar(conexao, m2, a2);
                });
            }
            return resultado;
        });
    }

    private static Object invocar(Object alvo, java.lang.reflect.Method metodo, Object[] args) throws Throwable {
        try {
            return metodo.invoke(alvo, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
