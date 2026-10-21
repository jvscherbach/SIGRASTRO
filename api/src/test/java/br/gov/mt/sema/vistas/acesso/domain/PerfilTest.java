package br.gov.mt.sema.vistas.acesso.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PerfilTest {

    @Test
    void operadorSoVeProcessosDoInteressadoESemPrivilegios() {
        Perfil perfil = Perfil.OPERADOR;

        assertThat(perfil.restritoAoInteressado()).isTrue();
        assertThat(perfil.podeVerSigilosos()).isFalse();
        assertThat(perfil.podeVerAuditoriaAcessos()).isFalse();
        assertThat(perfil.podeAcessarConfiguracoes()).isFalse();
        assertThat(perfil.podeVerResponsavelTarefa()).isFalse();
    }

    @Test
    void gestorVeTudoMasNaoAuditoriaDeAcessosNemConfiguracoes() {
        Perfil perfil = Perfil.OPERADOR_SIGILO;

        assertThat(perfil.restritoAoInteressado()).isFalse();
        assertThat(perfil.podeVerSigilosos()).isTrue();
        assertThat(perfil.podeVerAuditoriaAcessos()).isFalse();
        assertThat(perfil.podeAcessarConfiguracoes()).isFalse();
        assertThat(perfil.podeVerResponsavelTarefa()).isTrue();
    }

    @Test
    void administradorTemAcessoIrrestrito() {
        Perfil perfil = Perfil.ADMIN;

        assertThat(perfil.restritoAoInteressado()).isFalse();
        assertThat(perfil.podeVerSigilosos()).isTrue();
        assertThat(perfil.podeVerAuditoriaAcessos()).isTrue();
        assertThat(perfil.podeAcessarConfiguracoes()).isTrue();
        assertThat(perfil.podeVerResponsavelTarefa()).isTrue();
    }
}
