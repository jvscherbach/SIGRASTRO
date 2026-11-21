package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Catálogo de grupos (rastro/grupos.yaml, S7 bloco 4): os grupos reais da regra técnica 13 e a validação na subida. */
class CatalogoDeGruposEmArquivoTest {

    private static final CatalogoDeGruposEmArquivo CATALOGO = new CatalogoDeGruposEmArquivo();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "GrpProcSurhResponsavelAtendimento|Atendimento (recursos hídricos)",
            "grpSurhConferirDocumentoSubterranea|Conferência de documentos (subterrânea)",
            "grpSurhConferirDocumentoSuperficial|Conferência de documentos (superficial)",
            "grpSurhConferirDocumentoBarramento|Conferência de documentos (barramento)",
            "grpProcSurhConferirDocumentoMultidisciplinar|Conferência de documentos (multidisciplinar)",
            "grpProcSurhResponsavelDistribuicao|Distribuição de requerimentos",
            "grpSurhAtribuirRequerimentoSubterranea|Atribuição de requerimentos (subterrânea)",
            "grpSurhAtribuirRequerimentoSuperficial|Atribuição de requerimentos (superficial)",
            "grpSurhAtribuirRequerimentoBarramento|Atribuição de requerimentos (barramento)",
            "grpProcSurhAtribuirRequerimentoMultidisciplinar|Atribuição de requerimentos (multidisciplinar)",
            "GrpProcPublicacaoOficial|Publicação oficial",
            "grpSurhRegistrarPublicacaoSubterranea|Registro de publicação (subterrânea)",
            "grpSurhRegistrarPublicacaoSuperficial|Registro de publicação (superficial)",
            "grpSurhRegistrarPublicacaoBarramento|Registro de publicação (barramento)",
            "grpProcSurhRegistrarPublicacaoMultidisciplinar|Registro de publicação (multidisciplinar)",
            "grpSurhRevisarDecisaoSubterranea|Revisão da decisão (subterrânea)",
            "grpSurhRevisarDecisaoSuperficial|Revisão da decisão (superficial)",
            "grpSurhRevisarDecisaoBarramento|Revisão da decisão (barramento)",
            "grpSurhRevisarDecisaoMultidisciplinar|Revisão da decisão (multidisciplinar)",
            "GrpProcSurhEmissorTitulos|Emissão de títulos",
            "GrpProcResponsavelAtendimentoLac|Atendimento (LAC)",
            "suporte|Suporte técnico"})
    void cada_grupo_real_tem_area(String grupo, String area) {
        assertThat(CATALOGO.area(grupo)).contains(area);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "grpFicticioDesconhecido", " ", "GRPPROCPUBLICACAOOFICIAL"})
    void desconhecido_zero_ou_vazio_nao_tem_area(String grupo) {
        assertThat(CATALOGO.area(grupo)).isEmpty();
        assertThat(CATALOGO.area(null)).isEmpty();
    }

    @Test
    void id_repetido_rotulo_vazio_zero_e_campo_desconhecido_derrubam_a_subida() {
        assertThatThrownBy(() -> new CatalogoDeGruposEmArquivo("""
                grupos:
                  - {id: grpA, rotulo: Área A}
                  - {id: grpA, rotulo: Outra}
                  - {id: grpB, rotulo: " "}
                  - {id: "0", rotulo: Sem área}
                  - {id: grpC, rotulo: C, cor: azul}
                """))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("grupos.yaml")
                .hasMessageContaining("grupo 'grpA' repetido")
                .hasMessageContaining("grupo 'grpB' sem rótulo")
                .hasMessageContaining("reservado")
                .hasMessageContaining("campo desconhecido 'cor'");
    }

    @Test
    void arquivo_vazio_ou_malformado_derruba_a_subida() {
        assertThatThrownBy(() -> new CatalogoDeGruposEmArquivo("")).isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("sem a lista");
        assertThatThrownBy(() -> new CatalogoDeGruposEmArquivo("grupos: [")).isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("YAML malformado");
    }
}
