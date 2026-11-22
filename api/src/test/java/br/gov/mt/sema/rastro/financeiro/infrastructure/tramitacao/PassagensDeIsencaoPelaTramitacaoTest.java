package br.gov.mt.sema.rastro.financeiro.infrastructure.tramitacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * Elo do fluxo da data da isenção (S9, bloco 4): só as passagens "Isenção confirmada" do caminho exato; plano B,
 * indisponível, não suportado e falha viram lista vazia (o financeiro não depende do Camunda).
 */
class PassagensDeIsencaoPelaTramitacaoTest {

    private static final LocalDateTime ENTRADA = LocalDateTime.of(2026, 3, 4, 10, 0);

    private final LerTramitacao tramitacao = mock(LerTramitacao.class);
    private final PassagensDeIsencaoPelaTramitacao passagens = new PassagensDeIsencaoPelaTramitacao(tramitacao);

    private static Etapa etapa(String id, String rotulo, int ordem) {
        return new Etapa(id, rotulo, ordem, TipoDeEtapa.AUTOMATICA, PapelDoResponsavel.SISTEMA, Ramo.PRINCIPAL, false,
                List.of("idFicticio" + ordem), List.of(), List.of());
    }

    private static PassagemPelaEtapa passagem(Etapa etapa, LocalDateTime entrada) {
        return new PassagemPelaEtapa(Optional.of(etapa), "idFicticio", 1, Ramo.PRINCIPAL, entrada,
                Optional.of(entrada.plusMinutes(5)), List.of(), false, List.of(), Optional.empty(), false, 1, false);
    }

    private static TramitacaoDoRequerimento com(TramitacaoDoRequerimento.Estado estado, PassagemPelaEtapa... ps) {
        CaminhoReconstruido caminho = new CaminhoReconstruido(List.of(ps), List.of(),
                CaminhoReconstruido.ClassificacaoDaTaxa.ISENTA, Optional.empty(), Set.of(), 0, Set.of(), false, 1);
        return new TramitacaoDoRequerimento(estado, Optional.of("procOutorgaAguaSupSub"), Optional.empty(), caminho,
                Optional.empty(), Optional.empty(), List.of());
    }

    @Test
    void so_as_passagens_de_isencao_confirmada_do_caminho_exato() {
        when(tramitacao.ler(1)).thenReturn(com(TramitacaoDoRequerimento.Estado.ATIVO,
                passagem(etapa("conferencia_dos_documentos", "Conferência dos documentos", 2), ENTRADA.minusDays(1)),
                passagem(etapa("isencao_confirmada", "Isenção confirmada", 5), ENTRADA),
                passagem(etapa("atribuicao_ao_analista", "Atribuição ao analista", 6), ENTRADA.plusHours(1))));
        assertThat(passagens.entradas(1)).containsExactly(ENTRADA);
    }

    @Test
    void encerrado_tambem_serve() {
        when(tramitacao.ler(1)).thenReturn(com(TramitacaoDoRequerimento.Estado.ENCERRADO,
                passagem(etapa("isencao_confirmada", "Isenção confirmada", 5), ENTRADA)));
        assertThat(passagens.entradas(1)).containsExactly(ENTRADA);
    }

    @Test
    void indisponivel_e_nao_suportado_viram_lista_vazia() {
        when(tramitacao.ler(1)).thenReturn(TramitacaoDoRequerimento.indisponivel(Optional.empty(), Optional.empty()));
        when(tramitacao.ler(2)).thenReturn(TramitacaoDoRequerimento.naoSuportado(Optional.empty()));
        assertThat(passagens.entradas(1)).isEmpty();
        assertThat(passagens.entradas(2)).isEmpty();
    }

    @Test
    void plano_b_aproximado_nao_serve_de_data() {
        TramitacaoDoRequerimento planoB = new TramitacaoDoRequerimento(TramitacaoDoRequerimento.Estado.SEM_INSTANCIA,
                Optional.of("procOutorgaAguaSupSub"), Optional.empty(), null, Optional.empty(),
                Optional.of(mock(HistoricoAproximado.class)), List.of());
        when(tramitacao.ler(1)).thenReturn(planoB);
        assertThat(passagens.entradas(1)).isEmpty();
    }

    @Test
    void falha_da_tramitacao_vira_lista_vazia() {
        when(tramitacao.ler(1)).thenThrow(new IllegalStateException("fora do ar"));
        assertThat(passagens.entradas(1)).isEmpty();
    }
}
