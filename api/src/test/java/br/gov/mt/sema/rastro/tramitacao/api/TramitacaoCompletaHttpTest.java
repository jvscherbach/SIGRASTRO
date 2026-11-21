package br.gov.mt.sema.rastro.tramitacao.api;

import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_01;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_02;
import static br.gov.mt.sema.rastro.tramitacao.application.TramitacoesDeTeste.IDS_TECNICOS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.consulta.application.ObjetoEPrioridadeDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.TarefaAtualDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TipoBusca;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.MontarTramitacao;
import br.gov.mt.sema.rastro.tramitacao.application.TramitacoesDeTeste;
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

/**
 * Contrato HTTP da tramitação completa (S8, bloco 6), por perfil e por cenário. A tramitação vem pronta do dublê de
 * {@link MontarTramitacao} (o caminho é provado nos testes dos blocos 2 a 5); aqui se prova o acesso antes de montar, o que
 * sai para cada perfil, o evento de auditoria "histórico" e a ausência de CPF e de ids técnicos para quem não é
 * Administrador.
 */
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.datasource.siga.url=",
        "rastro.tramitacao.tramitacao-cache-segundos=0"})
@AutoConfigureMockMvc
class TramitacaoCompletaHttpTest {

    private static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";
    private static final String CPF_DE_OUTRO = "000.000.000-91";
    private static final String PROCESSO = "7900/2022";
    private static final String PROCESSO_SO_DE_OUTRO = "7901/2022";

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
    MontarTramitacao montar;

    @MockitoBean
    RegistrarEvento registrar;

    private final Requerimento doOperador = requerimento(7101, "7101/2026", PROCESSO, CNPJ_DO_OPERADOR);
    private final Requerimento deOutro = requerimento(7102, "7102/2026", PROCESSO, CPF_DE_OUTRO);
    private final Requerimento soDeOutro = requerimento(7103, "7103/2026", PROCESSO_SO_DE_OUTRO, CPF_DE_OUTRO);

    @BeforeEach
    void preparar() {
        reset(siga, montar, registrar);
        for (Requerimento r : List.of(doOperador, deOutro, soDeOutro)) {
            when(siga.buscarPorId(r.id())).thenReturn(Optional.of(r));
        }
        when(siga.buscarPorId(404L)).thenReturn(Optional.empty());
        when(siga.buscarPorCriterios(any())).thenAnswer(c -> buscarProcesso(c.getArgument(0)));
        when(montar.montar(7101)).thenReturn(TramitacoesDeTeste.ativaComVoltas());
        when(montar.montar(7102)).thenReturn(TramitacoesDeTeste.encerradaPorTaxa());
        when(montar.montar(7103)).thenReturn(TramitacoesDeTeste.concluidaNaVerificacaoTruncada());
    }

    private static Requerimento requerimento(long id, String numero, String processo, String cpfCnpj) {
        return new Requerimento(id, numero, processo, "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2026, 3, 1, 9, 0), null, new Interessado("Interessado Fictício S7", cpfCnpj),
                "Outorga de recursos hídricos (FICTÍCIO)");
    }

    private Pagina<ResumoRequerimento> buscarProcesso(CriterioBusca criterio) {
        List<ResumoRequerimento> visiveis = new ArrayList<>();
        if (criterio.tipo() == TipoBusca.PROCESSO && criterio.numero() != null) {
            for (Requerimento r : List.of(doOperador, deOutro, soDeOutro)) {
                boolean doProcesso = criterio.numero().texto().equals(r.numeroProcesso());
                boolean permitido = criterio.restricaoDigitos() == null
                        || r.cpfCnpjDoInteressado().replaceAll("\\D", "").equals(criterio.restricaoDigitos());
                if (doProcesso && permitido) {
                    visiveis.add(r.resumo());
                }
            }
        }
        return new Pagina<>(visiveis, visiveis.size(), criterio.pagina(), criterio.tamanho());
    }

    private String token(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", "senha-de-teste"));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("token").asText();
    }

    private ResultActions obter(String usuario, String caminho) throws Exception {
        return mvc.perform(get(caminho).header("Authorization", "Bearer " + token(usuario)));
    }

