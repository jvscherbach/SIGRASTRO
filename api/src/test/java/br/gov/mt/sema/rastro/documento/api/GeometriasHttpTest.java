package br.gov.mt.sema.rastro.documento.api;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
import br.gov.mt.sema.rastro.documento.application.port.EspacoLivreEmDisco;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.dossie.application.port.FilaDeGeracao;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Geometrias (RN10, S4): lista própria em GET /requerimentos/{id}/documentos e ZIP tudo ou nada em
 * GET /requerimentos/{id}/geometrias/arquivo. O SIGA é falso; os arquivos vêm do cliente real da API de arquivos contra
 * um servidor HTTP local de teste (nenhum sistema real). Arquivos sintéticos, dados fictícios.
 */
@SpringBootTest(properties = {"rastro.datasource.siga.url=",
        "rastro.arquivos.token=TOKEN-GEOMETRIAS-DE-TESTE", "rastro.dossie.espaco-minimo-mb=1",
        "rastro.dossie.download-timeout-segundos=1", "rastro.dossie.download-total-maximo-segundos=2",
        "rastro.geometrias.max-mb=1", "rastro.geometrias.max-arquivos=6", "rastro.agendamento.enabled=false"})
@AutoConfigureMockMvc
class GeometriasHttpTest {

    private static final String CNPJ_DO_OPERADOR = "11.222.333/0001-81";
    private static final String TOKEN = "TOKEN-GEOMETRIAS-DE-TESTE";
    private static final String GEO_1 = "CHAVE-GEO-0001";
    private static final String GEO_2 = "CHAVE-GEO-0002";
    private static final String GEO_3 = "CHAVE-GEO-0003";
    private static final String GEO_SIGILOSA = "CHAVE-GEO-SIGILOSA";
    private static final String NOME_SIGILOSO = "limite-reservado";
    private static final LocalDateTime D = LocalDateTime.of(2025, 4, 1, 10, 0);

    private static final ServidorHttpDeTeste SERVIDOR;
    private static final Path DIRETORIO;

