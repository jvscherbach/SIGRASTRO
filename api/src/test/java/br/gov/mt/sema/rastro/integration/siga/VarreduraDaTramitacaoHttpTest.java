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
