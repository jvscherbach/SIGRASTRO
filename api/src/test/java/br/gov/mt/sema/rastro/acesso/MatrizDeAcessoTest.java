package br.gov.mt.sema.rastro.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResponsavelTarefa;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.consulta.domain.TipoBusca;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento.Pessoa;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz de perfis completa (S4, bloco 9, RNF10): todos os endpoints de {@code /api} × perfil (Operador, Gestor,
 * Administrador) × interessado ou não × documento sigiloso ou não × dono do pedido ou não, pelo MockMvc, com o resultado
 * esperado de cada combinação. A lista de endpoints é a da "Matriz de acesso por endpoint" do docs/07: um mapeamento do
 * Spring sem linha lá (ou sem cenário aqui) faz {@link #todoEndpointTemLinhaNaMatrizDoDocs07ECenarioAqui} falhar.
 *
 * <p>Todas as respostas são guardadas e varridas (corpo e cabeçalhos) atrás de chave de arquivo, token, URL e caminho
 * interno e, para o Operador, de qualquer dado sigiloso ou de outro interessado. SIGA, API de arquivos e SCORP são
 * falsos (o SCORP é um servidor HTTP local); dados fictícios. Documentos pessoais sigilosos por padrão
 * ({@code SCORP_SIGILO_PADRAO=true}), para cobrir o mascaramento da origem "Pessoal".
 */
@TestPropertySource(properties = {"rastro.scorp.sigilo-padrao=true", "rastro.conversao.habilitada=false"})
class MatrizDeAcessoTest extends BaseHttpDeDocumentos {

    /** Endpoints cobertos por esta matriz; têm de ser exatamente os mapeamentos de /api e as linhas do docs/07. */
    static final Set<String> ENDPOINTS = Set.of(
            "POST /api/auth/login", "GET /api/auth/me", "POST /api/auth/logout", "POST /api/auth/senha",
            "GET /api/estado",
            "GET /api/busca", "GET /api/requerimentos/{id}", "GET /api/requerimentos/{id}/documentos",
            "GET /api/requerimentos/{id}/geometrias/arquivo", "GET /api/documentos/{origem}/{id}/conteudo",
            "GET /api/dossies", "POST /api/dossies", "GET /api/dossies/{id}", "GET /api/dossies/{id}/arquivo",
            "POST /api/dossies/{id}/arquivo/ticket", "POST /api/dossies/{id}/continuacao", "DELETE /api/dossies/{id}",
            "GET /api/auditoria/documentos", "GET /api/auditoria/acessos", "GET /api/auditoria/{grupo}/{id}",
            "GET /api/auditoria/{grupo}/exportacao",
            "GET /api/configuracoes/usuarios", "POST /api/configuracoes/usuarios", "PATCH /api/configuracoes/usuarios/{id}",
            "GET /api/configuracoes/parametros", "PUT /api/configuracoes/parametros",
            "DELETE /api/configuracoes/parametros/{nome}", "GET /api/configuracoes/conexoes",
            "POST /api/configuracoes/conexoes/{nome}/teste",
            "GET /api/requerimentos/{id}/posicao", "GET /api/processos/{numero}/posicao",
            "GET /api/tramitacao/parametros",
            "GET /api/requerimentos/{id}/tramitacao", "GET /api/processos/{numero}/tramitacao",
            "GET /api/requerimentos/{id}/financeiro", "GET /api/processos/{numero}/financeiro",
            "GET /api/painel", "POST /api/painel/atualizacao",
            "GET /api/painel/analistas", "GET /api/painel/analistas/{chave}/requerimentos",
            "GET /api/caixa", "GET /api/caixa/avisos", "POST /api/caixa/avisos/visto", "POST /api/caixa/atualizar");

    private enum Perfil {
        OPERADOR("operador"), GESTOR("gestor"), ADMIN("admin"), ANALISTA("analista");

        final String login;

        Perfil(String login) {
            this.login = login;
        }
    }

    /** Operador, Gestor e Administrador têm a matriz completa; o Analista (L1, bloco 6) tem a seção própria {@link #analista}. */
    private static final List<Perfil> PERFIS_COM_CENARIO_COMPLETO = List.of(Perfil.OPERADOR, Perfil.GESTOR, Perfil.ADMIN);

    // Dados fictícios. 101 é do Operador; 202 é de outro interessado.
    private static final String NUMERO_DO_OPERADOR = "3331/2025";
    private static final String PROCESSO_DO_OPERADOR = "2825/2025";
    private static final String NUMERO_DE_OUTRO = "7777/2024";
    private static final String PROCESSO_DE_OUTRO = "88/2024";
    private static final String CNPJ_DE_OUTRO = "98.765.432/0001-10";
    private static final String COMUM = "804328";
    private static final String SIGILOSO = "804329";
    private static final String TITULO_SIGILOSO = "Laudo reservado";
    private static final String DOC_DE_OUTRO = "707070";
    private static final String TITULO_DE_OUTRO = "Contrato de outro interessado";
    private static final String GEO_SIGILOSA = "area-reservada";
    private static final String SERVIDORA = "Servidora Fictícia da Tarefa";
    private static final String PESSOA_SCORP = "CORP-MATRIZ-0001";
    private static final String CHAVE_SCORP = "CHAVE-INTERNA-SCORP-MATRIZ";
    private static final String TITULO_PESSOAL = "RG pessoal do interessado";
    private static final String TOKEN_SCORP = TOKEN_DE_ARQUIVOS;

    private static final ServidorHttpDeTeste SCORP;

    static {
        try {
            SCORP = ServidorHttpDeTeste.iniciar();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void scorp(DynamicPropertyRegistry registro) {
        registro.add("rastro.scorp.url", SCORP::url);
    }

    @AfterAll
    static void encerrar() {
        SCORP.close();
    }

    @MockitoBean
    PessoasDoRequerimento pessoas;

    @Autowired
    br.gov.mt.sema.rastro.painel.application.AtualizarPainel atualizarPainel;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamentos;

    private final SoftAssertions soft = new SoftAssertions();
    private final Set<String> cobertos = new TreeSet<>();
    private final List<Map.Entry<Perfil, MockHttpServletResponse>> respostas = new ArrayList<>();
    private final List<String[]> rotasS5 = new ArrayList<>();
    private final Map<Perfil, String> tokens = new LinkedHashMap<>();
    private int cenarios;

    @BeforeEach
    void preparar() throws Exception {
        LocalDateTime d = LocalDateTime.of(2025, 4, 1, 10, 0);
        List<Documento> do101 = new ArrayList<>(listaPadrao());
        do101.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804390", 101, "CHAVE-INTERNA-GEO", "Polígono",
                "area.kml", d.plusDays(5), null, 0));
        do101.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "804391", 101, "CHAVE-INTERNA-GEO-SIGILOSA",
                "Polígono reservado", GEO_SIGILOSA + ".kml", d.plusDays(6), null, 1));
        List<Documento> do202 = new ArrayList<>(do101);
        do202.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, DOC_DE_OUTRO, 202, "CHAVE-INTERNA-OUTRO",
                TITULO_DE_OUTRO, "contrato.pdf", d.plusDays(7), null, 0));
        when(documentos.listar(101)).thenReturn(do101);
        when(documentos.listar(202)).thenReturn(do202);

        Requerimento de202 = new Requerimento(202, NUMERO_DE_OUTRO, PROCESSO_DE_OUTRO, "Requerimento de Outorga",
                Situacao.de("EM_ANALISE"), d, null, new Interessado("Fazenda Fictícia Ltda.", CNPJ_DE_OUTRO),
                "Uso de Recursos Hídricos");
        Requerimento de101 = requerimento(101, CNPJ_DO_OPERADOR);
        when(siga.buscarPorNumeroDeRequerimento(any())).thenAnswer(c -> {
            String texto = ((NumeroRequerimento) c.getArgument(0)).texto();
            return NUMERO_DO_OPERADOR.equals(texto) ? Optional.of(de101)
                    : NUMERO_DE_OUTRO.equals(texto) ? Optional.of(de202) : Optional.empty();
        });
        when(siga.buscarPorCriterios(any())).thenAnswer(c -> buscar(c.getArgument(0), de101, de202));
        when(tarefas.obter(anyLong())).thenReturn(TarefaAtual.ativa("Análise técnica", d,
                ResponsavelTarefa.atribuido(SERVIDORA), "outorga"));

        when(pessoas.listarPessoas(anyLong())).thenReturn(List.of(new Pessoa(PESSOA_SCORP, PapelDaPessoa.INTERESSADO)));
        SCORP.responderCom(troca -> troca.responder(200, ("[{\"chave\":\"" + CHAVE_SCORP + "\",\"nomeArquivo\":"
                + "\"rg-pessoal.pdf\",\"descricao\":\"" + TITULO_PESSOAL + "\",\"dataInicio\":\"2025-03-01T08:00:00\"}]")
                .getBytes(StandardCharsets.UTF_8)));

        for (Perfil perfil : Perfil.values()) {
            tokens.put(perfil, "Bearer " + token(perfil.login));
        }
    }

    /** Busca falsa com a restrição do interessado aplicada "na consulta", como o adapter real. */
    private static Pagina<ResumoRequerimento> buscar(CriterioBusca criterio, Requerimento de101, Requerimento de202) {
        List<Requerimento> todos = new ArrayList<>();
        if (criterio.numero() != null && criterio.tipo() == TipoBusca.PROCESSO) {
            String numero = criterio.numero().texto();
            if (PROCESSO_DO_OPERADOR.equals(numero)) {
                todos.add(de101);
            } else if (PROCESSO_DE_OUTRO.equals(numero)) {
                todos.add(de202);
            }
        } else {
            todos.add(de101);
            todos.add(de202);
        }
        List<ResumoRequerimento> visiveis = todos.stream()
                .filter(r -> criterio.restricaoDigitos() == null
                        || r.cpfCnpjDoInteressado().replaceAll("\\D", "").equals(criterio.restricaoDigitos()))
                .map(Requerimento::resumo).toList();
        return new Pagina<>(visiveis, visiveis.size(), criterio.pagina(), criterio.tamanho());
    }

    // ------------------------------------------------------------------ cobertura

    @Test
    void todoEndpointTemLinhaNaMatrizDoDocs07ECenarioAqui() throws IOException {
        Set<String> doSpring = new TreeSet<>();
        for (RequestMappingInfo info : mapeamentos.getHandlerMethods().keySet()) {
            for (String caminho : info.getPatternValues()) {
                if (!caminho.startsWith("/api/")) {
                    continue;
                }
                info.getMethodsCondition().getMethods().forEach(m -> doSpring.add(m.name() + " " + caminho));
                if (info.getMethodsCondition().getMethods().isEmpty()) {
                    doSpring.add("* " + caminho);
                }
            }
        }

        assertThat(doSpring).as("mapeamentos do Spring sem cenário na matriz (ou cenário sem endpoint)")
                .containsExactlyInAnyOrderElementsOf(ENDPOINTS);
        assertThat(linhasDaMatrizDoDocs07()).as("linhas da \"Matriz de acesso por endpoint\" do docs/07")
                .containsExactlyInAnyOrderElementsOf(ENDPOINTS);
    }

    /** Primeira coluna da tabela, sem a query string e com o prefixo /api: "`GET /dossies/{id}/arquivo?ticket=`". */
    static Set<String> linhasDaMatrizDoDocs07() throws IOException {
        Path doc = Path.of("..", "docs", "07-api.md");
        assertThat(doc).as("docs/07 a partir do módulo api").exists();
        String texto = Files.readString(doc, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int inicio = texto.indexOf("## Matriz de acesso por endpoint");
        assertThat(inicio).as("seção da matriz no docs/07").isNotNegative();
        int fim = texto.indexOf("\n## ", inicio + 3);
        String secao = texto.substring(inicio, fim < 0 ? texto.length() : fim);
        Set<String> linhas = new TreeSet<>();
        Matcher m = Pattern.compile("^\\| `(GET|POST|PUT|PATCH|DELETE) (/[^`?\\s]*)[^`]*`", Pattern.MULTILINE).matcher(secao);
        while (m.find()) {
            linhas.add(m.group(1) + " /api" + m.group(2));
        }
        return linhas;
    }

    // ------------------------------------------------------------------ matriz

    @Test
    void matrizCompletaPorPerfilInteressadoSigiloEDonoSemVazamento() throws Exception {
        autenticacao();
        busca();
        requerimento();
        listaDeDocumentos();
        geometrias();
        conteudo();
        criacaoDeDossie();
        pedidos();
        auditoria();
        usuarios();
        parametrosEConexoes();
        dossiesGerados();
        posicao();
        financeiro();
        painel();
        caixa();
        analista();
        mascaramentoNoPdfDoOperador();

        varreduraDeVazamento();
        assertThat(cobertos).as("endpoints exercitados").containsExactlyInAnyOrderElementsOf(ENDPOINTS);
        assertThat(cenarios).as("cenários").isGreaterThan(230);
        soft.assertAll();
    }

    private void autenticacao() throws Exception {
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            verificar("POST /api/auth/login", "login", p, 200, null, () -> mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("usuario", p.login, "senha", UsuariosDeTeste.senhaDe(p.login))))));
            verificar("GET /api/auth/me", "me", p, 200, null, () -> comToken(get("/api/auth/me"), p));
            String descartavel = "Bearer " + token(p.login);
            verificar("POST /api/auth/logout", "logout", p, 204, null,
                    () -> mvc.perform(post("/api/auth/logout").header("Authorization", descartavel)));
            verificar("GET /api/auth/me", "token encerrado", p, 401, "NAO_AUTENTICADO",
                    () -> mvc.perform(get("/api/auth/me").header("Authorization", descartavel)));
        }
        verificar("GET /api/auth/me", "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/auth/me")));
        // Troca da própria senha (L1): todo perfil pode; aqui a nova senha repete a atual, então nada muda (400) e as
        // sessões dos demais cenários seguem valendo.
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            String atual = UsuariosDeTeste.senhaDe(p.login);
            verificar("POST /api/auth/senha", "própria senha, nova igual à atual", p, 400, "PARAMETRO_INVALIDO",
                    () -> comToken(post("/api/auth/senha").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("senhaAtual", atual, "novaSenha", atual,
                                    "confirmacao", atual))), p));
        }
        verificar("POST /api/auth/senha", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(post("/api/auth/senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"x\",\"novaSenha\":\"y\",\"confirmacao\":\"y\"}")));
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            verificar("GET /api/estado", "estado do serviço (S6)", p, 200, null, () -> comToken(get("/api/estado"), p));
        }
        verificar("GET /api/estado", "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/estado")));
    }

    private void busca() throws Exception {
        String rota = "GET /api/busca";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(rota, "requerimento do interessado", p, 200, null,
                    () -> comToken(get("/api/busca?tipo=REQUERIMENTO&numero=" + NUMERO_DO_OPERADOR), p));
            verificar(rota, "requerimento de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/busca?tipo=REQUERIMENTO&numero=" + NUMERO_DE_OUTRO), p));
            verificar(rota, "processo do interessado", p, 200, null,
                    () -> comToken(get("/api/busca?tipo=PROCESSO&numero=" + PROCESSO_DO_OPERADOR), p));
            verificar(rota, "processo de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/busca?tipo=PROCESSO&numero=" + PROCESSO_DE_OUTRO), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/busca?tipo=REQUERIMENTO&numero=9999/1999"), p));
            MockHttpServletResponse porCnpj = verificar(rota, "por CPF/CNPJ de outro", p, 200, null,
                    () -> comToken(get("/api/busca?cpfCnpj=98765432000110"), p));
            JsonNode pagina = lerJson(porCnpj);
            soft.assertThat(pagina.get("conteudo").size()).as("busca por CPF/CNPJ (%s): só os vinculados", p)
                    .isEqualTo(op ? 1 : 2);
        }
        verificar(rota, "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/busca?cpfCnpj=1")));
    }

    private void requerimento() throws Exception {
        String rota = "GET /api/requerimentos/{id}";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            MockHttpServletResponse doInteressado = verificar(rota, "do interessado", p, 200, null,
                    () -> comToken(get("/api/requerimentos/101"), p));
            // RN12: o responsável pela tarefa só para Gestor e Administrador
            soft.assertThat(doInteressado.getContentAsString(StandardCharsets.UTF_8).contains(SERVIDORA))
                    .as("responsável pela tarefa visível para %s", p).isEqualTo(!op);
            verificar(rota, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202"), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO", () -> comToken(get("/api/requerimentos/404"), p));
        }
    }

    /**
     * S7, bloco 6: posição na tramitação, com a mesma regra da consulta (RN07). Sem SIGA, a posição é INDISPONIVEL
     * (200): o acesso é conferido antes. O bloco 7 estende com a varredura de ids técnicos e CPF por cenário.
     */
    private void posicao() throws Exception {
        String rota = "GET /api/requerimentos/{id}/posicao";
        String doProcesso = "GET /api/processos/{numero}/posicao";
        String processoDoOperador = PROCESSO_DO_OPERADOR.replace('/', '-');
        String processoDeOutro = PROCESSO_DE_OUTRO.replace('/', '-');
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(rota, "do interessado", p, 200, null, () -> comToken(get("/api/requerimentos/101/posicao"), p));
            verificar(rota, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202/posicao"), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/requerimentos/404/posicao"), p));
            verificar(doProcesso, "processo do interessado", p, 200, null,
                    () -> comToken(get("/api/processos/" + processoDoOperador + "/posicao"), p));
            verificar(doProcesso, "processo de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/processos/" + processoDeOutro + "/posicao"), p));
            verificar(doProcesso, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/processos/9999-1999/posicao"), p));
        }
        verificar(rota, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/requerimentos/101/posicao")));
        verificar(doProcesso, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/processos/" + processoDoOperador + "/posicao")));
        // S8, bloco 6: tramitação completa, com a mesma regra de acesso (RN07) conferida antes de ler o Camunda.
        String tramitacao = "GET /api/requerimentos/{id}/tramitacao";
        String tramitacaoDoProcesso = "GET /api/processos/{numero}/tramitacao";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(tramitacao, "do interessado", p, 200, null,
                    () -> comToken(get("/api/requerimentos/101/tramitacao"), p));
            verificar(tramitacao, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202/tramitacao"), p));
            verificar(tramitacao, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/requerimentos/404/tramitacao"), p));
            verificar(tramitacaoDoProcesso, "processo do interessado", p, 200, null,
                    () -> comToken(get("/api/processos/" + processoDoOperador + "/tramitacao"), p));
            verificar(tramitacaoDoProcesso, "processo de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/processos/" + processoDeOutro + "/tramitacao"), p));
            verificar(tramitacaoDoProcesso, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/processos/9999-1999/tramitacao"), p));
        }
        verificar(tramitacao, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/requerimentos/101/tramitacao")));
        verificar(tramitacaoDoProcesso, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/processos/" + processoDoOperador + "/tramitacao")));
        // S7, bloco 10: parâmetros de exibição para qualquer perfil (só números da interface).
        String parametros = "GET /api/tramitacao/parametros";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            verificar(parametros, "parâmetros de exibição", p, 200, null,
                    () -> comToken(get("/api/tramitacao/parametros"), p));
        }
        verificar(parametros, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/tramitacao/parametros")));
    }

    /**
     * S9, bloco 6: histórico financeiro, com a mesma regra da consulta (RN07) conferida antes de ler os lançamentos. Sem
     * SIGA, o financeiro é INDISPONIVEL (200). O bloco 7 estende com a varredura de vazamento por cenário.
     */
    private void financeiro() throws Exception {
        String rota = "GET /api/requerimentos/{id}/financeiro";
        String doProcesso = "GET /api/processos/{numero}/financeiro";
        String processoDoOperador = PROCESSO_DO_OPERADOR.replace('/', '-');
        String processoDeOutro = PROCESSO_DE_OUTRO.replace('/', '-');
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(rota, "do interessado", p, 200, null, () -> comToken(get("/api/requerimentos/101/financeiro"), p));
            verificar(rota, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202/financeiro"), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/requerimentos/404/financeiro"), p));
            verificar(doProcesso, "processo do interessado", p, 200, null,
                    () -> comToken(get("/api/processos/" + processoDoOperador + "/financeiro"), p));
            verificar(doProcesso, "processo de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/processos/" + processoDeOutro + "/financeiro"), p));
            verificar(doProcesso, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/processos/9999-1999/financeiro"), p));
        }
        verificar(rota, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/requerimentos/101/financeiro")));
        verificar(doProcesso, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/processos/" + processoDoOperador + "/financeiro")));
    }

    /**
     * S10, bloco 5: painel do Gestor. O Operador leva 403 {@code PERFIL_SEM_PERMISSAO} nas duas rotas (antes de qualquer
     * leitura); Gestor e Administrador veem. Sem SIGA, o painel responde 200 (PREPARANDO ou INDISPONIVEL), nunca 5xx. A
     * atualização manual do Gestor e do Administrador vem depois da primeira consulta (que já iniciou uma leitura), então
     * cai no intervalo mínimo: 429 {@code ATUALIZACAO_RECENTE}, depois de a leitura em segundo plano terminar.
     */
    private void painel() throws Exception {
        String leitura = "GET /api/painel";
        String atualizacao = "POST /api/painel/atualizacao";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(leitura, "painel", p, op ? 403 : 200, op ? "PERFIL_SEM_PERMISSAO" : null,
                    () -> comToken(get("/api/painel"), p));
        }
        aguardarFimDaAtualizacaoDoPainel();
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar(atualizacao, "atualização manual", p, op ? 403 : 429, op ? "PERFIL_SEM_PERMISSAO" : "ATUALIZACAO_RECENTE",
                    () -> comToken(post("/api/painel/atualizacao"), p));
        }
        // S11, bloco 9: a lista por analista usa a mesma permissão (403 antes de ler nada).
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            verificar("GET /api/painel/analistas", "lista por analista", p, op ? 403 : 200,
                    op ? "PERFIL_SEM_PERMISSAO" : null, () -> comToken(get("/api/painel/analistas"), p));
            verificar("GET /api/painel/analistas/{chave}/requerimentos", "lista de um analista", p, op ? 403 : 404,
                    op ? "PERFIL_SEM_PERMISSAO" : "NAO_ENCONTRADO",
                    () -> comToken(get("/api/painel/analistas/0000000000000000/requerimentos"), p));
        }
        verificar("GET /api/painel/analistas", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/painel/analistas")));
        verificar("GET /api/painel/analistas/{chave}/requerimentos", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/painel/analistas/0000000000000000/requerimentos")));
        verificar(leitura, "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/painel")));
        verificar(atualizacao, "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(post("/api/painel/atualizacao")));
    }

    /**
     * S11, bloco 6: a caixa do analista é só do perfil Analista. Operador, Gestor e Administrador levam 403
     * {@code PERFIL_SEM_PERMISSAO} nas quatro rotas, antes de qualquer leitura; sem token, 401. O Analista (com CPF, sem CPF,
     * a própria caixa) é provado em {@code CaixaHttpTest}.
     */
    private void caixa() throws Exception {
        String corpo = json.writeValueAsString(Map.of("ids", List.of(1)));
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            verificar("GET /api/caixa", "sem caixa", p, 403, "PERFIL_SEM_PERMISSAO", () -> comToken(get("/api/caixa"), p));
            verificar("GET /api/caixa/avisos", "sem caixa", p, 403, "PERFIL_SEM_PERMISSAO",
                    () -> comToken(get("/api/caixa/avisos"), p));
            verificar("POST /api/caixa/avisos/visto", "sem caixa", p, 403, "PERFIL_SEM_PERMISSAO",
                    () -> comToken(post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content(corpo), p));
            verificar("POST /api/caixa/atualizar", "sem caixa", p, 403, "PERFIL_SEM_PERMISSAO",
                    () -> comToken(post("/api/caixa/atualizar"), p));
        }
        verificar("GET /api/caixa", "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/caixa")));
        verificar("GET /api/caixa/avisos", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/caixa/avisos")));
        verificar("POST /api/caixa/avisos/visto", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON).content(corpo)));
        verificar("POST /api/caixa/atualizar", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(post("/api/caixa/atualizar")));
    }

    /**
     * L1, bloco 6: o Analista (usuário padrão) em todos os endpoints de {@code /api}. Não é restrito ao interessado (vê
     * requerimentos de qualquer um), mas não vê sigilosos, responsável da tarefa, painel, auditoria nem configurações; só
     * ele tem caixa. Pedido de dossiê de outro usuário não existe para ele (404).
     */
    private void analista() throws Exception {
        Perfil a = Perfil.ANALISTA;
        String semPermissao = "PERFIL_SEM_PERMISSAO";
        String senha = UsuariosDeTeste.senhaDe(a.login);
        verificar("POST /api/auth/login", "login", a, 200, null, () -> mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("usuario", a.login, "senha", senha)))));
        verificar("GET /api/auth/me", "me", a, 200, null, () -> comToken(get("/api/auth/me"), a));
        verificar("POST /api/auth/senha", "própria senha, nova igual à atual", a, 400, "PARAMETRO_INVALIDO",
                () -> comToken(post("/api/auth/senha").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("senhaAtual", senha, "novaSenha", senha,
                                "confirmacao", senha))), a));
        verificar("GET /api/estado", "estado do serviço", a, 200, null, () -> comToken(get("/api/estado"), a));
        verificar("GET /api/busca", "requerimento de outro", a, 200, null,
                () -> comToken(get("/api/busca?tipo=REQUERIMENTO&numero=" + NUMERO_DE_OUTRO), a));
        verificar("GET /api/busca", "inexistente", a, 404, "NAO_ENCONTRADO",
                () -> comToken(get("/api/busca?tipo=REQUERIMENTO&numero=9999/1999"), a));
        for (long req : new long[] {101, 202}) {
            MockHttpServletResponse requerimento = verificar("GET /api/requerimentos/{id}", "req " + req, a, 200, null,
                    () -> comToken(get("/api/requerimentos/" + req), a));
            soft.assertThat(texto(requerimento)).as("responsável da tarefa oculto ao Analista").doesNotContain(SERVIDORA);
            JsonNode lista = lerJson(verificar("GET /api/requerimentos/{id}/documentos", "req " + req, a, 200, null,
                    () -> comToken(get("/api/requerimentos/" + req + "/documentos"), a)));
            JsonNode sigiloso = item(lista.get("itens"), "DOCUMENTO_PROCESSO", SIGILOSO);
            soft.assertThat(sigiloso.get("titulo").asText()).as("título mascarado").isEqualTo("Documento sigiloso");
            soft.assertThat(sigiloso.get("acessivel").asBoolean()).isFalse();
            MockHttpServletResponse zip = verificar("GET /api/requerimentos/{id}/geometrias/arquivo", "req " + req, a, 200,
                    null, () -> comToken(get("/api/requerimentos/" + req + "/geometrias/arquivo"), a));
            soft.assertThat(entradasDoZip(zip.getContentAsByteArray())).as("ZIP do Analista")
                    .doesNotContain(GEO_SIGILOSA + ".kml");
            verificar("GET /api/documentos/{origem}/{id}/conteudo", "comum req " + req, a, 200, null,
                    () -> comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + COMUM + "/conteudo?requerimento=" + req), a));
            verificar("GET /api/documentos/{origem}/{id}/conteudo", "sigiloso req " + req, a, 403, "DOCUMENTO_SIGILOSO",
                    () -> comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=" + req), a));
            verificar("POST /api/dossies", "sem sigiloso req " + req, a, 202, null,
                    () -> comToken(criacao(req, "DOCUMENTO_PROCESSO", COMUM), a));
            verificar("POST /api/dossies", "com sigiloso req " + req, a, 403, "DOCUMENTO_SIGILOSO",
                    () -> comToken(criacao(req, "DOCUMENTO_PROCESSO", COMUM, "DOCUMENTO_PROCESSO", SIGILOSO), a));
            verificar("GET /api/requerimentos/{id}/posicao", "req " + req, a, 200, null,
                    () -> comToken(get("/api/requerimentos/" + req + "/posicao"), a));
            verificar("GET /api/requerimentos/{id}/tramitacao", "req " + req, a, 200, null,
                    () -> comToken(get("/api/requerimentos/" + req + "/tramitacao"), a));
            verificar("GET /api/requerimentos/{id}/financeiro", "req " + req, a, 200, null,
                    () -> comToken(get("/api/requerimentos/" + req + "/financeiro"), a));
        }
        verificar("GET /api/requerimentos/{id}", "inexistente", a, 404, "NAO_ENCONTRADO",
                () -> comToken(get("/api/requerimentos/404"), a));
        verificar("GET /api/processos/{numero}/posicao", "processo de outro", a, 200, null,
                () -> comToken(get("/api/processos/" + PROCESSO_DE_OUTRO.replace('/', '-') + "/posicao"), a));
        verificar("GET /api/processos/{numero}/tramitacao", "processo de outro", a, 200, null,
                () -> comToken(get("/api/processos/" + PROCESSO_DE_OUTRO.replace('/', '-') + "/tramitacao"), a));
        verificar("GET /api/processos/{numero}/financeiro", "processo de outro", a, 200, null,
                () -> comToken(get("/api/processos/" + PROCESSO_DE_OUTRO.replace('/', '-') + "/financeiro"), a));
        verificar("GET /api/tramitacao/parametros", "parâmetros de exibição", a, 200, null,
                () -> comToken(get("/api/tramitacao/parametros"), a));

        // Pedidos de dossiê: o próprio vale; o de outro usuário (aqui o do Gestor, com sigiloso) não existe para ele.
        String proprio = novoPedido(a);
        String doGestor = novoPedido(Perfil.GESTOR);
        verificar("GET /api/dossies/{id}", "próprio", a, 200, null, () -> comToken(get("/api/dossies/" + proprio), a));
        verificar("GET /api/dossies/{id}", "de outro", a, 404, "NAO_ENCONTRADO", () -> comToken(get("/api/dossies/" + doGestor), a));
        verificar("GET /api/dossies/{id}/arquivo", "próprio", a, 200, null,
                () -> comToken(get("/api/dossies/" + proprio + "/arquivo"), a));
        verificar("GET /api/dossies/{id}/arquivo", "de outro", a, 404, "NAO_ENCONTRADO",
                () -> comToken(get("/api/dossies/" + doGestor + "/arquivo"), a));
        verificar("POST /api/dossies/{id}/arquivo/ticket", "de outro", a, 404, "NAO_ENCONTRADO",
                () -> comToken(post("/api/dossies/" + doGestor + "/arquivo/ticket"), a));
        verificar("POST /api/dossies/{id}/continuacao", "de outro", a, 404, "NAO_ENCONTRADO",
                () -> comToken(post("/api/dossies/" + doGestor + "/continuacao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\":\"TENTAR_NOVAMENTE\"}"), a));
        verificar("DELETE /api/dossies/{id}", "de outro", a, 404, "NAO_ENCONTRADO",
                () -> comToken(delete("/api/dossies/" + doGestor), a));
        verificar("DELETE /api/dossies/{id}", "próprio", a, 204, null, () -> comToken(delete("/api/dossies/" + proprio), a));
        JsonNode pagina = lerJson(verificar("GET /api/dossies", "lista", a, 200, null,
                () -> comToken(get("/api/dossies?tamanho=50"), a)));
        for (JsonNode item : pagina.get("conteudo")) {
            soft.assertThat(item.get("id").asText()).as("Analista não vê pedido de outro").isNotEqualTo(doGestor);
            soft.assertThat(item.has("geradoPor")).as("geradoPor oculto").isFalse();
        }

        // Só do Gestor e do Administrador, ou só do Administrador: 403 antes de qualquer leitura.
        verificar("GET /api/auditoria/documentos", "lista", a, 403, semPermissao,
                () -> comToken(get("/api/auditoria/documentos"), a));
        verificar("GET /api/auditoria/acessos", "lista", a, 403, semPermissao, () -> comToken(get("/api/auditoria/acessos"), a));
        verificar("GET /api/auditoria/{grupo}/{id}", "documentos", a, 403, semPermissao,
                () -> comToken(get("/api/auditoria/documentos/1"), a));
        verificar("GET /api/auditoria/{grupo}/exportacao", "documentos", a, 403, semPermissao,
                () -> comToken(get("/api/auditoria/documentos/exportacao"), a));
        verificar("GET /api/configuracoes/usuarios", "lista", a, 403, semPermissao,
                () -> comToken(get("/api/configuracoes/usuarios"), a));
        verificar("POST /api/configuracoes/usuarios", "corpo vazio", a, 403, semPermissao,
                () -> comToken(post("/api/configuracoes/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"), a));
        verificar("PATCH /api/configuracoes/usuarios/{id}", "redefinir senha de outro", a, 403, semPermissao,
                () -> comToken(patch("/api/configuracoes/usuarios/operador").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"" + UsuariosDeTeste.SENHA_ADMIN + "x\"}"), a));
        verificar("GET /api/configuracoes/parametros", "lista", a, 403, semPermissao,
                () -> comToken(get("/api/configuracoes/parametros"), a));
        verificar("PUT /api/configuracoes/parametros", "sem alterações", a, 403, semPermissao,
                () -> comToken(put("/api/configuracoes/parametros").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alteracoes\":{}}"), a));
        verificar("DELETE /api/configuracoes/parametros/{nome}", "desconhecido", a, 403, semPermissao,
                () -> comToken(delete("/api/configuracoes/parametros/nao.existe"), a));
        verificar("GET /api/configuracoes/conexoes", "lista", a, 403, semPermissao,
                () -> comToken(get("/api/configuracoes/conexoes"), a));
        verificar("POST /api/configuracoes/conexoes/{nome}/teste", "siga", a, 403, semPermissao,
                () -> comToken(post("/api/configuracoes/conexoes/siga/teste"), a));
        verificar("GET /api/painel", "painel", a, 403, semPermissao, () -> comToken(get("/api/painel"), a));
        verificar("POST /api/painel/atualizacao", "atualização manual", a, 403, semPermissao,
                () -> comToken(post("/api/painel/atualizacao"), a));
        verificar("GET /api/painel/analistas", "lista por analista", a, 403, semPermissao,
                () -> comToken(get("/api/painel/analistas"), a));
        verificar("GET /api/painel/analistas/{chave}/requerimentos", "lista de um analista", a, 403, semPermissao,
                () -> comToken(get("/api/painel/analistas/0000000000000000/requerimentos"), a));

        // A caixa é só dele: 200 (o analista de teste não tem CPF, então a caixa vem vazia, nunca 5xx).
        verificar("GET /api/caixa", "caixa", a, 200, null, () -> comToken(get("/api/caixa"), a));
        verificar("GET /api/caixa/avisos", "avisos", a, 200, null, () -> comToken(get("/api/caixa/avisos"), a));
        verificar("POST /api/caixa/avisos/visto", "sem avisos", a, 204, null, () -> comToken(
                post("/api/caixa/avisos/visto").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("ids", List.of(1)))), a));
        // A leitura da caixa acima já iniciou uma atualização, então a manual cai no intervalo mínimo (como no painel).
        verificar("POST /api/caixa/atualizar", "atualizar", a, 429, "ATUALIZACAO_RECENTE",
                () -> comToken(post("/api/caixa/atualizar"), a));
    }

    private static String texto(MockHttpServletResponse resposta) throws IOException {
        return resposta.getContentAsString(StandardCharsets.UTF_8);
    }

    private void aguardarFimDaAtualizacaoDoPainel()throws InterruptedException {
        long limite = System.nanoTime() + 10_000_000_000L;
        while (atualizarPainel.emAndamento() && System.nanoTime() < limite) {
            Thread.sleep(20);
        }
    }

    private void listaDeDocumentos() throws Exception {
        String rota = "GET /api/requerimentos/{id}/documentos";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            MockHttpServletResponse lista = verificar(rota, "do interessado", p, 200, null,
                    () -> comToken(get("/api/requerimentos/101/documentos"), p));
            JsonNode corpo = lerJson(lista);
            JsonNode sigiloso = item(corpo.get("itens"), "DOCUMENTO_PROCESSO", SIGILOSO);
            JsonNode pessoal = primeiroDaOrigem(corpo.get("itens"), "SCORP");
            if (op) {
                for (JsonNode mascarado : new JsonNode[] {sigiloso, pessoal}) {
                    soft.assertThat(mascarado.get("titulo").asText()).as("título mascarado").isEqualTo("Documento sigiloso");
                    soft.assertThat(mascarado.get("nomeArquivo").isNull()).as("sem nome de arquivo").isTrue();
                    soft.assertThat(mascarado.get("extensao").isNull()).as("sem extensão").isTrue();
                    soft.assertThat(mascarado.get("tipoArquivo").isNull()).as("sem tipo").isTrue();
                    soft.assertThat(mascarado.get("acessivel").asBoolean()).isFalse();
                }
                soft.assertThat(corpo.at("/totais/geometriasOmitidas").asInt()).as("geometria sigilosa omitida").isEqualTo(1);
                soft.assertThat(corpo.get("geometrias").size()).isEqualTo(1);
            } else {
                soft.assertThat(sigiloso.get("titulo").asText()).isEqualTo(TITULO_SIGILOSO);
                soft.assertThat(pessoal.get("titulo").asText()).isEqualTo(TITULO_PESSOAL);
                soft.assertThat(corpo.get("geometrias").size()).isEqualTo(2);
                soft.assertThat(corpo.at("/totais/geometriasOmitidas").asInt()).isZero();
            }
            soft.assertThat(pessoal.get("origemDescricao").asText()).isEqualTo("Pessoal · Interessado");
            verificar(rota, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202/documentos"), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/requerimentos/404/documentos"), p));
        }
    }

    private void geometrias() throws Exception {
        String rota = "GET /api/requerimentos/{id}/geometrias/arquivo";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            MockHttpServletResponse zip = verificar(rota, "do interessado", p, 200, null,
                    () -> comToken(get("/api/requerimentos/101/geometrias/arquivo"), p));
            Set<String> nomes = entradasDoZip(zip.getContentAsByteArray());
            soft.assertThat(nomes).as("ZIP de %s", p).contains("area.kml");
            soft.assertThat(nomes.contains(GEO_SIGILOSA + ".kml")).as("geometria sigilosa no ZIP de %s", p).isEqualTo(!op);
            verificar(rota, "de outro", p, op ? 403 : 200, op ? "NAO_INTERESSADO" : null,
                    () -> comToken(get("/api/requerimentos/202/geometrias/arquivo"), p));
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/requerimentos/404/geometrias/arquivo"), p));
        }
    }

    private void conteudo() throws Exception {
        String rota = "GET /api/documentos/{origem}/{id}/conteudo";
        String idPessoal = primeiroDaOrigem(lerJson(comToken(get("/api/requerimentos/101/documentos"), Perfil.GESTOR)
                .andReturn().getResponse()).get("itens"), "SCORP").get("id").asText();
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            for (long req : new long[] {101, 202}) {
                for (String doc : new String[] {COMUM, SIGILOSO}) {
                    for (String formato : new String[] {"pdf", "original"}) {
                        boolean outro = req == 202;
                        boolean sigiloso = doc.equals(SIGILOSO);
                        int status = op && outro ? 403 : op && sigiloso ? 403 : 200;
                        String codigo = op && outro ? "NAO_INTERESSADO" : op && sigiloso ? "DOCUMENTO_SIGILOSO" : null;
                        verificar(rota, "req " + req + " doc " + doc + " " + formato, p, status, codigo,
                                () -> comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + doc + "/conteudo?requerimento="
                                        + req + "&formato=" + formato), p));
                    }
                }
            }
            verificar(rota, "documento pessoal sigiloso", p, op ? 403 : 200, op ? "DOCUMENTO_SIGILOSO" : null,
                    () -> comToken(get("/api/documentos/SCORP/" + idPessoal + "/conteudo?requerimento=101"), p));
            verificar(rota, "requerimento inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + COMUM + "/conteudo?requerimento=404"), p));
            verificar(rota, "id que não é do requerimento", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + DOC_DE_OUTRO + "/conteudo?requerimento=101"), p));
        }
    }

    private void criacaoDeDossie() throws Exception {
        String rota = "POST /api/dossies";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            for (long req : new long[] {101, 202}) {
                for (boolean comSigiloso : new boolean[] {false, true}) {
                    boolean outro = req == 202;
                    int status = op && outro ? 403 : op && comSigiloso ? 403 : 202;
                    String codigo = op && outro ? "NAO_INTERESSADO" : op && comSigiloso ? "DOCUMENTO_SIGILOSO" : null;
                    String[] refs = comSigiloso ? new String[] {"DOCUMENTO_PROCESSO", COMUM, "DOCUMENTO_PROCESSO", SIGILOSO}
                            : new String[] {"DOCUMENTO_PROCESSO", COMUM};
                    verificar(rota, "req " + req + (comSigiloso ? " com sigiloso" : ""), p, status, codigo,
                            () -> comToken(criacao(req, refs), p));
                }
            }
            verificar(rota, "inexistente", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(criacao(404, "DOCUMENTO_PROCESSO", COMUM), p));
        }
    }

    /** Dono × perfil em todas as rotas de /dossies/{id}. O pedido do Gestor tem documento sigiloso. */
    private void pedidos() throws Exception {
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            for (boolean dono : new boolean[] {true, false}) {
                Perfil criador = dono ? p : (op ? Perfil.GESTOR : Perfil.OPERADOR);
                boolean permitido = dono || !op;
                String caso = dono ? "próprio" : "de " + criador;
                String id = novoPedido(criador);
                verificar("GET /api/dossies/{id}", caso, p, permitido ? 200 : 404, permitido ? null : "NAO_ENCONTRADO",
                        () -> comToken(get("/api/dossies/" + id), p));
                verificar("GET /api/dossies/{id}/arquivo", caso + " inline", p, permitido ? 200 : 404,
                        permitido ? null : "NAO_ENCONTRADO", () -> comToken(get("/api/dossies/" + id + "/arquivo"), p));
                verificar("POST /api/dossies/{id}/arquivo/ticket", caso, p, permitido ? 200 : 404,
                        permitido ? null : "NAO_ENCONTRADO", () -> comToken(post("/api/dossies/" + id + "/arquivo/ticket"), p));
                verificar("POST /api/dossies/{id}/continuacao", caso, p, permitido ? 409 : 404,
                        permitido ? "DOSSIE_SEM_DECISAO_PENDENTE" : "NAO_ENCONTRADO",
                        () -> comToken(post("/api/dossies/" + id + "/continuacao").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"acao\":\"TENTAR_NOVAMENTE\"}"), p));
                String paraCancelar = novoPedido(criador);
                verificar("DELETE /api/dossies/{id}", caso, p, permitido ? 204 : 404, permitido ? null : "NAO_ENCONTRADO",
                        () -> comToken(delete("/api/dossies/" + paraCancelar), p));
            }
            // Ticket: vale sem sessão; com a sessão de outro usuário, nunca.
            String doPerfil = novoPedido(p);
            String ticket = lerJson(comToken(post("/api/dossies/" + doPerfil + "/arquivo/ticket"), p).andReturn()
                    .getResponse()).get("ticket").asText();
            Perfil outroUsuario = op ? Perfil.GESTOR : Perfil.OPERADOR;
            verificar("GET /api/dossies/{id}/arquivo", "ticket com a sessão de outro usuário", outroUsuario, 403,
                    "TICKET_INVALIDO", () -> comToken(get("/api/dossies/" + doPerfil + "/arquivo?disposicao=attachment&ticket="
                            + ticket), outroUsuario));
            String ticket2 = lerJson(comToken(post("/api/dossies/" + doPerfil + "/arquivo/ticket"), p).andReturn()
                    .getResponse()).get("ticket").asText();
            verificar("GET /api/dossies/{id}/arquivo", "ticket sem sessão", p, 200, null,
                    () -> mvc.perform(get("/api/dossies/" + doPerfil + "/arquivo?disposicao=attachment&ticket=" + ticket2)));
        }
        verificar("GET /api/dossies/{id}", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/dossies/qualquer")));
        verificar("GET /api/dossies/{id}/arquivo", "ticket inexistente sem sessão", null, 403, "TICKET_INVALIDO",
                () -> mvc.perform(get("/api/dossies/qualquer/arquivo?disposicao=attachment&ticket=inventado")));
    }

    private void dossiesGerados() throws Exception {
        String doOperador = novoPedido(Perfil.OPERADOR);
        String doGestor = novoPedido(Perfil.GESTOR);
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            for (boolean somenteMeus : new boolean[] {false, true}) {
                JsonNode pagina = lerJson(verificar("GET /api/dossies", "somenteMeus=" + somenteMeus, p, 200, null,
                        () -> comToken(get("/api/dossies?tamanho=50&somenteMeus=" + somenteMeus), p)));
                List<String> ids = new ArrayList<>();
                pagina.get("conteudo").forEach(i -> {
                    ids.add(i.get("id").asText());
                    soft.assertThat(i.has("geradoPor")).as("geradoPor para %s", p).isEqualTo(!op);
                });
                soft.assertThat(ids.contains(doOperador)).as("%s vê o do Operador (somenteMeus=%s)", p, somenteMeus)
                        .isEqualTo(op || !somenteMeus);
                soft.assertThat(ids.contains(doGestor)).as("%s vê o do Gestor (somenteMeus=%s)", p, somenteMeus)
                        .isEqualTo(p == Perfil.GESTOR || (p == Perfil.ADMIN && !somenteMeus));
            }
        }
        verificar("GET /api/dossies", "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get("/api/dossies")));
    }

    /**
     * Auditoria (S5): acessos só para o Administrador; eventos só do interessado para o Operador, de qualquer usuário e
     * mascarados. Roda depois dos cenários acima, que deixaram eventos de sigilosos do requerimento 101 e de documentos
     * do 202. Cada id de evento vem da leitura do Gestor; a varredura final cobre estas respostas.
     */
    private void auditoria() throws Exception {
        comToken(get("/api/requerimentos/202"), Perfil.GESTOR);
        comToken(get("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=101"), Perfil.GESTOR);
        long idDeOutro = idDoEvento("CONSULTA", 202);
        long idSigiloso = idDoEvento("VISUALIZACAO", 101);
        JsonNode entradas = lerJson(comToken(get("/api/auditoria/acessos?tipo=LOGIN"), Perfil.ADMIN).andReturn().getResponse());
        long idDeAcesso = entradas.get("conteudo").get(0).get("id").asLong();
        String detalheDeAcesso = "GET /api/auditoria/{grupo}/{id}";
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean op = p == Perfil.OPERADOR;
            boolean admin = p == Perfil.ADMIN;
            String soAdmin = admin ? null : "PERFIL_SEM_PERMISSAO";
            JsonNode lista = lerJson(verificar("GET /api/auditoria/documentos", "lista", p, 200, null,
                    () -> comToken(get("/api/auditoria/documentos?tamanho=100&periodo=todo"), p)));
            soft.assertThat(lista.get("totalElementos").asInt()).as("eventos vistos por %s", p).isPositive();
            for (JsonNode evento : lista.get("conteudo")) {
                if (op) {
                    soft.assertThat(evento.get("idRequerimento").asLong()).as("Operador só vê o 101").isEqualTo(101);
                }
            }
            // O Operador vê os eventos dos seus requerimentos feitos por qualquer usuário.
            JsonNode deOutroUsuario = lerJson(verificar("GET /api/auditoria/documentos", "feitos pelo Gestor", p, 200, null,
                    () -> comToken(get("/api/auditoria/documentos?usuario=gestor&tamanho=100"), p)));
            soft.assertThat(deOutroUsuario.get("totalElementos").asInt()).as("eventos do Gestor vistos por %s", p).isPositive();
            JsonNode busca = lerJson(verificar("GET /api/auditoria/documentos", "busca por título sigiloso", p, 200, null,
                    () -> comToken(get("/api/auditoria/documentos?texto=Laudo&tamanho=100"), p)));
            soft.assertThat(busca.get("totalElementos").asInt() > 0).as("busca por sigiloso de %s", p).isEqualTo(!op);
            verificar("GET /api/auditoria/documentos", "só sigilosos", p, op ? 400 : 200, op ? "PARAMETRO_INVALIDO" : null,
                    () -> comToken(get("/api/auditoria/documentos?somenteSigilosos=true&tamanho=100"), p));
            verificar("GET /api/auditoria/acessos", "lista", p, admin ? 200 : 403, soAdmin,
                    () -> comToken(get("/api/auditoria/acessos"), p));
            verificar(detalheDeAcesso, "evento de outro interessado", p, op ? 404 : 200,
                    op ? "NAO_ENCONTRADO" : null, () -> comToken(get("/api/auditoria/documentos/" + idDeOutro), p));
            JsonNode detalhe = lerJson(verificar(detalheDeAcesso, "evento sigiloso do interessado", p, 200, null,
                    () -> comToken(get("/api/auditoria/documentos/" + idSigiloso), p)));
            soft.assertThat(detalhe.at("/documentos/0/titulo").asText()).as("título visto por %s", p)
                    .isEqualTo(op ? "Documento sigiloso" : TITULO_SIGILOSO);
            soft.assertThat(detalhe.at("/documentos/0/nomeDoArquivo").isNull()).as("arquivo mascarado para %s", p).isEqualTo(op);
            verificar(detalheDeAcesso, "evento de acessos", p, admin ? 200 : 403, soAdmin,
                    () -> comToken(get("/api/auditoria/acessos/" + idDeAcesso), p));
            verificar(detalheDeAcesso, "evento de documentos pelo grupo de acessos", p, admin ? 404 : 403,
                    admin ? "NAO_ENCONTRADO" : soAdmin, () -> comToken(get("/api/auditoria/acessos/" + idDeOutro), p));
            verificar(detalheDeAcesso, "grupo desconhecido", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/auditoria/outro/1"), p));
            String documentosCsv = "GET /api/auditoria/{grupo}/exportacao";
            MockHttpServletResponse csv = verificar(documentosCsv, "documentos", p, 200, null,
                    () -> comToken(get("/api/auditoria/documentos/exportacao?periodo=todo"), p));
            soft.assertThat(csv.getContentAsString(StandardCharsets.UTF_8).contains(TITULO_SIGILOSO))
                    .as("título sigiloso no CSV de %s", p).isEqualTo(!op);
            verificar(documentosCsv, "só sigilosos", p, op ? 400 : 200, op ? "PARAMETRO_INVALIDO" : null,
                    () -> comToken(get("/api/auditoria/documentos/exportacao?somenteSigilosos=true"), p));
            verificar(documentosCsv, "acessos", p, admin ? 200 : 403, soAdmin,
                    () -> comToken(get("/api/auditoria/acessos/exportacao"), p));
            verificar(documentosCsv, "grupo desconhecido", p, 404, "NAO_ENCONTRADO",
                    () -> comToken(get("/api/auditoria/outro/exportacao"), p));
        }
        for (String url : List.of("/api/auditoria/documentos", "/api/auditoria/acessos", "/api/auditoria/documentos/1",
                "/api/auditoria/documentos/exportacao")) {
            verificar(rotaDaAuditoria(url), "sem token", null, 401, "NAO_AUTENTICADO", () -> mvc.perform(get(url)));
        }
    }

    private static String rotaDaAuditoria(String url) {
        return url.endsWith("/exportacao") ? "GET /api/auditoria/{grupo}/exportacao"
                : url.endsWith("/1") ? "GET /api/auditoria/{grupo}/{id}" : "GET " + url;
    }

    /** O evento mais recente do tipo no requerimento, como o Gestor o vê. */
    private long idDoEvento(String tipo, long requerimento) throws Exception {
        JsonNode pagina = lerJson(comToken(get("/api/auditoria/documentos?tamanho=100&periodo=todo&tipo=" + tipo), Perfil.GESTOR)
                .andReturn().getResponse());
        for (JsonNode evento : pagina.get("conteudo")) {
            if (evento.get("idRequerimento").asLong() == requerimento) {
                return evento.get("id").asLong();
            }
        }
        throw new AssertionError("nenhum evento " + tipo + " do requerimento " + requerimento);
    }

    /** Usuários (S5, bloco 7): só Administrador, sem alterar o cadastro. O CPF/CNPJ fica mascarado até para ele. */
    private void usuarios() throws Exception {
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean admin = p == Perfil.ADMIN;
            String codigo = admin ? null : "PERFIL_SEM_PERMISSAO";
            MockHttpServletResponse lista = verificar("GET /api/configuracoes/usuarios", "lista", p, admin ? 200 : 403,
                    codigo, () -> comToken(get("/api/configuracoes/usuarios"), p));
            soft.assertThat(lista.getContentAsString()).doesNotContain(CNPJ_DO_OPERADOR, "11222333000181");
            verificar("POST /api/configuracoes/usuarios", "corpo vazio", p, admin ? 400 : 403,
                    admin ? "PARAMETRO_INVALIDO" : codigo,
                    () -> comToken(post("/api/configuracoes/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"), p));
            verificar("PATCH /api/configuracoes/usuarios/{id}", "inexistente", p, admin ? 404 : 403,
                    admin ? "NAO_ENCONTRADO" : codigo, () -> comToken(patch("/api/configuracoes/usuarios/nao.existe")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"), p));
        }
    }

    /** Parâmetros e conexões (S5, bloco 8): só Administrador, sem alterar nada. */
    private void parametrosEConexoes() throws Exception {
        for (Perfil p : PERFIS_COM_CENARIO_COMPLETO) {
            boolean admin = p == Perfil.ADMIN;
            String negado = admin ? null : "PERFIL_SEM_PERMISSAO";
            int ok = admin ? 200 : 403;
            verificar("GET /api/configuracoes/parametros", "lista", p, ok, negado,
                    () -> comToken(get("/api/configuracoes/parametros"), p));
            verificar("PUT /api/configuracoes/parametros", "sem alterações", p, ok, negado,
                    () -> comToken(put("/api/configuracoes/parametros").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"alteracoes\":{}}"), p));
            verificar("DELETE /api/configuracoes/parametros/{nome}", "desconhecido", p, admin ? 404 : 403,
                    admin ? "NAO_ENCONTRADO" : negado, () -> comToken(delete("/api/configuracoes/parametros/nao.existe"), p));
            verificar("GET /api/configuracoes/conexoes", "lista", p, ok, negado,
                    () -> comToken(get("/api/configuracoes/conexoes"), p));
            for (String nome : List.of("siga", "arquivos", "scorp", "taxas")) {
                verificar("POST /api/configuracoes/conexoes/{nome}/teste", nome, p, ok, negado,
                        () -> comToken(post("/api/configuracoes/conexoes/" + nome + "/teste"), p));
            }
            verificar("POST /api/configuracoes/conexoes/{nome}/teste", "desconhecida", p, admin ? 404 : 403,
                    admin ? "NAO_ENCONTRADO" : negado, () -> comToken(post("/api/configuracoes/conexoes/outra/teste"), p));
        }
        verificar("GET /api/configuracoes/usuarios", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/configuracoes/usuarios")));
        verificar("GET /api/configuracoes/parametros", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/configuracoes/parametros")));
        verificar("GET /api/configuracoes/conexoes", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(get("/api/configuracoes/conexoes")));
        verificar("POST /api/configuracoes/conexoes/{nome}/teste", "sem token", null, 401, "NAO_AUTENTICADO",
                () -> mvc.perform(post("/api/configuracoes/conexoes/siga/teste")));
    }

    /** O PDF, o andamento e a lista do Operador mascaram o sigiloso (capa, sumário, "Documentos não incluídos"). */
    private void mascaramentoNoPdfDoOperador() throws Exception {
        String id = novoPedido(Perfil.OPERADOR);
        MockHttpServletResponse pdf = verificar("GET /api/dossies/{id}/arquivo", "PDF do Operador", Perfil.OPERADOR, 200,
                null, () -> comToken(get("/api/dossies/" + id + "/arquivo"), Perfil.OPERADOR));
        try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.Loader.loadPDF(pdf.getContentAsByteArray())) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
            soft.assertThat(texto).contains("Documentos não incluídos", "Documento sigiloso");
            soft.assertThat(texto).doesNotContain(TITULO_SIGILOSO, "laudo-reservado", TITULO_PESSOAL, "rg-pessoal",
                    CHAVE_SIGILOSA, CHAVE_SCORP);
        }
    }

    // ------------------------------------------------------------------ varredura

    private void varreduraDeVazamento() throws IOException {
        // Consulta e dossiê mostram o interessado a quem o acessa; auditoria e configurações nunca trazem CPF/CNPJ completo.
        for (String[] registro : rotasS5) {
            soft.assertThat(registro[1]).as("resposta da auditoria ou das configurações")
                    .doesNotContain(CNPJ_DO_OPERADOR, CNPJ_DE_OUTRO, "11222333000181", "98765432000110");
        }
        List<String> sempre = new ArrayList<>(List.of(SEGREDOS));
        sempre.addAll(List.of(CHAVE_SCORP, "CHAVE-INTERNA", TOKEN_SCORP, PESSOA_SCORP, SCORP.url(), "127.0.0.1",
                "pessoa-licenciamento", diretorioTemporario().toString(), diretorioTemporario().getFileName().toString()));
        List<String> doOperador = List.of(TITULO_SIGILOSO, "laudo-reservado", DOC_DE_OUTRO, TITULO_DE_OUTRO, SERVIDORA,
                GEO_SIGILOSA, "Polígono reservado", TITULO_PESSOAL, "rg-pessoal", "Fazenda Fictícia");
        for (Map.Entry<Perfil, MockHttpServletResponse> registro : respostas) {
            MockHttpServletResponse resposta = registro.getValue();
            StringBuilder tudo = new StringBuilder(new String(resposta.getContentAsByteArray(), StandardCharsets.UTF_8));
            for (String nome : resposta.getHeaderNames()) {
                tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
            }
            // "instance" e "path" do ProblemDetail só ecoam a URL que o próprio cliente pediu
            // e a origem de um evento de auditoria é o IP do cliente (no MockMvc, 127.0.0.1), não um host interno
            String texto = tudo.toString().replaceAll("\"(instance|path)\":\"[^\"]*\"", "")
                    .replace("\"127.0.0.1\"", "");
            for (String proibido : sempre) {
                soft.assertThat(texto).as("resposta %s de %s", resposta.getStatus(), registro.getKey()).doesNotContain(proibido);
            }
            if (registro.getKey() == Perfil.OPERADOR) {
                for (String proibido : doOperador) {
                    soft.assertThat(texto).as("resposta %s do Operador", resposta.getStatus()).doesNotContain(proibido);
                }
            }
            if (registro.getKey() == Perfil.ANALISTA) {
                // Como o Operador, o Analista não vê sigiloso nem o responsável pela tarefa (mas vê requerimentos de qualquer interessado).
                for (String proibido : List.of(TITULO_SIGILOSO, "laudo-reservado", GEO_SIGILOSA, "Polígono reservado",
                        TITULO_PESSOAL, "rg-pessoal", SERVIDORA)) {
                    soft.assertThat(texto).as("resposta %s do Analista", resposta.getStatus()).doesNotContain(proibido);
                }
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    @FunctionalInterface
    private interface Chamada {
        ResultActions executar() throws Exception;
    }

    private MockHttpServletResponse verificar(String endpoint, String caso, Perfil perfil, int status, String codigo,
            Chamada chamada) throws Exception {
        cenarios++;
        cobertos.add(endpoint);
        MockHttpServletResponse resposta = chamada.executar().andReturn().getResponse();
        respostas.add(new java.util.AbstractMap.SimpleEntry<>(perfil, resposta));
        if (endpoint.contains("/api/auditoria") || endpoint.contains("/api/configuracoes")) {
            rotasS5.add(new String[] {endpoint, resposta.getContentAsString(StandardCharsets.UTF_8)});
        }
        String descricao = endpoint + " [" + caso + "] como " + (perfil == null ? "anônimo" : perfil);
        soft.assertThat(resposta.getStatus()).as(descricao).isEqualTo(status);
        if (codigo != null) {
            JsonNode corpo = lerJson(resposta);
            soft.assertThat(corpo.path("errorCode").asText()).as(descricao + " errorCode").isEqualTo(codigo);
        }
        return resposta;
    }

    private ResultActions comToken(MockHttpServletRequestBuilder requisicao, Perfil perfil) throws Exception {
        return mvc.perform(requisicao.header("Authorization", tokens.get(perfil)));
    }

    private MockHttpServletRequestBuilder criacao(long requerimento, String... referencias) throws Exception {
        List<Map<String, String>> docs = new ArrayList<>();
        for (int i = 0; i < referencias.length; i += 2) {
            docs.add(Map.of("origem", referencias[i], "id", referencias[i + 1]));
        }
        return post("/api/dossies").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", requerimento, "documentos", docs)));
    }

    /** Pedido concluído do perfil no requerimento 101; o do Gestor e o do Administrador incluem o sigiloso. */
    private String novoPedido(Perfil criador) throws Exception {
        MockHttpServletRequestBuilder corpo = criador == Perfil.OPERADOR || criador == Perfil.ANALISTA
                ? criacao(101, "DOCUMENTO_PROCESSO", COMUM)
                : criacao(101, "DOCUMENTO_PROCESSO", COMUM, "DOCUMENTO_PROCESSO", SIGILOSO);
        MockHttpServletResponse resposta = comToken(corpo, criador).andReturn().getResponse();
        assertThat(resposta.getStatus()).as("criação do pedido de apoio por %s", criador).isEqualTo(202);
        return json.readTree(resposta.getContentAsString()).get("id").asText();
    }

    private JsonNode lerJson(MockHttpServletResponse resposta) throws IOException {
        return json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode item(JsonNode itens, String origem, String id) {
        for (JsonNode item : itens) {
            if (item.get("origem").asText().equals(origem) && item.get("id").asText().equals(id)) {
                return item;
            }
        }
        throw new AssertionError("item ausente: " + origem + "/" + id);
    }

    private static JsonNode primeiroDaOrigem(JsonNode itens, String origem) {
        for (JsonNode item : itens) {
            if (item.get("origem").asText().equals(origem)) {
                return item;
            }
        }
        throw new AssertionError("nenhum item da origem " + origem);
    }

    private static Set<String> entradasDoZip(byte[] zip) throws IOException {
        Set<String> nomes = new TreeSet<>();
        try (ZipInputStream leitura = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entrada = leitura.getNextEntry(); entrada != null; entrada = leitura.getNextEntry()) {
                nomes.add(entrada.getName());
            }
        }
        return nomes;
    }
}
