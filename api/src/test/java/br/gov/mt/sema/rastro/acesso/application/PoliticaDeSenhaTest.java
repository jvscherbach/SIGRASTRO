package br.gov.mt.sema.rastro.acesso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;

class PoliticaDeSenhaTest {

    @Test
    void aceita_senha_de_12_a_200_caracteres() {
        assertThat(PoliticaDeSenha.atende("operador", "senha-de-teste-123")).isTrue();
        assertThat(PoliticaDeSenha.atende("operador", "x".repeat(12))).isTrue();
        assertThat(PoliticaDeSenha.atende("operador", "x".repeat(200))).isTrue();
    }

    @Test
    void recusa_curta_longa_nula_e_so_espacos() {
        assertThat(PoliticaDeSenha.atende("operador", "x".repeat(11))).isFalse();
        assertThat(PoliticaDeSenha.atende("operador", "x".repeat(201))).isFalse();
        assertThat(PoliticaDeSenha.atende("operador", null)).isFalse();
        assertThat(PoliticaDeSenha.atende("operador", "                ")).isFalse();
    }

    @Test
    void recusa_senha_igual_ao_login_ou_que_o_contenha() {
        assertThat(PoliticaDeSenha.atende("usuario-com-login-longo", "usuario-com-login-longo")).isFalse();
        assertThat(PoliticaDeSenha.atende("operador", "minha-OPERADOR-123")).isFalse();
    }

    @Test
    void mensagem_cita_o_campo_e_nao_revela_regra_alem_da_politica() {
        assertThatThrownBy(() -> PoliticaDeSenha.validar("operador", "curta"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> {
                    assertThat(e.campo()).isEqualTo("senha");
                    assertThat(e.getMessage()).contains("12").contains("200").doesNotContain("curta");
                });
    }
}
