package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Varredura com o nome do responsável ligado (padrão do protótipo): para todos os cenários e os três perfis, resposta e
 * log sem CPF, sem grupo técnico, sem chave e sem token; ids técnicos só para o Administrador; nome de servidor nunca
 * para o Operador (RN12). Também confere que a varredura não é vazia: os nomes e os ids aparecem para quem pode.
 */
class VarreduraDaTramitacaoHttpTest extends BaseVarreduraDaTramitacao {

    @Test
    void nenhum_cenario_nem_perfil_vaza(CapturedOutput saida) throws Exception {
        List<Long> ids = requerimentosFicticios();
        assertThat(ids).hasSizeGreaterThanOrEqualTo(33).contains(DO_OPERADOR);
        SoftAssertions soft = new SoftAssertions();
        int respostas200 = 0;
        for (String perfil : PERFIS) {
            for (long id : ids) {
                MockHttpServletResponse r = obter(perfil, "/api/requerimentos/" + id + "/posicao");
                String onde = perfil + " /requerimentos/" + id;
                soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                String texto = corpo(r);
                varrer(soft, onde, texto, ADMIN.equals(perfil));
                if (OPERADOR.equals(perfil)) {
                    soft.assertThat(texto).as(onde + ": nome de servidor para o Operador").doesNotContain("Servidor Exemplo");
                }
                respostas200 += r.getStatus() == 200 ? 1 : 0;
            }
            for (String processo : List.of("7900-2022", PROCESSO_DO_OPERADOR.replace('/', '-'))) {
                MockHttpServletResponse r = obter(perfil, "/api/processos/" + processo + "/posicao");
                String onde = perfil + " /processos/" + processo;
                soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                varrer(soft, onde, corpo(r), ADMIN.equals(perfil));
            }
        }
        varrerLog(soft, saida.getAll());
        soft.assertAll();
        // Operador só no dele; Gestor e Administrador em todos.
        assertThat(respostas200).isEqualTo(1 + 2 * ids.size());
    }

    /**
     * S8, bloco 11: a tramitação completa ({@code /tramitacao}) de todos os cenários HC, HF, LC, LF e PB da S8 e dos da
     * S7, nos três perfis e nas rotas por processo: zero CPF (inclusive o {@code CPF_USUARIO} fictício do plano B), zero
     * grupo técnico, chave, token ou coluna do Camunda; ids técnicos ({@code idTecnico}, {@code idsNaoMapeados},
     * {@code diagnosticos}, ids do BPMN) só para o Administrador; nenhum nome de servidor para o Operador; plano B sem
     * nenhum responsável. O log também é varrido.
     */
    @Test
    void nenhum_cenario_da_s8_nem_perfil_vaza_na_tramitacao_completa(CapturedOutput saida) throws Exception {
        List<Long> ids = requerimentosDaTramitacaoCompleta();
        assertThat(ids).contains(CenariosFicticiosDaS8.HC01, CenariosFicticiosDaS8.HC15, CenariosFicticiosDaS8.LC03,
                CenariosFicticiosDaS8.PB01, CenariosFicticiosDaS8.PB03, DO_OPERADOR);
        SoftAssertions soft = new SoftAssertions();
        int respostas200 = 0;
        for (String perfil : PERFIS) {
            boolean administrador = ADMIN.equals(perfil);
            for (long id : ids) {
                MockHttpServletResponse r = obter(perfil, "/api/requerimentos/" + id + "/tramitacao");
                String onde = perfil + " /requerimentos/" + id + "/tramitacao";
                soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                String texto = corpo(r);
                varrer(soft, onde, texto, administrador);
                if (!administrador) {
                    soft.assertThat(texto).as(onde + ": campos só do Administrador")
                            .doesNotContain("\"idTecnico\"", "\"idsNaoMapeados\"", "\"diagnosticos\"", "\"repeticoes\"");
                }
                if (OPERADOR.equals(perfil)) {
                    soft.assertThat(texto).as(onde + ": nome de servidor para o Operador").doesNotContain("Servidor Exemplo");
                }
                if (r.getStatus() == 200 && id >= CenariosFicticiosDaS8.PB01 && id <= CenariosFicticiosDaS8.PB03) {
                    soft.assertThat(texto).as(onde + ": plano B aproximado").contains("\"aproximado\":true");
                    soft.assertThat(texto).as(onde + ": plano B sem responsável").doesNotContain("Servidor Exemplo")
                            .doesNotContain("Interessado (cadastrante)").doesNotContainPattern("\"(responsaveis|participantes)\":\\[\\{");
                }
                respostas200 += r.getStatus() == 200 ? 1 : 0;
            }
            for (String processo : PROCESSOS_DA_TRAMITACAO) {
                MockHttpServletResponse r = obter(perfil, "/api/processos/" + processo + "/tramitacao");
                String onde = perfil + " /processos/" + processo + "/tramitacao";
                soft.assertThat(r.getStatus()).as(onde + ": sem 5xx").isLessThan(500);
                varrer(soft, onde, corpo(r), administrador);
            }
        }
        varrerLog(soft, saida.getAll());
        soft.assertAll();
        // Operador só no dele; Gestor e Administrador em todos (INDISPONIVEL e NAO_SUPORTADO também são 200).
        assertThat(respostas200).isEqualTo(1 + 2 * ids.size());
    }

    @Test
    void a_varredura_da_tramitacao_nao_e_vazia() throws Exception {
        String hc01Gestor = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS8.HC01 + "/tramitacao"));
        assertThat(hc01Gestor).contains("Servidor Exemplo").contains("Emissão do ato").doesNotContain("\"idTecnico\"");
        String hc11Admin = corpo(obter(ADMIN, "/api/requerimentos/" + CenariosFicticiosDaS8.HC11 + "/tramitacao"));
        assertThat(hc11Admin).contains("\"idTecnico\"").contains("Activity_FicticioReportKafka");
        String pb01 = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS8.PB01 + "/tramitacao"));
        assertThat(pb01).contains("\"aproximado\":true").contains("Histórico aproximado");
        String doOperador = corpo(obter(OPERADOR, "/api/requerimentos/" + DO_OPERADOR + "/tramitacao"));
        assertThat(doOperador).contains("Análise do processo").doesNotContain("Servidor Exemplo");
    }

    @Test
    void a_varredura_nao_e_vazia_nomes_e_ids_aparecem_para_quem_pode() throws Exception {
        assertThat(corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H08 + "/posicao")))
                .contains("Servidor Exemplo 04");
        assertThat(corpo(obter(ADMIN, "/api/requerimentos/" + CenariosFicticiosDaS7.H01 + "/posicao")))
                .contains("taskCadastrarRequerimento");
        String doOperador = corpo(obter(OPERADOR, "/api/requerimentos/" + DO_OPERADOR + "/posicao"));
        assertThat(doOperador).contains("Análise do processo").contains("\"papel\":\"Analista\"")
                .doesNotContain("Servidor Exemplo");
        assertThat(corpo(obter(GESTOR, "/api/requerimentos/" + DO_OPERADOR + "/posicao"))).contains("Servidor Exemplo");
    }
}
