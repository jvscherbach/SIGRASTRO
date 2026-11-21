package br.gov.mt.sema.rastro.integration.siga;

import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de negação (S7, bloco 7): com {@code RASTRO_MOSTRAR_NOME_RESPONSAVEL} desligado, nenhuma resposta da posição,
 * de nenhum cenário nem perfil, traz nome de servidor; ficam só a área e o papel. A varredura de vazamento vale também
 * aqui.
 */
@TestPropertySource(properties = "rastro.tramitacao.mostrar-nome-responsavel=false")
class VarreduraSemNomeDoResponsavelHttpTest extends BaseVarreduraDaTramitacao {

    /** Campo {@code nome} preenchido (responsável ou participante). */
    private static final Pattern NOME_PREENCHIDO = Pattern.compile("\"nome\":\"");

    @Test
    void desligar_o_parametro_tira_os_nomes_de_todas_as_respostas() throws Exception {
        SoftAssertions soft = new SoftAssertions();
        List<Long> ids = requerimentosFicticios();
        for (String perfil : PERFIS) {
            for (long id : ids) {
                MockHttpServletResponse r = obter(perfil, "/api/requerimentos/" + id + "/posicao");
                String onde = perfil + " /requerimentos/" + id;
                String texto = corpo(r);
                soft.assertThat(texto).as(onde).doesNotContain("Servidor Exemplo").doesNotContainPattern(NOME_PREENCHIDO);
                varrer(soft, onde, texto, ADMIN.equals(perfil));
            }
            String processo = corpo(obter(perfil, "/api/processos/7900-2022/posicao"));
            soft.assertThat(processo).as(perfil + " processo").doesNotContain("Servidor Exemplo");
        }
        // Continua dizendo com quem está, pelo papel e pela área.
        soft.assertThat(corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H08 + "/posicao")))
                .contains("\"papel\":\"Chefia\"");
        soft.assertThat(corpo(obter(ADMIN, "/api/requerimentos/" + CenariosFicticiosDaS7.H02 + "/posicao")))
                .contains("Conferência de documentos (subterrânea)");
        soft.assertAll();
    }
}