    private static String corpo(ResultActions resposta) throws Exception {
        return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<NovoEvento> eventosDeTramitacao() {
        ArgumentCaptor<NovoEvento> evento = ArgumentCaptor.forClass(NovoEvento.class);
        verify(registrar, org.mockito.Mockito.atLeast(0)).registrar(evento.capture());
        return evento.getAllValues().stream().filter(x -> x.tipo() == TipoDeEvento.TRAMITACAO).toList();
    }

    private static void semIdsTecnicosNemCpf(String corpo) {
        for (String id : IDS_TECNICOS) {
            assertThat(corpo).as("id técnico").doesNotContain(id);
        }
        assertThat(corpo).doesNotContainPattern("(?<![0-9])\\d{11}(?![0-9])")
                .doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}")
                .doesNotContain("idTecnico").doesNotContain("idsNaoMapeados").doesNotContain("diagnosticos")
                .doesNotContain("repeticoes").doesNotContain("ROTA_DIVERGE_DO_STATUS");
    }

    // --- o caminho

    @Test
    void gestorVeOCaminhoComVoltasMotivoQuemEsteveParticipantesAtosECorrecao() throws Exception {
        String texto = corpo(obter("gestor.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idRequerimento").value(7101))
                .andExpect(jsonPath("$.numeroRequerimento").value("7101/2026"))
                .andExpect(jsonPath("$.numeroProcesso").value(PROCESSO))
                .andExpect(jsonPath("$.situacaoLicenciamento.codigo").value("EM_ANALISE"))
                .andExpect(jsonPath("$.modelo").value("Outorga de recursos hídricos"))
                .andExpect(jsonPath("$.estado").value("ATIVO"))
                .andExpect(jsonPath("$.aproximado").value(false))
                .andExpect(jsonPath("$.truncado").value(false))
                .andExpect(jsonPath("$.passagens", hasSize(7)))
                // 1ª e 2ª passagem pelo cadastro, com o motivo da volta
                .andExpect(jsonPath("$.passagens[0].etapa.id").value("cadastro_do_requerimento"))
                .andExpect(jsonPath("$.passagens[0].etapa.rotulo").value("Cadastro do requerimento"))
                .andExpect(jsonPath("$.passagens[0].ciclo").value(1))
                .andExpect(jsonPath("$.passagens[0].ramo").value("PRINCIPAL"))
                .andExpect(jsonPath("$.passagens[0].entrada").value("2026-03-02T08:00:00"))
                .andExpect(jsonPath("$.passagens[0].saida").value("2026-03-03T08:00:00"))
                .andExpect(jsonPath("$.passagens[0].duracaoSegundos").value(86400))
                .andExpect(jsonPath("$.passagens[0].motivoDaVolta").value(nullValue()))
                .andExpect(jsonPath("$.passagens[0].responsaveis[0].tipo").value("INTERESSADO"))
                .andExpect(jsonPath("$.passagens[2].ciclo").value(2))
                .andExpect(jsonPath("$.passagens[2].motivoDaVolta.codigo").value("REPROVADO_NA_CONFERENCIA"))
                .andExpect(jsonPath("$.passagens[2].motivoDaVolta.texto").value("devolvido na conferência dos documentos"))
                // quem esteve (nome para o Gestor)
                .andExpect(jsonPath("$.passagens[1].responsaveis[0].nome").value(SERVIDOR_01))
                .andExpect(jsonPath("$.passagens[1].responsaveis[0].estado").value("CONCLUIU"))
                // multi-instância: participantes, não responsaveis
                .andExpect(jsonPath("$.passagens[3].responsaveis", hasSize(0)))
                .andExpect(jsonPath("$.passagens[3].participantes", hasSize(3)))
                .andExpect(jsonPath("$.passagens[3].participantes[1].nome").value(SERVIDOR_02))
                .andExpect(jsonPath("$.passagens[3].participantes[2].estado").value("ENCERRADA_SEM_ACAO"))
                // emissão com atos
                .andExpect(jsonPath("$.passagens[4].atos", hasSize(2)))
                .andExpect(jsonPath("$.passagens[4].atos[0].tipo").value("PORTARIA"))
                .andExpect(jsonPath("$.passagens[4].atos[0].ocorreu").value(true))
                .andExpect(jsonPath("$.passagens[4].atos[1].rotulo").value("Autorização"))
                .andExpect(jsonPath("$.passagens[4].atos[1].ocorreu").value(false))
                // ramo da correção
                .andExpect(jsonPath("$.passagens[5].ramo").value("CORRECAO"))
                .andExpect(jsonPath("$.passagens[5].etapa.rotulo").value("Correção do requerimento"))
                // etapa atual: aberta
                .andExpect(jsonPath("$.passagens[6].atual").value(true))
                .andExpect(jsonPath("$.passagens[6].saida").value(nullValue()))
                .andExpect(jsonPath("$.passagens[6].duracaoSegundos").value(nullValue()))
                .andExpect(jsonPath("$.passagens[6].participantes[0].estado").value("PENDENTE"))
                .andExpect(jsonPath("$.passagens[6].participantes[0].fim").value(nullValue()))
                .andExpect(jsonPath("$.complementacoes", hasSize(1)))
                .andExpect(jsonPath("$.taxa.principal").value("TAXAVEL"))
                .andExpect(jsonPath("$.taxa.correcao").value("SEM_NOVA_TAXA"))
                .andExpect(jsonPath("$.final").value(nullValue()))
                .andExpect(jsonPath("$.planoB").value(nullValue())));
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void administradorRecebeOsIdsTecnicosEODiagnostico() throws Exception {
        obter("admin.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passagens[0].idTecnico").value("taskCadastrarRequerimento"))
                .andExpect(jsonPath("$.passagens[3].idTecnico").value("taskAnalisarRequerimento"))
                .andExpect(jsonPath("$.passagens[3].repeticoes").value(1))
                .andExpect(jsonPath("$.idsNaoMapeados[0]").value("taskFicticiaNaoMapeada"))
                .andExpect(jsonPath("$.diagnosticos[0]").value("ROTA_DIVERGE_DO_STATUS"));
        obter("admin.demo", "/api/requerimentos/7102/tramitacao")
                .andExpect(jsonPath("$.final.idTecnico").value("Event_0h1oige"));
    }

    @Test
    void operadorDoInteressadoVePapelMasNaoONomeDoServidorEmPassagensEParticipantes() throws Exception {
        String texto = corpo(obter("operador.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passagens[1].responsaveis[0].nome").value(nullValue()))
                .andExpect(jsonPath("$.passagens[1].responsaveis[0].papel").value("Conferidor"))
                .andExpect(jsonPath("$.passagens[3].participantes[0].nome").value(nullValue()))
                .andExpect(jsonPath("$.passagens[3].participantes[0].papel").value("Analista"))
                .andExpect(jsonPath("$.passagens[3].participantes[0].estado").value("CONCLUIU")));
        assertThat(texto).doesNotContain(SERVIDOR_01).doesNotContain(SERVIDOR_02);
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void finalComBolaPretaEOAvisoSemIdTecnicoParaOGestor() throws Exception {
        String texto = corpo(obter("gestor.demo", "/api/requerimentos/7102/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCERRADO"))
                .andExpect(jsonPath("$.final.rotulo").value("Encerrado por taxa não paga no prazo"))
                .andExpect(jsonPath("$.final.tipo").value("FINAL"))
                .andExpect(jsonPath("$.final.encerradoEm").value("2026-04-01T08:00:00"))
                .andExpect(jsonPath("$.final.idTecnico").doesNotExist())
                .andExpect(jsonPath("$.avisos[0].codigo").value("FLUXO_ENCERRADO_SEM_STATUS"))
                .andExpect(jsonPath("$.avisos[0].gravidade").value("ATENCAO")));
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void verificacaoDaRevisaoEConcluidoSemBolaPretaEHistoricoTruncadoTemAviso() throws Exception {
        obter("gestor.demo", "/api/requerimentos/7103/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.final.rotulo").value("Concluído"))
                .andExpect(jsonPath("$.final.tipo").value("CONCLUIDO_NA_VERIFICACAO"))
                .andExpect(jsonPath("$.passagens[1].processoConcluido").value(true))
                .andExpect(jsonPath("$.truncado").value(true))
                .andExpect(jsonPath("$.avisos[0].codigo").value("HISTORICO_TRUNCADO"));
    }

    // --- plano B e estados

    @Test
    void planoBVemMarcadoComoAproximadoSemResponsavelEComSelo() throws Exception {
        when(montar.montar(7101)).thenReturn(TramitacoesDeTeste.planoB());
        String texto = corpo(obter("operador.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEM_INSTANCIA"))
                .andExpect(jsonPath("$.aproximado").value(true))
                .andExpect(jsonPath("$.passagens", hasSize(0)))
                .andExpect(jsonPath("$.taxa").value(nullValue()))
                .andExpect(jsonPath("$.planoB.selo").value(
                        "Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo"))
                .andExpect(jsonPath("$.planoB.semDetalhamento").value(false))
                .andExpect(jsonPath("$.planoB.etapas[0].etapa.rotulo").value("Conferência dos documentos"))
                .andExpect(jsonPath("$.planoB.etapas[0].eventos").value(2))
                .andExpect(jsonPath("$.planoB.marcos[0].tipo").value("ABERTURA"))
                .andExpect(jsonPath("$.planoB.eventosIgnorados").value(1))
                .andExpect(jsonPath("$.avisos[0].codigo").value("HISTORICO_APROXIMADO")));
        assertThat(texto).doesNotContain("responsav").doesNotContain("participantes").doesNotContain("nome");
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void camundaIndisponivelENaoSuportadoSao200ComOEstadoSemPlanoB() throws Exception {
        when(montar.montar(7101)).thenReturn(TramitacoesDeTeste.indisponivel());
        obter("operador.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.aproximado").value(false))
                .andExpect(jsonPath("$.modelo").value("Outorga de recursos hídricos"))
                .andExpect(jsonPath("$.passagens", hasSize(0)))
                .andExpect(jsonPath("$.planoB").value(nullValue()));
        // A consulta aconteceu: registra mesmo indisponível.
        assertThat(eventosDeTramitacao()).hasSize(1);

        when(montar.montar(7101)).thenReturn(TramitacoesDeTeste.naoSuportado());
        obter("gestor.demo", "/api/requerimentos/7101/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NAO_SUPORTADO"))
                .andExpect(jsonPath("$.modelo").value(nullValue()))
                .andExpect(jsonPath("$.passagens", hasSize(0)));
    }

    // --- acesso

    @Test
    void operadorDeOutroInteressadoRecebe403SemMontarNemRegistrar() throws Exception {
        obter("operador.demo", "/api/requerimentos/7102/tramitacao")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
        verify(montar, never()).montar(anyLong());
        assertThat(eventosDeTramitacao()).isEmpty();
    }

    @Test
    void requerimentoInexistenteEh404SemMontarNemRegistrar() throws Exception {
        for (String usuario : List.of("operador.demo", "gestor.demo", "admin.demo")) {
            obter(usuario, "/api/requerimentos/404/tramitacao")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        }
        verify(montar, never()).montar(anyLong());
        assertThat(eventosDeTramitacao()).isEmpty();
    }

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/api/requerimentos/7101/tramitacao")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
        mvc.perform(get("/api/processos/7900-2022/tramitacao")).andExpect(status().isUnauthorized());
        verify(montar, never()).montar(anyLong());
    }

    // --- auditoria

    @Test
    void registraTramitacaoComDetalheHistoricoSemResponsavelNemCpfNemIdTecnico() throws Exception {
        obter("gestor.demo", "/api/requerimentos/7101/tramitacao").andExpect(status().isOk());

        List<NovoEvento> eventos = eventosDeTramitacao();
        assertThat(eventos).hasSize(1);
        NovoEvento e = eventos.getFirst();
        assertThat(e.tipo().grupo().name()).isEqualTo("DOCUMENTOS");
        assertThat(e.usuario().login()).isEqualTo("gestor.demo");
        assertThat(e.idRequerimento()).isEqualTo(7101L);
        assertThat(e.numeroRequerimento()).isEqualTo("7101/2026");
        assertThat(e.numeroProcesso()).isEqualTo(PROCESSO);
        String retrato = e.toString();
        assertThat(retrato).contains("histórico").doesNotContain(SERVIDOR_01).doesNotContain(SERVIDOR_02)
                .doesNotContain("Analista");
        for (String id : IDS_TECNICOS) {
            assertThat(retrato).doesNotContain(id);
        }
    }

    // --- processo (1:N)

    @Test
    void processoDevolveUmaTramitacaoPorRequerimentoVinculado() throws Exception {
        String texto = corpo(obter("gestor.demo", "/api/processos/7900-2022/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].idRequerimento").value(7101))
                .andExpect(jsonPath("$[0].estado").value("ATIVO"))
                .andExpect(jsonPath("$[1].idRequerimento").value(7102))
                .andExpect(jsonPath("$[1].estado").value("ENCERRADO")));
        semIdsTecnicosNemCpf(texto);
        assertThat(eventosDeTramitacao()).extracting(NovoEvento::idRequerimento).containsExactly(7101L, 7102L);
    }

    @Test
    void processoParaOOperadorSoTrazOsDeleENenhumDeleEh403() throws Exception {
        String texto = corpo(obter("operador.demo", "/api/processos/7900-2022/tramitacao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idRequerimento").value(7101)));
        assertThat(texto).doesNotContain(SERVIDOR_01);
        verify(montar, never()).montar(7102);

        obter("operador.demo", "/api/processos/7901-2022/tramitacao")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
    }

    @Test
    void processoInexistenteEh404EFormatoInvalidoEh400() throws Exception {
        obter("gestor.demo", "/api/processos/1-1999/tramitacao")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        obter("gestor.demo", "/api/processos/abc/tramitacao")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        assertThat(eventosDeTramitacao()).isEmpty();
    }
}
