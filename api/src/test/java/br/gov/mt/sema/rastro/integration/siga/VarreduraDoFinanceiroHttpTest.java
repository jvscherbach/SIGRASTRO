package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariDataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Varredura de vazamento do financeiro ponta a ponta (S9, bloco 7): o serviço inteiro com o Oracle de teste (usuário de
 * leitura) e todos os cenários fictícios da S9 (FIN01 a FIN16, os de data e os criados pelos testes, 9000 a 9299), nos
 * três perfis. Varre o histórico financeiro (por requerimento e por processo), a posição e a tramitação (aviso e marcas
 * financeiras), os eventos {@code FINANCEIRO} da auditoria (lista e CSV) e o log, atrás de: chave do arquivo da guia
 * ({@code CHAVE_GED}), {@code MENSAGEM}, titulação, CPF (com e sem máscara, 11 dígitos soltos), token, ids técnicos do
 * Camunda e, para quem não é Administrador, ids técnicos de lançamento e o diagnóstico. Zero ocorrências.
 */
class VarreduraDoFinanceiroHttpTest extends BaseVarreduraDaTramitacao {

    private static final Pattern COLUNA_PROIBIDA = Pattern.compile("(?i)chave_?ged|chaveDaGuia|\\bMENSAGEM\\b|titula");
    private static final List<String> SO_DO_ADMINISTRADOR = List.of("\"idTecnico\"", "\"statusProcessado\"",
            "\"passouPelaReaprovacaoDaCorrecao\"", "\"inconsistencias\"", "\"origemDaData\"", "\"naoProcessado\"",
            "\"ignoradosPorMotivo\"");
    /** Valores dos cenários (CenariosFicticiosDaS9) como saem em JSON e em texto: não podem ir para a auditoria nem log. */
    private static final List<String> VALORES = List.of("1250.5", "1250,5", "980.0", "980,0", "35.7286", "28.0000",
            "310.25", "8.8643");

    /** Todos os requerimentos fictícios do financeiro no Oracle de teste. */
    static List<Long> requerimentosDoFinanceiro() {
        try (HikariDataSource leitura = SigaOracleTeste.dataSourceLeitura()) {
            return new NamedParameterJdbcTemplate(leitura).queryForList(
                    "SELECT ID FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID BETWEEN 9000 AND 9299 ORDER BY ID",
                    new MapSqlParameterSource(), Long.class);
        }
    }

    /** Ids técnicos dos lançamentos do requerimento ({@code <requerimento> * 100 + ordem}, como o construtor grava). */
    private static Pattern idsDeLancamento(long requerimento) {
        return Pattern.compile("(?<![0-9])" + requerimento + "\\d{2}(?![0-9])");
    }

    private static void varrerFinanceiro(SoftAssertions soft, String onde, String texto, boolean administrador,
            long requerimento) {
        varrer(soft, onde, texto, administrador);
        soft.assertThat(texto).as(onde + ": coluna que o Rastro nunca lê").doesNotContainPattern(COLUNA_PROIBIDA);
        if (!administrador) {
            soft.assertThat(texto).as(onde + ": campos só do Administrador")
                    .doesNotContain(SO_DO_ADMINISTRADOR.toArray(String[]::new));
            soft.assertThat(texto).as(onde + ": id técnico de lançamento").doesNotContainPattern(idsDeLancamento(requerimento));
        }
    }

