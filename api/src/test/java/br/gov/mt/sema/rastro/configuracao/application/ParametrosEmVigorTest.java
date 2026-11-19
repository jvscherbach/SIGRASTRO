package br.gov.mt.sema.rastro.configuracao.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.configuracao.application.port.RepositorioDeSobreposicoes;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ParametrosEmVigorTest {

    private final Map<String, String> atuais = new HashMap<>();
    private final ParametrosEmVigor vigentes = new ParametrosEmVigor(new RepositorioDeSobreposicoes() {
        @Override
        public Map<String, String> todas() {
            return Map.copyOf(atuais);
        }

        @Override
        public void substituir(Map<String, String> sobreposicoes) {
            atuais.clear();
            atuais.putAll(sobreposicoes);
        }
    }, new MockEnvironment().withProperty("rastro.dossie.ttl-minutos", "45"));

    @Test
    void sem_sobreposicao_vale_o_ambiente() {
        assertThat(vigentes.inteiro(CatalogoDeParametros.DOSSIE_TTL, 45)).isEqualTo(45);
    }

    @Test
    void retrato_guarda_o_ambiente_quando_nada_foi_sobreposto_e_protege_de_mudanca_posterior() {
        Map<String, String> retrato = vigentes.retrato();
        atuais.put(CatalogoDeParametros.DOSSIE_TTL, "200");

        assertThat(vigentes.inteiro(CatalogoDeParametros.DOSSIE_TTL, 45)).isEqualTo(200);
        assertThat(vigentes.comRetrato(retrato, () -> vigentes.inteiro(CatalogoDeParametros.DOSSIE_TTL, 45))).isEqualTo(45);
    }

    @Test
    void retrato_nao_alcanca_parametro_imediato() {
        Map<String, String> retrato = vigentes.retrato();
        atuais.put(CatalogoDeParametros.VISUALIZACAO_TTL, "20");

        assertThat(retrato).doesNotContainKey(CatalogoDeParametros.VISUALIZACAO_TTL);
        assertThat(vigentes.comRetrato(retrato, () -> vigentes.inteiro(CatalogoDeParametros.VISUALIZACAO_TTL, 10))).isEqualTo(20);
    }

    @Test
    void retrato_nao_vaza_para_depois_nem_com_excecao() {
        Map<String, String> retrato = vigentes.retrato();
        atuais.put(CatalogoDeParametros.DOSSIE_TTL, "200");

        try {
            vigentes.comRetrato(retrato, () -> {
                throw new IllegalStateException();
            });
        } catch (IllegalStateException esperado) {
            // segue
        }

        assertThat(vigentes.inteiro(CatalogoDeParametros.DOSSIE_TTL, 45)).isEqualTo(200);
    }
}
