package br.gov.mt.sema.rastro.tramitacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Resiliência da posição (S7, bloco 7), com o caso de uso real e só a leitura do Camunda trocada: fora do ar ou com o
 * tempo limite estourado, a posição responde 200 com {@code INDISPONIVEL} (nunca 5xx) e o detalhe do requerimento segue
 * funcionando. O limite de tempo de cada consulta real é provado em {@code CamundaPosicaoJdbcTest}.
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=",
        "rastro.tramitacao.posicao-cache-segundos=0"})
@AutoConfigureMockMvc
class ResilienciaDaTramitacaoHttpTest {

    private static final long ID = 7101;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    RequerimentosDoSiga siga;

    @MockitoBean
    TarefaAtualDoRequerimento tarefas;

    @MockitoBean
    ObjetoEPrioridadeDoRequerimento objetoEPrioridade;

    @MockitoBean
    DocumentosDoRequerimento documentos;

    @MockitoBean
    PosicaoNoFluxo camunda;

    @BeforeEach
    void preparar() {
        Requerimento r = new Requerimento(ID, "7101/2026", "7900/2022", "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2026, 3, 1, 9, 0), null,
                new Interessado("Associação Residencial Vale Verde", "12.345.678/0001-90"),
                "Outorga de recursos hídricos (FICTÍCIO)");
        when(siga.buscarPorId(ID)).thenReturn(Optional.of(r));
        when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.indisponivel());
        when(objetoEPrioridade.objeto(anyLong())).thenReturn(ObjetoDoRequerimento.vazio());
        when(objetoEPrioridade.prioridade(anyLong())).thenReturn(PrioridadeAtendimento.de(List.of()));
        when(camunda.requerimento(ID)).thenReturn(Optional.of(
                new PosicaoNoFluxo.RequerimentoNoFluxo("RECURSO_HIDRICO", "EM_ANALISE")));
    }

    private String token() throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", "gestor.demo", "senha", "senha-de-teste"));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }

    private void posicaoIndisponivelEDetalheNormal() throws Exception {
        String token = token();
        mvc.perform(get("/api/requerimentos/" + ID + "/posicao").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.modelo").value("Outorga de recursos hídricos"))
                .andExpect(jsonPath("$.situacaoLicenciamento.codigo").value("EM_ANALISE"));
        mvc.perform(get("/api/requerimentos/" + ID).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroRequerimento").value("7101/2026"));
    }

    @Test
    void camunda_fora_do_ar_nao_derruba_nada() throws Exception {
        when(camunda.ler(anyLong(), anyString())).thenThrow(new DataAccessResourceFailureException("conexão recusada"));
        posicaoIndisponivelEDetalheNormal();
    }

    @Test
    void tempo_limite_estourado_vira_indisponivel_sem_5xx() throws Exception {
        when(camunda.ler(anyLong(), anyString())).thenAnswer(c -> {
            Thread.sleep(200); // a consulta esperou o limite e o driver cancelou
            throw new QueryTimeoutException("ORA-01013: tempo limite da consulta");
        });
        long inicio = System.nanoTime();
        posicaoIndisponivelEDetalheNormal();
        // A thread volta assim que a consulta é cancelada: nada além do limite.
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void falha_ao_ler_o_tipo_tambem_vira_indisponivel() throws Exception {
        when(camunda.requerimento(ID)).thenThrow(new QueryTimeoutException("tempo limite"));
        String token = token();
        mvc.perform(get("/api/requerimentos/" + ID + "/posicao").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INDISPONIVEL"));
    }
}
