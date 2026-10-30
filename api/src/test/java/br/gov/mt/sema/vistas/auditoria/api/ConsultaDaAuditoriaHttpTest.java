package br.gov.mt.sema.vistas.auditoria.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.vistas.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.NovoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.vistas.auditoria.domain.UsuarioDoEvento;
import br.gov.mt.sema.vistas.testsupport.BaseHttpDeDocumentos;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

/**
 * Consulta da auditoria (RF21 a RF24): perfil, vínculo, mascaramento, filtros, indicadores, detalhe e CSV. Cada teste
 * avança o relógio além de 30 dias: "7d" e "30d" só enxergam os eventos dele. Dados fictícios.
 */
@Import(ConsultaDaAuditoriaHttpTest.Relogio.class)
@TestPropertySource(properties = "vistas.auditoria.csv-max-linhas=5")
class ConsultaDaAuditoriaHttpTest extends BaseHttpDeDocumentos {

    private static final String DOCS = "/api/auditoria/documentos";
    private static final String ACESSOS = "/api/auditoria/acessos";
    private static final String CNPJ_DE_OUTRO = "98.765.432/0001-10";
    private static final String DIGITOS_DO_OPERADOR = "12345678000190";
    private static final String DIGITOS_DE_OUTRO = "98765432000110";
    private static final UsuarioDoEvento GESTOR = new UsuarioDoEvento("gestor.demo", "Gestor Demo", "Gestor");
    private static final UsuarioDoEvento ADMIN = new UsuarioDoEvento("admin.demo", "Admin Demo", "Administrador");
    private static final String TITULO_SIGILOSO = "Laudo Ambiental Reservado";
    private static final String ARQUIVO_SIGILOSO = "laudo-reservado-final.pdf";
    private static final DocumentoDoEvento SIGILOSO =
            new DocumentoDoEvento("Requerimento", "20", TITULO_SIGILOSO, ARQUIVO_SIGILOSO, true, 2);
    private static final DocumentoDoEvento COMUM =
            new DocumentoDoEvento("Requerimento", "10", "Licença prévia", "licenca.pdf", false, 1);

    static final class RelogioControlado extends Clock {

