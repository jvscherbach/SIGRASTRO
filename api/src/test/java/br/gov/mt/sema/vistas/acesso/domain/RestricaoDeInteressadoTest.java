package br.gov.mt.sema.vistas.acesso.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RestricaoDeInteressadoTest {

    private static Usuario usuario(Perfil perfil, String cpfCnpj) {
        return new Usuario("u", "U", perfil, cpfCnpj, true);
    }

    @Test
    void operadorSoPermiteOMesmoCpfCnpjComOuSemMascara() {
        RestricaoDeInteressado restricao = RestricaoDeInteressado.de(usuario(Perfil.OPERADOR, "12.345.678/0001-90"));

        assertThat(restricao.restrita()).isTrue();
        assertThat(restricao.digitos()).isEqualTo("12345678000190");
        assertThat(restricao.permite("12345678000190")).isTrue();
        assertThat(restricao.permite("12.345.678/0001-90")).isTrue();
        assertThat(restricao.permite("45.678.901/0001-23")).isFalse();
        assertThat(restricao.permite(null)).isFalse();
    }

    @Test
    void operadorSemCpfCnpjCadastradoNaoAcessaNada() {
        for (String vazio : new String[] {null, "", "  ", "--"}) {
            RestricaoDeInteressado restricao = RestricaoDeInteressado.de(usuario(Perfil.OPERADOR, vazio));

            assertThat(restricao.semAcessoAlgum()).as("[" + vazio + "]").isTrue();
            assertThat(restricao.permite("12345678000190")).isFalse();
            assertThat(restricao.permite(null)).isFalse();
        }
    }

    @Test
    void gestorEAdministradorNaoTemRestricao() {
        for (Perfil perfil : new Perfil[] {Perfil.OPERADOR_SIGILO, Perfil.ADMIN}) {
            RestricaoDeInteressado restricao = RestricaoDeInteressado.de(usuario(perfil, null));

            assertThat(restricao.restrita()).isFalse();
            assertThat(restricao.semAcessoAlgum()).isFalse();
            assertThat(restricao.permite("12345678000190")).isTrue();
            assertThat(restricao.permite(null)).isTrue();
        }
    }
}
