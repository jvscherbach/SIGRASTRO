package br.gov.mt.sema.rastro.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.gov.mt.sema.rastro.configuracao.application.ConsultarEstadoDoServico;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** Modo do serviço (S6, bloco 7): demonstração só com o pedido explícito e nenhum sistema externo configurado. */
class EstadoDoServicoTest extends BaseHttpDeDocumentos {

    private static String modo(String pedido, String siga, String arquivos, String scorp) {
        return new ConsultarEstadoDoServico(pedido, siga, arquivos, scorp).consultar().modo();
    }

    @Test
    void demonstracao_so_com_o_pedido_e_sem_nenhum_externo() {
        assertThat(modo("demonstracao", "", " ", null)).isEqualTo("demonstracao");
        assertThat(modo(" DEMONSTRACAO ", "", "", "")).isEqualTo("demonstracao");
        assertThat(modo("demonstracao", "jdbc:oracle:thin:@//host-de-teste:1521/x", "", "")).isEqualTo("padrao");
        assertThat(modo("demonstracao", "", "http://arquivos.invalid", "")).isEqualTo("padrao");
        assertThat(modo("demonstracao", "", "", "http://scorp.invalid/api")).isEqualTo("padrao");
        assertThat(modo("padrao", "", "", "")).isEqualTo("padrao");
        assertThat(modo(null, "", "", "")).isEqualTo("padrao");
    }

    @Test
    void rota_exige_sessao_e_nao_traz_endereco() throws Exception {
        mvc.perform(get("/api/estado")).andExpect(status().isUnauthorized());

        String corpo = mvc.perform(get("/api/estado").header(HttpHeaders.AUTHORIZATION, bearer("operador.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.modo").value("padrao"))
                .andReturn().getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("http").doesNotContain("jdbc").doesNotContain(TOKEN_DE_ARQUIVOS);
    }
}
