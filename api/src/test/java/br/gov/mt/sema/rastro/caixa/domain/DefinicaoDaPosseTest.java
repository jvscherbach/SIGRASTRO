package br.gov.mt.sema.rastro.caixa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;

/** A definição da posse vem do catálogo (nada de id inventado) e a chave opaca não revela o identificador. Sem Docker. */
class DefinicaoDaPosseTest {

    private static final byte[] SAL = "sal-de-teste-0123456789".getBytes(StandardCharsets.UTF_8);

    @Test
    void soOHidricoTemAnaliseERevisaoNoCatalogo() {
        DefinicaoDaPosse definicao = DefinicaoDaPosse.doCatalogo(CatalogoDeEtapasDeTeste.padrao());
        assertThat(definicao.chavesDosModelos()).containsExactly("procOutorgaAguaSupSub");
        assertThat(definicao.atividadesDePosse()).containsExactlyInAnyOrder("taskAnalisarRequerimento",
                "taskRevisarParecerTecnico");
        assertThat(definicao.atividadesDeCorrecao()).contains("taskCorrecaoRequerimento")
                .doesNotContain("taskAnalisarRequerimento");
        assertThat(definicao.vazia()).isFalse();
    }

    @Test
    void semModeloOuSemAtividadeDePosseEstaVazia() {
        assertThat(new DefinicaoDaPosse(java.util.Set.of(), java.util.Set.of("a"), java.util.Set.of()).vazia()).isTrue();
        assertThat(new DefinicaoDaPosse(java.util.Set.of("m"), java.util.Set.of(), java.util.Set.of("c")).vazia()).isTrue();
    }

    @Test
    void aChaveEEstavelComOMesmoSalDiferenteComOutroENaoMostraOIdentificador() {
        IdentificadorDoResponsavel id = IdentificadorDoResponsavel.de("00000000001").orElseThrow();
        ChaveDoResponsavel chave = ChaveDoResponsavel.de(id, SAL);
        assertThat(chave).isEqualTo(ChaveDoResponsavel.de(id, SAL));
        assertThat(chave).isNotEqualTo(ChaveDoResponsavel.de(id, "outro-sal-de-teste-0123".getBytes(StandardCharsets.UTF_8)));
        assertThat(chave.valor()).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(chave.toString()).doesNotContain("00000000001");
        assertThatThrownBy(() -> ChaveDoResponsavel.de(id, new byte[4])).isInstanceOf(IllegalArgumentException.class);
    }
}
