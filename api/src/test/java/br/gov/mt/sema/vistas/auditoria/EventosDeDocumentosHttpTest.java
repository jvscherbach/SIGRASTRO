package br.gov.mt.sema.vistas.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.vistas.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.vistas.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.vistas.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.vistas.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.vistas.testsupport.PdfsDeTeste;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Eventos de documentos (S5, bloco 3): cada um sai uma vez, no momento certo, com os campos de docs/11; acesso negado não
 * registra; falha da auditoria não muda a resposta. SIGA e API de arquivos falsos, dados fictícios.
 */
class EventosDeDocumentosHttpTest extends BaseHttpDeDocumentos {

    private static final String DIGITOS_DO_INTERESSADO = "12345678000190";
    private static final String COMUM = "804328";
    private static final String SIGILOSO = "804329";

    @MockitoSpyBean
    RepositorioDeEventos eventos;

    private long ultimoId;

    @BeforeEach
    void marcarInicio() {
        reset(eventos);
        ultimoId = todos().stream().mapToLong(EventoDeAuditoria::id).max().orElse(0);
    }

    private List<EventoDeAuditoria> todos() {
        return new ArrayList<>(eventos.doPeriodo(GrupoDeEvento.DOCUMENTOS, null, null).toList());
    }

    /** Os eventos deste teste, do mais antigo para o mais novo. */
    private List<EventoDeAuditoria> novos() {
        return todos().stream().filter(e -> e.id() > ultimoId)
                .sorted(Comparator.comparingLong(EventoDeAuditoria::id)).toList();
    }

    private List<TipoDeEvento> tiposNovos() {
        return novos().stream().map(EventoDeAuditoria::tipo).toList();
    }

