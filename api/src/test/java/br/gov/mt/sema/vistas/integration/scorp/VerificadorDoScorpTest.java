package br.gov.mt.sema.vistas.integration.scorp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao.Estado;
import br.gov.mt.sema.vistas.configuracao.application.port.VerificadorDeConexao.Resultado;
import br.gov.mt.sema.vistas.testsupport.ServidorHttpDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerificadorDoScorpTest {

    private ServidorHttpDeTeste servidor;

    @BeforeEach
    void iniciar() throws IOException {
        servidor = ServidorHttpDeTeste.iniciar();
    }

    @AfterEach
    void parar() {
        servidor.close();
    }

    private VerificadorDoScorp verificador() {
        return new VerificadorDoScorp(servidor.url(), origem -> true, CredencialDoScorp.de("token", null, null), "bearer", "");
    }

    private Resultado comStatus(int status) {
        servidor.responderCom(troca -> troca.responder(status, new byte[0]));
        return verificador().verificar();
    }

    @Test
    void resposta_com_404_ou_200_prova_que_responde() {
        assertThat(comStatus(404).sucesso()).isTrue();
        assertThat(comStatus(200).sucesso()).isTrue();
    }

    @Test
    void credencial_recusada_e_falha_do_servico() {
        assertThat(comStatus(401)).isEqualTo(new Resultado(false, "Credencial recusada."));
        assertThat(comStatus(500)).isEqualTo(new Resultado(false, "Serviço respondeu HTTP 500."));
    }

    @Test
    void estado() {
        assertThat(verificador().estado()).isEqualTo(Estado.CONFIGURADA);
        assertThat(new VerificadorDoScorp(" ", origem -> true, CredencialDoScorp.de("t", null, null), "bearer", "").estado())
                .isEqualTo(Estado.NAO_CONFIGURADA);
        assertThat(new VerificadorDoScorp(servidor.url(), origem -> false, CredencialDoScorp.de("t", null, null), "bearer", "")
                .estado()).isEqualTo(Estado.DESATIVADA);
    }
}