    @Test
    void nenhum_cenario_nem_perfil_vaza_no_financeiro(CapturedOutput saida) throws Exception {
        List<Long> ids = requerimentosDoFinanceiro();
        assertThat(ids).contains(CenariosFicticiosDaS9.FIN01, CenariosFicticiosDaS9.FIN16, CenariosFicticiosDaS9.VENCE_HOJE);
        List<Long> todos = new ArrayList<>(ids);
        todos.add(DO_OPERADOR);
        Set<String> processos = new LinkedHashSet<>();
        SoftAssertions soft = new SoftAssertions();
        int respostas200 = 0;
        for (String perfil : PERFIS) {
            boolean administrador = ADMIN.equals(perfil);
            for (long id : todos) {
                for (String rota : List.of("financeiro", "posicao", "tramitacao")) {
                    MockHttpServletResponse r = obter(perfil, "/api/requerimentos/" + id + "/" + rota);
                    String onde = perfil + " /requerimentos/" + id + "/" + rota;
                    soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                    String texto = corpo(r);
                    varrerFinanceiro(soft, onde, texto, administrador, id);
                    if ("financeiro".equals(rota) && r.getStatus() == 200) {
                        respostas200++;
                        JsonNode no = json.readTree(texto);
                        soft.assertThat(no.get("estado").asText()).as(onde + ": estado").isEqualTo("ATIVO");
                        if (no.hasNonNull("numeroProcesso")) {
                            processos.add(no.get("numeroProcesso").asText().replace('/', '-'));
                        }
                    }
                }
            }
        }
        for (String perfil : PERFIS) {
            for (String processo : processos) {
                MockHttpServletResponse r = obter(perfil, "/api/processos/" + processo + "/financeiro");
                String onde = perfil + " /processos/" + processo + "/financeiro";
                soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                String texto = corpo(r);
                varrer(soft, onde, texto, ADMIN.equals(perfil));
                soft.assertThat(texto).as(onde + ": coluna que o Rastro nunca lê").doesNotContainPattern(COLUNA_PROIBIDA);
                if (!ADMIN.equals(perfil)) {
                    soft.assertThat(texto).as(onde + ": campos só do Administrador")
                            .doesNotContain(SO_DO_ADMINISTRADOR.toArray(String[]::new));
                }
            }
        }
        varrerAuditoria(soft);
        String log = saida.getAll();
        varrerLog(soft, log);
        soft.assertThat(log).as("log: coluna que o Rastro nunca lê").doesNotContainPattern(COLUNA_PROIBIDA);
        soft.assertThat(log).as("log: número do DAR").doesNotContain("FICT-");
        soft.assertAll();
        // Operador só no dele (sem lançamento); Gestor e Administrador em todos.
        assertThat(respostas200).isEqualTo(1 + 2 * todos.size());
    }

    /** Os eventos FINANCEIRO (lista e CSV, visão do Administrador): sem DAR, sem valor, sem CPF, sem chave. */
    private void varrerAuditoria(SoftAssertions soft) throws Exception {
        for (String caminho : List.of("/api/auditoria/documentos?periodo=todo&tipo=FINANCEIRO&tamanho=100",
                "/api/auditoria/documentos/exportacao?periodo=todo&tipo=FINANCEIRO")) {
            MockHttpServletResponse r = obter(ADMIN, caminho);
            String onde = "auditoria " + caminho;
            soft.assertThat(r.getStatus()).as(onde).isEqualTo(200);
            String texto = corpo(r);
            // A lista traz o código; o CSV, o rótulo do tipo.
            soft.assertThat(texto).as(onde + ": há eventos FINANCEIRO").containsAnyOf("\"FINANCEIRO\"",
                    "Histórico financeiro");
            varrer(soft, onde, texto, true);
            soft.assertThat(texto).as(onde + ": coluna que o Rastro nunca lê").doesNotContainPattern(COLUNA_PROIBIDA);
            soft.assertThat(texto).as(onde + ": número do DAR").doesNotContain("FICT-");
            soft.assertThat(texto).as(onde + ": valor").doesNotContain(VALORES.toArray(String[]::new));
        }
    }

    @Test
    void a_varredura_do_financeiro_nao_e_vazia() throws Exception {
        String fin01Gestor = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN01 + "/financeiro"));
        assertThat(fin01Gestor).contains("FICT-0001").contains("\"valor\":1250.5").doesNotContain("\"idTecnico\"");
        String fin14Admin = corpo(obter(ADMIN, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN14 + "/financeiro"));
        assertThat(fin14Admin).contains("\"idTecnico\"").contains("\"ignoradosPorMotivo\"").contains("FALSO_SEM_DAR")
                .containsPattern(idsDeLancamento(CenariosFicticiosDaS9.FIN14));
        String fin03Posicao = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN03 + "/posicao"));
        assertThat(fin03Posicao).contains("TAXA_VENCIDA");
        String fin11Tramitacao = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN11 + "/tramitacao"));
        assertThat(fin11Tramitacao).contains("\"marcasFinanceiras\":[{").contains("DA_CORRECAO").contains("FICT-0111");
    }
}
