package br.gov.mt.sema.rastro.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.io.IOException;
import br.gov.mt.sema.rastro.testsupport.UsuariosDeTeste;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.rastro.testsupport.ServidorHttpDeTeste;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Varredura da S5 (bloco 11): a auditoria e as configurações recebem de propósito texto com token, cookie, URL, caminho e
 * CPF/CNPJ, vindos de uma falha de download e do título de um documento; nenhuma resposta dessas rotas (corpo e
 * cabeçalhos, lista, detalhe de todo evento, CSV, usuários, parâmetros e conexões) pode devolvê-los. Credenciais em modo
 * cookie. A API de arquivos aponta para um nome reservado que não existe e o SCORP é um servidor local que devolve 500
 * com segredo no corpo. Altera cadastro e parâmetros, por isso o contexto não é reaproveitado. Dados fictícios.
 */
@DirtiesContext
class VarreduraDaAuditoriaHttpTest extends BaseHttpDeDocumentos {

    private static final String CPF = "111.444.777-35";
    private static final String CPF_DIGITOS = "11144477735";
    private static final String CNPJ_DE_OUTRO = "98.765.432/0001-10";
    private static final String HOST_INTERNO = "arquivos.interno.test";
    private static final String NOME_DO_COOKIE = "sessao_vistas";
    private static final String COOKIE = "valor-do-cookie-de-teste";
    private static final String SEGREDO_DO_SCORP = "segredo-na-resposta-do-scorp";
    private static final String COMUM = "804328";
    private static final String SIGILOSO = "804329";

    private static final ServidorHttpDeTeste SCORP;

