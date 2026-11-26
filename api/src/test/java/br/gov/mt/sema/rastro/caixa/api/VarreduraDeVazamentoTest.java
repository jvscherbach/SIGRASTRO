package br.gov.mt.sema.rastro.caixa.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Prova do helper de varredura do teste de vazamento da caixa, sem Spring: UUID de correlação não é falso positivo. */
class VarreduraDeVazamentoTest {

    // último grupo com 11 dígitos decimais precedidos de uma letra hexadecimal: caso do falso positivo
    private static final String UUID_COM_11_DIGITOS = "ce428a6b-3943-472b-92a3-c90247559032";
    private static final String UUID_SO_DIGITOS = "ce428a6b-3943-472b-92a3-a12345678901";

    @Test
    void uuidCujoUltimoGrupoTemOnzeDigitosNaoDisparaMasOCpfSinteticoDispara() {
        String cabecalhos = "[X-Correlation-Id=[" + UUID_SO_DIGITOS + "], Content-Type=[application/json]]";
        assertThatCode(() -> CaixaAcessoVazamentoResilienciaHttpTest.semDadoPessoalNemChave(cabecalhos))
                .doesNotThrowAnyException();
        assertThatCode(() -> CaixaAcessoVazamentoResilienciaHttpTest
                .semDadoPessoalNemChave("{\"correlationId\":\"" + UUID_COM_11_DIGITOS + "\"}")).doesNotThrowAnyException();
    }

    @Test
    void cpfOnzeDigitosEChaveLongaAindaDisparamMesmoComUuidNoTexto() {
        String cpf = CaixaAcessoVazamentoResilienciaHttpTest.cpfSinteticoDigitos();
        assertThatThrownBy(() -> CaixaAcessoVazamentoResilienciaHttpTest.semDadoPessoalNemChave(cpf))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> CaixaAcessoVazamentoResilienciaHttpTest
                .semDadoPessoalNemChave("id=" + UUID_SO_DIGITOS + " cpf=" + cpf)).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> CaixaAcessoVazamentoResilienciaHttpTest
                .semDadoPessoalNemChave("x=" + "a1b2c3d4".repeat(5))).isInstanceOf(AssertionError.class);
        // outro valor de 11 dígitos, que não é o CPF da fixture, também dispara
        assertThatThrownBy(() -> CaixaAcessoVazamentoResilienciaHttpTest
                .semDadoPessoalNemChave("n=" + "00000000001" + " " + UUID_SO_DIGITOS)).isInstanceOf(AssertionError.class);
    }
}