    private MockHttpServletResponse obter(String url, String usuario) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .header("X-Forwarded-For", "203.0.113.9, 198.51.100.7")).andReturn().getResponse();
    }

    private String gerar(String usuario, String... ids) throws Exception {
        List<Map<String, String>> documentos = java.util.Arrays.stream(ids)
                .map(id -> Map.of("origem", "DOCUMENTO_PROCESSO", "id", id)).toList();
        MockHttpServletResponse resposta = mvc.perform(post("/api/dossies").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .header("X-Forwarded-For", "198.51.100.8").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", 101, "documentos", documentos))))
                .andReturn().getResponse();
        assertThat(resposta.getStatus()).isEqualTo(202);
        return json.readTree(resposta.getContentAsString()).get("id").asText();
    }

    private void continuar(String id, String acao) throws Exception {
        assertThat(mvc.perform(post("/api/dossies/" + id + "/continuacao").header(HttpHeaders.AUTHORIZATION, bearer("gestor.demo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"acao\":\"" + acao + "\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(202);
    }

    private void comGeometria() {
        LocalDateTime d = LocalDateTime.of(2025, 4, 1, 10, 0);
        when(documentos.listar(anyLong())).thenReturn(List.of(
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 101, "CHAVE-INTERNA-GEO", "Polígono", "area.kml", d, null, 0),
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "2", 101, "CHAVE-INTERNA-GEO-2", "Reservado", "reservado.kml", d, null, 1)));
    }

    @Test
    void consulta_uma_vez_com_retrato_interessado_e_origem() throws Exception {
        assertThat(obter("/api/requerimentos/101", "operador.demo").getStatus()).isEqualTo(200);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.CONSULTA);
        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.usuario().login()).isEqualTo("operador.demo");
        assertThat(evento.usuario().nome()).isEqualTo("Operador Demo");
        assertThat(evento.usuario().perfil()).isEqualTo("Operador");
        assertThat(evento.idRequerimento()).isEqualTo(101);
        assertThat(evento.numeroProcesso()).isEqualTo("2825/2025");
        assertThat(evento.numeroRequerimento()).isEqualTo("3331/2025");
        assertThat(evento.detalhe()).isEqualTo("Processo 2825/2025");
        assertThat(evento.origem()).isEqualTo("198.51.100.7");
        assertThat(evento.correlationId()).isNotBlank();
        assertThat(evento.vinculo().atende(DIGITOS_DO_INTERESSADO::equals)).isTrue();
    }

    @Test
    void lista_e_busca_nao_sao_consulta() throws Exception {
        obter("/api/requerimentos/101/documentos", "gestor.demo");

        assertThat(novos()).isEmpty();
    }

    @Test
    void acesso_negado_ou_inexistente_nao_registra() throws Exception {
        assertThat(obter("/api/requerimentos/202", "operador.demo").getStatus()).isEqualTo(403);
        assertThat(obter("/api/requerimentos/404", "gestor.demo").getStatus()).isEqualTo(404);
        assertThat(obter("/api/documentos/DOCUMENTO_PROCESSO/" + COMUM + "/conteudo?requerimento=202", "operador.demo")
                .getStatus()).isEqualTo(403);
        assertThat(obter("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=101", "operador.demo")
                .getStatus()).isEqualTo(403);
        assertThat(obter("/api/documentos/DOCUMENTO_PROCESSO/999/conteudo?requerimento=101", "gestor.demo").getStatus())
                .isEqualTo(404);
        assertThat(obter("/api/requerimentos/202/geometrias/arquivo", "operador.demo").getStatus()).isEqualTo(403);

        assertThat(novos()).isEmpty();
    }

    @Test
    void visualizacao_com_documento_e_sigilo() throws Exception {
        assertThat(obter("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=101", "gestor.demo")
                .getStatus()).isEqualTo(200);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.VISUALIZACAO);
        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.sigiloso()).isTrue();
        assertThat(evento.documentos()).extracting(DocumentoDoEvento::id, DocumentoDoEvento::titulo,
                DocumentoDoEvento::nomeDoArquivo, DocumentoDoEvento::sigiloso)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(SIGILOSO, "Laudo reservado", "laudo-reservado.pdf", true));
    }

    @Test
    void dossie_e_lista_na_ordem_com_a_autoria_do_pedido_e_nao_gera_consulta() throws Exception {
        String id = gerar("gestor.demo", SIGILOSO, COMUM);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE);
        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.usuario().login()).isEqualTo("gestor.demo");
        assertThat(evento.usuario().perfil()).isEqualTo("Gestor");
        assertThat(evento.origem()).isEqualTo("198.51.100.8");
        assertThat(evento.correlationId()).isNotBlank();
        assertThat(evento.idPedido()).isEqualTo(id);
        assertThat(evento.arquivo()).isEqualTo("dossie-processo-2825-2025.pdf");
        assertThat(evento.detalhe()).isEqualTo("2 documento(s), 1 sigiloso(s)");
        assertThat(evento.sigiloso()).isTrue();
        assertThat(evento.numeroProcesso()).isEqualTo("2825/2025");
        assertThat(evento.vinculo().atende(DIGITOS_DO_INTERESSADO::equals)).isTrue();
        assertThat(evento.documentos()).extracting(DocumentoDoEvento::id).containsExactly(COMUM, SIGILOSO);
        assertThat(evento.documentos()).extracting(DocumentoDoEvento::ordem).containsExactly(1, 2);
        assertThat(evento.documentos()).extracting(DocumentoDoEvento::nomeDoArquivo)
                .containsExactly("requerimento.pdf", "laudo-reservado.pdf");
    }

    @Test
    void uma_falha_por_tentativa_e_dossie_so_no_sucesso() throws Exception {
        doThrow(new FalhaNoDocumentoException("recusado em /var/tmp/vistas/pedido/a.pdf", 1))
                .when(arquivos).baixar(eq(CHAVE_1), any(Path.class));

        String id = gerar("gestor.demo", COMUM, SIGILOSO);
        assertThat(tiposNovos()).containsExactly(TipoDeEvento.FALHA);
        EventoDeAuditoria falha = novos().get(0);
        assertThat(falha.idPedido()).isEqualTo(id);
        assertThat(falha.detalhe()).startsWith("recusado em").doesNotContain("/var/tmp");
        assertThat(falha.documentos()).extracting(DocumentoDoEvento::id).containsExactly(COMUM);
        assertThat(falha.usuario().login()).isEqualTo("gestor.demo");

        continuar(id, "TENTAR_NOVAMENTE");
        assertThat(tiposNovos()).containsExactly(TipoDeEvento.FALHA, TipoDeEvento.FALHA);

        doAnswer(chamada -> {
            Files.write((Path) chamada.getArgument(1), PdfsDeTeste.bytes(1, "agora vai"));
            return 1;
        }).when(arquivos).baixar(eq(CHAVE_1), any(Path.class));
        continuar(id, "TENTAR_NOVAMENTE");

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.FALHA, TipoDeEvento.FALHA, TipoDeEvento.DOSSIE);
    }

    @Test
    void remover_e_continuar_registra_so_o_que_entrou() throws Exception {
        doThrow(new FalhaNoDocumentoException("recusado", 1)).when(arquivos).baixar(eq(CHAVE_1), any(Path.class));
        String id = gerar("gestor.demo", COMUM, SIGILOSO);

        continuar(id, "REMOVER_E_CONTINUAR");

        EventoDeAuditoria dossie = novos().get(1);
        assertThat(dossie.tipo()).isEqualTo(TipoDeEvento.DOSSIE);
        assertThat(dossie.documentos()).extracting(DocumentoDoEvento::id).containsExactly(SIGILOSO);
        assertThat(dossie.documentos()).extracting(DocumentoDoEvento::ordem).containsExactly(1);
    }

    @Test
    void previa_nao_e_download_e_download_sai_em_nome_de_quem_baixou() throws Exception {
        String id = gerar("operador.demo", COMUM);

        assertThat(obter("/api/dossies/" + id + "/arquivo", "operador.demo").getStatus()).isEqualTo(200);
        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE);

        assertThat(obter("/api/dossies/" + id + "/arquivo?disposicao=attachment", "gestor.demo").getStatus()).isEqualTo(200);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE, TipoDeEvento.DOWNLOAD);
        EventoDeAuditoria download = novos().get(1);
        assertThat(download.usuario().login()).isEqualTo("gestor.demo");
        assertThat(download.idPedido()).isEqualTo(id);
        assertThat(download.arquivo()).isEqualTo("dossie-processo-2825-2025.pdf");
        assertThat(download.origem()).isEqualTo("198.51.100.7");
        assertThat(download.sigiloso()).isFalse();
        assertThat(download.vinculo().atende(DIGITOS_DO_INTERESSADO::equals)).isTrue();
    }

    @Test
    void download_por_ticket_sem_sessao_usa_quem_pediu_o_ticket() throws Exception {
        String id = gerar("gestor.demo", SIGILOSO);
        String ticket = json.readTree(mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket")
                .header(HttpHeaders.AUTHORIZATION, bearer("admin.demo"))).andReturn().getResponse().getContentAsString())
                .get("ticket").asText();
        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE);

        assertThat(mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment&ticket=" + ticket))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE, TipoDeEvento.DOWNLOAD);
        EventoDeAuditoria download = novos().get(1);
        assertThat(download.usuario().login()).isEqualTo("admin.demo");
        assertThat(download.usuario().perfil()).isEqualTo("Administrador");
        assertThat(download.sigiloso()).isTrue();
        assertThat(download.toString()).doesNotContain(ticket);
    }

    @Test
    void ticket_invalido_nao_registra_download() throws Exception {
        String id = gerar("gestor.demo", COMUM);

        assertThat(mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment&ticket=invalido"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.DOSSIE);
    }

    @Test
    void geometrias_quando_o_zip_e_entregue() throws Exception {
        comGeometria();

        assertThat(obter("/api/requerimentos/101/geometrias/arquivo", "gestor.demo").getStatus()).isEqualTo(200);

        assertThat(tiposNovos()).containsExactly(TipoDeEvento.GEOMETRIAS);
        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.arquivo()).isEqualTo("geometrias-processo-2825-2025.zip");
        assertThat(evento.detalhe()).isEqualTo("2 arquivo(s), 1 sigiloso(s)");
        assertThat(evento.sigiloso()).isTrue();
    }

    @Test
    void operador_nao_conta_geometria_sigilosa_que_nao_recebeu() throws Exception {
        comGeometria();

        assertThat(obter("/api/requerimentos/101/geometrias/arquivo", "operador.demo").getStatus()).isEqualTo(200);

        EventoDeAuditoria evento = novos().get(0);
        assertThat(evento.detalhe()).isEqualTo("1 arquivo(s), 0 sigiloso(s)");
        assertThat(evento.sigiloso()).isFalse();
    }

    @Test
    void falha_do_registro_nao_muda_as_respostas() throws Exception {
        String comAuditoria = obter("/api/requerimentos/101", "gestor.demo").getContentAsString();
        String id = gerar("gestor.demo", COMUM);
        doThrow(new IllegalStateException("repositório fora")).when(eventos).incluir(any());

        MockHttpServletResponse consulta = obter("/api/requerimentos/101", "gestor.demo");
        assertThat(consulta.getStatus()).isEqualTo(200);
        assertThat(consulta.getContentAsString()).isEqualTo(comAuditoria);
        assertThat(obter("/api/documentos/DOCUMENTO_PROCESSO/" + COMUM + "/conteudo?requerimento=101", "gestor.demo")
                .getStatus()).isEqualTo(200);
        assertThat(obter("/api/dossies/" + gerar("gestor.demo", COMUM), "gestor.demo").getContentAsString())
                .contains("CONCLUIDO");
        assertThat(obter("/api/dossies/" + id + "/arquivo?disposicao=attachment", "gestor.demo").getStatus()).isEqualTo(200);
        comGeometria();
        assertThat(obter("/api/requerimentos/101/geometrias/arquivo", "gestor.demo").getStatus()).isEqualTo(200);
    }

    @Test
    void nenhum_evento_guarda_chave_token_cookie_ou_caminho() throws Exception {
        obter("/api/requerimentos/101", "gestor.demo");
        obter("/api/documentos/DOCUMENTO_PROCESSO/" + SIGILOSO + "/conteudo?requerimento=101", "gestor.demo");
        String id = gerar("gestor.demo", COMUM, SIGILOSO);
        obter("/api/dossies/" + id + "/arquivo?disposicao=attachment", "gestor.demo");
        doThrow(new FalhaNoDocumentoException("Bearer " + TOKEN_DE_ARQUIVOS + " Cookie: sessao=abc", 1))
                .when(arquivos).baixar(eq(CHAVE_1), any(Path.class));
        gerar("gestor.demo", COMUM);
        comGeometria();
        obter("/api/requerimentos/101/geometrias/arquivo", "gestor.demo");

        assertThat(novos()).hasSize(6);
        assertThat(novos()).allSatisfy(evento -> assertThat(evento.toString()).doesNotContain(SEGREDOS)
                .doesNotContain(diretorioTemporario().toString(), "sessao=abc", DIGITOS_DO_INTERESSADO, "12.345.678/0001-90"));
    }
}
