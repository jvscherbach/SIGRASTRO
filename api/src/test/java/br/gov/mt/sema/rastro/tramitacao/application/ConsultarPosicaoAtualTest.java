package br.gov.mt.sema.rastro.tramitacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

/** Caso de uso da posição (S7, bloco 3): acesso antes de tudo, não suportado sem Camunda e indisponível sem 5xx. */
class ConsultarPosicaoAtualTest {

    private static final long ID = 7001;
    private final VerificarAcessoAoRequerimento acesso = mock(VerificarAcessoAoRequerimento.class);
    private final PosicaoNoFluxo camunda = mock(PosicaoNoFluxo.class);
    private final CatalogoDeEtapas catalogo = CatalogoDeEtapasDeTeste.padrao();
    private final ConsultarPosicaoAtual caso = new ConsultarPosicaoAtual(acesso, camunda, catalogo);

    @Test
    void sem_acesso_nao_le_o_camunda() {
        doThrow(new RecursoNaoEncontradoException("Requerimento não encontrado.")).when(acesso).executar(ID);
        assertThatThrownBy(() -> caso.executar(ID)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(camunda, never()).requerimento(ID);
    }

    @Test
    void tipo_fora_do_catalogo_e_nao_suportado_sem_ler_o_camunda() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("LICENCIAMENTO_TRIFASICO_FICTICIO", "EM_ANALISE")));
        PosicaoAtual p = caso.executar(ID);
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.NAO_SUPORTADO);
        assertThat(p.chaveDoModelo()).isEmpty();
        verify(camunda, never()).ler(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void tipo_nulo_tambem_e_nao_suportado() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(new PosicaoNoFluxo.RequerimentoNoFluxo(null, null)));
        assertThat(caso.executar(ID).estado()).isEqualTo(EstadoDaPosicao.NAO_SUPORTADO);
    }

    @Test
    void falha_ou_tempo_limite_no_camunda_vira_indisponivel_com_o_modelo() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE")));
        when(camunda.ler(ID, "procOutorgaAguaSupSub")).thenThrow(new QueryTimeoutException("tempo limite"));
        PosicaoAtual p = caso.executar(ID);
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(p.rotuloDoModelo()).contains("Outorga de recursos hídricos");
    }

    @Test
    void falha_ao_ler_o_tipo_vira_indisponivel_sem_modelo() {
        when(camunda.requerimento(ID)).thenThrow(new IllegalStateException("fora do ar"));
        PosicaoAtual p = caso.executar(ID);
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(p.chaveDoModelo()).isEmpty();
    }

    @Test
    void sem_instancia_no_modelo_do_tipo() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("LICENCIAMENTO_ADESAO", "EM_ANALISE")));
        when(camunda.ler(ID, "procLicenciamentoLac")).thenReturn(LeituraDoFluxo.semInstancia());
        assertThat(caso.executar(ID).estado()).isEqualTo(EstadoDaPosicao.SEM_INSTANCIA);
    }
}
