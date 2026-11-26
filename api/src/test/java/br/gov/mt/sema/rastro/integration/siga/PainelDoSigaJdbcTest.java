package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS10.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.painel.application.LerTarefasEmLotes;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.ContagemDoMes;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.RequerimentoDaBase;
import br.gov.mt.sema.rastro.painel.domain.ResponsavelDaTarefa;
import br.gov.mt.sema.rastro.painel.domain.TarefaAtiva;
import br.gov.mt.sema.rastro.tramitacao.application.LimiteDeLeiturasDoCamunda;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Leitura agregada do painel no Oracle fictício (S10, bloco 2), com o usuário de leitura: a série por mês contra PN01 a
 * PN04 (o de 2025 fora, o sem data só no aviso), o recorte por intervalo nas viradas de ano (UTC × Cuiabá e o fuso da
 * JVM), a base com o teto, as tarefas ativas de PN05 a PN15, o PN16 em 3 lotes, o SQL enviado sem a coluna do CPF fora da
 * resolução do nome e nenhum CPF na resposta, e o banco fora do ar ou mudo.
 *
 * <p>As fixtures da S7 a S9 também estão no banco (e várias sem data de protocolo): os números do painel são conferidos como
 * "o que o adaptador leu" menos "o que um cálculo independente acha fora da faixa 10001 a 12999", que tem de dar exatamente
 * os totais de {@link CenariosFicticiosDaS10}.
 */
@Testcontainers(disabledWithoutDocker = true)
class PainelDoSigaJdbcTest {

    private static final long FAIXA_INICIO = 10001;
    private static final long FAIXA_FIM = 12999;
    private static final Duration TEMPO = Duration.ofSeconds(30);

