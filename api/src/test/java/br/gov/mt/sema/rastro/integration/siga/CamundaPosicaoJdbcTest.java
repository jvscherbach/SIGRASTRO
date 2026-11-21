package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H01;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H06;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H07;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H09;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H10;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H15;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H19;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H20;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.H21;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS7.TRIFASICO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ElementoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MarcaDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.MontadorDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Posição atual lida do Camunda fictício (S7, bloco 3): um caso por cenário H01 a H21, L01 a L07 e Trifásico
 * ({@link CenariosFicticiosDaS7}), mais colisão de ids, duas instâncias, modelo errado para o tipo e contagem de consultas.
 * Usuário de leitura (só {@code SELECT}), como em produção.
 */
@Testcontainers(disabledWithoutDocker = true)
class CamundaPosicaoJdbcTest {

    /** Cenários próprios deste teste (fora da faixa 7000 a 7199 da S7). */
    private static final long DUAS_INSTANCIAS = 7200;
    private static final long MODELO_ERRADO = 7202;

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final AtomicInteger CONSULTAS = new AtomicInteger();
    private static HikariDataSource leitura;
    private static CamundaPosicaoJdbc camunda;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S7).isNotEmpty();
        criarCenariosProprios();
        leitura = SigaOracleTeste.dataSourceLeitura();
        camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(contando(leitura)), 10);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    private static void criarCenariosProprios() {
        try (HikariDataSource admin = SigaOracleTeste.dataSourceAdministrador()) {
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(admin);
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                    new MapSqlParameterSource("id", DUAS_INSTANCIAS), Integer.class);
            if (existe != null && existe > 0) {
                return;
            }
            CamundaFicticioBuilder construtor = new CamundaFicticioBuilder(jdbc, CATALOGO);
            construtor.processo(Fluxo.HIDRICO, DUAS_INSTANCIAS).situacao("EM_CONFERENCIA")
                    .naEtapa("taskConferirDocumentosApresentados").paraGrupo("grpSurhConferirDocumentoSuperficial")
                    .desde(LocalDateTime.of(2026, 5, 4, 9, 0)).gravar();
            // Instância anterior do mesmo requerimento, já encerrada: a mais recente por início é a que vale.
            jdbc.update("""
                    INSERT INTO SIGA2_CAMUNDA.ACT_HI_PROCINST
                           (ID_, PROC_INST_ID_, BUSINESS_KEY_, PROC_DEF_KEY_, PROC_DEF_ID_, START_TIME_, END_TIME_, END_ACT_ID_,
                            SUPER_PROCESS_INSTANCE_ID_, ROOT_PROC_INST_ID_, DELETE_REASON_, STATE_)
                    VALUES ('fict-7200-anterior', 'fict-7200-anterior', '7200', 'procOutorgaAguaSupSub',
                            'procOutorgaAguaSupSub:1:ficticio', :inicio, :fim, 'Event_1kq2k0z', NULL, 'fict-7200-anterior',
                            NULL, 'COMPLETED')""",
                    new MapSqlParameterSource().addValue("inicio", Timestamp.valueOf(LocalDateTime.of(2025, 1, 6, 9, 0)))
                            .addValue("fim", Timestamp.valueOf(LocalDateTime.of(2025, 2, 6, 9, 0))));
            // Instância do Hídrico num requerimento cujo tipo é LAC: com a chave do LAC nada é encontrado.
            construtor.processo(Fluxo.HIDRICO, MODELO_ERRADO).situacao("EM_CONFERENCIA")
                    .naEtapa("taskConferirDocumentosApresentados").paraGrupo("grpSurhConferirDocumentoSuperficial")
                    .desde(LocalDateTime.of(2026, 5, 5, 9, 0)).gravar();
            jdbc.update("UPDATE SIGA2_LICENCIAMENTO.REQUERIMENTO SET ID_TIPO_PROCESSO = 72 WHERE ID = :id",
                    new MapSqlParameterSource("id", MODELO_ERRADO));
        }
    }

    /** O que o caso de uso faz depois do acesso: tipo → modelo do catálogo → leitura → montagem. */
    private static PosicaoAtual posicao(long id) {
        PosicaoNoFluxo.RequerimentoNoFluxo requerimento = camunda.requerimento(id).orElseThrow();
        return CATALOGO.modeloDaInstancia(requerimento.instancia())
                .map(modelo -> MontadorDaPosicao.montar(modelo, camunda.ler(id, modelo.chave())))
                .orElseGet(PosicaoAtual::naoSuportado);
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource({
            "H01, 7001, cadastro_do_requerimento, PRINCIPAL, 0",
            "H02, 7002, conferencia_dos_documentos, PRINCIPAL, 0",
            "H03, 7003, aguardando_pagamento, PRINCIPAL, 0",
            "H04, 7004, isencao_confirmada, PRINCIPAL, 0",
            "H05, 7005, atribuicao_ao_analista, PRINCIPAL, 0",
            "H06, 7006, aceite_pelo_analista, PRINCIPAL, 0",
            "H07, 7007, analise_do_processo, PRINCIPAL, 0",
            "H08, 7008, revisao_do_parecer_tecnico, PRINCIPAL, 0",
            "H09, 7009, validacao_do_parecer_tecnico, PRINCIPAL, 0",
            "H10, 7010, emissao_do_ato, PRINCIPAL, 1",
            "H11, 7011, registro_do_comprovante_de_publicacao, PRINCIPAL, 0",
            "H12, 7012, verificacao_da_revisao_do_processo, PRINCIPAL, 0",
            "H13, 7013, correcao_do_requerimento, CORRECAO, 0",
            "H14, 7014, aguardando_pagamento_correcao, CORRECAO, 0",
            "H19, 7019, conferencia_dos_documentos, PRINCIPAL, 0",
            "H20, 7020, analise_do_processo, PRINCIPAL, 0",
            "L01, 7051, conferencia_dos_documentos, PRINCIPAL, 0",
            "L02, 7052, aguardando_pagamento, PRINCIPAL, 0",
            "L03, 7053, correcao_do_requerimento, CORRECAO, 0",
            "L04, 7054, envio_do_termo_assinado, PRINCIPAL, 0",
            "G01, 7101, conferencia_dos_documentos, PRINCIPAL, 0",
            "G02, 7102, conferencia_dos_documentos, PRINCIPAL, 0",
            "G03, 7103, revisao_do_parecer_tecnico, PRINCIPAL, 0"})
    void cenario_ativo_mostra_a_etapa_certa(String cenario, long id, String etapa, Ramo ramo, int emParalelo) {
        PosicaoAtual p = posicao(id);
        assertThat(p.estado()).as(cenario).isEqualTo(EstadoDaPosicao.ATIVO);
        assertThat(p.principal()).as(cenario).hasValueSatisfying(e -> {
            assertThat(e.etapa()).map(Etapa::id).contains(etapa);
            assertThat(e.etapa()).map(Etapa::ramo).contains(ramo);
        });
        assertThat(p.emParalelo()).as(cenario).hasSize(emParalelo);
        assertThat(p.idsNaoMapeados()).as(cenario).isEmpty();
        assertThat(p.marcas()).as(cenario).doesNotContain(MarcaDaPosicao.ETAPA_NAO_MAPEADA, MarcaDaPosicao.VARIAS_INSTANCIAS);
        if (id != H19) {
            assertThat(p.principal().orElseThrow().desde()).as(cenario).isNotNull();
        }
    }

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource({
            "H15, 7015, Concluído",
            "H16, 7016, Cancelado pelo cadastrante",
            "H17, 7017, Expirado no cadastro",
            "H18, 7018, Encerrado por taxa não paga no prazo",
            "L05, 7055, Concluído",
            "L06, 7056, Arquivado (termo não enviado no prazo)",
            "L07, 7057, Expirado na correção"})
    void cenario_encerrado_mostra_o_final(String cenario, long id, String rotulo) {
        PosicaoAtual p = posicao(id);
        assertThat(p.estado()).as(cenario).isEqualTo(EstadoDaPosicao.ENCERRADO);
        assertThat(p.fim()).as(cenario).map(FinalDoFluxo::rotulo).contains(rotulo);
        assertThat(p.encerradaEm()).as(cenario).isPresent();
        assertThat(p.principal()).as(cenario).isEmpty();
    }

    @Test
    void h01_desde_e_responsavel_do_cadastro() {
        ElementoDaPosicao cadastro = posicao(H01).principal().orElseThrow();
        assertThat(cadastro.desde()).isEqualTo(LocalDateTime.of(2026, 3, 2, 9, 30));
        assertThat(cadastro.desdeUltimaEntrada()).isTrue();
        assertThat(cadastro.tarefas()).singleElement().satisfies(t -> assertThat(t.responsavel()).isPresent());
    }

    @Test
    void h02_grupo_candidato_vem_na_tarefa() {
        assertThat(posicao(CenariosFicticiosDaS7.H02).principal().orElseThrow().tarefas()).singleElement()
                .satisfies(t -> {
                    assertThat(t.grupos()).containsExactly("grpSurhConferirDocumentoSubterranea");
                    assertThat(t.responsavel()).isEmpty();
                });
    }

    @Test
    void h03_espera_de_pagamento_confirmada_pela_assinatura() {
        ElementoDaPosicao espera = posicao(CenariosFicticiosDaS7.H03).principal().orElseThrow();
        assertThat(espera.tarefas()).isEmpty();
        assertThat(espera.desde()).isEqualTo(LocalDateTime.of(2026, 3, 4, 9, 30));
    }

    @Test
    void multi_instancia_x_de_y() {
        assertThat(posicao(H06).principal().orElseThrow().multiInstancia()).hasValueSatisfying(m -> {
            assertThat(m.concluidos()).contains(2);
            assertThat(m.total()).contains(3);
        });
        assertThat(posicao(H07).principal().orElseThrow().multiInstancia()).hasValueSatisfying(m -> {
            assertThat(m.concluidos()).contains(0);
            assertThat(m.ativas()).isEqualTo(3);
        });
        assertThat(posicao(H09).principal().orElseThrow().tarefas()).hasSize(3)
                .allSatisfy(t -> assertThat(t.responsavel()).isPresent());
    }

    @Test
    void h10_duas_filhas_em_paralelo() {
        PosicaoAtual p = posicao(H10);
        assertThat(p.principal().orElseThrow().processoFilho()).contains("procSurhEmitirAutorizacoes");
        assertThat(p.emParalelo()).singleElement().satisfies(e -> {
            assertThat(e.processoFilho()).contains("procSurhEmissaoPortaria");
            assertThat(e.tarefas()).singleElement().satisfies(t -> assertThat(t.grupos()).containsExactly("GrpProcSurhEmissorTitulos"));
        });
    }

    @Test
    void h19_suporte_mostra_a_ultima_etapa_real() {
        PosicaoAtual p = posicao(H19);
        assertThat(p.marcas()).contains(MarcaDaPosicao.EM_SUPORTE);
        assertThat(p.emSuporteDesde()).contains(LocalDateTime.of(2026, 3, 20, 9, 30));
        assertThat(p.principal().orElseThrow().desde()).isNull();
    }

    @Test
    void complementacao_a_parte_e_colisao_de_ids() {
        PosicaoAtual h20 = posicao(H20);
        assertThat(h20.complementacao()).hasValueSatisfying(c -> assertThat(c.quantidade()).isEqualTo(1));
        assertThat(h20.emParalelo()).isEmpty();
        // A complementação do H20 tem BUSINESS_KEY_ = 7001, a mesma do H01: a chave da definição separa os dois.
        PosicaoAtual h01 = posicao(H01);
        assertThat(h01.complementacao()).isEmpty();
        assertThat(h01.marcas()).doesNotContain(MarcaDaPosicao.VARIAS_INSTANCIAS);
        assertThat(h01.principal().orElseThrow().etapa()).map(Etapa::id).contains("cadastro_do_requerimento");
    }

    @Test
    void sem_instancia_e_nao_suportado() {
        assertThat(posicao(H21).estado()).isEqualTo(EstadoDaPosicao.SEM_INSTANCIA);
        assertThat(posicao(TRIFASICO).estado()).isEqualTo(EstadoDaPosicao.NAO_SUPORTADO);
    }

    @Test
    void duas_instancias_vale_a_mais_recente_com_marca() {
        PosicaoAtual p = posicao(DUAS_INSTANCIAS);
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.ATIVO);
        assertThat(p.marcas()).contains(MarcaDaPosicao.VARIAS_INSTANCIAS);
        assertThat(p.principal().orElseThrow().etapa()).map(Etapa::id).contains("conferencia_dos_documentos");
    }

    @Test
    void modelo_errado_para_o_tipo_nao_cruza_dados() {
        PosicaoAtual p = posicao(MODELO_ERRADO);
        assertThat(p.chaveDoModelo()).contains("procLicenciamentoLac");
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.SEM_INSTANCIA);
    }

    @Test
    void requerimento_inexistente_nao_tem_tipo() {
        assertThat(camunda.requerimento(999_999)).isEmpty();
    }

    @Test
    void numero_de_consultas_por_requerimento_tem_teto() {
        CONSULTAS.set(0);
        posicao(H10); // ativa, com filhas: o caso mais caro
        assertThat(CONSULTAS.get()).isEqualTo(CamundaPosicaoJdbc.CONSULTAS_NO_MAXIMO);
        CONSULTAS.set(0);
        posicao(H07); // ativa com 3 analistas: as mesmas 7, sem consulta por linha
        assertThat(CONSULTAS.get()).isEqualTo(CamundaPosicaoJdbc.CONSULTAS_NO_MAXIMO);
        CONSULTAS.set(0);
        posicao(H15); // encerrada
        assertThat(CONSULTAS.get()).isEqualTo(2);
        CONSULTAS.set(0);
        posicao(TRIFASICO); // não suportado: nem vai ao Camunda
        assertThat(CONSULTAS.get()).isEqualTo(1);
    }

    @Test
    void nenhum_cpf_na_posicao_montada() {
        for (long id : new long[] {H01, H06, H09, H20, CenariosFicticiosDaS7.G03}) {
            assertThat(posicao(id).toString()).doesNotContainPattern("\\d{11}");
        }
    }

    @Test
    void banco_fora_do_ar_lanca_para_o_caso_de_uso_tratar() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            CamundaPosicaoJdbc indisponivel = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(fora), 1);
            assertThatThrownBy(() -> indisponivel.ler(H01, "procOutorgaAguaSupSub")).isInstanceOf(DataAccessException.class);
        }
    }

    /** Conta cada comando preparado (uma consulta) que passa pelo datasource. */
    private static DataSource contando(DataSource alvo) {
        return (DataSource) Proxy.newProxyInstance(CamundaPosicaoJdbcTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, metodo, args) -> {
                    Object resultado = invocar(alvo, metodo, args);
                    if (resultado instanceof Connection conexao) {
                        return Proxy.newProxyInstance(CamundaPosicaoJdbcTest.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (p2, m2, a2) -> {
                                    if (m2.getName().startsWith("prepare") || m2.getName().equals("createStatement")) {
                                        CONSULTAS.incrementAndGet();
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
