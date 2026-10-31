package br.gov.mt.sema.vistas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import br.gov.mt.sema.vistas.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.vistas.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.vistas.configuracao.application.port.RepositorioDeSobreposicoes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ParametrosDaAuditoriaDoAmbienteTest {

    private static final ParametrosEmVigor SEM_SOBREPOSICAO = new ParametrosEmVigor(new RepositorioDeSobreposicoes() {
        @Override
        public Map<String, String> todas() {
            return Map.of();
        }

        @Override
        public void substituir(Map<String, String> sobreposicoes) {
        }
    }, new MockEnvironment());

    @Test
    void aceita_as_faixas_de_retencao() {
        ParametrosDaAuditoriaDoAmbiente parametros = new ParametrosDaAuditoriaDoAmbiente(10, 10, 2, 7, SEM_SOBREPOSICAO);

        assertThat(parametros.maximoDeEventos()).isEqualTo(10);
        assertThat(parametros.maximoDeLinhasDoCsv()).isEqualTo(7);
        assertThat(parametros.retencaoEmAnos(GrupoDeEvento.DOCUMENTOS)).isEqualTo(10);
        assertThat(parametros.retencaoEmAnos(GrupoDeEvento.ACESSOS)).isEqualTo(2);
    }

    @Test
    void valor_fora_da_faixa_cai_no_padrao() {
        ParametrosDaAuditoriaDoAmbiente parametros = new ParametrosDaAuditoriaDoAmbiente(0, 3, 7, 0, SEM_SOBREPOSICAO);

        assertThat(parametros.maximoDeEventos()).isEqualTo(50_000);
        assertThat(parametros.maximoDeLinhasDoCsv()).isEqualTo(50_000);
        assertThat(parametros.retencaoDocumentosAnos()).isEqualTo(5);
        assertThat(parametros.retencaoAcessosAnos()).isEqualTo(1);
    }
}
