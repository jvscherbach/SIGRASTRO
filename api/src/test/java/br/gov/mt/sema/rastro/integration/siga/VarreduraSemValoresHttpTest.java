package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de negação do parâmetro de valores (S9, bloco 7): com {@code RASTRO_MOSTRAR_VALORES} desligado, valor, desconto e
 * UPF somem de <b>todas</b> as respostas do financeiro (por requerimento e por processo), em todos os cenários e perfis;
 * o número do DAR continua. Pela variável de ambiente, como em produção (o {@code application.yml} a liga à propriedade).
 */
@TestPropertySource(properties = "RASTRO_MOSTRAR_VALORES=false")
class VarreduraSemValoresHttpTest extends BaseVarreduraDaTramitacao {

    private static final List<String> CAMPOS_DE_VALOR = List.of("\"valor\"", "\"desconto\"", "\"upf\"");
    private static final List<String> VALORES = List.of("1250.5", "980.0", "35.7286", "28.0", "310.25", "8.8643");

    @Test
    void nenhuma_resposta_traz_valor_desconto_nem_upf() throws Exception {
        List<Long> ids = VarreduraDoFinanceiroHttpTest.requerimentosDoFinanceiro();
        SoftAssertions soft = new SoftAssertions();
        int respostas200 = 0;
        for (String perfil : PERFIS) {
            for (long id : ids) {
                MockHttpServletResponse r = obter(perfil, "/api/requerimentos/" + id + "/financeiro");
                if (r.getStatus() != 200) {
                    continue;
                }
                respostas200++;
                String onde = perfil + " /requerimentos/" + id + "/financeiro";
                String texto = corpo(r);
                soft.assertThat(texto).as(onde + ": campos de valor").doesNotContain(CAMPOS_DE_VALOR.toArray(String[]::new));
                soft.assertThat(texto).as(onde + ": números de valor").doesNotContain(VALORES.toArray(String[]::new));
                soft.assertThat(texto).as(onde + ": mostraValores").contains("\"mostraValores\":false");
            }
            String processo = json.readTree(corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN01
                    + "/financeiro"))).get("numeroProcesso").asText().replace('/', '-');
            MockHttpServletResponse r = obter(perfil, "/api/processos/" + processo + "/financeiro");
            if (r.getStatus() == 200) {
                String texto = corpo(r);
                soft.assertThat(texto).as(perfil + " processo: campos de valor")
                        .doesNotContain(CAMPOS_DE_VALOR.toArray(String[]::new));
                soft.assertThat(texto).as(perfil + " processo: números de valor")
                        .doesNotContain(VALORES.toArray(String[]::new));
            }
        }
        soft.assertAll();
        assertThat(respostas200).as("Gestor e Administrador em todos").isEqualTo(2 * ids.size());
    }

    @Test
    void o_dar_continua() throws Exception {
        String fin01 = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN01 + "/financeiro"));
        assertThat(fin01).contains("\"numeroDar\":\"FICT-0001\"").doesNotContain("\"valor\"");
        String fin11 = corpo(obter(ADMIN, "/api/requerimentos/" + CenariosFicticiosDaS9.FIN11 + "/financeiro"));
        assertThat(fin11).contains("FICT-0011", "FICT-0111").doesNotContain("\"upf\"");
    }
}
