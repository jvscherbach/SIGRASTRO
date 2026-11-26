package br.gov.mt.sema.rastro.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.auditoria.application.port.ConsultarEventos;
import br.gov.mt.sema.rastro.auditoria.application.port.ParametrosDaAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaVisualizacao;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.financeiro.application.port.ParametrosDoFinanceiro;
import br.gov.mt.sema.rastro.painel.application.ParametrosDoPainel;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;

/** Parâmetros em tempo de execução (RF26 a RF28): faixa, atomicidade, classes de aplicação e evento único. */
@DirtiesContext
class ParametrosHttpTest extends BaseHttpDeDocumentos {

    private static final String PARAMETROS = "/api/configuracoes/parametros";

    @Autowired
    ConsultarEventos eventos;

    @Autowired
    ParametrosDoDossie dossie;

    @Autowired
    ParametrosDaConversao conversao;

    @Autowired
    ParametrosDaVisualizacao visualizacao;

    @Autowired
    ParametrosDaAuditoria auditoria;

    @Autowired
    OrigensLigadas origens;

    @Autowired
    ParametrosDoRastro rastro;

    @Autowired
    ParametrosDoFinanceiro financeiro;

    @Autowired
    ParametrosDoPainel painel;

    /** Nenhum teste deixa sobreposição para o próximo. */
    @AfterEach
    void restaurar() throws Exception {
        JsonNode lista = json.readTree(chamarParametros());
        for (JsonNode parametro : lista.get("parametros")) {
            if (parametro.get("sobreposto").asBoolean()) {
                mvc.perform(delete(PARAMETROS + "/" + parametro.get("nome").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin.demo")));
            }
        }
    }

    private String chamarParametros() throws Exception {
        return mvc.perform(get(PARAMETROS).header(HttpHeaders.AUTHORIZATION, bearer("admin.demo"))).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletResponse salvar(String usuario, Map<String, ?> alteracoes) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("alteracoes", alteracoes);
        return mvc.perform(put(PARAMETROS).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo))).andReturn().getResponse();
    }

    private JsonNode parametro(String nome) throws Exception {
        for (JsonNode parametro : json.readTree(chamarParametros()).get("parametros")) {
            if (parametro.get("nome").asText().equals(nome)) {
                return parametro;
            }
        }
        throw new AssertionError(nome);
    }

