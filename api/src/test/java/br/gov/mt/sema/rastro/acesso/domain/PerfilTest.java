package br.gov.mt.sema.rastro.acesso.domain;

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
    void analistaSoTemACaixa() {
        Perfil perfil = Perfil.ANALISTA;

        assertThat(perfil.podeVerCaixa()).isTrue();
        assertThat(perfil.restritoAoInteressado()).isFalse();
        assertThat(perfil.podeVerSigilosos()).isFalse();
        assertThat(perfil.podeVerAuditoriaAcessos()).isFalse();
        assertThat(perfil.podeAcessarConfiguracoes()).isFalse();
        assertThat(perfil.podeVerResponsavelTarefa()).isFalse();
        assertThat(perfil.podeVerPainel()).isFalse();
        assertThat(perfil.podeVerIdsTecnicosDoFluxo()).isFalse();
        assertThat(perfil.rotulo()).isEqualTo("Analista");
    }

    @Test
    void soOAnalistaTemCaixa() {
        assertThat(Perfil.OPERADOR.podeVerCaixa()).isFalse();
        assertThat(Perfil.OPERADOR_SIGILO.podeVerCaixa()).isFalse();
        assertThat(Perfil.ADMIN.podeVerCaixa()).isFalse();
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
