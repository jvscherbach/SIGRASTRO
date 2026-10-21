package br.gov.mt.sema.vistas.consulta.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.vistas.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.vistas.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.vistas.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.CriterioBusca;
import br.gov.mt.sema.vistas.consulta.domain.Interessado;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.AtividadeLicenciamento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.FinalidadeAtividade;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.TipoObjetivo;
import br.gov.mt.sema.vistas.consulta.domain.Pagina;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.vistas.consulta.domain.ResponsavelTarefa;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.Situacao;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.shared.application.exception.SigaIndisponivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP da consulta. A propriedade do SIGA é forçada vazia para não depender da máquina. */
@SpringBootTest(properties = {"vistas.demo.senha=senha-de-teste", "vistas.datasource.siga.url="})
@AutoConfigureMockMvc
class ConsultaHttpTest {

    private static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";

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

    @BeforeEach
    void limpar() {
        reset(siga, tarefas, objetoEPrioridade);
        when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.semTarefa());
        when(objetoEPrioridade.objeto(anyLong())).thenReturn(ObjetoDoRequerimento.vazio());
        when(objetoEPrioridade.prioridade(anyLong())).thenReturn(PrioridadeAtendimento.de(List.of()));
    }

    private static Requerimento requerimento(long id, String cpfCnpj, String processo) {
        return new Requerimento(id, "3331/2025", processo, "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2025, 3, 31, 10, 12), LocalDateTime.of(2025, 4, 29, 9, 14),
                new Interessado("Associação Residencial Vale Verde", cpfCnpj), "Uso de Recursos Hídricos");
    }

    private String token(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", "senha-de-teste"));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }

    private ResultActions buscar(String usuario, String query) throws Exception {
        return mvc.perform(get("/api/busca?" + query).header("Authorization", "Bearer " + token(usuario)));
    }

    // --- busca

    @Test
    void buscaPorNumeroDevolvePaginaComIdentificacaoDeProcesso() throws Exception {
        when(siga.buscarPorNumeroDeRequerimento(any())).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, "2825/2025")));

        buscar("operador.demo", "tipo=REQUERIMENTO&numero=3331/2025")
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].id").value(101))
                .andExpect(jsonPath("$.conteudo[0].identificacao.rotulo").value("Processo"))
                .andExpect(jsonPath("$.conteudo[0].identificacao.numero").value("2825/2025"))
                .andExpect(jsonPath("$.conteudo[0].identificacao.protocolado").value(true))
                .andExpect(jsonPath("$.conteudo[0].situacao.codigo").value("EM_ANALISE"))
                .andExpect(jsonPath("$.conteudo[0].situacao.descricao").value("Em análise"))
                .andExpect(jsonPath("$.conteudo[0].abertura").value("2025-03-31T10:12:00"))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void buscaPorListaDevolvePaginaComTotalEPassaARestricaoDoOperador() throws Exception {
        Requerimento r = requerimento(101, CNPJ_DO_OPERADOR, null);
        when(siga.buscarPorCriterios(any())).thenReturn(new Pagina<>(List.of(r.resumo()), 41, 1, 20));

        buscar("operador.demo", "nome=vale&pagina=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(1)))
                .andExpect(jsonPath("$.conteudo[0].identificacao.rotulo").value("Requerimento"))
                .andExpect(jsonPath("$.conteudo[0].identificacao.protocolado").value(false))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(41))
                .andExpect(jsonPath("$.totalPaginas").value(3));

        ArgumentCaptor<CriterioBusca> captor = ArgumentCaptor.forClass(CriterioBusca.class);
        verify(siga).buscarPorCriterios(captor.capture());
        assertThat(captor.getValue().restricaoDigitos()).isEqualTo("12345678000190");
    }

    @Test
    void gestorNaoTemRestricaoNaLista() throws Exception {
        when(siga.buscarPorCriterios(any())).thenReturn(Pagina.vazia(0, 20));

        buscar("gestor.demo", "nome=vale").andExpect(status().isOk()).andExpect(jsonPath("$.conteudo", hasSize(0)));

        ArgumentCaptor<CriterioBusca> captor = ArgumentCaptor.forClass(CriterioBusca.class);
        verify(siga).buscarPorCriterios(captor.capture());
        assertThat(captor.getValue().restricaoDigitos()).isNull();
    }

    @Test
    void semCriterioEh400ComProblemDetail() throws Exception {
        buscar("operador.demo", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].campo").value("criterio"))
                .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())))
                .andExpect(header().exists("X-Correlation-Id"));
        verifyNoInteractions(siga);
    }

    @Test
    void numeroForaDoFormatoEh400IndicandoOCampo() throws Exception {
        buscar("operador.demo", "tipo=PROCESSO&numero=2825")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].campo").value("numero"));
    }

    @Test
    void numeroSemTipoEh400() throws Exception {
        buscar("operador.demo", "numero=2825/2025")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[0].campo").value("tipo"));
    }

    @Test
    void paginaComTipoErradoEh400() throws Exception {
        buscar("gestor.demo", "nome=vale&pagina=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void numeroInexistenteEh404() throws Exception {
        when(siga.buscarPorCriterios(any())).thenReturn(Pagina.vazia(0, 20));

        buscar("gestor.demo", "tipo=PROCESSO&numero=9/2020")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("Processo 9/2020 não encontrado."));
    }

    @Test
    void operadorBuscandoNumeroDeOutroInteressadoEh403() throws Exception {
        when(siga.buscarPorNumeroDeRequerimento(any())).thenReturn(Optional.of(requerimento(102, "45.678.901/0001-23", null)));

        buscar("operador.demo", "tipo=REQUERIMENTO&numero=1187/2026")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"))
                .andExpect(jsonPath("$.detail").value("Você não é o interessado deste processo ou requerimento."));
    }

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/api/busca?nome=vale"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
    }

    // --- detalhe

    private void comDetalheCompleto() {
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, "2825/2025")));
        when(tarefas.obter(101)).thenReturn(TarefaAtual.ativa("Analisar requerimento",
                LocalDateTime.of(2025, 4, 29, 9, 14), ResponsavelTarefa.atribuido("Maria Nova de Souza"),
                "Outorga de Recursos Hídricos"));
        when(objetoEPrioridade.objeto(101)).thenReturn(new ObjetoDoRequerimento(
                List.of("Consumo humano", "Irrigação"),
                List.of(new FinalidadeAtividade("Abastecimento público", "Captação para abastecimento"),
                        new FinalidadeAtividade("Agricultura", null)),
                List.of(TipoObjetivo.de("SUBTERRANEA", "Consumo humano"), TipoObjetivo.de("SUPERFICIAL", "Irrigação")),
                new AtividadeLicenciamento("Mineração", "Extração de areia")));
        when(objetoEPrioridade.prioridade(101)).thenReturn(PrioridadeAtendimento.de(List.of(2, 5)));
    }

    @Test
    void detalheDevolveRequerimentoObjetoPrioridadeETarefaAtual() throws Exception {
        comDetalheCompleto();

        mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token("gestor.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.identificacao.numero").value("2825/2025"))
                .andExpect(jsonPath("$.numeroRequerimento").value("3331/2025"))
                .andExpect(jsonPath("$.tipoRequerimento").value("Requerimento de Outorga"))
                .andExpect(jsonPath("$.interessado.cpfCnpj").value(CNPJ_DO_OPERADOR))
                .andExpect(jsonPath("$.tipoProcesso").value("Uso de Recursos Hídricos"))
                .andExpect(jsonPath("$.atualizacao").value("2025-04-29T09:14:00"))
                .andExpect(jsonPath("$.objeto.objetivos", hasSize(2)))
                .andExpect(jsonPath("$.objeto.finalidadesAtividades[0].finalidade").value("Abastecimento público"))
                .andExpect(jsonPath("$.objeto.finalidadesAtividades[0].atividade").value("Captação para abastecimento"))
                .andExpect(jsonPath("$.objeto.finalidadesAtividades[1].atividade").doesNotExist())
                .andExpect(jsonPath("$.objeto.tiposObjetivo[0].tipo").value("SUBTERRANEA"))
                .andExpect(jsonPath("$.objeto.tiposObjetivo[0].tipoDescricao").value("Subterrânea"))
                .andExpect(jsonPath("$.objeto.tiposObjetivo[0].objetivo").value("Consumo humano"))
                .andExpect(jsonPath("$.objeto.atividadeLicenciamento.grupo").value("Mineração"))
                .andExpect(jsonPath("$.prioridade.codigo").value("PRIORITARIO"))
                .andExpect(jsonPath("$.prioridade.descricao").value("Atendimento prioritário"))
                .andExpect(jsonPath("$.prioridade.criterios", hasSize(2)))
                .andExpect(jsonPath("$.prioridade.criterios[1].codigo").value(5))
                .andExpect(jsonPath("$.prioridade.criterios[1].descricao").value("Pessoa com doença grave"))
                .andExpect(jsonPath("$.tarefaAtual.estado").value("ATIVA"))
                .andExpect(jsonPath("$.tarefaAtual.nome").value("Analisar requerimento"))
                .andExpect(jsonPath("$.tarefaAtual.responsavel.situacao").value("ATRIBUIDO"))
                .andExpect(jsonPath("$.tarefaAtual.responsavel.nome").value("Maria Nova de Souza"))
                .andExpect(jsonPath("$.tarefaAtual.fluxo").value("Outorga de Recursos Hídricos"));
    }

    @Test
    void responsavelApareceParaGestorEAdminESomeDoJsonDoOperador() throws Exception {
        comDetalheCompleto();

        for (String usuario : new String[] {"gestor.demo", "admin.demo"}) {
            mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token(usuario)))
                    .andExpect(jsonPath("$.tarefaAtual.responsavel.nome").value("Maria Nova de Souza"));
        }
        String corpoOperador = mvc.perform(get("/api/requerimentos/101")
                        .header("Authorization", "Bearer " + token("operador.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefaAtual.nome").value("Analisar requerimento"))
                .andExpect(jsonPath("$.tarefaAtual.responsavel").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(corpoOperador).doesNotContain("responsavel", "Maria Nova");
    }

    @Test
    void prioridadeSemCriteriosEhAtendimentoNormal() throws Exception {
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, null)));

        mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token("operador.demo")))
                .andExpect(jsonPath("$.prioridade.codigo").value("NORMAL"))
                .andExpect(jsonPath("$.prioridade.descricao").value("Atendimento normal"))
                .andExpect(jsonPath("$.prioridade.criterios", hasSize(0)))
                .andExpect(jsonPath("$.objeto.objetivos", hasSize(0)))
                .andExpect(jsonPath("$.objeto.atividadeLicenciamento").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void falhaAoLerObjetoOuPrioridadeDerrubaODetalheCom503() throws Exception {
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, null)));
        when(objetoEPrioridade.prioridade(101)).thenThrow(new SigaIndisponivelException());

        mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token("admin.demo")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SIGA_INDISPONIVEL"));
    }

    @Test
    void buscaPorProcessoRepetidoDevolveListaComTodos() throws Exception {
        Requerimento a = requerimento(401, "55555555555", "445/2022");
        Requerimento b = requerimento(402, "66666666666", "445/2022");
        when(siga.buscarPorCriterios(any())).thenReturn(new Pagina<>(List.of(b.resumo(), a.resumo()), 2, 0, 20));

        buscar("gestor.demo", "tipo=PROCESSO&numero=445/2022")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.conteudo[0].id").value(402))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void detalheNaoExpoeChaveDeArquivoNemNomesInternosDoSiga() throws Exception {
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, null)));

        String corpo = mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token("operador.demo")))
                .andReturn().getResponse().getContentAsString().toLowerCase();

        assertThat(corpo).doesNotContain("chave", "camunda", "siga2_");
    }

    @Test
    void detalheDeOutroInteressadoEh403ParaOperadorEOkParaGestor() throws Exception {
        when(siga.buscarPorId(102)).thenReturn(Optional.of(requerimento(102, "45.678.901/0001-23", null)));

        mvc.perform(get("/api/requerimentos/102").header("Authorization", "Bearer " + token("operador.demo")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
        mvc.perform(get("/api/requerimentos/102").header("Authorization", "Bearer " + token("gestor.demo")))
                .andExpect(status().isOk());
    }

    @Test
    void detalheInexistenteEh404() throws Exception {
        when(siga.buscarPorId(7)).thenReturn(Optional.empty());

        mvc.perform(get("/api/requerimentos/7").header("Authorization", "Bearer " + token("admin.demo")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
    }

    @Test
    void idQueNaoEhNumeroEh400() throws Exception {
        mvc.perform(get("/api/requerimentos/abc").header("Authorization", "Bearer " + token("admin.demo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void falhaNaTarefaNaoDerrubaODetalhe() throws Exception {
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR, null)));
        when(tarefas.obter(101)).thenReturn(TarefaAtual.indisponivel());

        mvc.perform(get("/api/requerimentos/101").header("Authorization", "Bearer " + token("operador.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tarefaAtual.estado").value("INDISPONIVEL"));
    }

    @Test
    void detalheSemTokenEh401() throws Exception {
        mvc.perform(get("/api/requerimentos/101")).andExpect(status().isUnauthorized());
    }
}
