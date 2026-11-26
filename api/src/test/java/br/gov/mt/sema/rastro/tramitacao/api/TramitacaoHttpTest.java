package br.gov.mt.sema.rastro.tramitacao.api;

import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.AREA;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.ID_NAO_MAPEADO;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_01;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_02;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.quandoLer;
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
import br.gov.mt.sema.rastro.tramitacao.application.ConsultarPosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste;
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
 * Contrato HTTP da posição (S7, bloco 6), por perfil e por cenário. A posição vem pronta do dublê de
 * {@link ConsultarPosicaoAtual} (a leitura do Camunda é provada em {@code CamundaPosicaoJdbcTest}); aqui se prova o
 * acesso, o que sai para cada perfil, o evento de auditoria e a ausência de CPF e de ids técnicos.
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url="})
@AutoConfigureMockMvc
class TramitacaoHttpTest {

    // Dados fictícios: 7101 é do Operador (CNPJ de demonstração); 7102 é de outro interessado; ambos no mesmo processo.
    private static final String CNPJ_DO_OPERADOR = "11.222.333/0001-81";
    private static final String CPF_DE_OUTRO = "000.000.000-91";
    private static final String PROCESSO = "7900/2022";
    private static final String PROCESSO_SO_DE_OUTRO = "7901/2022";
    private static final List<String> IDS_TECNICOS = List.of("taskAnalisarRequerimento", "taskEmitirPortarias",
            "procSurhEmissaoPortaria", ID_NAO_MAPEADO, "Event_0h1oige", "procOutorgaAguaSupSub");

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

    // SigaNaoConfigurado implementa também a porta de documentos; trocar as outras deixaria esta sem bean.
    @MockitoBean
    DocumentosDoRequerimento documentos;

    @MockitoBean
    ConsultarPosicaoAtual consultar;

    @MockitoBean
    RegistrarEvento registrar;

    private final Requerimento doOperador = requerimento(7101, "7101/2026", PROCESSO, CNPJ_DO_OPERADOR);
    private final Requerimento deOutro = requerimento(7102, "7102/2026", PROCESSO, CPF_DE_OUTRO);
    private final Requerimento soDeOutro = requerimento(7103, "7103/2026", PROCESSO_SO_DE_OUTRO, CPF_DE_OUTRO);

    @BeforeEach
    void preparar() {
        reset(siga, consultar, registrar);
        for (Requerimento r : List.of(doOperador, deOutro, soDeOutro)) {
            when(siga.buscarPorId(r.id())).thenReturn(Optional.of(r));
        }
        when(siga.buscarPorId(404L)).thenReturn(Optional.empty());
        when(siga.buscarPorCriterios(any())).thenAnswer(c -> buscarProcesso(c.getArgument(0)));
        quandoLer(consultar, 7101, PosicoesDeTeste.ativaComMultiInstancia());
        quandoLer(consultar, 7102, PosicoesDeTeste.encerradaPorTaxa());
        quandoLer(consultar, 7103, PosicoesDeTeste.ativaComServidor());
    }

    private static Requerimento requerimento(long id, String numero, String processo, String cpfCnpj) {
        return new Requerimento(id, numero, processo, "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2026, 3, 1, 9, 0), null, new Interessado("Interessado Fictício S7", cpfCnpj),
                "Outorga de recursos hídricos (FICTÍCIO)");
    }

    /** Busca falsa por número de processo, com a restrição do interessado aplicada "na consulta", como o adapter. */
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
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", UsuariosDeTeste.senhaDe(usuario)));
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

