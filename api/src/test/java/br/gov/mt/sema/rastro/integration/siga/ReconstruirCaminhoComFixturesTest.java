package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta;
import br.gov.mt.sema.rastro.tramitacao.domain.AtoDaEmissao;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido.ClassificacaoDaTaxa;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.InstanciaDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivoDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ReconstruirCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaPassagem;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reconstrução do caminho (S8, bloco 3) sobre as fixtures do bloco 1, lidas pelo SQL de produção (bloco 2) e com a
 * identificação real dos responsáveis (S7, bloco 4): a sequência exata de passagens por cenário (HC01 a HC15, LC01 a
 * LC03), ciclos, motivos, atos, taxa e as propriedades (nenhuma etapa futura, ordem por entrada, nenhum CPF).
 *
 * <p>Notação da sequência: id da etapa, {@code #n} no ciclo n (a partir do 2º), {@code *} na passagem atual.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReconstruirCaminhoComFixturesTest {

    private static final String CAD = "cadastro_do_requerimento";
    private static final String CONF = "conferencia_dos_documentos";
    private static final String TAXA = "emissao_da_taxa";
    private static final String PAG = "aguardando_pagamento";
    private static final String ISEN = "isencao_confirmada";
    private static final String ATR = "atribuicao_ao_analista";
    private static final String ACE = "aceite_pelo_analista";
    private static final String ANA = "analise_do_processo";
    private static final String REV = "revisao_do_parecer_tecnico";
    private static final String VAL = "validacao_do_parecer_tecnico";
    private static final String EMI = "emissao_do_ato";
    private static final String PUB = "registro_do_comprovante_de_publicacao";
    private static final String VER = "verificacao_da_revisao_do_processo";
    private static final String COR = "correcao_do_requerimento";
    private static final String TAXC = "emissao_da_taxa_correcao";
    private static final String PAGC = "aguardando_pagamento_correcao";
    private static final String TERMO = "termo_de_adesao_gerado";
    private static final String ENVIO = "envio_do_termo_assinado";
    private static final String CONCL = "conclusao";

    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final String LAC = "procLicenciamentoLac";
    private static final Pattern CPF_SOLTO = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");
    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();

    private static HikariDataSource leitura;
    private static CamundaPosicaoJdbc camunda;

    @BeforeAll
    static void abrir() {
        assertThat(SigaOracleTeste.CENARIOS_S8).isNotEmpty();
        leitura = SigaOracleTeste.dataSourceLeitura();
        camunda = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(leitura), 10);
    }

    @AfterAll
    static void fechar() {
        leitura.close();
    }

    private static String chave(long id) {
        return id >= LC01 && id < PB01 ? LAC : HIDRICO;
    }

    private static CaminhoBruto bruto(long id) {
        return camunda.lerCaminho(id, chave(id));
    }

    private static CaminhoReconstruido reconstruir(long id) {
        CaminhoBruto c = bruto(id);
        ModeloDeFluxo modelo = CATALOGO.modelo(chave(id)).orElseThrow();
        return ReconstruirCaminho.reconstruir(c, modelo, camunda.identificar(id, ReconstruirCaminho.paraConsultar(c)));
    }

    private static List<String> sequencia(CaminhoReconstruido c) {
        return c.passagens().stream().map(p -> p.idDaEtapa().orElse("?" + p.idTecnico())
                + (p.ciclo() > 1 ? "#" + p.ciclo() : "") + (p.atual() ? "*" : "")).toList();
    }

    private static List<String> seq(String... partes) {
        return List.of(partes);
    }

    static Stream<Arguments> cenarios() {
        List<String> ateAceite = seq(CAD, CONF, TAXA, PAG, ATR, ACE);
        List<String> ateValidacao = concat(ateAceite, seq(ANA, REV, VAL));
        return Stream.of(
                Arguments.of("HC01", HC01, concat(ateValidacao, seq(EMI, PUB, VER + "*"))),
                Arguments.of("HC02", HC02, seq(CAD, CONF, ISEN, ATR, ACE, ANA + "*")),
                Arguments.of("HC03", HC03, seq(CAD, CONF, CAD + "#2", CONF + "#2", TAXA, PAG + "*")),
                Arguments.of("HC04", HC04, concat(ateValidacao, seq(ANA + "#2", REV + "#2", VAL + "#2", REV + "#3",
                        VAL + "#3*"))),
                Arguments.of("HC05", HC05, concat(ateAceite, seq(ANA, REV, ANA + "#2", REV + "#2", VAL, ANA + "#3",
                        REV + "#3*"))),
                Arguments.of("HC06", HC06, seq(CAD, CONF, TAXA, PAG, ATR, ACE, ATR + "#2", ACE + "#2", ANA, ATR + "#3",
                        ACE + "#3*")),
                Arguments.of("HC07", HC07, concat(ateAceite, seq(ANA, COR, TAXC, PAGC, ANA + "#2*"))),
                Arguments.of("HC08", HC08, concat(ateAceite, seq(ANA, COR, ANA + "#2*"))),
                Arguments.of("HC09", HC09, concat(ateAceite, seq(ANA, COR, ANA + "#2*"))),
                Arguments.of("HC10", HC10, concat(ateAceite, seq(ANA, COR + "*"))),
                Arguments.of("HC11", HC11, seq(CAD, CONF, TAXA, PAG + "*")),
                Arguments.of("HC12", HC12, concat(ateValidacao, seq(EMI, REV + "#2", VAL + "#2", EMI + "#2", PUB + "*"))),
                Arguments.of("HC13", HC13, concat(ateValidacao, seq(EMI, PUB, VER, EMI + "#2", PUB + "#2*"))),
                Arguments.of("HC14", HC14, concat(ateAceite, seq(ANA, REV + "*"))),
                Arguments.of("LC01", LC01, seq(CAD, CONF, TAXA, PAG, TERMO, ENVIO, CONCL)),
                Arguments.of("LC02", LC02, seq(CAD, CONF, ISEN, TERMO, ENVIO + "*")),
                Arguments.of("LC03", LC03, seq(CAD, CONF, COR, CONF + "#2", TAXA, PAG + "*")));
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> l = new ArrayList<>(a);
        l.addAll(b);
        return l;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cenarios")
    void sequencia_exata_de_passagens(String cenario, long id, List<String> esperada) {
        assertThat(sequencia(reconstruir(id))).as(cenario).containsExactlyElementsOf(esperada);
    }

    // --- motivos da volta

    private static List<Optional<MotivoDaVolta>> motivos(CaminhoReconstruido c) {
        return c.passagens().stream().filter(p -> p.ciclo() > 1).map(PassagemPelaEtapa::motivoDaVolta).toList();
    }

    @Test
    void motivos_das_voltas_por_cenario() {
        assertThat(motivos(reconstruir(HC03))).containsExactly(Optional.of(MotivoDaVolta.REPROVADO_NA_CONFERENCIA),
                Optional.empty());
        assertThat(motivos(reconstruir(HC04))).containsExactly(
                Optional.of(MotivoDaVolta.DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE), Optional.empty(), Optional.empty(),
                Optional.of(MotivoDaVolta.DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO), Optional.empty());
        assertThat(motivos(reconstruir(HC05))).containsExactly(Optional.of(MotivoDaVolta.TROCA_DE_ANALISTA),
                Optional.empty(), Optional.of(MotivoDaVolta.TROCA_DE_ANALISTA), Optional.empty());
        assertThat(motivos(reconstruir(HC06))).containsExactly(Optional.of(MotivoDaVolta.RECUSA_NO_ACEITE),
                Optional.empty(), Optional.of(MotivoDaVolta.REDISTRIBUICAO), Optional.empty());
        for (long id : new long[] {HC07, HC08, HC09}) {
            assertThat(motivos(reconstruir(id))).as("correção " + id)
                    .containsExactly(Optional.of(MotivoDaVolta.CORRECAO_DO_REQUERIMENTO));
        }
        assertThat(motivos(reconstruir(HC12))).containsExactly(Optional.of(MotivoDaVolta.DEVOLVIDO_NA_EMISSAO),
                Optional.empty(), Optional.empty());
        assertThat(motivos(reconstruir(HC13))).containsExactly(Optional.of(MotivoDaVolta.REVISAR_NA_VERIFICACAO),
                Optional.empty());
        assertThat(motivos(reconstruir(LC03))).containsExactly(Optional.of(MotivoDaVolta.CORRECAO_DO_REQUERIMENTO));
    }

    // --- multi-instância, ruído, emissão, correção, complementação, limite

    @Test
    void analista_que_pede_correcao_conclui_e_os_outros_ficam_encerrados_sem_acao() {
        PassagemPelaEtapa analise = reconstruir(HC10).passagens().stream()
                .filter(p -> p.idDaEtapa().orElse("").equals(ANA)).findFirst().orElseThrow();
        assertThat(analise.multiInstancia()).isTrue();
        assertThat(analise.responsaveis()).extracting(ResponsavelDaPassagem::estado).containsExactlyInAnyOrder(
                ResponsavelDaPassagem.Estado.CONCLUIU, ResponsavelDaPassagem.Estado.ENCERRADA_SEM_ACAO,
                ResponsavelDaPassagem.Estado.ENCERRADA_SEM_ACAO);
        assertThat(analise.responsaveis()).allSatisfy(r -> assertThat(r.nome()).isPresent());
    }

    @Test
    void participantes_do_ciclo_tem_nome_e_o_ciclo_aberto_tem_pendentes() {
        CaminhoReconstruido c = reconstruir(HC01);
        PassagemPelaEtapa aceite = c.passagens().stream().filter(p -> p.idDaEtapa().orElse("").equals(ACE))
                .findFirst().orElseThrow();
        assertThat(aceite.responsaveis()).extracting(r -> r.nome().orElseThrow())
                .containsExactlyInAnyOrder("Servidor Exemplo 05", "Servidor Exemplo 06", "Servidor Exemplo 07");
        PassagemPelaEtapa analiseAtual = reconstruir(HC02).passagens().getLast();
        assertThat(analiseAtual.atual()).isTrue();
        assertThat(analiseAtual.responsaveis()).isNotEmpty()
                .allSatisfy(r -> assertThat(r.estado()).isEqualTo(ResponsavelDaPassagem.Estado.PENDENTE));
    }

    @Test
    void ruido_colapsado_suporte_fora_e_ids_desconhecidos_so_no_diagnostico() {
        CaminhoReconstruido c = reconstruir(HC11);
        PassagemPelaEtapa taxa = c.passagens().get(2);
        assertThat(taxa.idDaEtapa()).contains(TAXA);
        assertThat(taxa.repeticoes()).isEqualTo(4);
        assertThat(c.ignoradas()).isPositive();
        assertThat(c.idsNaoMapeados()).contains("Activity_FicticioReportKafka", "Event_FicticioRepeticaoPorErroDeApi");
    }

    @Test
    void emissao_e_uma_passagem_com_os_atos_e_a_devolvida_nao_conta_como_emitida() {
        CaminhoReconstruido feliz = reconstruir(HC01);
        PassagemPelaEtapa emissao = feliz.passagens().stream().filter(p -> p.idDaEtapa().orElse("").equals(EMI))
                .findFirst().orElseThrow();
        assertThat(emissao.atos()).extracting(AtoDaEmissao::tipo)
                .containsExactlyInAnyOrder(AtoDaEmissao.Tipo.AUTORIZACAO, AtoDaEmissao.Tipo.PORTARIA);
        assertThat(emissao.atos()).allMatch(AtoDaEmissao::ocorreu);
        assertThat(emissao.responsaveis()).isNotEmpty().allSatisfy(r -> assertThat(r.papel()).isEqualTo("Signatário"));

        List<PassagemPelaEtapa> emissoes = reconstruir(HC12).passagens().stream()
                .filter(p -> p.idDaEtapa().orElse("").equals(EMI)).toList();
        assertThat(emissoes.getFirst().atos()).hasSize(3);
        assertThat(emissoes.getFirst().atos().stream().filter(AtoDaEmissao::ocorreu)).hasSize(2);
        assertThat(emissoes.get(1).atos()).hasSize(3).allMatch(AtoDaEmissao::ocorreu);
        assertThat(emissoes).noneMatch(PassagemPelaEtapa::emParalelo);
    }

    @Test
    void verificacao_da_revisao_leva_o_selo_de_processo_concluido() {
        assertThat(reconstruir(HC01).passagens().getLast().processoConcluido()).isTrue();
        assertThat(reconstruir(HC01).passagens().stream().filter(PassagemPelaEtapa::processoConcluido)).hasSize(1);
    }

    @Test
    void correcao_fica_no_ramo_da_correcao_com_ou_sem_nova_taxa() {
        CaminhoReconstruido comTaxa = reconstruir(HC07);
        assertThat(comTaxa.passagens().stream().filter(p -> p.ramo() == Ramo.CORRECAO).map(p -> p.idDaEtapa().orElse("")))
                .containsExactly(COR, TAXC, PAGC);
        assertThat(comTaxa.taxaDaCorrecao()).contains(ClassificacaoDaTaxa.TAXAVEL);
        assertThat(reconstruir(HC08).taxaDaCorrecao()).contains(ClassificacaoDaTaxa.SEM_NOVA_TAXA);
        assertThat(reconstruir(HC01).taxaDaCorrecao()).isEmpty();
    }

    @Test
    void taxavel_e_isento_pela_rota() {
        assertThat(reconstruir(HC01).taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.TAXAVEL);
        assertThat(reconstruir(HC02).taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.ISENTA);
        assertThat(reconstruir(LC02).taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.ISENTA);
        assertThat(reconstruir(HC03).taxaPrincipal()).isEqualTo(ClassificacaoDaTaxa.TAXAVEL);
        assertThat(reconstruir(HC03).diagnosticos()).isEmpty();
    }

    @Test
    void complementacao_paralela_marca_a_analise_como_em_paralelo() {
        CaminhoReconstruido c = reconstruir(HC14);
        assertThat(c.complementacoes()).hasSize(1);
        assertThat(c.complementacoes().getFirst().saida()).isPresent();
        assertThat(c.passagens().stream().filter(PassagemPelaEtapa::emParalelo).map(p -> p.idDaEtapa().orElse("")))
                .containsExactly(ANA);
    }

    @Test
    void milhares_de_repeticoes_viram_uma_passagem_e_a_etapa_atual_aparece() {
        // Bloco 7: o colapso acontece na leitura, antes do limite de linhas; o HC15 cabe e não é truncado.
        CaminhoReconstruido c = reconstruir(HC15);
        assertThat(c.truncado()).isFalse();
        assertThat(sequencia(c)).containsExactly(CAD, CONF, TAXA, PAG + "*");
        assertThat(c.passagens().get(2).repeticoes()).isEqualTo(REPETICOES_DO_HC15);
    }

    @Test
    void se_ainda_passa_do_limite_o_caminho_sai_truncado_com_o_que_coube() {
        CaminhoBruto c = new CamundaPosicaoJdbc(new NamedParameterJdbcTemplate(leitura), 10, 3).lerCaminho(HC15, HIDRICO);
        CaminhoReconstruido r = ReconstruirCaminho.reconstruir(c, CATALOGO.modelo(HIDRICO).orElseThrow(),
                java.util.Map.of());
        assertThat(r.truncado()).isTrue();
        assertThat(sequencia(r)).containsExactly(CAD, CONF, TAXA);
    }

    // --- propriedades

    static Stream<Long> todos() {
        return Stream.of(HC01, HC02, HC03, HC04, HC05, HC06, HC07, HC08, HC09, HC10, HC11, HC12, HC13, HC14, HF01, HF02,
                HF03, HF04, LC01, LC02, LC03, LF01, LF02, LF03, LF04, LF05);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("todos")
    void nenhuma_etapa_futura_aparece(long id) {
        CaminhoBruto c = bruto(id);
        ModeloDeFluxo modelo = CATALOGO.modelo(chave(id)).orElseThrow();
        Set<String> vistos = new HashSet<>();
        c.atividades().stream().map(AtividadeBruta::idTecnico).forEach(vistos::add);
        c.instancias().stream().map(InstanciaDoHistorico::chave).forEach(vistos::add);
        Set<String> etapasVistas = new HashSet<>();
        for (Etapa e : modelo.etapas()) {
            if (e.idsTecnicos().stream().anyMatch(vistos::contains) || e.processosFilhos().stream().anyMatch(vistos::contains)) {
                etapasVistas.add(e.id());
            }
        }
        assertThat(reconstruir(id).passagens()).allSatisfy(
                p -> assertThat(etapasVistas).as("etapa %s sem atividade lida", p.rotulo()).contains(p.idDaEtapa().orElseThrow()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("todos")
    void passagens_ordenadas_por_entrada_e_so_a_ultima_pode_ser_atual_sem_paralelo(long id) {
        List<PassagemPelaEtapa> passagens = reconstruir(id).passagens();
        assertThat(passagens).isSortedAccordingTo(Comparator.comparing(PassagemPelaEtapa::entrada));
        assertThat(passagens.stream().filter(PassagemPelaEtapa::atual).count()).isLessThanOrEqualTo(1);
        passagens.forEach(p -> p.saida().ifPresent(s -> assertThat(s).isAfterOrEqualTo(p.entrada())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("todos")
    void cpf_nunca_aparece_em_nenhuma_passagem(long id) {
        CaminhoReconstruido c = reconstruir(id);
        assertThat(CPF_SOLTO.matcher(c.toString()).find()).isFalse();
        assertThat(CPF_SOLTO.matcher(c.semNomes().toString()).find()).isFalse();
        assertThat(c.semNomes().passagens()).allSatisfy(
                p -> assertThat(p.responsaveis()).allSatisfy(r -> assertThat(r.nome()).isEmpty()));
    }

    @Test
    void finais_encerrados_nao_tem_passagem_atual() {
        for (long id : new long[] {HF01, HF02, HF04, LC01, LF01, LF04, LF05}) {
            CaminhoReconstruido c = reconstruir(id);
            assertThat(c.passagens()).as("final " + id).isNotEmpty().noneMatch(PassagemPelaEtapa::atual);
        }
        // Cadastro expirado: a tarefa foi encerrada sem ação, então nada foi percorrido.
        assertThat(reconstruir(HF03).passagens()).isEmpty();
    }
}
