package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.application.LerTramitacao;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.PapelDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.TipoDeEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.junit.jupiter.api.Test;

/**
 * O caminho da tramitação para o financeiro (S9, blocos 4 e 5): só o caminho exato, com etapa, ramo e entrada; plano B,
 * indisponível, não suportado e falha viram {@link CaminhoDoFluxo#NENHUM} (o financeiro não depende do Camunda).
 */
class CaminhoPelaTramitacaoTest {

    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 3, 4, 10, 0);

    private final LerTramitacao tramitacao = mock(LerTramitacao.class);
    private final CaminhoPelaTramitacao caminhos = new CaminhoPelaTramitacao(tramitacao);

    private static PassagemPelaEtapa passagem(String id, Ramo ramo, LocalDateTime entrada) {
        Etapa etapa = new Etapa(id, id, 1, TipoDeEtapa.AUTOMATICA, PapelDoResponsavel.SISTEMA, ramo, false,
                List.of("idFicticio"), List.of(), List.of());
        return new PassagemPelaEtapa(Optional.of(etapa), "idFicticio", 1, ramo, entrada,
                Optional.of(entrada.plusMinutes(5)), List.of(), false, List.of(), Optional.empty(), false, 1, false);
    }

    private static TramitacaoDoRequerimento com(TramitacaoDoRequerimento.Estado estado, PassagemPelaEtapa... ps) {
        CaminhoReconstruido caminho = new CaminhoReconstruido(List.of(ps), List.of(),
                CaminhoReconstruido.ClassificacaoDaTaxa.ISENTA, Optional.empty(), Set.of(), 0, Set.of(), false, 1);
        return new TramitacaoDoRequerimento(estado, Optional.of("procOutorgaAguaSupSub"), Optional.empty(), caminho,
                Optional.empty(), Optional.empty(), List.of());
    }

    @Test
    void caminho_exato_com_etapa_ramo_e_entrada_na_mesma_ordem() {
        when(tramitacao.ler(1)).thenReturn(com(TramitacaoDoRequerimento.Estado.ATIVO,
                passagem("isencao_confirmada", Ramo.PRINCIPAL, ENTRADA),
                passagem("correcao_do_requerimento", Ramo.CORRECAO, ENTRADA.plusDays(8))));
        CaminhoDoFluxo c = caminhos.ler(1);
        assertThat(c.passagens()).hasSize(2);
        assertThat(c.entradasDeIsencao()).containsExactly(ENTRADA);
        assertThat(c.entradaNaCorrecao()).contains(ENTRADA.plusDays(8));
    }

    @Test
    void encerrado_tambem_serve() {
        when(tramitacao.ler(1)).thenReturn(com(TramitacaoDoRequerimento.Estado.ENCERRADO,
                passagem("isencao_confirmada", Ramo.PRINCIPAL, ENTRADA)));
        assertThat(caminhos.ler(1).entradasDeIsencao()).containsExactly(ENTRADA);
    }

    @Test
    void indisponivel_e_nao_suportado_viram_nenhum() {
        when(tramitacao.ler(1)).thenReturn(TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty()));
        when(tramitacao.ler(2)).thenReturn(TramitacaoDoRequerimento.naoSuportado(Optional.empty()));
        assertThat(caminhos.ler(1)).isEqualTo(CaminhoDoFluxo.NENHUM);
        assertThat(caminhos.ler(2)).isEqualTo(CaminhoDoFluxo.NENHUM);
    }

    @Test
    void plano_b_aproximado_nao_serve() {
        TramitacaoDoRequerimento planoB = new TramitacaoDoRequerimento(TramitacaoDoRequerimento.Estado.SEM_INSTANCIA,
                Optional.of("procOutorgaAguaSupSub"), Optional.empty(), null, Optional.empty(),
                Optional.of(mock(HistoricoAproximado.class)), List.of());
        when(tramitacao.ler(1)).thenReturn(planoB);
        assertThat(caminhos.ler(1)).isEqualTo(CaminhoDoFluxo.NENHUM);
    }

    @Test
    void falha_da_tramitacao_vira_nenhum() {
        when(tramitacao.ler(1)).thenThrow(new IllegalStateException("fora do ar"));
        assertThat(caminhos.ler(1)).isEqualTo(CaminhoDoFluxo.NENHUM);
    }
}
