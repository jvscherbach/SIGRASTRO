package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.junit.jupiter.api.Test;

/** Catálogo do confronto (rastro/confronto.yaml, S7 bloco 5): conteúdo real e validação na subida. */
class CatalogoDoConfrontoEmArquivoTest {

    private static final CatalogoDeEtapas ETAPAS = CatalogoDeEtapasDeTeste.padrao();
    private static final CatalogoDoConfrontoEmArquivo CATALOGO = new CatalogoDoConfrontoEmArquivo(ETAPAS);

    @Test
    void todo_codigo_tem_texto_e_gravidade() {
        Arrays.stream(CodigoDoAviso.values()).forEach(c -> assertThat(CATALOGO.aviso(c, "X"))
                .satisfies(a -> {
                    assertThat(a.texto()).isNotBlank().doesNotContain("{situacao}");
                    assertThat(a.gravidade()).isNotNull();
                }));
        assertThat(CATALOGO.aviso(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO, "X").gravidade())
                .isEqualTo(Aviso.Gravidade.INFO);
    }

    @Test
    void toda_etapa_e_todo_final_dos_dois_modelos_tem_tabela() {
        ETAPAS.modelos().forEach(m -> {
            m.etapas().stream().map(Etapa::id)
                    .forEach(id -> assertThat(CATALOGO.daEtapa(m.chave(), id)).as(m.chave() + "." + id).isPresent());
            m.finais().stream().map(FinalDoFluxo::id)
                    .forEach(id -> assertThat(CATALOGO.doFinal(m.chave(), id)).as(m.chave() + "." + id).isPresent());
        });
    }

    @Test
    void aguardando_emissao_de_taxas_aceita_duas_etapas_do_hidrico() {
        assertThat(ETAPAS.modelo("procOutorgaAguaSupSub").orElseThrow().etapas())
                .filteredOn(e -> CATALOGO.daEtapa("procOutorgaAguaSupSub", e.id()).orElseThrow().aceitas()
                        .contains("AGUARDANDO_EMISSAO_DE_TAXAS"))
                .extracting(Etapa::id).containsExactlyInAnyOrder("emissao_da_taxa", "emissao_da_taxa_correcao");
    }

    private static final String MINIMO = """
            situacoes: [EM_ANALISE, CONCLUIDO]
            avisos:
              - {codigo: FLUXO_ENCERRADO_SEM_STATUS, gravidade: ATENCAO, texto: a}
              - {codigo: CONCLUIDO_ANTES_DA_VERIFICACAO, gravidade: INFO, texto: b}
              - {codigo: SITUACAO_NAO_CONFERE, gravidade: ATENCAO, texto: "c {situacao}"}
              - {codigo: FINAL_E_SITUACAO_DIVERGEM, gravidade: ATENCAO, texto: "i {situacao}"}
              - {codigo: SITUACAO_ARQUIVADO_TEMPORARIAMENTE, gravidade: INFO, texto: j}
              - {codigo: EXPIRADO_NA_CORRECAO_LAC, gravidade: INFO, texto: d}
              - {codigo: VARIAS_INSTANCIAS, gravidade: ATENCAO, texto: e}
              - {codigo: EM_SUPORTE, gravidade: INFO, texto: f}
              - {codigo: ETAPA_NAO_MAPEADA, gravidade: ATENCAO, texto: g}
              - {codigo: SEM_INSTANCIA, gravidade: INFO, texto: h}
              - {codigo: HISTORICO_APROXIMADO, gravidade: INFO, texto: k}
              - {codigo: HISTORICO_TRUNCADO, gravidade: ATENCAO, texto: l}
              - {codigo: TAXA_VENCIDA, gravidade: ATENCAO, texto: "m{situacao}."}
            compatibilidade:
              procOutorgaAguaSupSub:
                etapas:
                  analise_do_processo: {aceitas: [EM_ANALISE]}
            """;

    @Test
    void arquivo_minimo_carrega_e_troca_o_marcador() {
        CatalogoDoConfrontoEmArquivo c = new CatalogoDoConfrontoEmArquivo(MINIMO, ETAPAS);
        assertThat(c.aviso(CodigoDoAviso.SITUACAO_NAO_CONFERE, "EM_ANALISE").texto()).isEqualTo("c EM_ANALISE");
        assertThat(c.daEtapa("procOutorgaAguaSupSub", "cadastro_do_requerimento")).isEmpty();
    }

    @Test
    void aviso_de_taxa_vencida_com_e_sem_data_e_a_explicacao_so_por_data() {
        assertThat(br.gov.mt.sema.rastro.tramitacao.domain.AvisoDeTaxaVencida.de(CATALOGO,
                java.util.Optional.of(java.time.LocalDate.of(2026, 3, 19)), true).texto())
                .isEqualTo("Taxa vencida em 19/03/2026. O SIGA ainda não marca a taxa como vencida; o Rastro compara a "
                        + "data de vencimento com hoje.");
        assertThat(br.gov.mt.sema.rastro.tramitacao.domain.AvisoDeTaxaVencida.de(CATALOGO,
                java.util.Optional.of(java.time.LocalDate.of(2026, 3, 19)), false).texto())
                .isEqualTo("Taxa vencida em 19/03/2026.");
        assertThat(br.gov.mt.sema.rastro.tramitacao.domain.AvisoDeTaxaVencida.de(CATALOGO, java.util.Optional.empty(),
                false).texto()).isEqualTo("Taxa vencida.");
    }

    @Test
    void situacao_codigo_modelo_etapa_e_final_desconhecidos_derrubam_a_subida() {
        String ruim = MINIMO
                .replace("analise_do_processo: {aceitas: [EM_ANALISE]}", """
                        analise_do_processo: {aceitas: [EM_ANALIZE]}
                              etapa_inexistente: {aceitas: [EM_ANALISE]}
                              aceite_pelo_analista: {conhecidas: {CONCLUIDO: SITUACAO_NAO_CONFERE}, cor: azul}
                            finais:
                              final_inexistente: {aceitas: [CONCLUIDO]}
                          procInexistente:
                            etapas: {}""")
                .replace("- {codigo: SEM_INSTANCIA, gravidade: INFO, texto: h}", "- {codigo: SEM_INSTANCIA, gravidade: ALTA, texto: h}");
        assertThatThrownBy(() -> new CatalogoDoConfrontoEmArquivo(ruim, ETAPAS))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("confronto.yaml")
                .hasMessageContaining("situação desconhecida 'EM_ANALIZE'")
                .hasMessageContaining("etapa_inexistente: id fora do catálogo")
                .hasMessageContaining("final_inexistente: id fora do catálogo")
                .hasMessageContaining("não é aviso de divergência conhecida")
                .hasMessageContaining("campo desconhecido 'cor'")
                .hasMessageContaining("modelo 'procInexistente' fora do catálogo")
                .hasMessageContaining("gravidade desconhecido 'ALTA'")
                .hasMessageContaining("aviso 'SEM_INSTANCIA' sem texto no arquivo");
    }

    @Test
    void aviso_sem_texto_ou_faltando_derruba_a_subida() {
        assertThatThrownBy(() -> new CatalogoDoConfrontoEmArquivo(MINIMO.replace("texto: g", "texto: \" \""), ETAPAS))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("'ETAPA_NAO_MAPEADA' sem texto");
        assertThatThrownBy(() -> new CatalogoDoConfrontoEmArquivo("", ETAPAS)).isInstanceOf(ModeloDeFluxoInvalido.class);
    }
}