    /** Só os eventos TRAMITACAO (o login também registra eventos, do grupo de acessos). */
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
                .doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    }

    // --- requerimento

    @Test
    void gestorVeEtapaDesdeQuandoComQuemMultiInstanciaParalelosEComplementacaoSemIdTecnico() throws Exception {
        ResultActions r = obter("gestor", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idRequerimento").value(7101))
                .andExpect(jsonPath("$.numeroRequerimento").value("7101/2026"))
                .andExpect(jsonPath("$.numeroProcesso").value(PROCESSO))
                .andExpect(jsonPath("$.situacaoLicenciamento.codigo").value("EM_ANALISE"))
                .andExpect(jsonPath("$.situacaoLicenciamento.descricao").value("Em análise"))
                .andExpect(jsonPath("$.modelo").value("Outorga de recursos hídricos"))
                .andExpect(jsonPath("$.estado").value("ATIVO"))
                .andExpect(jsonPath("$.etapaAtual.id").value("analise_do_processo"))
                .andExpect(jsonPath("$.etapaAtual.rotulo").value("Análise do processo"))
                .andExpect(jsonPath("$.etapaAtual.tipo").value("TAREFA_HUMANA"))
                .andExpect(jsonPath("$.etapaAtual.desde").value("2026-03-08T09:30:00"))
                .andExpect(jsonPath("$.etapaAtual.desdeUltimaEntrada").value(true))
                .andExpect(jsonPath("$.etapaAtual.responsavel.tipo").value("VARIOS"))
                .andExpect(jsonPath("$.etapaAtual.responsavel.papel").value("Analista"))
                .andExpect(jsonPath("$.etapaAtual.idTecnico").doesNotExist())
                .andExpect(jsonPath("$.etapaAtual.processoFilho").doesNotExist())
                .andExpect(jsonPath("$.multiInstancia.concluidos").value(1))
                .andExpect(jsonPath("$.multiInstancia.ativas").value(2))
                .andExpect(jsonPath("$.multiInstancia.total").value(3))
                .andExpect(jsonPath("$.multiInstancia.participantes", hasSize(3)))
                .andExpect(jsonPath("$.multiInstancia.participantes[0].nome").value(SERVIDOR_01))
                .andExpect(jsonPath("$.multiInstancia.participantes[0].concluiu").value(true))
                .andExpect(jsonPath("$.multiInstancia.participantes[1].nome").value(SERVIDOR_02))
                .andExpect(jsonPath("$.multiInstancia.participantes[1].concluiu").value(false))
                .andExpect(jsonPath("$.multiInstancia.participantes[2].nome").value(nullValue()))
                .andExpect(jsonPath("$.multiInstancia.participantes[2].papel").value("Responsável não identificado"))
                .andExpect(jsonPath("$.emParalelo", hasSize(2)))
                .andExpect(jsonPath("$.emParalelo[0].rotulo").value("Emissão do ato"))
                .andExpect(jsonPath("$.emParalelo[0].responsavel.area").value(AREA))
                .andExpect(jsonPath("$.emParalelo[0].responsavel.texto").value(AREA))
                .andExpect(jsonPath("$.emParalelo[1].id").value(nullValue()))
                .andExpect(jsonPath("$.emParalelo[1].rotulo").value("Etapa não mapeada"))
                .andExpect(jsonPath("$.emParalelo[1].idTecnico").doesNotExist())
                .andExpect(jsonPath("$.complementacao.quantidade").value(1))
                .andExpect(jsonPath("$.complementacao.desde").value("2026-03-06T09:30:00"))
                .andExpect(jsonPath("$.avisos", hasSize(1)))
                .andExpect(jsonPath("$.avisos[0].codigo").value("ETAPA_NAO_MAPEADA"))
                .andExpect(jsonPath("$.avisos[0].gravidade").value("INFO"))
                .andExpect(jsonPath("$.final").value(nullValue()))
                .andExpect(jsonPath("$.idsNaoMapeados").doesNotExist());
        String texto = corpo(r);
        semIdsTecnicosNemCpf(texto);
        assertThat(texto).doesNotContain("ACTIVE").doesNotContain("tarefas").doesNotContain("concluidas")
                .doesNotContain("diagnostico");
    }

    @Test
    void administradorRecebeOsIdsTecnicos() throws Exception {
        obter("admin", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual.idTecnico").value("taskAnalisarRequerimento"))
                .andExpect(jsonPath("$.emParalelo[0].idTecnico").value("taskEmitirPortarias"))
                .andExpect(jsonPath("$.emParalelo[0].processoFilho").value("procSurhEmissaoPortaria"))
                .andExpect(jsonPath("$.emParalelo[1].idTecnico").value(ID_NAO_MAPEADO))
                .andExpect(jsonPath("$.idsNaoMapeados[0]").value(ID_NAO_MAPEADO));
        obter("admin", "/api/requerimentos/7102/posicao")
                .andExpect(jsonPath("$.final.idTecnico").value("Event_0h1oige"));
    }

    @Test
    void operadorDoInteressadoVeAreaEPapelMasNaoONomeDoServidor() throws Exception {
        String texto = corpo(obter("operador", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapaAtual.rotulo").value("Análise do processo"))
                .andExpect(jsonPath("$.multiInstancia.participantes", hasSize(3)))
                .andExpect(jsonPath("$.multiInstancia.participantes[0].nome").value(nullValue()))
                .andExpect(jsonPath("$.multiInstancia.participantes[0].papel").value("Analista"))
                .andExpect(jsonPath("$.multiInstancia.participantes[0].concluiu").value(true))
                .andExpect(jsonPath("$.emParalelo[0].responsavel.area").value(AREA))
                .andExpect(jsonPath("$.etapaAtual.idTecnico").doesNotExist()));
        assertThat(texto).doesNotContain(SERVIDOR_01).doesNotContain(SERVIDOR_02);
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void nomeDoServidorSoParaGestorEAdministrador() throws Exception {
        obter("gestor", "/api/requerimentos/7103/posicao")
                .andExpect(jsonPath("$.etapaAtual.responsavel.nome").value(SERVIDOR_01))
                .andExpect(jsonPath("$.etapaAtual.responsavel.texto").value(SERVIDOR_01));
        obter("admin", "/api/requerimentos/7103/posicao")
                .andExpect(jsonPath("$.etapaAtual.responsavel.nome").value(SERVIDOR_01));
    }

    @Test
    void operadorDeOutroInteressadoRecebe403SemLerNemRegistrar() throws Exception {
        obter("operador", "/api/requerimentos/7102/posicao")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
        verify(consultar, never()).executar(anyLong());
        assertThat(eventosDeTramitacao()).isEmpty();
    }

    @Test
    void requerimentoInexistenteEh404() throws Exception {
        for (String usuario : List.of("operador", "gestor", "admin")) {
            obter(usuario, "/api/requerimentos/404/posicao")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        }
        assertThat(eventosDeTramitacao()).isEmpty();
    }

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/api/requerimentos/7101/posicao")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
        mvc.perform(get("/api/processos/7900-2022/posicao")).andExpect(status().isUnauthorized());
    }

    @Test
    void encerradoTrazOFinalEOAvisoSemIdTecnicoParaOGestor() throws Exception {
        String texto = corpo(obter("gestor", "/api/requerimentos/7102/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENCERRADO"))
                .andExpect(jsonPath("$.etapaAtual").value(nullValue()))
                .andExpect(jsonPath("$.emParalelo", hasSize(0)))
                .andExpect(jsonPath("$.final.rotulo").value("Encerrado por taxa não paga no prazo"))
                .andExpect(jsonPath("$.final.encerradoEm").value("2026-03-08T09:30:00"))
                .andExpect(jsonPath("$.final.idTecnico").doesNotExist())
                .andExpect(jsonPath("$.situacaoLicenciamento.codigo").value("AGUARDANDO_PAGAMENTO_DE_TAXAS"))
                .andExpect(jsonPath("$.avisos[0].codigo").value("FLUXO_ENCERRADO_SEM_STATUS"))
                .andExpect(jsonPath("$.avisos[0].gravidade").value("ATENCAO")));
        semIdsTecnicosNemCpf(texto);
    }

    @Test
    void verificacaoDaRevisaoEConcluidoSemEtapaEmAndamentoNemBolaPreta() throws Exception {
        quandoLer(consultar, 7101, PosicoesDeTeste.naVerificacaoDaRevisao());
        for (String usuario : List.of("operador", "gestor", "admin")) {
            String texto = corpo(obter(usuario, "/api/requerimentos/7101/posicao")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CONCLUIDO"))
                    .andExpect(jsonPath("$.etapaAtual").value(nullValue()))
                    .andExpect(jsonPath("$.multiInstancia").value(nullValue()))
                    .andExpect(jsonPath("$.final.rotulo").value("Concluído"))
                    .andExpect(jsonPath("$.final.encerradoEm").value("2026-03-08T09:30:00"))
                    .andExpect(jsonPath("$.final.idTecnico").doesNotExist())
                    .andExpect(jsonPath("$.avisos[0].codigo").value("CONCLUIDO_ANTES_DA_VERIFICACAO"))
                    .andExpect(jsonPath("$.avisos[0].gravidade").value("INFO")));
            assertThat(texto).doesNotContain("Verificação da revisão").doesNotContain("taskVerificarRevisaoProcesso");
        }
    }

    @Test
    void nenhumaRespostaDeConsultaMencionaOMotorDeFluxoParaNenhumPerfil() throws Exception {
        for (var posicao : List.of(PosicoesDeTeste.ativaComMultiInstancia(), PosicoesDeTeste.encerradaPorTaxa(),
                PosicoesDeTeste.naVerificacaoDaRevisao(), PosicoesDeTeste.indisponivel(), PosicoesDeTeste.naoSuportado(),
                PosicoesDeTeste.semInstancia())) {
            quandoLer(consultar, 7101, posicao);
            for (String usuario : List.of("operador", "gestor", "admin")) {
                assertThat(corpo(obter(usuario, "/api/requerimentos/7101/posicao")).toLowerCase())
                        .as("posição de %s", usuario).doesNotContain("camunda");
            }
        }
    }

    @Test
    void camundaIndisponivelEh200ComOEstadoENaoErro() throws Exception {
        quandoLer(consultar, 7101, PosicoesDeTeste.indisponivel());

        obter("operador", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.modelo").value("Outorga de recursos hídricos"))
                .andExpect(jsonPath("$.situacaoLicenciamento.codigo").value("EM_ANALISE"))
                .andExpect(jsonPath("$.etapaAtual").value(nullValue()));
        // A consulta aconteceu: registra mesmo indisponível.
        assertThat(eventosDeTramitacao()).hasSize(1);
    }

    @Test
    void naoSuportadoESemInstancia() throws Exception {
        quandoLer(consultar, 7101, PosicoesDeTeste.naoSuportado());
        obter("gestor", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NAO_SUPORTADO"))
                .andExpect(jsonPath("$.modelo").value(nullValue()))
                .andExpect(jsonPath("$.etapaAtual").value(nullValue()));

        quandoLer(consultar, 7101, PosicoesDeTeste.semInstancia());
        obter("gestor", "/api/requerimentos/7101/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SEM_INSTANCIA"))
                .andExpect(jsonPath("$.situacaoLicenciamento.descricao").value("Concluído"))
                .andExpect(jsonPath("$.avisos[0].codigo").value("SEM_INSTANCIA"));
    }

    // --- auditoria

    @Test
    void registraTramitacaoComUsuarioENumerosSemResponsavelNemCpf() throws Exception {
        obter("gestor", "/api/requerimentos/7101/posicao").andExpect(status().isOk());

        List<NovoEvento> eventos = eventosDeTramitacao();
        assertThat(eventos).hasSize(1);
        NovoEvento e = eventos.getFirst();
        assertThat(e.tipo().grupo().name()).isEqualTo("DOCUMENTOS");
        assertThat(e.usuario().login()).isEqualTo("gestor");
        assertThat(e.idRequerimento()).isEqualTo(7101L);
        assertThat(e.numeroRequerimento()).isEqualTo("7101/2026");
        assertThat(e.numeroProcesso()).isEqualTo(PROCESSO);
        String retrato = e.toString();
        assertThat(retrato).doesNotContain(SERVIDOR_01).doesNotContain(SERVIDOR_02).doesNotContain(AREA)
                .doesNotContain("Analista");
        for (String id : IDS_TECNICOS) {
            assertThat(retrato).doesNotContain(id);
        }
    }

    // --- processo (1:N)

    @Test
    void processoDevolveUmaPosicaoPorRequerimentoVinculado() throws Exception {
        String texto = corpo(obter("gestor", "/api/processos/7900-2022/posicao")
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
        obter("operador", "/api/processos/7900-2022/posicao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idRequerimento").value(7101));
        verify(consultar, never()).executar(anyLong());

        obter("operador", "/api/processos/7901-2022/posicao")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
    }

    @Test
    void processoInexistenteEh404EFormatoInvalidoEh400() throws Exception {
        obter("gestor", "/api/processos/1-1999/posicao")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        obter("gestor", "/api/processos/abc/posicao")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        assertThat(eventosDeTramitacao()).isEmpty();
    }
}
