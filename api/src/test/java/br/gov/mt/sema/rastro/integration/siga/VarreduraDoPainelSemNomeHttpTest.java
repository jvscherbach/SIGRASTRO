package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Teste de negação do painel (S10, bloco 6): com {@code RASTRO_MOSTRAR_NOME_RESPONSAVEL} desligado, nenhuma resposta do
 * painel (nem a auditoria) traz nome de servidor, para os perfis que leem; fica só categoria sem nome.
 */
@TestPropertySource(properties = "rastro.tramitacao.mostrar-nome-responsavel=false")
class VarreduraDoPainelSemNomeHttpTest extends BaseDoPainelHttp {

    @Test
    void sem_o_parametro_nenhum_nome_de_servidor_sai_do_painel() throws Exception {
        atualizarEEsperar();
        SoftAssertions soft = new SoftAssertions();
        for (String perfil : List_PERFIS) {
            String texto = corpo(obter(perfil, "/api/painel"));
            soft.assertThat(texto).as(perfil).doesNotContain("Servidor Exemplo");
            varrer(soft, perfil + " /painel", texto, false);
            if (!OPERADOR.equals(perfil)) {
                JsonNode raiz = json.readTree(texto);
                soft.assertThat(raiz.get("nomesOcultos").asBoolean()).as(perfil + " nomesOcultos").isTrue();
                soft.assertThat(raiz.get("cargaPorResponsavel").findValuesAsText("tipo"))
                        .as(perfil).doesNotContain("SERVIDOR", "OUTROS");
            }
        }
        assertThat(corpo(obter(ADMIN, "/api/auditoria/documentos?periodo=todo&tamanho=100")))
                .doesNotContain("Servidor Exemplo");
        soft.assertAll();
    }

    private static final java.util.List<String> List_PERFIS = PERFIS;
}