    static {
        try {
            SCORP = ServidorHttpDeTeste.iniciar();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void conexoes(DynamicPropertyRegistry registro) {
        registro.add("rastro.scorp.url", SCORP::url);
        registro.add("rastro.arquivos.url", () -> "http://" + HOST_INTERNO + ":8443/arquivos");
        registro.add("rastro.arquivos.auth-mode", () -> "cookie");
        registro.add("rastro.arquivos.cookie-name", () -> NOME_DO_COOKIE);
    }

    @AfterAll
    static void encerrar() {
        SCORP.close();
    }

    @MockitoBean
    PessoasDoRequerimento pessoas;

    private final Map<String, MockHttpServletResponse> respostas = new LinkedHashMap<>();

    private MockHttpServletResponse obter(String url, String usuario) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(usuario))).andReturn().getResponse();
    }

    private MockHttpServletResponse guardar(String url, String usuario) throws Exception {
        MockHttpServletResponse resposta = obter(url, usuario);
        respostas.put(usuario + " " + url, resposta);
        return resposta;
    }

    private String gerar(String usuario, String... ids) throws Exception {
        List<Map<String, String>> documentosDoPedido = java.util.Arrays.stream(ids)
                .map(id -> Map.of("origem", "DOCUMENTO_PROCESSO", "id", id)).toList();
        MockHttpServletResponse resposta = mvc.perform(post("/api/dossies").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", 101, "documentos", documentosDoPedido))))
                .andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(202);
        return json.readTree(resposta.getContentAsString()).get("id").asText();
    }

    /** Eventos e dados de todos os tipos, com o texto perigoso na falha, no título e no cadastro. */
    private String produzirEventos() throws Exception {
        LocalDateTime d = LocalDateTime.of(2025, 4, 1, 10, 0);
        List<Documento> lista = new ArrayList<>(listaPadrao());
        lista.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "55", 101, "CHAVE-INTERNA-TITULO",
                "Contrato de " + CPF + " e " + CNPJ_DE_OUTRO, "contrato-" + CPF_DIGITOS + ".pdf", d, null, 0));
        when(documentos.listar(anyLong())).thenReturn(lista);
        when(pessoas.listarPessoas(anyLong())).thenReturn(List.of());
        SCORP.responderCom(troca -> troca.responder(500, SEGREDO_DO_SCORP.getBytes(StandardCharsets.UTF_8)));

        obter("/api/requerimentos/101", "operador");
        obter("/api/documentos/DOCUMENTO_PROCESSO/55/conteudo?requerimento=101", "gestor");
        obter("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=101", "gestor");

        String motivo = "Bearer " + TOKEN_DE_ARQUIVOS + " http://" + HOST_INTERNO + "/api/arquivo/" + CHAVE_1 + " em "
                + diretorioTemporario() + " do CPF " + CPF + " Cookie: " + NOME_DO_COOKIE + "=" + COOKIE;
        doThrow(new FalhaNoDocumentoException(motivo, 1)).when(arquivos).baixar(eq(CHAVE_1), any(Path.class));
        gerar("gestor", COMUM, SIGILOSO);
        String id = gerar("gestor", SIGILOSO);
        String ticket = json.readTree(mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket")
                .header(HttpHeaders.AUTHORIZATION, bearer("gestor"))).andReturn().getResponse().getContentAsString())
                .get("ticket").asText();
        assertThat(mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment&ticket=" + ticket))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        String sessaoDoAdmin = bearer("admin");
        assertThat(mvc.perform(post("/api/configuracoes/usuarios").header(HttpHeaders.AUTHORIZATION, sessaoDoAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nome", "Pessoa de Teste",
                        "login", "varredura.teste", "cpfCnpj", CPF, "perfil", "OPERADOR",
                        "senha", UsuariosDeTeste.senhaDe("varredura.teste")))))
                .andReturn().getResponse().getStatus()).isEqualTo(201);
        assertThat(mvc.perform(patch("/api/configuracoes/usuarios/varredura.teste").header(HttpHeaders.AUTHORIZATION, sessaoDoAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"OPERADOR_SIGILO\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(put("/api/configuracoes/parametros").header(HttpHeaders.AUTHORIZATION, sessaoDoAdmin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alteracoes\":{\"dossie.sumario\":false}}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        mvc.perform(delete("/api/configuracoes/parametros/dossie.sumario").header(HttpHeaders.AUTHORIZATION, sessaoDoAdmin));
        for (String conexao : List.of("siga", "arquivos", "scorp", "taxas")) {
            MockHttpServletResponse teste = mvc.perform(post("/api/configuracoes/conexoes/" + conexao + "/teste")
                    .header(HttpHeaders.AUTHORIZATION, sessaoDoAdmin)).andReturn().getResponse();
            respostas.put("teste " + conexao, teste);
        }
        return ticket;
    }

    private void lerTudo(String usuario, String grupo) throws Exception {
        String base = "/api/auditoria/" + grupo;
        JsonNode pagina = json.readTree(guardar(base + "?periodo=todo&tamanho=100", usuario)
                .getContentAsString(StandardCharsets.UTF_8));
        guardar(base + "/exportacao?periodo=todo", usuario);
        for (JsonNode evento : pagina.get("conteudo")) {
            guardar(base + "/" + evento.get("id").asLong(), usuario);
        }
    }

    private static String textoDe(MockHttpServletResponse resposta) {
        StringBuilder tudo = new StringBuilder(new String(resposta.getContentAsByteArray(), StandardCharsets.UTF_8));
        for (String nome : resposta.getHeaderNames()) {
            tudo.append('\n').append(nome).append(": ").append(String.join(",", resposta.getHeaders(nome)));
        }
        return tudo.toString();
    }

    @Test
    void nenhuma_resposta_da_auditoria_ou_das_configuracoes_traz_dado_protegido() throws Exception {
        String ticket = produzirEventos();

        lerTudo("operador", "documentos");
        lerTudo("gestor", "documentos");
        lerTudo("admin", "documentos");
        lerTudo("admin", "acessos");
        guardar("/api/configuracoes/usuarios", "admin");
        guardar("/api/configuracoes/parametros", "admin");
        guardar("/api/configuracoes/conexoes", "admin");

        assertThat(respostas.size()).isGreaterThan(30);
        List<String> proibidos = new ArrayList<>(List.of(SEGREDOS));
        proibidos.addAll(List.of(ticket, COOKIE, NOME_DO_COOKIE, SEGREDO_DO_SCORP, SCORP.url(), HOST_INTERNO, CPF, CPF_DIGITOS,
                CNPJ_DE_OUTRO, "98765432000110", CNPJ_DO_OPERADOR, "11222333000181", diretorioTemporario().toString(),
                diretorioTemporario().getFileName().toString(), "vinculo", "Bearer "));
        assertThat(respostas).allSatisfy((nome, resposta) -> assertThat(textoDe(resposta)).as(nome)
                .doesNotContain(proibidos));
        for (Map.Entry<String, MockHttpServletResponse> resposta : respostas.entrySet()) {
            if (resposta.getKey().startsWith("operador")) {
                assertThat(textoDe(resposta.getValue())).as(resposta.getKey()).doesNotContain("Laudo reservado", "laudo-reservado");
            }
        }
    }

    @Test
    void busca_na_auditoria_nao_acha_o_interessado_pelo_documento() throws Exception {
        obter("/api/requerimentos/101", "gestor");

        for (String termo : List.of("11222333000181", "11.222.333/0001-81")) {
            JsonNode pagina = json.readTree(obter("/api/auditoria/documentos?periodo=todo&texto=" + termo, "gestor")
                    .getContentAsString(StandardCharsets.UTF_8));
            assertThat(pagina.get("totalElementos").asInt()).as(termo).isZero();
        }
    }
}