    private static final List<String> SQL_ENVIADO = new CopyOnWriteArrayList<>();
    private static final List<Integer> TEMPOS_LIMITE = new CopyOnWriteArrayList<>();
    private static HikariDataSource leitura;
    private static NamedParameterJdbcTemplate jdbc;
    private static PainelDoSigaJdbc painel;
    private static CatalogoDeEtapas catalogo;
    private static List<String> modelos;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.cenariosDoPainel()).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        jdbc = new NamedParameterJdbcTemplate(leitura);
        painel = new PainelDoSigaJdbc(new NamedParameterJdbcTemplate(contando(leitura)));
        catalogo = CatalogoDeEtapasDeTeste.padrao();
        modelos = catalogo.modelos().stream().map(ModeloDeFluxo::chave).toList();
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    @BeforeEach
    void zerar() {
        SQL_ENVIADO.clear();
        TEMPOS_LIMITE.clear();
    }

    // ---- série (gráfico 1) ----

    /** Linha crua de REQUERIMENTO para o cálculo independente (Java, sem GROUP BY no banco). */
    private record Bruto(long id, LocalDateTime protocolo, LocalDateTime alteracao, LocalDateTime abertura,
            String situacao) {
    }

    private static List<Bruto> brutos() {
        return jdbc.query("SELECT ID, DATA_PROTOCOLO_PROCESSO, DATA_ALTERACAO, DATA_ABERTURA, SITUACAO_PROCESSO "
                + "FROM SIGA2_LICENCIAMENTO.REQUERIMENTO", new MapSqlParameterSource(),
                (rs, n) -> new Bruto(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                        rs.getObject(3, LocalDateTime.class), rs.getObject(4, LocalDateTime.class), rs.getString(5)));
    }

    private static boolean naFaixa(Bruto b) {
        return b.id() >= FAIXA_INICIO && b.id() <= FAIXA_FIM;
    }

    private static boolean noAno(LocalDateTime data, int ano) {
        return data != null && data.getYear() == ano;
    }

    /** Série esperada, calculada em Java a partir das linhas cruas. */
    private record Esperada(Map<Optional<YearMonth>, Long> protocolados, Map<Optional<YearMonth>, Long> concluidos,
            long semData) {

        static Esperada de(List<Bruto> linhas, int ano) {
            Map<Optional<YearMonth>, Long> p = linhas.stream().filter(b -> noAno(b.protocolo(), ano))
                    .collect(Collectors.groupingBy(b -> Optional.of(YearMonth.from(b.protocolo())), Collectors.counting()));
            Map<Optional<YearMonth>, Long> c = linhas.stream()
                    .filter(b -> noAno(b.protocolo(), ano) && "CONCLUIDO".equals(b.situacao()))
                    .collect(Collectors.groupingBy(b -> Optional.ofNullable(b.alteracao()).map(YearMonth::from),
                            Collectors.counting()));
            long s = linhas.stream().filter(b -> b.protocolo() == null && noAno(b.abertura(), ano)).count();
            return new Esperada(p, c, s);
        }

        static Esperada de(LeituraDaSerie lida) {
            return new Esperada(mapa(lida.protocolados()), mapa(lida.concluidos()), lida.semDataDeProtocolo());
        }

        private static Map<Optional<YearMonth>, Long> mapa(List<ContagemDoMes> contagens) {
            Map<Optional<YearMonth>, Long> m = new HashMap<>();
            contagens.forEach(c -> m.merge(c.mes(), c.total(), Long::sum));
            return m;
        }

        /** Esta menos a outra, mês a mês (mês zerado sai do mapa). */
        Esperada menos(Esperada outra) {
            return new Esperada(diferenca(protocolados, outra.protocolados), diferenca(concluidos, outra.concluidos),
                    semData - outra.semData);
        }

        private static Map<Optional<YearMonth>, Long> diferenca(Map<Optional<YearMonth>, Long> a,
                Map<Optional<YearMonth>, Long> b) {
            Map<Optional<YearMonth>, Long> r = new HashMap<>(a);
            b.forEach((k, v) -> r.merge(k, -v, Long::sum));
            r.values().removeIf(v -> v == 0);
            return r;
        }
    }

    private static Map<Optional<YearMonth>, Long> porMes(int ano, int[] meses) {
        Map<Optional<YearMonth>, Long> m = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            if (meses[i] != 0) {
                m.put(Optional.of(YearMonth.of(ano, i + 1)), (long) meses[i]);
            }
        }
        return m;
    }

    @Test
    void serie_de_2026_tem_exatamente_os_numeros_da_populacao() {
        LeituraDaSerie lida = painel.lerSerie(new AnoDoPainel(ANO), TEMPO);
        List<Bruto> linhas = brutos();
        Esperada doAdaptador = Esperada.de(lida);
        assertThat(doAdaptador).as("adaptador = cálculo independente sobre o banco inteiro")
                .isEqualTo(Esperada.de(linhas, ANO));

        Esperada daPopulacao = doAdaptador.menos(Esperada.de(linhas.stream().filter(b -> !naFaixa(b)).toList(), ANO));
        int[] protocolados = new int[12];
        for (int i = 0; i < 12; i++) {
            protocolados[i] = PROTOCOLADOS_POR_MES_SEM_VOLUME[i] + PN16_POR_MES;
        }
        assertThat(daPopulacao.protocolados()).isEqualTo(porMes(ANO, protocolados));
        assertThat(daPopulacao.concluidos()).as("PN02; o PN03 de 2025, concluído em 2026, não conta")
                .isEqualTo(porMes(ANO, CONCLUIDOS_POR_MES));
        assertThat(daPopulacao.semData()).as("PN03 sem data de protocolo").isEqualTo(1);
        assertThat(daPopulacao.protocolados().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(BASE_DO_ANO);
        // uma consulta, com tempo limite
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(TEMPOS_LIMITE).containsExactly(30);
    }

    @Test
    void o_de_2025_so_aparece_no_ano_de_2025_e_janeiro_comeca_a_meia_noite() {
        Esperada de2025 = Esperada.de(painel.lerSerie(new AnoDoPainel(2025), TEMPO));
        List<Bruto> foraDaFaixa = brutos().stream().filter(b -> !naFaixa(b)).toList();
        Esperada daPopulacao = de2025.menos(Esperada.de(foraDaFaixa, 2025));
        assertThat(daPopulacao.protocolados()).as("31/12/2025 23:59:59 é de 2025")
                .isEqualTo(Map.of(Optional.of(YearMonth.of(2025, 12)), 1L));
        assertThat(daPopulacao.concluidos()).as("concluído em fev/2026, mas da base de 2025")
                .isEqualTo(Map.of(Optional.of(YearMonth.of(2026, 2)), 1L));

        Esperada de2026 = Esperada.de(painel.lerSerie(new AnoDoPainel(ANO), TEMPO))
                .menos(Esperada.de(foraDaFaixa, ANO));
        assertThat(de2026.protocolados().get(Optional.of(YearMonth.of(ANO, 1))))
                .as("PN01 de 1º/jan 00:00 entra em janeiro").isEqualTo(PROTOCOLADOS_POR_MES_SEM_VOLUME[0] + PN16_POR_MES);
        assertThat(de2026.protocolados()).doesNotContainKey(Optional.of(YearMonth.of(2025, 12)));
    }

    @Test
    void ano_corrente_pelo_relogio_de_cuiaba_na_virada_e_qualquer_fuso_da_jvm() {
        assertThat(AnoDoPainel.corrente(RELOGIO)).isEqualTo(new AnoDoPainel(2026));
        assertThat(AnoDoPainel.corrente(RELOGIO_VIRADA_PARA_2027_NO_UTC)).as("em UTC já é 2027").isEqualTo(new AnoDoPainel(2026));
        assertThat(AnoDoPainel.corrente(RELOGIO_VIRADA_PARA_2026_NO_UTC)).as("em UTC já é 2026").isEqualTo(new AnoDoPainel(2025));

        LeituraDaSerie referencia = painel.lerSerie(AnoDoPainel.corrente(RELOGIO_VIRADA_PARA_2027_NO_UTC), TEMPO);
        TimeZone original = TimeZone.getDefault();
        try {
            for (String fuso : List.of("UTC", "Asia/Tokyo", "Pacific/Kiritimati", "America/Cuiaba")) {
                TimeZone.setDefault(TimeZone.getTimeZone(fuso));
                assertThat(Esperada.de(painel.lerSerie(AnoDoPainel.corrente(RELOGIO_VIRADA_PARA_2027_NO_UTC), TEMPO)))
                        .as(fuso).isEqualTo(Esperada.de(referencia));
                assertThat(painel.lerBase(AnoDoPainel.corrente(RELOGIO_VIRADA_PARA_2026_NO_UTC), 20_000, TEMPO)
                        .requerimentos()).as(fuso).extracting(RequerimentoDaBase::id).contains(PN03_DE_2025)
                        .doesNotContain(PN01_JAN);
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ---- base (gráficos 2 e 3) ----

    private static List<Long> idsDaPopulacao(BaseDoAno base) {
        return base.requerimentos().stream().map(RequerimentoDaBase::id)
                .filter(id -> id >= FAIXA_INICIO && id <= FAIXA_FIM).toList();
    }

    @Test
    void base_de_2026_por_id_com_tipo_e_situacao() {
        BaseDoAno base = painel.lerBase(new AnoDoPainel(ANO), 20_000, TEMPO);
        assertThat(base.truncado()).isFalse();
        assertThat(base.requerimentos()).extracting(RequerimentoDaBase::id).isSorted().doesNotHaveDuplicates();
        List<Long> daPopulacao = idsDaPopulacao(base);
        assertThat(daPopulacao).hasSize(BASE_DO_ANO).doesNotContain(PN03_DE_2025, PN03_SEM_DATA)
                .contains(PN01_JAN, PN01_DEZ, PN04_CANCELADO, PN14_FORA_DO_CATALOGO, PN15_SEM_INSTANCIA);
        Map<Long, RequerimentoDaBase> porId = base.requerimentos().stream()
                .collect(Collectors.toMap(RequerimentoDaBase::id, r -> r));
        assertThat(porId.get(PN06_ENVIO_DO_TERMO).tipoDeProcesso()).contains("LICENCIAMENTO_ADESAO");
        assertThat(porId.get(PN05_ANALISE_SERVIDOR_01).tipoDeProcesso()).contains("RECURSO_HIDRICO");
        assertThat(porId.get(PN14_FORA_DO_CATALOGO).tipoDeProcesso()).contains("LICENCIAMENTO_TRIFASICO_FICTICIO");
        assertThat(porId.get(PN02_AGO).situacao()).contains("CONCLUIDO");
        assertThat(porId.get(PN04_ARQUIVADO).situacao()).contains("ARQUIVADO_DEFINITIVAMENTE");
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(TEMPOS_LIMITE).containsExactly(30);
    }

    @Test
    void teto_traz_os_primeiros_por_id_e_liga_truncado() {
        BaseDoAno inteira = painel.lerBase(new AnoDoPainel(ANO), 20_000, TEMPO);
        int total = inteira.requerimentos().size();
        assertThat(total).isGreaterThan(1_000);

        BaseDoAno cortada = painel.lerBase(new AnoDoPainel(ANO), 1_000, TEMPO);
        assertThat(cortada.truncado()).isTrue();
        assertThat(cortada.requerimentos()).hasSize(1_000).isEqualTo(inteira.requerimentos().subList(0, 1_000));

        BaseDoAno justa = painel.lerBase(new AnoDoPainel(ANO), total, TEMPO);
        assertThat(justa.truncado()).as("exatamente o teto não é truncado").isFalse();
        assertThat(justa.requerimentos()).hasSize(total);
    }

    // ---- tarefas ativas (fila e carga) ----

    private static LeituraDasTarefas tarefas(List<Long> ids) {
        return painel.ler(ids, modelos, TEMPO);
    }

    private static List<TarefaAtiva> doRequerimento(LeituraDasTarefas lidas, long id) {
        return lidas.tarefas().stream().filter(t -> t.idRequerimento() == id)
                .sorted(java.util.Comparator.comparing(TarefaAtiva::idDaTarefa)).toList();
    }

    private static String chave(String instancia) {
        return catalogo.modeloDaInstancia(instancia).orElseThrow().chave();
    }

    @Test
    void pn05_a_pn15_tarefas_ativas_da_raiz_com_o_nome_resolvido() {
        List<Long> ids = List.of(PN04_CANCELADO, PN04_ARQUIVADO, PN05_ANALISE_SERVIDOR_01, PN05_REVISAO_SERVIDOR_02,
                PN05_VALIDACAO_SERVIDOR_03, PN05_ANALISE_SERVIDOR_01_B, PN05_ACEITE_SERVIDOR_02, PN06_ENVIO_DO_TERMO,
                PN06_CONFERENCIA_DE_GRUPO, PN07_PARALELA, PN08_COMPLEMENTACAO, PN09_GRUPO, PN10_INTERESSADO,
                PN11_SEM_ATRIBUICAO, PN12_SEM_SERVIDOR, PN13_NAO_MAPEADA, PN14_FORA_DO_CATALOGO, PN15_SEM_INSTANCIA);
        LeituraDasTarefas lidas = tarefas(ids);
        String hidrico = chave("RECURSO_HIDRICO");
        String lac = chave("LICENCIAMENTO_ADESAO");

        assertThat(lidas.alemDoLimite()).isFalse();
        assertThat(SQL_ENVIADO).hasSize(1);
        assertThat(lidas.comInstancia()).as("PN04 encerrados, PN14 e PN15 sem instância")
                .doesNotContain(PN04_CANCELADO, PN04_ARQUIVADO, PN14_FORA_DO_CATALOGO, PN15_SEM_INSTANCIA)
                .hasSize(ids.size() - 4);

        assertThat(doRequerimento(lidas, PN05_ANALISE_SERVIDOR_01)).containsExactly(new TarefaAtiva(
                PN05_ANALISE_SERVIDOR_01, hidrico, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR,
                Optional.of("Servidor Exemplo 01"), List.of()));
        assertThat(doRequerimento(lidas, PN05_REVISAO_SERVIDOR_02)).singleElement()
                .satisfies(t -> assertThat(t.nome()).contains("Servidor Exemplo 02"));
        assertThat(doRequerimento(lidas, PN05_VALIDACAO_SERVIDOR_03)).singleElement()
                .satisfies(t -> assertThat(t.nome()).contains("Servidor Exemplo 03"));
        assertThat(doRequerimento(lidas, PN05_ACEITE_SERVIDOR_02)).singleElement()
                .satisfies(t -> assertThat(t.idDaTarefa()).isEqualTo("taskAceitarAtribuicao"));

        assertThat(doRequerimento(lidas, PN06_ENVIO_DO_TERMO)).containsExactly(new TarefaAtiva(PN06_ENVIO_DO_TERMO, lac,
                "taskEnviarTermoAdesao", ResponsavelDaTarefa.INTERESSADO, Optional.empty(), List.of()));
        assertThat(doRequerimento(lidas, PN06_CONFERENCIA_DE_GRUPO)).singleElement().satisfies(t -> {
            assertThat(t.chaveDoModelo()).isEqualTo(lac);
            assertThat(t.responsavel()).isEqualTo(ResponsavelDaTarefa.NAO_ATRIBUIDA);
            assertThat(t.grupos()).containsExactly("GrpProcResponsavelAtendimentoLac");
        });

        assertThat(doRequerimento(lidas, PN07_PARALELA)).extracting(TarefaAtiva::idDaTarefa, TarefaAtiva::responsavel)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR),
                        org.assertj.core.groups.Tuple.tuple("taskCorrecaoRequerimento", ResponsavelDaTarefa.INTERESSADO));

        assertThat(doRequerimento(lidas, PN08_COMPLEMENTACAO)).as("a complementação (raiz à parte) não vira tarefa")
                .containsExactly(new TarefaAtiva(PN08_COMPLEMENTACAO, hidrico, "taskAnalisarRequerimento",
                        ResponsavelDaTarefa.SERVIDOR, Optional.of("Servidor Exemplo 02"), List.of()));

        assertThat(doRequerimento(lidas, PN09_GRUPO)).containsExactly(new TarefaAtiva(PN09_GRUPO, hidrico,
                "taskAtribuirRequerimento", ResponsavelDaTarefa.NAO_ATRIBUIDA, Optional.empty(),
                List.of("grpSurhAtribuirRequerimentoSuperficial")));
        assertThat(doRequerimento(lidas, PN10_INTERESSADO)).containsExactly(new TarefaAtiva(PN10_INTERESSADO, hidrico,
                "taskCadastrarRequerimento", ResponsavelDaTarefa.INTERESSADO, Optional.empty(), List.of()));
        assertThat(doRequerimento(lidas, PN11_SEM_ATRIBUICAO)).containsExactly(new TarefaAtiva(PN11_SEM_ATRIBUICAO,
                hidrico, "taskRevisarParecerTecnico", ResponsavelDaTarefa.NAO_ATRIBUIDA, Optional.empty(), List.of()));
        assertThat(doRequerimento(lidas, PN12_SEM_SERVIDOR)).containsExactly(new TarefaAtiva(PN12_SEM_SERVIDOR, hidrico,
                "taskRevisarParecerTecnico", ResponsavelDaTarefa.NAO_IDENTIFICADO, Optional.empty(), List.of()));
        assertThat(doRequerimento(lidas, PN13_NAO_MAPEADA)).singleElement().satisfies(t -> {
            assertThat(t.idDaTarefa()).isEqualTo(TAREFA_NAO_MAPEADA);
            assertThat(t.chaveDoModelo()).isEqualTo(hidrico);
        });
        for (long sem : List.of(PN04_CANCELADO, PN04_ARQUIVADO, PN14_FORA_DO_CATALOGO, PN15_SEM_INSTANCIA)) {
            assertThat(doRequerimento(lidas, sem)).as("sem tarefa: %d", sem).isEmpty();
        }
    }

    @Test
    void sem_o_modelo_no_pedido_a_instancia_nao_entra() {
        LeituraDasTarefas soLac = painel.ler(List.of(PN05_ANALISE_SERVIDOR_01, PN06_ENVIO_DO_TERMO),
                List.of(chave("LICENCIAMENTO_ADESAO"), PainelDoSigaJdbc.CHAVE_COMPLEMENTACAO), TEMPO);
        assertThat(soLac.comInstancia()).containsExactly(PN06_ENVIO_DO_TERMO);
        assertThat(soLac.tarefas()).extracting(TarefaAtiva::idRequerimento).containsExactly(PN06_ENVIO_DO_TERMO);
        // só a complementação pedida: nada (a chave da complementação é sempre retirada)
        assertThat(painel.ler(List.of(PN08_COMPLEMENTACAO), List.of(PainelDoSigaJdbc.CHAVE_COMPLEMENTACAO), TEMPO))
                .isEqualTo(LeituraDasTarefas.NENHUMA);
    }

    @Test
    void pn16_em_tres_lotes_de_ate_500_pelo_limite_de_leituras() {
        List<Long> volume = LongStream.range(PN16_PRIMEIRO, PN16_PRIMEIRO + PN16_TOTAL).boxed().toList();
        LimiteDeLeiturasDoCamunda limite = new LimiteDeLeiturasDoCamunda(parametros(1));
        LeituraDasTarefas lidas = new LerTarefasEmLotes(painel, limite).ler(volume, modelos, TEMPO);

        assertThat(SQL_ENVIADO).as("1.200 ids = 3 consultas").hasSize(3);
        assertThat(TEMPOS_LIMITE).hasSize(3).allSatisfy(s -> assertThat(s).isBetween(1, 30));
        assertThat(lidas.alemDoLimite()).isFalse();
        assertThat(lidas.comInstancia()).hasSize(PN16_TOTAL);
        assertThat(lidas.tarefas()).hasSize(PN16_TOTAL);
        Map<String, Long> porEtapa = lidas.tarefas().stream()
                .collect(Collectors.groupingBy(TarefaAtiva::idDaTarefa, Collectors.counting()));
        assertThat(porEtapa).containsOnlyKeys("taskConferirDocumentosApresentados", "taskAtribuirRequerimento",
                "taskRevisarParecerTecnico", "taskCadastrarRequerimento");
        assertThat(porEtapa.values()).containsOnly((long) PN16_POR_ETAPA);
        Map<ResponsavelDaTarefa, Long> porResponsavel = lidas.tarefas().stream()
                .collect(Collectors.groupingBy(TarefaAtiva::responsavel, Collectors.counting()));
        assertThat(porResponsavel).containsEntry(ResponsavelDaTarefa.SERVIDOR, (long) PN16_POR_ETAPA)
                .containsEntry(ResponsavelDaTarefa.INTERESSADO, (long) PN16_POR_ETAPA)
                .containsEntry(ResponsavelDaTarefa.NAO_ATRIBUIDA, 2L * PN16_POR_ETAPA);
    }

    @Test
    void lote_acima_de_500_e_recusado_pelo_adaptador_e_vazio_nao_consulta() {
        List<Long> demais = LongStream.rangeClosed(1, 501).map(i -> 13_000 + i).boxed().toList();
        assertThatThrownBy(() -> painel.ler(demais, modelos, TEMPO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(painel.ler(List.of(), modelos, TEMPO)).isEqualTo(LeituraDasTarefas.NENHUMA);
        assertThat(SQL_ENVIADO).isEmpty();
    }

    // ---- CPF ----

    @Test
    void sql_enviado_so_cita_a_coluna_do_cpf_na_resolucao_do_nome_e_nada_e_escrito() {
        painel.lerSerie(new AnoDoPainel(ANO), TEMPO);
        painel.lerBase(new AnoDoPainel(ANO), 100, TEMPO);
        tarefas(List.of(PN05_ANALISE_SERVIDOR_01, PN10_INTERESSADO, PN12_SEM_SERVIDOR));
        assertThat(SQL_ENVIADO).hasSize(3).allSatisfy(sql -> {
            String codigo = PainelSqlTest.semComentarios(sql);
            String maiusculo = codigo.toUpperCase(Locale.ROOT);
            assertThat(maiusculo).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP)\\b");
            assertThat(maiusculo).doesNotContainPattern("SELECT\\s+\\*");
            assertThat(maiusculo).containsPattern("FETCH\\s+FIRST\\s+\\?");
            int inicio = codigo.indexOf("tarefa AS (");
            if (inicio < 0) {
                assertThat(maiusculo).doesNotContain("ASSIGNEE_");
            } else {
                int fim = codigo.indexOf("), servidores AS (");
                assertThat(codigo.substring(0, inicio) + codigo.substring(fim)).doesNotContain("ASSIGNEE_");
            }
        });
        assertThat(TEMPOS_LIMITE).containsOnly(30);
    }

    @Test
    void nenhum_cpf_volta_na_resposta() {
        List<Long> todos = new ArrayList<>(idsSemVolume());
        LeituraDasTarefas lidas = tarefas(todos);
        String resposta = lidas.toString() + painel.lerBase(new AnoDoPainel(ANO), 20_000, TEMPO)
                + painel.lerSerie(new AnoDoPainel(ANO), TEMPO);
        assertThat(lidas.tarefas()).isNotEmpty();
        assertThat(resposta).as("11 dígitos seguidos").doesNotContainPattern("(?<!\\d)\\d{11}(?!\\d)");
        assertThat(resposta).as("CPF com máscara").doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
        assertThat(resposta).doesNotContain(CamundaFicticioBuilder.CPF_DO_INTERESSADO)
                .doesNotContain(CamundaFicticioBuilder.CPF_SEM_SERVIDOR).doesNotContain(CamundaFicticioBuilder.cpfDoServidor(1));
        // S11, bloco 9: a base traz o NOME do interessado (para a lista por analista), nunca o CPF/CNPJ; as tarefas não.
        assertThat(lidas.toString() + painel.lerSerie(new AnoDoPainel(ANO), TEMPO))
                .as("o nome do interessado não sai com as tarefas").doesNotContain("Interessado Fictício");
        assertThat(painel.lerBase(new AnoDoPainel(ANO), 20_000, TEMPO).requerimentos())
                .extracting(r -> r.interessado().orElse("")).anyMatch(n -> n.startsWith("Interessado Fictício"));
        // e a varredura não é vazia: os nomes de servidor vêm
        assertThat(resposta).contains("Servidor Exemplo 01");
        // e nenhum componente do tipo guarda CPF
        assertThat(TarefaAtiva.class.getRecordComponents()).extracting(c -> c.getName().toLowerCase(Locale.ROOT))
                .noneMatch(n -> n.contains("cpf") || n.contains("assignee"));
    }

    // ---- falhas ----

    @Test
    void banco_fora_do_ar_lanca_para_a_atualizacao_tratar() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            PainelDoSigaJdbc indisponivel = new PainelDoSigaJdbc(new NamedParameterJdbcTemplate(fora));
            Duration curto = Duration.ofSeconds(1);
            assertThatThrownBy(() -> indisponivel.lerSerie(new AnoDoPainel(ANO), curto))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> indisponivel.lerBase(new AnoDoPainel(ANO), 100, curto))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> indisponivel.ler(List.of(PN05_ANALISE_SERVIDOR_01), modelos, curto))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void servidor_mudo_nao_segura_a_thread_alem_do_limite() throws Exception {
        try (java.net.ServerSocket mudo = new java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())) {
            Thread aceitador = Thread.ofVirtual().start(() -> {
                List<java.net.Socket> abertos = new ArrayList<>();
                try {
                    while (!mudo.isClosed()) {
                        abertos.add(mudo.accept()); // aceita e nunca responde
                    }
                } catch (java.io.IOException fim) {
                    abertos.forEach(s -> {
                        try {
                            s.close();
                        } catch (java.io.IOException ignorada) {
                            // fechando
                        }
                    });
                }
            });
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setJdbcUrl("jdbc:oracle:thin:@//127.0.0.1:" + mudo.getLocalPort() + "/MUDO");
            config.setUsername("x");
            config.setPassword("x");
            config.setConnectionTimeout(1000);
            config.setInitializationFailTimeout(-1);
            config.setMinimumIdle(0);
            config.setMaximumPoolSize(1);
            try (HikariDataSource lento = new HikariDataSource(config)) {
                PainelDoSigaJdbc semResposta = new PainelDoSigaJdbc(new NamedParameterJdbcTemplate(lento));
                Duration curto = Duration.ofSeconds(1);
                long inicio = System.nanoTime();
                assertThatThrownBy(() -> semResposta.lerSerie(new AnoDoPainel(ANO), curto))
                        .isInstanceOf(DataAccessException.class);
                assertThatThrownBy(() -> semResposta.ler(List.of(PN05_ANALISE_SERVIDOR_01), modelos, curto))
                        .isInstanceOf(DataAccessException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(10));
            }
            mudo.close();
            aceitador.join(Duration.ofSeconds(5));
        }
    }

    @Test
    void tempo_limite_de_cada_consulta_arredonda_para_cima() {
        painel.lerSerie(new AnoDoPainel(ANO), Duration.ofMillis(2_500));
        painel.lerBase(new AnoDoPainel(ANO), 10, Duration.ZERO);
        assertThat(TEMPOS_LIMITE).containsExactly(3, 1);
        assertThat(PainelDoSigaJdbc.segundos(Duration.ofSeconds(-5))).isEqualTo(1);
    }

    private static ParametrosDoRastro parametros(int leiturasSimultaneas) {
        return new ParametrosDoRastro() {
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
                return 30;
            }

            @Override
            public int leiturasSimultaneasDoCamunda() {
                return leiturasSimultaneas;
            }
        };
    }

    /** Guarda cada SQL preparado (uma consulta) e cada tempo limite aplicado a ele. */
    private static DataSource contando(DataSource alvo) {
        ClassLoader cl = PainelDoSigaJdbcTest.class.getClassLoader();
        return (DataSource) Proxy.newProxyInstance(cl, new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
            Object resultado = invocar(alvo, metodo, args);
            if (resultado instanceof Connection conexao) {
                return Proxy.newProxyInstance(cl, new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                        SQL_ENVIADO.add(a2 != null && a2.length > 0 ? String.valueOf(a2[0]) : "");
                    }
                    Object criado = invocar(conexao, m2, a2);
                    if (criado instanceof PreparedStatement comando) {
                        return Proxy.newProxyInstance(cl, new Class<?>[] {PreparedStatement.class}, (p3, m3, a3) -> {
                            if (m3.getName().equals("setQueryTimeout")) {
                                TEMPOS_LIMITE.add((Integer) a3[0]);
                            }
                            return invocar(comando, m3, a3);
                        });
                    }
                    return criado;
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