    static {
        try {
            SERVIDOR = ServidorHttpDeTeste.iniciar();
            DIRETORIO = Files.createTempDirectory("vistas-geometrias-teste");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("rastro.arquivos.url", SERVIDOR::url);
        registro.add("rastro.dossie.diretorio", DIRETORIO::toString);
    }

    @AfterAll
    static void encerrar() {
        SERVIDOR.close();
    }

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
    EspacoLivreEmDisco espaco;

    @MockitoBean
    FilaDeGeracao fila;

    /** Conteúdo por chave; chave ausente = 404. Status especial por chave (401, 500, tempo limite). */
    private final Map<String, byte[]> arquivos = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusPorChave = new ConcurrentHashMap<>();
    private final List<String> pedidos = new CopyOnWriteArrayList<>();
    private final List<String> tokensRecebidos = new CopyOnWriteArrayList<>();
    private final List<MockHttpServletResponse> respostas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        reset(siga, tarefas, objetoEPrioridade, documentos, espaco, fila);
        arquivos.clear();
        statusPorChave.clear();
        pedidos.clear();
        tokensRecebidos.clear();
        respostas.clear();
        when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.semTarefa());
        when(objetoEPrioridade.objeto(anyLong())).thenReturn(ObjetoDoRequerimento.vazio());
        when(objetoEPrioridade.prioridade(anyLong())).thenReturn(PrioridadeAtendimento.de(List.of()));
        when(siga.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, "2825/2025", CNPJ_DO_OPERADOR)));
        when(siga.buscarPorId(102)).thenReturn(Optional.of(requerimento(102, null, CNPJ_DO_OPERADOR)));
        when(siga.buscarPorId(202)).thenReturn(Optional.of(requerimento(202, "77/2024", "98.765.432/0001-10")));
        when(siga.buscarPorId(404)).thenReturn(Optional.empty());
        when(documentos.listar(anyLong())).thenReturn(listaComGeometrias());
        when(espaco.livreEmBytes(any())).thenReturn(Long.MAX_VALUE);

        arquivos.put(GEO_1, kml("Área de reserva"));
        arquivos.put(GEO_2, kml("Outra área com o mesmo nome"));
        arquivos.put(GEO_3, "shp sintético".getBytes(StandardCharsets.UTF_8));
        arquivos.put(GEO_SIGILOSA, kml("Reservado"));
        SERVIDOR.responderCom(troca -> {
            String chave = URLDecoder.decode(troca.caminhoBruto().substring("/api/arquivo/".length()), StandardCharsets.UTF_8);
            pedidos.add(chave);
            tokensRecebidos.add(String.valueOf(troca.cabecalho("Authorization")));
            Integer especial = statusPorChave.get(chave);
            if (especial != null && especial == 0) {
                // tempo limite: não responde até o cliente desistir
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            if (especial != null) {
                troca.responder(especial, new byte[0]);
                return;
            }
            byte[] corpo = arquivos.get(chave);
            troca.responder(corpo == null ? 404 : 200, corpo == null ? new byte[0] : corpo);
        });
    }

    // --- lista ---

    @Test
    void listaTrazGeometriasEmSecaoPropriaForaDosDocumentosEDosTotais() throws Exception {
        JsonNode corpo = lerJson(listar("gestor", 101).andExpect(status().isOk()));

        assertThat(corpo.get("itens")).hasSize(2);
        corpo.get("itens").forEach(item -> assertThat(item.get("tipoArquivo").asText()).isNotEqualTo("GEOMETRIA"));
        assertThat(corpo.at("/totais/total").asInt()).isEqualTo(2);
        assertThat(corpo.at("/totais/geometrias").asInt()).isEqualTo(6);
        assertThat(corpo.at("/totais/geometriasOmitidas").asInt()).isZero();
        JsonNode geometrias = corpo.get("geometrias");
        assertThat(geometrias).hasSize(6);
        assertThat(geometrias.get(0).get("tipoArquivo").asText()).isEqualTo("GEOMETRIA");
        assertThat(geometrias.get(0).has("tamanho")).isTrue();
        assertThat(geometrias.get(0).has("selecionavelPorPadrao")).isFalse();
        assertThat(texto(geometrias)).contains(NOME_SIGILOSO);
        assertThat(pedidos).isEmpty();
    }

    @Test
    void operadorNaoVeGeometriaSigilosaSoAContagem() throws Exception {
        MockHttpServletResponse resposta = listar("operador", 101).andExpect(status().isOk()).andReturn().getResponse();
        JsonNode corpo = json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));

        assertThat(corpo.get("geometrias")).hasSize(5);
        assertThat(corpo.at("/totais/geometrias").asInt()).isEqualTo(5);
        assertThat(corpo.at("/totais/geometriasOmitidas").asInt()).isEqualTo(1);
        assertThat(resposta.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(NOME_SIGILOSO)
                .doesNotContain("900777");
    }

    @Test
    void listaSemGeometrias() throws Exception {
        when(documentos.listar(101)).thenReturn(List.of(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "CHAVE-GEO-PDF",
                "Requerimento", "requerimento.pdf", D, null, 0)));

        listar("gestor", 101).andExpect(status().isOk())
                .andExpect(jsonPath("$.geometrias").isEmpty())
                .andExpect(jsonPath("$.totais.geometrias").value(0))
                .andExpect(jsonPath("$.totais.geometriasOmitidas").value(0));
    }

    // --- ZIP ---

    @Test
    void zipComTodasAsVigentesNomesUnicosECabecalhosSeguros() throws Exception {
        MockHttpServletResponse resposta = baixar("gestor", 101).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getContentType()).isEqualTo("application/zip");
        assertThat(resposta.getHeader("Content-Disposition")).startsWith("attachment")
                .contains("geometrias-processo-2825-2025.zip");
        assertThat(resposta.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        Map<String, byte[]> entradas = entradas(resposta.getContentAsByteArray());
        // vigentes: duas "area.kml", um shp e a sigilosa; a substituída e a indisponível ficam fora
        assertThat(entradas.keySet()).containsExactly("area.kml", "area-2.kml", "limite.shp", NOME_SIGILOSO + ".kml");
        assertThat(entradas.get("area.kml")).isEqualTo(arquivos.get(GEO_1));
        assertThat(entradas.get("area-2.kml")).isEqualTo(arquivos.get(GEO_2));
        assertThat(pedidos).containsExactlyInAnyOrder(GEO_1, GEO_2, GEO_3, GEO_SIGILOSA);
        assertThat(tokensRecebidos).allMatch(t -> t.equals("Bearer " + TOKEN));
        assertThat(pastasTemporarias()).isEmpty();
    }

    @Test
    void operadorRecebeZipSemAGeometriaSigilosa() throws Exception {
        MockHttpServletResponse resposta = baixar("operador", 101).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(entradas(resposta.getContentAsByteArray()).keySet())
                .containsExactly("area.kml", "area-2.kml", "limite.shp");
        assertThat(pedidos).doesNotContain(GEO_SIGILOSA);
    }

    @Test
    void requerimentoNaoProtocoladoUsaONumeroDoRequerimentoNoNome() throws Exception {
        MockHttpServletResponse resposta = baixar("gestor", 102).andExpect(status().isOk()).andReturn().getResponse();

        assertThat(resposta.getHeader("Content-Disposition")).contains("geometrias-requerimento-3331-2025.zip");
    }

    @Test
    void semGeometriaAcessivelEh404ComOMesmoTextoParaOperadorComSoSigilosas() throws Exception {
        when(documentos.listar(101)).thenReturn(List.of(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "CHAVE-GEO-PDF",
                "Requerimento", "requerimento.pdf", D, null, 0)));
        String semNenhuma = baixar("gestor", 101).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        when(documentos.listar(101)).thenReturn(List.of(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900777", GEO_SIGILOSA,
                "Limite reservado", NOME_SIGILOSO + ".kml", D, null, 1)));
        String soSigilosas = baixar("operador", 101).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(json.readTree(soSigilosas).get("detail")).isEqualTo(json.readTree(semNenhuma).get("detail"));
        assertThat(json.readTree(soSigilosas).get("detail").asText()).isEqualTo("Este requerimento não tem geometrias.");
        assertThat(soSigilosas).doesNotContain(NOME_SIGILOSO);
        assertThat(pedidos).isEmpty();
    }

    @Test
    void falhaDeUmArquivoNaoEntregaZipEListaOsArquivosComProblema() throws Exception {
        arquivos.remove(GEO_2);
        statusPorChave.put(GEO_3, 500);

        JsonNode erro = lerJson(baixar("gestor", 101)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("GEOMETRIAS_INCOMPLETAS")));

        assertThat(erro.get("arquivos")).hasSize(2);
        assertThat(erro.get("arquivos").get(0).get("nome").asText()).isEqualTo("area-2.kml");
        assertThat(erro.get("arquivos").get(0).get("motivo").asText()).isEqualTo("arquivo não encontrado no repositório");
        assertThat(erro.get("arquivos").get(1).get("nome").asText()).isEqualTo("limite.shp");
        assertThat(erro.get("detail").asText()).contains("2 de 4").contains("Nenhum arquivo foi entregue");
        // todos foram tentados (falhas coletadas) e nada sobrou no disco
        assertThat(pedidos).containsExactlyInAnyOrder(GEO_1, GEO_2, GEO_3, GEO_SIGILOSA);
        assertThat(pastasTemporarias()).isEmpty();
    }

    @Test
    void todasAsFalhasPorTempoLimiteSao504() throws Exception {
        when(documentos.listar(101)).thenReturn(List.of(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900701", GEO_1,
                "Área", "area.kml", D, null, 0)));
        statusPorChave.put(GEO_1, 0);

        baixar("gestor", 101)
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("GEOMETRIAS_INCOMPLETAS"))
                .andExpect(jsonPath("$.arquivos[0].nome").value("area.kml"));
        assertThat(pastasTemporarias()).isEmpty();
    }

    @Test
    void credencialRecusadaInterrompeSemZip() throws Exception {
        statusPorChave.put(GEO_1, 401);

        baixar("gestor", 101)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("CREDENCIAL_ARQUIVOS_RECUSADA"));
        assertThat(pastasTemporarias()).isEmpty();
    }

    @Test
    void espacoInsuficienteEhFalhaDosArquivos() throws Exception {
        when(espaco.livreEmBytes(any())).thenReturn(0L);

        baixar("gestor", 101)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.arquivos.length()").value(4))
                .andExpect(jsonPath("$.arquivos[0].motivo").value("espaço em disco insuficiente"));
        assertThat(pedidos).isEmpty();
    }

    @Test
    void acimaDoTamanhoMaximoEh413() throws Exception {
        arquivos.put(GEO_1, new byte[700 * 1024]);
        arquivos.put(GEO_2, new byte[700 * 1024]);

        baixar("gestor", 101)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("GEOMETRIAS_GRANDES_DEMAIS"));
        assertThat(pastasTemporarias()).isEmpty();
    }

    @Test
    void acimaDoMaximoDeArquivosEh413SemBaixarNada() throws Exception {
        List<Documento> muitas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            muitas.add(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "9008" + i, GEO_1 + i, "Área " + i, "area.kml", D, null, 0));
        }
        when(documentos.listar(101)).thenReturn(muitas);

        baixar("gestor", 101)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("GEOMETRIAS_GRANDES_DEMAIS"));
        assertThat(pedidos).isEmpty();
    }

    @Test
    void operadorDeOutroInteressadoRecebe403SemLerNada() throws Exception {
        baixar("operador", 202).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"));
        listar("operador", 202).andExpect(status().isForbidden());
        verify(documentos, never()).listar(202);
        assertThat(pedidos).isEmpty();
    }

    @Test
    void requerimentoInexistenteEh404ESemTokenEh401() throws Exception {
        baixar("gestor", 404).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        guardar(mvc.perform(get("/api/requerimentos/101/geometrias/arquivo"))).andExpect(status().isUnauthorized());
    }

    @Test
    void nomesHostisViramNomesSegurosNoZip() throws Exception {
        String longo = "z".repeat(300) + ".kml";
        when(documentos.listar(101)).thenReturn(List.of(
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900801", GEO_1, "a", "../../etc/x.kml", D, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900802", GEO_2, "b", "x\r\nSet-Cookie: a=b\u0000.kml", D, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900803", GEO_3, "c", "‮" + longo, D, null, 0)));

        MockHttpServletResponse resposta = baixar("gestor", 101).andExpect(status().isOk()).andReturn().getResponse();

        List<String> nomes = new ArrayList<>(entradas(resposta.getContentAsByteArray()).keySet());
        assertThat(nomes).hasSize(3);
        assertThat(nomes.get(0)).isEqualTo("x.kml");
        assertThat(nomes.get(1)).isEqualTo("xSet-Cookie_ a=b.kml");
        assertThat(nomes.get(2)).hasSize(120).endsWith(".kml").doesNotContain("‮");
        assertThat(nomes).allSatisfy(n -> assertThat(n).doesNotContain("/", "\\", "..").doesNotContainPattern("\\p{Cntrl}"));
        assertThat(resposta.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void geometriaNaoEntraNoDossie() throws Exception {
        String corpo = json.writeValueAsString(Map.of("idRequerimento", 101,
                "documentos", List.of(Map.of("origem", "DOCUMENTO_PROCESSO", "id", "900701"))));

        guardar(mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        verify(fila, never()).submeter(any());
    }

    /** Varredura: nenhuma resposta dos cenários traz chave, token, URL interna ou caminho do diretório temporário. */
    @Test
    void nenhumaRespostaTrazChaveTokenUrlOuCaminhoInterno() throws Exception {
        listar("gestor", 101);
        listar("operador", 101);
        baixar("gestor", 101);
        baixar("operador", 101);
        arquivos.remove(GEO_2);
        baixar("gestor", 101);
        statusPorChave.put(GEO_1, 401);
        baixar("gestor", 101);
        baixar("operador", 202);

        String[] proibidos = {"CHAVE-GEO", TOKEN, SERVIDOR.url(), "127.0.0.1", DIRETORIO.toString(),
                DIRETORIO.getFileName().toString(), "pedido-"};
        assertThat(respostas).hasSize(7);
        for (MockHttpServletResponse resposta : respostas) {
            StringBuilder tudo = new StringBuilder(new String(resposta.getContentAsByteArray(), StandardCharsets.ISO_8859_1));
            for (String nome : resposta.getHeaderNames()) {
                tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
            }
            for (String proibido : proibidos) {
                assertThat(tudo.toString()).as("resposta %s", resposta.getStatus()).doesNotContain(proibido);
            }
        }
    }

    // --- apoio ---

    /** 900701 e 900702: mesmo nome; 900703: shp; 900777: sigilosa; 900704: substituída; 900705: indisponível. */
    private static List<Documento> listaComGeometrias() {
        return List.of(
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "CHAVE-GEO-PDF", "Requerimento", "requerimento.pdf", D, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "CHAVE-GEO-PDF2", "Ofício", "oficio.pdf", D.plusDays(1), null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900701", GEO_1, "Área", "area.kml", D.plusDays(2), null, 0),
                doc(OrigemDocumento.IMOVEL, "900702", GEO_2, "Área do imóvel", "area.kml", D.plusDays(3), null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900703", GEO_3, "Limite", "limite.shp", D.plusDays(4), null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900777", GEO_SIGILOSA, "Limite reservado", NOME_SIGILOSO + ".kml",
                        D.plusDays(5), null, 1),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900704", "CHAVE-GEO-ANTIGA", "Antiga", "antiga.kmz",
                        D.minusDays(3), D, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "900705", "0", "Perdida", "perdida.kml", D.minusDays(2), null, 0));
    }

    private static Documento doc(OrigemDocumento origem, String id, String chave, String titulo, String arquivo,
            LocalDateTime data, LocalDateTime fim, Object sigilo) {
        return Documento.deLinha(origem, id, 101, chave, titulo, arquivo, data, fim, sigilo);
    }

    private static Requerimento requerimento(long id, String numeroProcesso, String cpfCnpj) {
        return new Requerimento(id, "3331/2025", numeroProcesso, "Requerimento de Outorga", Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2025, 3, 31, 10, 12), LocalDateTime.of(2025, 4, 29, 9, 14),
                new Interessado("Associação Residencial Vale Verde", cpfCnpj), "Uso de Recursos Hídricos");
    }

    private static byte[] kml(String nome) {
        return ("<?xml version=\"1.0\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\"><Placemark><name>" + nome
                + "</name><Polygon><outerBoundaryIs><LinearRing><coordinates>-56.1,-15.6 -56.0,-15.6 -56.0,-15.5 "
                + "-56.1,-15.6</coordinates></LinearRing></outerBoundaryIs></Polygon></Placemark></kml>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions listar(String usuario, long id) throws Exception {
        return guardar(mvc.perform(get("/api/requerimentos/" + id + "/documentos").header("Authorization", bearer(usuario))));
    }

    private ResultActions baixar(String usuario, long id) throws Exception {
        return guardar(mvc.perform(get("/api/requerimentos/" + id + "/geometrias/arquivo")
                .header("Authorization", bearer(usuario))));
    }

    private ResultActions guardar(ResultActions acoes) {
        respostas.add(acoes.andReturn().getResponse());
        return acoes;
    }

    private JsonNode lerJson(ResultActions acoes) throws Exception {
        return json.readTree(acoes.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String texto(JsonNode no) {
        return no.toString();
    }

    private static Map<String, byte[]> entradas(byte[] zip) throws IOException {
        Map<String, byte[]> entradas = new LinkedHashMap<>();
        try (ZipInputStream leitura = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entrada = leitura.getNextEntry(); entrada != null; entrada = leitura.getNextEntry()) {
                entradas.put(entrada.getName(), leitura.readAllBytes());
            }
        }
        return entradas;
    }

    private static List<Path> pastasTemporarias() throws IOException {
        Path raiz = DIRETORIO.resolve("geometrias");
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> filhos = Files.list(raiz)) {
            return filhos.toList();
        }
    }

    private String bearer(String usuario) throws Exception {
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", UsuariosDeTeste.senhaDe(usuario)));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(resposta).get("token").asText();
    }
}
