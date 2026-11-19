package br.gov.mt.sema.rastro.consulta.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;

class CriterioBuscaTest {

    private static void rejeita(String campo, Runnable criacao) {
        assertThatThrownBy(criacao::run)
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campo()).isEqualTo(campo));
    }

    @Test
    void semNenhumCriterioEhInvalido() {
        rejeita("criterio", () -> CriterioBusca.de(null, null, null, null, null, null));
        rejeita("criterio", () -> CriterioBusca.de(" ", "", "  ", "", null, null));
    }

    @Test
    void numeroExigeTipoETipoExigeNumero() {
        rejeita("tipo", () -> CriterioBusca.de(null, "3331/2025", null, null, null, null));
        rejeita("numero", () -> CriterioBusca.de("PROCESSO", null, null, null, null, null));
        rejeita("numero", () -> CriterioBusca.de("PROCESSO", "3331", null, null, null, null));
        rejeita("tipo", () -> CriterioBusca.de("OUTRO", "3331/2025", null, null, null, null));
    }

    @Test
    void tipoAceitaMinusculas() {
        assertThat(CriterioBusca.de("processo", "1/2025", null, null, null, null).tipo()).isEqualTo(TipoBusca.PROCESSO);
    }

    @Test
    void nomeComMenosDeTresCaracteresEhInvalido() {
        rejeita("nome", () -> CriterioBusca.de(null, null, null, "ab", null, null));
        assertThat(CriterioBusca.de(null, null, null, " abc ", null, null).nome()).isEqualTo("abc");
    }

    @Test
    void cpfCnpjInvalidoEhRejeitado() {
        rejeita("cpfCnpj", () -> CriterioBusca.de(null, null, "123", null, null, null));
    }

    @Test
    void paginacaoTemPadraoELimites() {
        CriterioBusca padrao = CriterioBusca.de(null, null, null, "silva", null, null);

        assertThat(padrao.pagina()).isZero();
        assertThat(padrao.tamanho()).isEqualTo(20);
        assertThat(CriterioBusca.de(null, null, null, "silva", 2, 50).inicio()).isEqualTo(100);
        rejeita("pagina", () -> CriterioBusca.de(null, null, null, "silva", -1, null));
        rejeita("tamanho", () -> CriterioBusca.de(null, null, null, "silva", 0, 0));
        rejeita("tamanho", () -> CriterioBusca.de(null, null, null, "silva", 0, 101));
        assertThat(CriterioBusca.de(null, null, null, "silva", 0, 100).tamanho()).isEqualTo(100);
    }

    @Test
    void apenasNumeroSoQuandoNaoHaCpfNemNome() {
        assertThat(CriterioBusca.de("PROCESSO", "1/2025", null, null, null, null).apenasNumero()).isTrue();
        assertThat(CriterioBusca.de("PROCESSO", "1/2025", "12345678909", null, null, null).apenasNumero()).isFalse();
        assertThat(CriterioBusca.de("PROCESSO", "1/2025", null, "silva", null, null).apenasNumero()).isFalse();
        assertThat(CriterioBusca.de(null, null, null, "silva", null, null).apenasNumero()).isFalse();
    }

    @Test
    void padraoLikeEscapaPercentUnderscoreEBarra() {
        CriterioBusca criterio = CriterioBusca.de(null, null, null, "a%b_c\\d", null, null);

        assertThat(criterio.nomeComoPadraoLike()).isEqualTo("%A\\%B\\_C\\\\D%");
    }

    @Test
    void restricaoNaoMudaOsDemaisCriterios() {
        CriterioBusca base = CriterioBusca.de(null, null, null, "silva", 1, 10);

        CriterioBusca restrito = base.comRestricao("12345678909");

        assertThat(base.restricaoDigitos()).isNull();
        assertThat(restrito.restricaoDigitos()).isEqualTo("12345678909");
        assertThat(restrito.nome()).isEqualTo("silva");
        assertThat(restrito.pagina()).isEqualTo(1);
    }
}
