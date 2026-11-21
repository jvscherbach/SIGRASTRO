package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;
import br.gov.mt.sema.rastro.tramitacao.domain.MapaDeEventosDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Mapa de eventos do plano B (S8, bloco 5): o arquivo real e a validação na subida. */
class MapaDeEventosEmArquivoTest {

    private static final MapaDeEventosEmArquivo MAPA = new MapaDeEventosEmArquivo(CatalogoDeEtapasDeTeste.padrao());

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "procOutorgaAguaSupSub, CRIACAO_REQUERIMENTO, cadastro_do_requerimento",
            "procOutorgaAguaSupSub, AGUARDANDO_CONFERENCIA, conferencia_dos_documentos",
            "procOutorgaAguaSupSub, CHECKLIST_APROVADO, conferencia_dos_documentos",
            "procOutorgaAguaSupSub, CHECKLIST_REPROVADO, conferencia_dos_documentos",
            "procOutorgaAguaSupSub, AGUARDANDO_PAGAMENTO_TAXA, aguardando_pagamento",
            "procOutorgaAguaSupSub, PAGAMENTO_CONFIRMADO, aguardando_pagamento",
            "procOutorgaAguaSupSub, EM_ANALISE, analise_do_processo",
            "procOutorgaAguaSupSub, REVISAR_PARECER_TECNICO, analise_do_processo",
            "procOutorgaAguaSupSub, VALIDAR_PARECER_TECNICO, analise_do_processo",
            "procOutorgaAguaSupSub, TITULOS_EMITIDOS, emissao_do_ato",
            "procOutorgaAguaSupSub, TITULACOES_DISPONIVEIS, registro_do_comprovante_de_publicacao",
            "procLicenciamentoLac, CRIACAO_REQUERIMENTO, cadastro_do_requerimento",
            "procLicenciamentoLac, TITULOS_EMITIDOS, conclusao"})
    void eventos_do_prompt_viram_as_etapas_do_catalogo(String chave, String evento, String etapa) {
        assertThat(MAPA.resolver(chave, evento)).isEqualTo(new MapaDeEventosDoHistorico.NaEtapa(etapa));
    }

    @Test
    void protocolo_e_conclusao_sao_marcos_e_o_resto_e_desconhecido() {
        assertThat(MAPA.resolver("procOutorgaAguaSupSub", "PROTOCOLO"))
                .isEqualTo(new MapaDeEventosDoHistorico.Marco(TipoDeMarco.PROTOCOLO));
        assertThat(MAPA.resolver("procOutorgaAguaSupSub", "CONCLUSAO_REQUERIMENTO"))
                .isEqualTo(new MapaDeEventosDoHistorico.Marco(TipoDeMarco.CONCLUSAO));
        assertThat(MAPA.resolver("procOutorgaAguaSupSub", "EVENTO_FICTICIO_DESCONHECIDO"))
                .isInstanceOf(MapaDeEventosDoHistorico.Desconhecido.class);
        assertThat(MAPA.resolver("procLicenciamentoLac", "EM_ANALISE"))
                .isInstanceOf(MapaDeEventosDoHistorico.Desconhecido.class);
        assertThat(MAPA.resolver("modeloFicticio", "PROTOCOLO")).isInstanceOf(MapaDeEventosDoHistorico.Desconhecido.class);
        assertThat(MAPA.resolver(null, null)).isInstanceOf(MapaDeEventosDoHistorico.Desconhecido.class);
    }

    @Test
    void problemas_derrubam_a_subida_todos_de_uma_vez() {
        String ruim = """
                procOutorgaAguaSupSub:
                  CRIACAO_REQUERIMENTO: {etapa: etapa_inexistente}
                  AMBOS: {etapa: cadastro_do_requerimento, marco: PROTOCOLO}
                  NENHUM: {}
                  MARCO_RUIM: {marco: ABERTURA}
                  CAMPO: {etapa: cadastro_do_requerimento, cor: azul}
                  minusculo: {etapa: cadastro_do_requerimento}
                modeloInexistente:
                  X: {etapa: y}
                """;
        assertThatThrownBy(() -> new MapaDeEventosEmArquivo(ruim, CatalogoDeEtapasDeTeste.padrao()))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("etapa_inexistente").hasMessageContaining("AMBOS").hasMessageContaining("NENHUM")
                .hasMessageContaining("ABERTURA").hasMessageContaining("cor").hasMessageContaining("minusculo")
                .hasMessageContaining("modeloInexistente");
        assertThatThrownBy(() -> new MapaDeEventosEmArquivo("", CatalogoDeEtapasDeTeste.padrao()))
                .isInstanceOf(ModeloDeFluxoInvalido.class);
        assertThatThrownBy(() -> new MapaDeEventosEmArquivo("a: [", CatalogoDeEtapasDeTeste.padrao()))
                .isInstanceOf(ModeloDeFluxoInvalido.class);
    }
}