        private volatile Instant agora = Instant.parse("2026-03-10T15:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.ofHours(-4);
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    @TestConfiguration
    static class Relogio {

        @Bean
        @Primary
        RelogioControlado relogioDeTeste() {
            return new RelogioControlado();
        }
    }

    @Autowired
    RelogioControlado relogio;

    @Autowired
    RegistrarEvento registrar;

    @BeforeEach
    void avancar() {
        relogio.avancar(Duration.ofDays(40));
    }

    private void doOperador(TipoDeEvento tipo, UsuarioDoEvento usuario, java.util.function.UnaryOperator<NovoEvento.Construtor> ajuste) {
        registrar.registrar(ajuste.apply(NovoEvento.de(tipo, usuario).requerimento(101L, "2825/2025", "3331/2025")
                .interessado(CNPJ_DO_OPERADOR).pedido("pedido-1").arquivo("dossie-processo-2825-2025.pdf")
                .detalhe("Processo 2825/2025").origem("198.51.100.7").correlacao("correlacao-1")
                .documentos(List.of(COMUM))).criar());
    }

    private void deOutro(TipoDeEvento tipo) {
        registrar.registrar(NovoEvento.de(tipo, GESTOR).requerimento(202L, "88/2024", "7777/2024")
                .interessado(CNPJ_DE_OUTRO).pedido("pedido-2").arquivo("dossie-processo-88-2024.pdf")
                .detalhe("Processo 88/2024").documentos(List.of(COMUM)).criar());
    }

    /** Falha em documento sigiloso do Operador, com o motivo citando o documento. */
    private void falhaSigilosa() {
        doOperador(TipoDeEvento.FALHA, GESTOR, e -> e.documentos(List.of(SIGILOSO))
                .detalhe("não foi possível ler " + TITULO_SIGILOSO));
    }

    private MockHttpServletResponse chamar(String url, String usuario) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(usuario))).andReturn().getResponse();
    }

    private JsonNode lista(String url, String usuario) throws Exception {
        MockHttpServletResponse resposta = chamar(url, usuario);
        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(200);
        return json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> tipos(JsonNode pagina) {
        List<String> tipos = new ArrayList<>();
        pagina.get("conteudo").forEach(e -> tipos.add(e.get("tipo").asText()));
        return tipos;
    }

    private String csv(String url, String usuario) throws Exception {
        return chamar(url, usuario).getContentAsString(StandardCharsets.UTF_8);
    }

    private static void semDadoProtegido(MockHttpServletResponse resposta) throws Exception {
        String corpo = resposta.getContentAsString(StandardCharsets.UTF_8);
        String cabecalhos = resposta.getHeaderNames().stream()
                .map(n -> n + ":" + String.join(",", resposta.getHeaders(n))).toList().toString();
        assertThat(corpo + cabecalhos).doesNotContain(DIGITOS_DO_OPERADOR, DIGITOS_DE_OUTRO, CNPJ_DO_OPERADOR,
                CNPJ_DE_OUTRO, "vinculo", "chave");
    }

    // ---------------------------------------------------------------- perfil e vínculo

    @Test
    void gestor_e_administrador_veem_todos_os_eventos_de_documentos() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        deOutro(TipoDeEvento.CONSULTA);

        assertThat(lista(DOCS, "gestor.demo").get("totalElementos").asInt()).isEqualTo(2);
        assertThat(lista(DOCS, "admin.demo").get("totalElementos").asInt()).isEqualTo(2);
    }

    @Test
    void operador_ve_eventos_de_qualquer_usuario_so_dos_seus_requerimentos() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        deOutro(TipoDeEvento.CONSULTA);

        JsonNode pagina = lista(DOCS, "operador.demo");

        assertThat(pagina.get("totalElementos").asInt()).isEqualTo(1);
        JsonNode evento = pagina.get("conteudo").get(0);
        assertThat(evento.get("usuario").get("login").asText()).isEqualTo("gestor.demo");
        assertThat(evento.get("idRequerimento").asInt()).isEqualTo(101);
    }

    @Test
    void acessos_so_para_o_administrador() throws Exception {
        for (String usuario : List.of("operador.demo", "gestor.demo")) {
            for (String url : List.of(ACESSOS, "/api/auditoria/acessos/1", "/api/auditoria/acessos/exportacao")) {
                MockHttpServletResponse resposta = chamar(url, usuario);
                assertThat(resposta.getStatus()).as(url).isEqualTo(403);
                assertThat(resposta.getContentAsString()).contains("PERFIL_SEM_PERMISSAO");
            }
        }
        assertThat(chamar(ACESSOS, "admin.demo").getStatus()).isEqualTo(200);
    }

    @Test
    void grupo_desconhecido_e_sem_sessao() throws Exception {
        assertThat(chamar("/api/auditoria/outro/1", "admin.demo").getStatus()).isEqualTo(404);
        assertThat(chamar("/api/auditoria/outro/exportacao", "admin.demo").getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get(DOCS)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    // ---------------------------------------------------------------- mascaramento

    @Test
    void mascaramento_em_lista_detalhe_e_csv_para_o_operador() throws Exception {
        falhaSigilosa();

        MockHttpServletResponse listaResposta = chamar(DOCS, "operador.demo");
        JsonNode evento = json.readTree(listaResposta.getContentAsString(StandardCharsets.UTF_8)).get("conteudo").get(0);
        long id = evento.get("id").asLong();
        MockHttpServletResponse detalhe = chamar(DOCS + "/" + id, "operador.demo");
        MockHttpServletResponse exportacao = chamar(DOCS + "/exportacao", "operador.demo");

        assertThat(evento.get("sigiloso").asBoolean()).isTrue();
        for (MockHttpServletResponse resposta : List.of(listaResposta, detalhe, exportacao)) {
            assertThat(resposta.getContentAsString(StandardCharsets.UTF_8))
                    .doesNotContain(TITULO_SIGILOSO, ARQUIVO_SIGILOSO, "laudo");
        }
        JsonNode documento = json.readTree(detalhe.getContentAsString(StandardCharsets.UTF_8)).get("documentos").get(0);
        assertThat(documento.get("titulo").asText()).isEqualTo("Documento sigiloso");
        assertThat(documento.get("nomeDoArquivo").isNull()).isTrue();
        assertThat(documento.get("sigiloso").asBoolean()).isTrue();
        assertThat(exportacao.getContentAsString(StandardCharsets.UTF_8)).contains("Documento sigiloso");
    }

    @Test
    void gestor_ve_o_documento_sigiloso() throws Exception {
        falhaSigilosa();

        JsonNode evento = lista(DOCS, "gestor.demo").get("conteudo").get(0);
        JsonNode detalhe = lista(DOCS + "/" + evento.get("id").asLong(), "gestor.demo");

        assertThat(evento.get("detalhe").asText()).contains(TITULO_SIGILOSO);
        assertThat(detalhe.get("documentos").get(0).get("nomeDoArquivo").asText()).isEqualTo(ARQUIVO_SIGILOSO);
    }

    @Test
    void busca_do_operador_nao_encontra_documento_sigiloso() throws Exception {
        falhaSigilosa();
        doOperador(TipoDeEvento.VISUALIZACAO, GESTOR, e -> e.documentos(List.of(COMUM, SIGILOSO)));

        for (String termo : List.of("Reservado", "laudo", "reservado-final")) {
            assertThat(lista(DOCS + "?texto=" + termo, "operador.demo").get("totalElementos").asInt()).as(termo).isZero();
            assertThat(lista(DOCS + "?texto=" + termo, "gestor.demo").get("totalElementos").asInt()).as(termo).isEqualTo(2);
        }
        assertThat(lista(DOCS + "?texto=licenca", "operador.demo").get("totalElementos").asInt()).isEqualTo(1);
    }

    @Test
    void filtro_somente_sigilosos_e_recusado_para_o_operador() throws Exception {
        for (String url : List.of(DOCS + "?somenteSigilosos=true", DOCS + "/exportacao?somenteSigilosos=true")) {
            MockHttpServletResponse resposta = chamar(url, "operador.demo");
            assertThat(resposta.getStatus()).isEqualTo(400);
            assertThat(resposta.getContentAsString()).contains("PARAMETRO_INVALIDO", "somenteSigilosos");
        }
        assertThat(chamar(DOCS + "?somenteSigilosos=false", "operador.demo").getStatus()).isEqualTo(200);
    }

    // ---------------------------------------------------------------- filtros

    @Test
    void periodo() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        relogio.avancar(Duration.ofDays(10));
        doOperador(TipoDeEvento.VISUALIZACAO, GESTOR, e -> e);
        relogio.avancar(Duration.ofDays(2));
        doOperador(TipoDeEvento.DOWNLOAD, GESTOR, e -> e);

        assertThat(tipos(lista(DOCS + "?periodo=hoje", "gestor.demo"))).containsExactly("DOWNLOAD");
        assertThat(tipos(lista(DOCS, "gestor.demo"))).containsExactly("DOWNLOAD", "VISUALIZACAO");
        assertThat(tipos(lista(DOCS + "?periodo=7d", "gestor.demo"))).containsExactly("DOWNLOAD", "VISUALIZACAO");
        assertThat(tipos(lista(DOCS + "?periodo=30d", "gestor.demo")))
                .containsExactly("DOWNLOAD", "VISUALIZACAO", "CONSULTA");
        assertThat(lista(DOCS + "?periodo=todo", "gestor.demo").get("totalElementos").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(chamar(DOCS + "?periodo=ontem", "gestor.demo").getStatus()).isEqualTo(400);
    }

    @Test
    void hoje_comeca_a_meia_noite_de_cuiaba() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        // Passa da meia-noite em Cuiabá por poucos minutos, ainda dentro de 24 h do primeiro evento.
        ZonedDateTime agora = relogio.instant().atZone(ZoneId.of("America/Cuiaba"));
        relogio.avancar(Duration.between(agora, agora.toLocalDate().plusDays(1).atStartOfDay(agora.getZone())).plusMinutes(5));
        doOperador(TipoDeEvento.VISUALIZACAO, GESTOR, e -> e);

        assertThat(tipos(lista(DOCS + "?periodo=hoje", "gestor.demo"))).containsExactly("VISUALIZACAO");
    }

    @Test
    void texto_tipo_usuario_sigilosos_e_ordem() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        doOperador(TipoDeEvento.VISUALIZACAO, ADMIN, e -> e.documentos(List.of(SIGILOSO)));
        deOutro(TipoDeEvento.DOSSIE);

        assertThat(tipos(lista(DOCS, "gestor.demo"))).containsExactly("DOSSIE", "VISUALIZACAO", "CONSULTA");
        assertThat(tipos(lista(DOCS + "?tipo=CONSULTA", "gestor.demo"))).containsExactly("CONSULTA");
        assertThat(tipos(lista(DOCS + "?usuario=ADMIN.DEMO", "gestor.demo"))).containsExactly("VISUALIZACAO");
        assertThat(tipos(lista(DOCS + "?somenteSigilosos=true", "gestor.demo"))).containsExactly("VISUALIZACAO");
        assertThat(tipos(lista(DOCS + "?texto=88/2024", "gestor.demo"))).containsExactly("DOSSIE");
        assertThat(tipos(lista(DOCS + "?texto=visualizacao", "gestor.demo"))).containsExactly("VISUALIZACAO");
        assertThat(chamar(DOCS + "?tipo=INEXISTENTE", "gestor.demo").getStatus()).isEqualTo(400);
        assertThat(chamar(DOCS + "?tipo=LOGIN", "gestor.demo").getStatus()).isEqualTo(400);
    }

    @Test
    void paginacao() throws Exception {
        for (int i = 0; i < 5; i++) {
            doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        }

        JsonNode segunda = lista(DOCS + "?pagina=1&tamanho=2", "gestor.demo");

        assertThat(segunda.get("conteudo")).hasSize(2);
        assertThat(segunda.get("pagina").asInt()).isEqualTo(1);
        assertThat(segunda.get("tamanho").asInt()).isEqualTo(2);
        assertThat(segunda.get("totalElementos").asInt()).isEqualTo(5);
        assertThat(segunda.get("totalPaginas").asInt()).isEqualTo(3);
        assertThat(lista(DOCS + "?pagina=2&tamanho=2", "gestor.demo").get("conteudo")).hasSize(1);
        assertThat(chamar(DOCS + "?tamanho=101", "gestor.demo").getStatus()).isEqualTo(400);
        assertThat(chamar(DOCS + "?tamanho=0", "gestor.demo").getStatus()).isEqualTo(400);
        assertThat(chamar(DOCS + "?pagina=-1", "gestor.demo").getStatus()).isEqualTo(400);
    }

    @Test
    void indicadores_de_documentos_seguem_o_periodo_e_o_perfil_e_nao_os_demais_filtros() throws Exception {
        doOperador(TipoDeEvento.DOSSIE, GESTOR, e -> e);
        doOperador(TipoDeEvento.VISUALIZACAO, ADMIN, e -> e.documentos(List.of(SIGILOSO)));
        deOutro(TipoDeEvento.DOSSIE);

        JsonNode gestor = lista(DOCS + "?tipo=VISUALIZACAO", "gestor.demo").get("indicadores");
        JsonNode operador = lista(DOCS, "operador.demo").get("indicadores");

        assertThat(gestor.get("dossiesGerados").asInt()).isEqualTo(2);
        assertThat(gestor.get("eventosComSigilosos").asInt()).isEqualTo(1);
        assertThat(gestor.get("usuarios").asInt()).isEqualTo(2);
        assertThat(gestor.has("entradas")).isFalse();
        assertThat(operador.get("dossiesGerados").asInt()).isEqualTo(1);
    }

    @Test
    void acessos_com_alteracoes_e_indicadores() throws Exception {
        registrar.registrar(NovoEvento.de(TipoDeEvento.USUARIO, ADMIN).origem("198.51.100.7")
                .alteracoes(List.of(new AlteracaoDoEvento("Perfil", "Operador", "Gestor"))).criar());
        registrar.registrar(NovoEvento.de(TipoDeEvento.LOGIN, GESTOR).criar());

        JsonNode pagina = lista(ACESSOS + "?tipo=USUARIO", "admin.demo");
        JsonNode detalhe = lista("/api/auditoria/acessos/" + pagina.get("conteudo").get(0).get("id").asLong(), "admin.demo");
        JsonNode indicadores = pagina.get("indicadores");

        assertThat(detalhe.get("alteracoes").get(0).get("valorNovo").asText()).isEqualTo("Gestor");
        assertThat(detalhe.get("origem").asText()).isEqualTo("198.51.100.7");
        assertThat(indicadores.get("alteracoes").asInt()).isEqualTo(1);
        assertThat(indicadores.get("entradas").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(indicadores.has("dossiesGerados")).isFalse();
    }

    // ---------------------------------------------------------------- detalhe

    @Test
    void detalhe_com_origem_documentos_e_sem_vinculo() throws Exception {
        doOperador(TipoDeEvento.DOSSIE, GESTOR, e -> e.documentos(List.of(COMUM, SIGILOSO)));
        long id = lista(DOCS, "gestor.demo").get("conteudo").get(0).get("id").asLong();

        MockHttpServletResponse resposta = chamar(DOCS + "/" + id, "gestor.demo");

        JsonNode detalhe = json.readTree(resposta.getContentAsString(StandardCharsets.UTF_8));
        assertThat(detalhe.get("origem").asText()).isEqualTo("198.51.100.7");
        assertThat(detalhe.get("correlationId").asText()).isEqualTo("correlacao-1");
        assertThat(detalhe.get("documentos")).hasSize(2);
        assertThat(detalhe.get("documentos").get(1).get("sigiloso").asBoolean()).isTrue();
        assertThat(detalhe.get("rotulo").asText()).isEqualTo("Dossiê gerado");
        semDadoProtegido(resposta);
    }

    @Test
    void detalhe_de_evento_alheio_ou_de_outro_grupo_e_404() throws Exception {
        deOutro(TipoDeEvento.CONSULTA);
        long id = lista(DOCS, "gestor.demo").get("conteudo").get(0).get("id").asLong();

        assertThat(chamar(DOCS + "/" + id, "operador.demo").getStatus()).isEqualTo(404);
        assertThat(chamar(ACESSOS + "/" + id, "admin.demo").getStatus()).isEqualTo(404);
        assertThat(chamar(DOCS + "/99999999", "gestor.demo").getStatus()).isEqualTo(404);
    }

    // ---------------------------------------------------------------- CSV

    @Test
    void csv_com_bom_cabecalho_e_cabecalhos_http() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);

        MockHttpServletResponse resposta = chamar(DOCS + "/exportacao", "gestor.demo");
        byte[] bytes = resposta.getContentAsByteArray();

        assertThat(resposta.getStatus()).isEqualTo(200);
        assertThat(resposta.getContentType()).startsWith("text/csv");
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(resposta.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .matches("attachment; filename=\"auditoria-documentos-\\d{8}\\.csv\"");
        assertThat(resposta.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(resposta.getHeader("X-Vistas-Truncado")).isNull();
        String[] linhas = new String(bytes, StandardCharsets.UTF_8).substring(1).split("\r\n");
        assertThat(linhas).hasSize(2);
        assertThat(linhas[0]).startsWith("\"Código\";\"Data e hora\";\"Usuário\"").contains("\"Sigiloso\"");
        assertThat(linhas[1]).contains("\"Gestor Demo\"", "\"Consulta\"", "\"2825/2025\"", "\"Não\"");
        semDadoProtegido(resposta);
    }

    @Test
    void csv_protege_contra_formula_aspas_separador_e_acentos() throws Exception {
        doOperador(TipoDeEvento.CONSULTA, new UsuarioDoEvento("@x", "=1+1", "+soma"),
                e -> e.detalhe("-2; \"aspas\" e ação").documentos(List.of()).requerimento(101L, "=HYPERLINK(1)", "@cmd"));

        String conteudo = csv(DOCS + "/exportacao?texto=aspas", "gestor.demo");

        assertThat(conteudo).contains("\"'=1+1\"", "\"'@x\"", "\"'+soma\"", "\"'=HYPERLINK(1)\"", "\"'@cmd\"");
        assertThat(conteudo).contains("\"'-2; \"\"aspas\"\" e ação\"");
        assertThat(conteudo.split("\r\n")).hasSize(2);
    }

    @Test
    void csv_segue_filtros_e_acesso_e_trunca_com_aviso() throws Exception {
        deOutro(TipoDeEvento.CONSULTA);
        for (int i = 0; i < 6; i++) {
            doOperador(TipoDeEvento.CONSULTA, GESTOR, e -> e);
        }

        MockHttpServletResponse truncado = chamar(DOCS + "/exportacao", "gestor.demo");
        MockHttpServletResponse doOperador = chamar(DOCS + "/exportacao?tipo=DOSSIE", "operador.demo");

        assertThat(truncado.getHeader("X-Vistas-Truncado")).isEqualTo("true");
        assertThat(truncado.getContentAsString(StandardCharsets.UTF_8).split("\r\n")).hasSize(6);
        assertThat(doOperador.getHeader("X-Vistas-Truncado")).isNull();
        assertThat(doOperador.getContentAsString(StandardCharsets.UTF_8).split("\r\n")).hasSize(1);
    }

    @Test
    void csv_de_acessos_nao_tem_colunas_de_processo() throws Exception {
        registrar.registrar(NovoEvento.de(TipoDeEvento.USUARIO, ADMIN)
                .alteracoes(List.of(new AlteracaoDoEvento("Perfil", "Operador", "Gestor"))).criar());

        String conteudo = csv(ACESSOS.replace("acessos", "acessos/exportacao") + "?tipo=USUARIO", "admin.demo");

        assertThat(conteudo).doesNotContain("Processo", "Sigiloso").contains("Perfil: Operador -> Gestor");
    }

    @Test
    void nenhuma_resposta_traz_vinculo() throws Exception {
        doOperador(TipoDeEvento.DOSSIE, GESTOR, e -> e);

        semDadoProtegido(chamar(DOCS, "gestor.demo"));
        semDadoProtegido(chamar(DOCS + "/exportacao", "gestor.demo"));
        semDadoProtegido(chamar(DOCS, "operador.demo"));
    }
}