    private long ultimoId() {
        return eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null).mapToLong(EventoDeAuditoria::id).max().orElse(0);
    }

    private List<EventoDeAuditoria> eventosConfigDepoisDe(long id) {
        return new ArrayList<>(eventos.doPeriodo(GrupoDeEvento.ACESSOS, null, null)
                .filter(e -> e.id() > id && e.tipo() == TipoDeEvento.CONFIG).toList());
    }

    private static void invalido(MockHttpServletResponse resposta, String nome) throws Exception {
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(400);
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8)).contains("PARAMETRO_INVALIDO", nome);
    }

    @Test
    void lista_com_faixa_padrao_e_classe() throws Exception {
        JsonNode ttl = parametro("dossie.ttlMinutos");

        assertThat(ttl.get("valor").asText()).isEqualTo("30");
        assertThat(ttl.get("padrao").asText()).isEqualTo("30");
        assertThat(ttl.get("minimo").asInt()).isEqualTo(5);
        assertThat(ttl.get("maximo").asInt()).isEqualTo(240);
        assertThat(ttl.get("classeDeAplicacao").asText()).isEqualTo("PROXIMA_GERACAO");
        assertThat(ttl.get("sobreposto").asBoolean()).isFalse();
        assertThat(parametro("conversao.planilhaModo").get("opcoes")).hasSize(2);
        assertThat(parametro("auditoria.retencaoDocumentosAnos").get("classeDeAplicacao").asText()).isEqualTo("IMEDIATO");
        assertThat(parametro("dossie.threads").get("classeDeAplicacao").asText()).isEqualTo("EXIGE_REINICIO");
    }

    @Test
    void nada_de_credencial_nem_sigilo_do_scorp_na_lista() throws Exception {
        String lista = chamarParametros();

        assertThat(lista).doesNotContainIgnoringCase("token").doesNotContainIgnoringCase("senha")
                .doesNotContain("sigilo-padrao", "sigiloPadrao", "rastro.arquivos", "datasource", diretorioTemporario().toString());
    }

    @Test
    void so_o_administrador() throws Exception {
        for (String usuario : List.of("operador.demo", "gestor.demo")) {
            String sessao = bearer(usuario);
            assertThat(mvc.perform(get(PARAMETROS).header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse()
                    .getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            assertThat(salvar(usuario, Map.of("dossie.ttlMinutos", 60)).getStatus()).isEqualTo(403);
            assertThat(mvc.perform(delete(PARAMETROS + "/dossie.ttlMinutos").header(HttpHeaders.AUTHORIZATION, sessao))
                    .andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(dossie.ttlMinutos()).isEqualTo(30);
    }

    @Test
    void valida_as_faixas() throws Exception {
        invalido(salvar("admin.demo", Map.of("dossie.ttlMinutos", 4)), "dossie.ttlMinutos");
        invalido(salvar("admin.demo", Map.of("dossie.ttlMinutos", 241)), "dossie.ttlMinutos");
        invalido(salvar("admin.demo", Map.of("dossie.threads", 9)), "dossie.threads");
        invalido(salvar("admin.demo", Map.of("conexoes.downloadTimeoutSegundos", 9)), "conexoes.downloadTimeoutSegundos");
        invalido(salvar("admin.demo", Map.of("auditoria.retencaoDocumentosAnos", 3)), "auditoria.retencaoDocumentosAnos");
        invalido(salvar("admin.demo", Map.of("conversao.planilhaModo", "OUTRO")), "conversao.planilhaModo");
        invalido(salvar("admin.demo", Map.of("dossie.sumario", "talvez")), "dossie.sumario");
        invalido(salvar("admin.demo", Map.of("dossie.ttlMinutos", "abc")), "dossie.ttlMinutos");
        Map<String, Object> nulo = new LinkedHashMap<>();
        nulo.put("dossie.ttlMinutos", null);
        invalido(salvar("admin.demo", nulo), "dossie.ttlMinutos");
    }

    @Test
    void recusa_parametro_desconhecido_e_os_que_nao_sao_editaveis() throws Exception {
        for (String nome : List.of("nao.existe", "rastro.scorp.sigilo-padrao", "SCORP_SIGILO_PADRAO", "LICENCIAMENTO_API_TOKEN",
                "rastro.arquivos.url")) {
            invalido(salvar("admin.demo", Map.of(nome, "true")), nome);
        }
    }

    @Test
    void um_invalido_nada_aplicado_e_nenhum_evento() throws Exception {
        long antes = ultimoId();
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("dossie.sumario", false);
        alteracoes.put("visualizacao.ttlMinutos", 20);
        alteracoes.put("dossie.ttlMinutos", 1);

        invalido(salvar("admin.demo", alteracoes), "dossie.ttlMinutos");

        assertThat(dossie.sumario()).isTrue();
        assertThat(visualizacao.ttlMinutos()).isEqualTo(10);
        assertThat(eventosConfigDepoisDe(antes)).isEmpty();
    }

    @Test
    void salvar_gera_um_unico_evento_com_antes_e_depois() throws Exception {
        long antes = ultimoId();
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("dossie.ttlMinutos", 60);
        alteracoes.put("conversao.planilhaModo", "PAGINA_POR_ABA");
        alteracoes.put("dossie.threads", 3);

        assertThat(salvar("admin.demo", alteracoes).getStatus()).isEqualTo(200);

        List<EventoDeAuditoria> novos = eventosConfigDepoisDe(antes);
        assertThat(novos).hasSize(1);
        assertThat(novos.get(0).usuario().login()).isEqualTo("admin.demo");
        assertThat(novos.get(0).alteracoes()).containsExactly(
                new AlteracaoDoEvento("Expiração do dossiê (min)", "30", "60"),
                new AlteracaoDoEvento("Planilhas", "LARGURA", "PAGINA_POR_ABA"),
                new AlteracaoDoEvento("Gerações simultâneas (exige reinício)", "2", "3"));
    }

    @Test
    void sem_mudanca_real_nao_gera_evento() throws Exception {
        long antes = ultimoId();

        assertThat(salvar("admin.demo", Map.of("dossie.ttlMinutos", 30, "dossie.sumario", true)).getStatus()).isEqualTo(200);
        assertThat(salvar("admin.demo", Map.of()).getStatus()).isEqualTo(200);

        assertThat(eventosConfigDepoisDe(antes)).isEmpty();
        assertThat(parametro("dossie.ttlMinutos").get("sobreposto").asBoolean()).isFalse();
    }

    @Test
    void imediato_vale_na_proxima_leitura() throws Exception {
        salvar("admin.demo", Map.of("visualizacao.ttlMinutos", 20, "visualizacao.cacheMaxMb", 700,
                "auditoria.retencaoAcessosAnos", 2, "dossie.origem.complementacao", false, "dossie.origem.scorp", false));

        assertThat(visualizacao.ttlMinutos()).isEqualTo(20);
        assertThat(visualizacao.cacheMaxMb()).isEqualTo(700);
        assertThat(auditoria.retencaoAcessosAnos()).isEqualTo(2);
        assertThat(origens.ligada(OrigemDocumento.COMPLEMENTACAO)).isFalse();
        assertThat(origens.ligada(OrigemDocumento.SCORP)).isFalse();
        assertThat(origens.ligada(OrigemDocumento.IMOVEL)).isTrue();
    }

    @Test
    void proxima_geracao_vale_para_pedido_novo_e_nao_muda_o_que_esta_em_andamento() throws Exception {
        List<Runnable> adiadas = new ArrayList<>();
        doAnswer(chamada -> {
            adiadas.add(chamada.getArgument(0));
            return null;
        }).when(fila).submeter(any(Runnable.class));
        String sessao = bearer("gestor.demo");
        String corpo = "{\"idRequerimento\":101,\"documentos\":[{\"origem\":\"DOCUMENTO_PROCESSO\",\"id\":\"804328\"}]}";
        String antigo = json.readTree(mvc.perform(post("/api/dossies").header(HttpHeaders.AUTHORIZATION, sessao)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        salvar("admin.demo", Map.of("dossie.ttlMinutos", 240));
        adiadas.get(0).run();

        assertThat(dossie.ttlMinutos()).isEqualTo(240);
        LocalDateTime expira = LocalDateTime.parse(json.readTree(mvc.perform(get("/api/dossies/" + antigo)
                .header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse().getContentAsString())
                .get("expiraEm").asText());
        assertThat(expira).isBefore(LocalDateTime.now(java.time.ZoneId.of("America/Cuiaba")).plusMinutes(60));

        String novo = json.readTree(mvc.perform(post("/api/dossies").header(HttpHeaders.AUTHORIZATION, sessao)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        adiadas.get(1).run();
        LocalDateTime expiraNovo = LocalDateTime.parse(json.readTree(mvc.perform(get("/api/dossies/" + novo)
                .header(HttpHeaders.AUTHORIZATION, sessao)).andReturn().getResponse().getContentAsString())
                .get("expiraEm").asText());
        assertThat(expiraNovo).isAfter(LocalDateTime.now(java.time.ZoneId.of("America/Cuiaba")).plusMinutes(200));
    }

    @Test
    void exige_reinicio_fica_pendente_e_nao_muda_o_valor_em_vigor() throws Exception {
        salvar("admin.demo", Map.of("dossie.threads", 5, "conversao.processos", 3));

        JsonNode threads = parametro("dossie.threads");
        assertThat(threads.get("valor").asText()).isEqualTo("5");
        assertThat(threads.get("sobreposto").asBoolean()).isTrue();
        assertThat(threads.get("pendenteDeReinicio").asBoolean()).isTrue();
        assertThat(dossie.threads()).isEqualTo(2);
        assertThat(conversao.processos()).isEqualTo(2);
    }

    @Test
    void delete_volta_ao_padrao_com_evento() throws Exception {
        salvar("admin.demo", Map.of("dossie.ttlMinutos", 60));
        long antes = ultimoId();

        assertThat(mvc.perform(delete(PARAMETROS + "/dossie.ttlMinutos").header(HttpHeaders.AUTHORIZATION, bearer("admin.demo")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(dossie.ttlMinutos()).isEqualTo(30);
        assertThat(parametro("dossie.ttlMinutos").get("sobreposto").asBoolean()).isFalse();
        assertThat(eventosConfigDepoisDe(antes)).hasSize(1);
        assertThat(eventosConfigDepoisDe(antes).get(0).alteracoes())
                .containsExactly(new AlteracaoDoEvento("Expiração do dossiê (min)", "60", "30"));
    }

    @Test
    void delete_sem_sobreposicao_nao_gera_evento_e_desconhecido_e_404() throws Exception {
        long antes = ultimoId();
        String sessao = bearer("admin.demo");

        assertThat(mvc.perform(delete(PARAMETROS + "/dossie.ttlMinutos").header(HttpHeaders.AUTHORIZATION, sessao))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(delete(PARAMETROS + "/nao.existe").header(HttpHeaders.AUTHORIZATION, sessao))
                .andReturn().getResponse().getStatus()).isEqualTo(404);

        assertThat(eventosConfigDepoisDe(antes)).isEmpty();
    }

    // --- S7, bloco 10: parâmetros do Rastro

    @Test
    void rastro_lista_os_cinco_parametros_imediatos_sem_credencial() throws Exception {
        String lista = chamarParametros();
        for (String nome : List.of("rastro.mostrarNomeResponsavel", "rastro.posicaoCacheSegundos",
                "rastro.maxNomesPorEquipe", "rastro.posicoesSimultaneas", "rastro.camundaTimeoutSegundos")) {
            JsonNode p = parametro(nome);
            assertThat(p.get("grupo").asText()).as(nome).isEqualTo("RASTRO");
            assertThat(p.get("classeDeAplicacao").asText()).as(nome).isEqualTo("IMEDIATO");
        }
        assertThat(parametro("rastro.posicoesSimultaneas").get("valor").asText()).isEqualTo("4");
        assertThat(parametro("rastro.camundaTimeoutSegundos").get("valor").asText()).isEqualTo("10");
        assertThat(lista).doesNotContainIgnoringCase("SIGA_DB").doesNotContainIgnoringCase("senha")
                .doesNotContainIgnoringCase("token");
    }

    @Test
    void rastro_alteracao_gera_um_evento_com_antes_e_depois_e_vale_na_proxima_leitura() throws Exception {
        long antes = ultimoId();
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("rastro.mostrarNomeResponsavel", false);
        alteracoes.put("rastro.posicaoCacheSegundos", 0);
        alteracoes.put("rastro.maxNomesPorEquipe", 3);
        alteracoes.put("rastro.posicoesSimultaneas", 6);
        alteracoes.put("rastro.camundaTimeoutSegundos", 20);

        assertThat(salvar("admin.demo", alteracoes).getStatus()).isEqualTo(200);

        List<EventoDeAuditoria> novos = eventosConfigDepoisDe(antes);
        assertThat(novos).hasSize(1);
        assertThat(novos.get(0).alteracoes()).containsExactly(
                new AlteracaoDoEvento("Mostrar o nome do responsável", "true", "false"),
                new AlteracaoDoEvento("Validade do cache da posição (s; 0 desliga)", "30", "0"),
                new AlteracaoDoEvento("Máximo de nomes por equipe", "10", "3"),
                new AlteracaoDoEvento("Posições consultadas ao mesmo tempo (lista da busca)", "4", "6"),
                new AlteracaoDoEvento("Tempo limite das consultas ao fluxo (s)", "10", "20"));
        assertThat(rastro.mostrarNomeDoResponsavel()).isFalse();
        assertThat(rastro.cacheDaPosicaoSegundos()).isZero();
        assertThat(rastro.maximoDeNomesPorEquipe()).isEqualTo(3);
        assertThat(rastro.posicoesSimultaneas()).isEqualTo(6);
        assertThat(rastro.camundaTimeoutSegundos()).isEqualTo(20);
        assertThat(json.readTree(mvc.perform(get("/api/tramitacao/parametros")
                        .header(HttpHeaders.AUTHORIZATION, bearer("operador.demo"))).andReturn().getResponse()
                .getContentAsString()).get("posicoesSimultaneas").asInt()).isEqualTo(6);
    }

    @Test
    void rastro_faixas_e_o_operador_nao_altera() throws Exception {
        invalido(salvar("admin.demo", Map.of("rastro.posicoesSimultaneas", 0)), "rastro.posicoesSimultaneas");
        invalido(salvar("admin.demo", Map.of("rastro.camundaTimeoutSegundos", 121)), "rastro.camundaTimeoutSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.mostrarNomeResponsavel", "talvez")), "rastro.mostrarNomeResponsavel");

        for (String usuario : List.of("operador.demo", "gestor.demo")) {
            MockHttpServletResponse resposta = salvar(usuario, Map.of("rastro.posicoesSimultaneas", 6));
            assertThat(resposta.getStatus()).isEqualTo(403);
            assertThat(resposta.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
        }
        assertThat(rastro.posicoesSimultaneas()).isEqualTo(4);
    }

    // --- S9, bloco 10: parâmetros do financeiro, na seção Rastro

    @Test
    void financeiro_lista_os_tres_parametros_imediatos_na_secao_rastro_sem_credencial() throws Exception {
        String lista = chamarParametros();
        for (String nome : List.of("rastro.mostrarValores", "rastro.financeiroLimiteLancamentos",
                "rastro.financeiroTimeoutSegundos")) {
            JsonNode p = parametro(nome);
            assertThat(p.get("grupo").asText()).as(nome).isEqualTo("RASTRO");
            assertThat(p.get("classeDeAplicacao").asText()).as(nome).isEqualTo("IMEDIATO");
            assertThat(p.get("sobreposto").asBoolean()).as(nome).isFalse();
        }
        assertThat(parametro("rastro.mostrarValores").get("valor").asText()).isEqualTo("true");
        assertThat(parametro("rastro.financeiroLimiteLancamentos").get("valor").asText()).isEqualTo("200");
        assertThat(parametro("rastro.financeiroTimeoutSegundos").get("valor").asText()).isEqualTo("10");
        assertThat(lista).doesNotContainIgnoringCase("SIGA_DB").doesNotContainIgnoringCase("senha")
                .doesNotContainIgnoringCase("token");
    }

    @Test
    void financeiro_alteracao_gera_um_evento_com_antes_e_depois_e_vale_na_proxima_leitura() throws Exception {
        long antes = ultimoId();
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("rastro.mostrarValores", false);
        alteracoes.put("rastro.financeiroLimiteLancamentos", 50);
        alteracoes.put("rastro.financeiroTimeoutSegundos", 30);

        assertThat(salvar("admin.demo", alteracoes).getStatus()).isEqualTo(200);

        List<EventoDeAuditoria> novos = eventosConfigDepoisDe(antes);
        assertThat(novos).hasSize(1);
        assertThat(novos.get(0).alteracoes()).containsExactly(
                new AlteracaoDoEvento("Mostrar valores das taxas (valor, desconto e UPF)", "true", "false"),
                new AlteracaoDoEvento("Limite de lançamentos de taxa por requerimento", "200", "50"),
                new AlteracaoDoEvento("Tempo limite da consulta financeira (s)", "10", "30"));
        assertThat(financeiro.mostrarValores()).isFalse();
        assertThat(financeiro.limiteDeLancamentos()).isEqualTo(50);
        assertThat(financeiro.consultaTimeoutSegundos()).isEqualTo(30);
        // Voltar ao padrão restaura o ambiente.
        assertThat(mvc.perform(delete(PARAMETROS + "/rastro.mostrarValores")
                .header(HttpHeaders.AUTHORIZATION, bearer("admin.demo"))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(financeiro.mostrarValores()).isTrue();
    }

    @Test
    void financeiro_faixas_e_so_o_administrador_altera() throws Exception {
        invalido(salvar("admin.demo", Map.of("rastro.financeiroLimiteLancamentos", 0)), "rastro.financeiroLimiteLancamentos");
        invalido(salvar("admin.demo", Map.of("rastro.financeiroLimiteLancamentos", 1001)), "rastro.financeiroLimiteLancamentos");
        invalido(salvar("admin.demo", Map.of("rastro.financeiroTimeoutSegundos", 121)), "rastro.financeiroTimeoutSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.mostrarValores", "talvez")), "rastro.mostrarValores");

        for (String usuario : List.of("operador.demo", "gestor.demo")) {
            MockHttpServletResponse resposta = salvar(usuario, Map.of("rastro.mostrarValores", false));
            assertThat(resposta.getStatus()).as(usuario).isEqualTo(403);
            assertThat(resposta.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            // O Operador nem lista a seção (a rota de parâmetros é do Administrador).
            MockHttpServletResponse lista = mvc.perform(get(PARAMETROS).header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                    .andReturn().getResponse();
            assertThat(lista.getStatus()).as(usuario).isEqualTo(403);
            assertThat(lista.getContentAsString()).contains("PERFIL_SEM_PERMISSAO").doesNotContain("rastro.mostrarValores");
        }
        assertThat(financeiro.mostrarValores()).isTrue();
    }

    // --- S10, bloco 5: parâmetros do painel, na seção Rastro

    @Test
    void painel_lista_os_cinco_parametros_imediatos_na_secao_rastro_com_o_padrao() throws Exception {
        Map<String, String> padroes = Map.of("rastro.painelAno", "0", "rastro.painelAtualizacaoMinutos", "30",
                "rastro.painelLimiteRequerimentos", "3000", "rastro.painelTimeoutSegundos", "60",
                "rastro.painelIntervaloMinimoManualSegundos", "120");
        for (Map.Entry<String, String> p : padroes.entrySet()) {
            JsonNode parametro = parametro(p.getKey());
            assertThat(parametro.get("grupo").asText()).as(p.getKey()).isEqualTo("RASTRO");
            assertThat(parametro.get("classeDeAplicacao").asText()).as(p.getKey()).isEqualTo("IMEDIATO");
            assertThat(parametro.get("valor").asText()).as(p.getKey()).isEqualTo(p.getValue());
            assertThat(parametro.get("sobreposto").asBoolean()).as(p.getKey()).isFalse();
        }
    }

    // --- S11, bloco 6: parâmetros da caixa do analista, na seção Rastro

    @Test
    void caixa_lista_os_dois_parametros_imediatos_na_secao_rastro_com_o_padrao_e_confere_a_faixa() throws Exception {
        Map<String, String> padroes = Map.of("rastro.caixaAtualizacaoMinutos", "10",
                "rastro.caixaIntervaloMinimoManualSegundos", "60");
        for (Map.Entry<String, String> p : padroes.entrySet()) {
            JsonNode parametro = parametro(p.getKey());
            assertThat(parametro.get("grupo").asText()).as(p.getKey()).isEqualTo("RASTRO");
            assertThat(parametro.get("classeDeAplicacao").asText()).as(p.getKey()).isEqualTo("IMEDIATO");
            assertThat(parametro.get("valor").asText()).as(p.getKey()).isEqualTo(p.getValue());
            assertThat(parametro.get("sobreposto").asBoolean()).as(p.getKey()).isFalse();
        }
        assertThat(parametro("rastro.caixaAtualizacaoMinutos").get("minimo").asInt()).isEqualTo(2);
        assertThat(parametro("rastro.caixaAtualizacaoMinutos").get("maximo").asInt()).isEqualTo(120);
        assertThat(parametro("rastro.caixaIntervaloMinimoManualSegundos").get("minimo").asInt()).isEqualTo(15);
        assertThat(parametro("rastro.caixaIntervaloMinimoManualSegundos").get("maximo").asInt()).isEqualTo(3600);
        invalido(salvar("admin.demo", Map.of("rastro.caixaAtualizacaoMinutos", 1)), "rastro.caixaAtualizacaoMinutos");
        invalido(salvar("admin.demo", Map.of("rastro.caixaAtualizacaoMinutos", 121)), "rastro.caixaAtualizacaoMinutos");
        invalido(salvar("admin.demo", Map.of("rastro.caixaIntervaloMinimoManualSegundos", 14)),
                "rastro.caixaIntervaloMinimoManualSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.caixaIntervaloMinimoManualSegundos", 3601)),
                "rastro.caixaIntervaloMinimoManualSegundos");
    }

    @Test
    void painel_alteracao_gera_um_evento_com_antes_e_depois_e_vale_no_proximo_ciclo() throws Exception {
        long antes = ultimoId();
        Map<String, Object> alteracoes = new LinkedHashMap<>();
        alteracoes.put("rastro.painelAno", 2025);
        alteracoes.put("rastro.painelAtualizacaoMinutos", 10);
        alteracoes.put("rastro.painelLimiteRequerimentos", 500);

        assertThat(salvar("admin.demo", alteracoes).getStatus()).isEqualTo(200);

        List<EventoDeAuditoria> novos = eventosConfigDepoisDe(antes);
        assertThat(novos).hasSize(1);
        assertThat(novos.get(0).alteracoes()).containsExactly(
                new AlteracaoDoEvento("Painel: ano considerado (0 = ano corrente)", "0", "2025"),
                new AlteracaoDoEvento("Painel: atualização automática (min)", "30", "10"),
                new AlteracaoDoEvento("Painel: teto de requerimentos por atualização", "3000", "500"));
        assertThat(painel.ano()).isEqualTo(2025);
        assertThat(painel.atualizacaoMinutos()).isEqualTo(10);
        assertThat(painel.limiteRequerimentos()).isEqualTo(500);
        assertThat(mvc.perform(delete(PARAMETROS + "/rastro.painelAno")
                .header(HttpHeaders.AUTHORIZATION, bearer("admin.demo"))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(painel.ano()).isZero();
    }

    @Test
    void painel_faixas_e_so_o_administrador_altera() throws Exception {
        invalido(salvar("admin.demo", Map.of("rastro.painelAno", 1999)), "rastro.painelAno");
        invalido(salvar("admin.demo", Map.of("rastro.painelAno", 2101)), "rastro.painelAno");
        invalido(salvar("admin.demo", Map.of("rastro.painelAtualizacaoMinutos", 4)), "rastro.painelAtualizacaoMinutos");
        invalido(salvar("admin.demo", Map.of("rastro.painelAtualizacaoMinutos", 1441)), "rastro.painelAtualizacaoMinutos");
        invalido(salvar("admin.demo", Map.of("rastro.painelLimiteRequerimentos", 99)), "rastro.painelLimiteRequerimentos");
        invalido(salvar("admin.demo", Map.of("rastro.painelLimiteRequerimentos", 20001)), "rastro.painelLimiteRequerimentos");
        invalido(salvar("admin.demo", Map.of("rastro.painelTimeoutSegundos", 9)), "rastro.painelTimeoutSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.painelTimeoutSegundos", 301)), "rastro.painelTimeoutSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.painelIntervaloMinimoManualSegundos", 29)),
                "rastro.painelIntervaloMinimoManualSegundos");
        invalido(salvar("admin.demo", Map.of("rastro.painelIntervaloMinimoManualSegundos", 3601)),
                "rastro.painelIntervaloMinimoManualSegundos");
        assertThat(salvar("admin.demo", Map.of("rastro.painelAno", 0)).getStatus()).isEqualTo(200);
        assertThat(salvar("admin.demo", Map.of("rastro.painelAno", 2000)).getStatus()).isEqualTo(200);

        for (String usuario : List.of("operador.demo", "gestor.demo")) {
            MockHttpServletResponse resposta = salvar(usuario, Map.of("rastro.painelAno", 2025));
            assertThat(resposta.getStatus()).as(usuario).isEqualTo(403);
            assertThat(resposta.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
        }
        assertThat(painel.ano()).isEqualTo(2000);
    }

    @Test
    void salvar_o_valor_do_padrao_tira_a_sobreposicao() throws Exception {
        salvar("admin.demo", Map.of("dossie.ttlMinutos", 60));

        salvar("admin.demo", Map.of("dossie.ttlMinutos", 30));

        assertThat(parametro("dossie.ttlMinutos").get("sobreposto").asBoolean()).isFalse();
    }
}
