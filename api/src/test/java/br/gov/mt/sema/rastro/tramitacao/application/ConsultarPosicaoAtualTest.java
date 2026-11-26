package br.gov.mt.sema.rastro.tramitacao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ElementoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoConfrontada;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDoConfrontoDeTeste;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeGruposDeTeste;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.QueryTimeoutException;

/**
 * Caso de uso da posição (S7, blocos 3 e 4): acesso antes de tudo, não suportado sem Camunda, indisponível sem 5xx,
 * responsável sem CPF, cache e parâmetros que valem na próxima leitura. CPF fictício, inválido de propósito.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConsultarPosicaoAtualTest {

    private static final long ID = 7001;
    private static final String CPF_FICTICIO = "00000000004";
    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 9, 9, 30);

    private final VerificarAcessoAoRequerimento acesso = mock(VerificarAcessoAoRequerimento.class);
    private final PosicaoNoFluxo camunda = mock(PosicaoNoFluxo.class);
    private final IdentificacaoDosResponsaveis siga = mock(IdentificacaoDosResponsaveis.class);
    private final CatalogoDeEtapas catalogo = CatalogoDeEtapasDeTeste.padrao();
    private final ParametrosDoRastroDeTeste parametros = new ParametrosDoRastroDeTeste();
    private final CacheEmMapa cache = new CacheEmMapa();
    private final ConsultarPosicaoAtual caso = new ConsultarPosicaoAtual(acesso, camunda, catalogo,
            new ResolverResponsavel(siga, CatalogoDeGruposDeTeste.padrao()), parametros, cache,
            CatalogoDoConfrontoDeTeste.padrao(), new LimiteDeLeiturasDoCamunda(parametros));

    /** Cache sem expiração: o teste controla só a validade (0 desliga). */
    private static final class CacheEmMapa implements CacheDaPosicao {
        final Map<String, PosicaoConfrontada> mapa = new HashMap<>();

        @Override
        public Optional<PosicaoConfrontada> obter(long id, String versao) {
            return Optional.ofNullable(mapa.get(id + "@" + versao));
        }

        @Override
        public void guardar(long id, String versao, PosicaoConfrontada posicao, Duration validade) {
            mapa.put(id + "@" + versao, posicao);
        }
    }

    private void revisaoComServidor() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE")));
        LeituraDoFluxo.Tarefa tarefa = new LeituraDoFluxo.Tarefa("pi", "taskRevisarParecerTecnico", T0,
                IdentificadorDoResponsavel.de(CPF_FICTICIO), List.of());
        when(camunda.ler(ID, HIDRICO)).thenReturn(new LeituraDoFluxo(
                List.of(new LeituraDoFluxo.InstanciaRaiz("pi", T0.minusDays(9), null, null, "ACTIVE", false)),
                List.of(), List.of(tarefa), List.of(), List.of(), List.of()));
        when(siga.identificar(anyLong(), any())).thenReturn(Map.of(IdentificadorDoResponsavel.de(CPF_FICTICIO).orElseThrow(),
                new IdentificacaoDoResponsavel(Optional.of("Servidor Exemplo 04"), false)));
    }

    private static ResponsavelDaEtapa responsavel(PosicaoAtual p) {
        return p.principal().flatMap(ElementoDaPosicao::responsavel).orElseThrow();
    }

    @Test
    void sem_acesso_nao_le_o_camunda() {
        doThrow(new RecursoNaoEncontradoException("Requerimento não encontrado.")).when(acesso).executar(ID);
        assertThatThrownBy(() -> caso.executar(ID).posicao()).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(camunda, never()).requerimento(ID);
    }

    @Test
    void tipo_fora_do_catalogo_e_nao_suportado_sem_ler_o_camunda() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("LICENCIAMENTO_TRIFASICO_FICTICIO", "EM_ANALISE")));
        PosicaoAtual p = caso.executar(ID).posicao();
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.NAO_SUPORTADO);
        assertThat(p.chaveDoModelo()).isEmpty();
        verify(camunda, never()).ler(anyLong(), anyString());
    }

    @Test
    void tipo_nulo_tambem_e_nao_suportado() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(new PosicaoNoFluxo.RequerimentoNoFluxo(null, null)));
        assertThat(caso.executar(ID).posicao().estado()).isEqualTo(EstadoDaPosicao.NAO_SUPORTADO);
    }

    @Test
    void falha_ou_tempo_limite_no_camunda_vira_indisponivel_com_o_modelo() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE")));
        when(camunda.ler(ID, HIDRICO)).thenThrow(new QueryTimeoutException("tempo limite"));
        PosicaoAtual p = caso.executar(ID).posicao();
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(p.rotuloDoModelo()).contains("Outorga de recursos hídricos");
    }

    @Test
    void falha_ao_ler_o_tipo_vira_indisponivel_sem_modelo() {
        when(camunda.requerimento(ID)).thenThrow(new IllegalStateException("fora do ar"));
        PosicaoAtual p = caso.executar(ID).posicao();
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(p.chaveDoModelo()).isEmpty();
    }

    @Test
    void sem_instancia_no_modelo_do_tipo() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("LICENCIAMENTO_ADESAO", "EM_ANALISE")));
        when(camunda.ler(ID, "procLicenciamentoLac")).thenReturn(LeituraDoFluxo.semInstancia());
        assertThat(caso.executar(ID).posicao().estado()).isEqualTo(EstadoDaPosicao.SEM_INSTANCIA);
    }

    @Test
    void responsavel_resolvido_e_sem_cpf_na_posicao() {
        revisaoComServidor();
        PosicaoAtual p = caso.executar(ID).posicao();
        assertThat(responsavel(p).tipo()).isEqualTo(ResponsavelDaEtapa.Tipo.SERVIDOR);
        assertThat(responsavel(p).nome()).contains("Servidor Exemplo 04");
        assertThat(responsavel(p).papel()).isEqualTo("Chefia");
        assertThat(p.principal().orElseThrow().tarefas()).allSatisfy(t -> assertThat(t.responsavel()).isEmpty());
        assertThat(p.toString()).doesNotContain(CPF_FICTICIO);
    }

    @Test
    void parametro_de_nome_vale_na_proxima_leitura_mesmo_com_cache() {
        revisaoComServidor();
        parametros.cacheSegundos = 30;
        assertThat(responsavel(caso.executar(ID).posicao()).nome()).contains("Servidor Exemplo 04");

        parametros.mostrarNome = false;
        ResponsavelDaEtapa semNome = responsavel(caso.executar(ID).posicao());
        assertThat(semNome.nome()).isEmpty();
        assertThat(semNome.texto()).isEqualTo("Chefia");
        verify(camunda, times(1)).ler(ID, HIDRICO); // a segunda veio do cache

        parametros.mostrarNome = true;
        assertThat(responsavel(caso.executar(ID).posicao()).nome()).contains("Servidor Exemplo 04");
    }

    @Test
    void cache_desligado_le_sempre_e_indisponivel_nao_entra_no_cache() {
        revisaoComServidor();
        caso.executar(ID).posicao();
        caso.executar(ID).posicao();
        verify(camunda, times(2)).ler(ID, HIDRICO);
        assertThat(cache.mapa).isEmpty();

        parametros.cacheSegundos = 30;
        when(camunda.ler(ID, HIDRICO)).thenThrow(new QueryTimeoutException("tempo limite"));
        assertThat(caso.executar(ID).posicao().estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(cache.mapa).isEmpty();
    }

    @Test
    void chave_do_cache_e_so_id_e_versao_do_catalogo_e_o_valor_nao_tem_cpf() {
        revisaoComServidor();
        parametros.cacheSegundos = 30;
        caso.executar(ID).posicao();
        assertThat(cache.mapa).containsOnlyKeys(ID + "@" + catalogo.versao());
        assertThat(cache.mapa.values().toString()).doesNotContain(CPF_FICTICIO);
    }

    @Test
    void falha_ao_identificar_responsavel_nao_derruba_e_o_log_nao_tem_cpf(CapturedOutput saida) {
        revisaoComServidor();
        // Exceção cuja mensagem traz o CPF, como um erro de driver com os valores dos parâmetros.
        when(siga.identificar(anyLong(), any())).thenThrow(new IllegalStateException("falha com " + CPF_FICTICIO));
        PosicaoAtual p = caso.executar(ID).posicao();
        assertThat(p.estado()).isEqualTo(EstadoDaPosicao.ATIVO);
        assertThat(responsavel(p).tipo()).isEqualTo(ResponsavelDaEtapa.Tipo.NAO_IDENTIFICADO);
        assertThat(saida.getAll()).contains("identificados").doesNotContain(CPF_FICTICIO);
    }

    @Test
    void indisponivel_leva_a_situacao_do_licenciamento_quando_ja_lida() {
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE")));
        when(camunda.ler(ID, HIDRICO)).thenThrow(new QueryTimeoutException("tempo limite"));
        PosicaoConfrontada p = caso.executar(ID);
        assertThat(p.posicao().estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(p.situacaoDoLicenciamento()).contains("EM_ANALISE");
        assertThat(p.avisos()).isEmpty();
    }

    @Test
    void situacao_incompativel_vira_aviso_sem_mudar_a_posicao() {
        revisaoComServidor();
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_CADASTRO")));
        PosicaoConfrontada p = caso.executar(ID);
        assertThat(p.posicao().principal().flatMap(ElementoDaPosicao::etapa).map(e -> e.id()))
                .contains("revisao_do_parecer_tecnico");
        assertThat(p.avisos()).singleElement().satisfies(a -> {
            assertThat(a.codigo()).isEqualTo(br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso.SITUACAO_NAO_CONFERE);
            assertThat(a.texto()).contains("'EM_CADASTRO'");
        });
    }

    @Test
    void falha_no_camunda_com_cpf_na_mensagem_nao_vai_para_o_log(CapturedOutput saida) {
        when(camunda.requerimento(ID)).thenThrow(new IllegalStateException("ORA-00000 bind " + CPF_FICTICIO));
        assertThat(caso.executar(ID).posicao().estado()).isEqualTo(EstadoDaPosicao.INDISPONIVEL);
        assertThat(saida.getAll()).contains("indispon").doesNotContain(CPF_FICTICIO);
    }
}
