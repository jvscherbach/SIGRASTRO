package br.gov.mt.sema.rastro.dossie.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException;
import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import br.gov.mt.sema.rastro.testsupport.PdfsDeTeste;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato de /api/dossies (docs/07). A geração roda na hora (fila síncrona) e os arquivos vêm de uma porta falsa. */
// Estes cenários descrevem o dossiê só-PDF da S2.2; a conversão entra no pipeline no bloco 5 da S3.
@org.springframework.test.context.TestPropertySource(properties = "rastro.conversao.habilitada=false")
class DossieHttpTest extends BaseHttpDeDocumentos {

    private String corpo(Object... referencias) throws Exception {
        List<Map<String, String>> docs = new java.util.ArrayList<>();
        for (int i = 0; i < referencias.length; i += 2) {
            docs.add(Map.of("origem", (String) referencias[i], "id", (String) referencias[i + 1]));
        }
        return json.writeValueAsString(Map.of("idRequerimento", 101, "documentos", docs));
    }

    private ResultActions criar(String usuario, long requerimento, Object... referencias) throws Exception {
        List<Map<String, String>> docs = new java.util.ArrayList<>();
        for (int i = 0; i < referencias.length; i += 2) {
            docs.add(Map.of("origem", (String) referencias[i], "id", (String) referencias[i + 1]));
        }
        return mvc.perform(post("/api/dossies").header("Authorization", bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idRequerimento", requerimento, "documentos", docs))));
    }

    private String idDe(ResultActions criacao) throws Exception {
        return json.readTree(criacao.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @org.springframework.beans.factory.annotation.Autowired
    br.gov.mt.sema.rastro.dossie.application.port.RepositorioDePedidos repositorio;

    /** Simula o fim da janela de nova tentativa (D77) sem esperar 5 minutos. */
    private void encerrarJanela(String id) {
        repositorio.buscar(id).orElseThrow().registrarAtividade(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
    }

    private String criarComSucesso(String usuario) throws Exception {
        return idDe(criar(usuario, 101, "DOCUMENTO_PROCESSO", "804328", "COMPLEMENTACAO", "900118").andExpect(status().isAccepted()));
    }

    // --- POST

    @Test
    void postDevolve202ComUuidEOCorpoSemNenhumSegredo() throws Exception {
        ResultActions r = criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804328", "COMPLEMENTACAO", "900118")
                .andExpect(status().isAccepted())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.id", not(emptyOrNullString())));
        String corpoDaResposta = r.andReturn().getResponse().getContentAsString();

        UUID.fromString(idDe(r));
        for (String segredo : SEGREDOS) {
            assertThat(corpoDaResposta).doesNotContain(segredo);
        }
    }

    @Test
    void postAceitaOIdDoDocumentoComoNumeroNoJson() throws Exception {
        mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor.demo")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idRequerimento\":101,\"documentos\":[{\"origem\":\"DOCUMENTO_PROCESSO\",\"id\":804328}]}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void postComListaVaziaOuSemCamposEh400() throws Exception {
        mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor.demo")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idRequerimento\":101,\"documentos\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.campos").isArray());
        mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor.demo")).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        mvc.perform(post("/api/dossies").header("Authorization", bearer("gestor.demo")).contentType(MediaType.APPLICATION_JSON)
                .content("não é json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        verifyNoInteractions(arquivos);
    }

    @Test
    void postComDocumentoDeOutroRequerimentoIndisponivelOuNaoPdfEh400IndicandoQual() throws Exception {
        criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "999999").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("DOCUMENTO_PROCESSO/999999")));
        criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804331").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("indisponível")));
        criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804330").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("não é PDF")));
        verifyNoInteractions(arquivos);
    }

    @Test
    void postComSigilosoParaOperadorEh403DocumentoSigilosoSemRevelarOTitulo() throws Exception {
        String resposta = criar("operador.demo", 101, "DOCUMENTO_PROCESSO", "804329").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DOCUMENTO_SIGILOSO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(resposta).doesNotContain("Laudo", "laudo");
        verifyNoInteractions(arquivos);
    }

    @Test
    void gestorPodeIncluirSigiloso() throws Exception {
        criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804329").andExpect(status().isAccepted());
    }

    @Test
    void postDeOperadorEmRequerimentoDeOutroInteressadoEh403NaoInteressado() throws Exception {
        criar("operador.demo", 202, "DOCUMENTO_PROCESSO", "804328").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(arquivos);
    }

    @Test
    void postEmRequerimentoInexistenteEh404() throws Exception {
        criar("gestor.demo", 404, "DOCUMENTO_PROCESSO", "804328").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
    }

    @Test
    void semTokenEh401EmTodasAsRotas() throws Exception {
        mvc.perform(post("/api/dossies").contentType(MediaType.APPLICATION_JSON).content(corpo("COMPLEMENTACAO", "900118")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
        mvc.perform(get("/api/dossies/qualquer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dossies/qualquer/arquivo")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/dossies/qualquer")).andExpect(status().isUnauthorized());
        verify(fila, never()).submeter(any());
    }

    // --- GET andamento

    @Test
    void andamentoConcluidoTrazItensProgressoEPaginas() throws Exception {
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.processados").value(2))
                // capa + sumário + "Documentos não incluídos" (4 dos 6 listados ficaram fora) + 2 páginas de documento
                .andExpect(jsonPath("$.paginas").value(5))
                .andExpect(jsonPath("$.erro").doesNotExist())
                .andExpect(jsonPath("$.expiraEm", not(emptyOrNullString())))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[0].origem").value("DOCUMENTO_PROCESSO"))
                .andExpect(jsonPath("$.itens[0].id").value("804328"))
                .andExpect(jsonPath("$.itens[0].titulo").value("requerimento.pdf"))
                .andExpect(jsonPath("$.itens[0].situacao").value("OK"))
                .andExpect(jsonPath("$.itens[1].situacao").value("OK"));
    }

    @Test
    void andamentoPendenteQuandoAFilaAindaNaoExecutou() throws Exception {
        Mockito.doNothing().when(fila).submeter(any(Runnable.class));
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO"))
                .andExpect(jsonPath("$.processados").value(0))
                .andExpect(jsonPath("$.itens[0].situacao").value("AGUARDANDO"));
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_SEM_ARQUIVO"));
    }

    @Test
    void andamentoAguardandoDecisaoTrazAsFalhasComMotivoENenhumArquivo() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("tempo limite excedido ao obter o arquivo"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"))
                .andExpect(jsonPath("$.processados").value(1))
                .andExpect(jsonPath("$.erro").doesNotExist())
                .andExpect(jsonPath("$.falhas", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.falhas[0].origem").value("COMPLEMENTACAO"))
                .andExpect(jsonPath("$.falhas[0].id").value("900118"))
                .andExpect(jsonPath("$.falhas[0].titulo").value("oficio.pdf"))
                .andExpect(jsonPath("$.falhas[0].motivo").value("tempo limite excedido ao obter o arquivo"))
                .andExpect(jsonPath("$.itens[1].motivo").value("tempo limite excedido ao obter o arquivo"))
                .andExpect(jsonPath("$.itens[0].motivo").doesNotExist())
                .andExpect(jsonPath("$.itens[0].situacao").value("OK"))
                .andExpect(jsonPath("$.itens[1].situacao").value("FALHA"));
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_SEM_ARQUIVO"));
    }

    @Test
    void credencialRecusadaAguardaDecisaoComCodigoESemOTokenNaResposta() throws Exception {
        Mockito.doThrow(new CredencialArquivosRecusadaException()).when(arquivos).baixar(anyString(), any(Path.class));
        String id = criarComSucesso("gestor.demo");

        String resposta = mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"))
                .andExpect(jsonPath("$.erro.codigo").value("CREDENCIAL_ARQUIVOS_RECUSADA"))
                .andExpect(jsonPath("$.erro.motivo").value("Token da API de arquivos expirado ou recusado: capture um novo token."))
                .andReturn().getResponse().getContentAsString();

        assertThat(resposta).doesNotContain(TOKEN_DE_ARQUIVOS);
    }

    @Test
    void pdfInvalidoNoRepositorioViraFalhaDoDocumento() throws Exception {
        conteudos.put(CHAVE_1, "<html>erro</html>".getBytes(StandardCharsets.UTF_8));
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"))
                .andExpect(jsonPath("$.falhas[0].motivo").value("o arquivo não é um PDF (tipo detectado: texto HTML) e a conversão está desligada"))
                .andExpect(jsonPath("$.falhas[0].id").value("804328"));
    }

    private void comDocumentoDeTipoDesconhecido() {
        List<Documento> lista = new java.util.ArrayList<>(listaPadrao());
        lista.add(Documento.deLinha(OrigemDocumento.IMOVEL, "31", 101, "CHAVE-INTERNA-SEM-TIPO", "Planta", null,
                java.time.LocalDateTime.of(2025, 4, 8, 10, 0), null, 0));
        when(documentos.listar(anyLong())).thenReturn(lista);
    }

    @Test
    void documentoDeTipoDesconhecidoQueEPdfEntraNoDossie() throws Exception {
        comDocumentoDeTipoDesconhecido();
        String id = idDe(criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804328", "IMOVEL", "31").andExpect(status().isAccepted()));

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    void documentoDeTipoDesconhecidoQueNaoEPdfFalhaSemEntregarArquivo() throws Exception {
        comDocumentoDeTipoDesconhecido();
        conteudos.put("CHAVE-INTERNA-SEM-TIPO", new byte[] {0x50, 0x4B, 0x03, 0x04, 0});
        String id = idDe(criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804328", "IMOVEL", "31").andExpect(status().isAccepted()));

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"))
                .andExpect(jsonPath("$.falhas[0].origem").value("IMOVEL"))
                .andExpect(jsonPath("$.falhas[0].motivo").value(
                        "o arquivo não é um PDF (tipo detectado: ZIP ou documento do Office (docx, xlsx))"));
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isConflict());
    }

    // --- continuação (S2.2)

    private org.springframework.test.web.servlet.ResultActions continuar(String login, String id, String acao)
            throws Exception {
        return mvc.perform(post("/api/dossies/" + id + "/continuacao").header("Authorization", bearer(login))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"acao\":\"" + acao + "\"}"));
    }

    @Test
    void tentarNovamenteResponde202EObtemSoOQueFalhou() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("tempo limite excedido ao obter o arquivo"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String id = criarComSucesso("gestor.demo");
        Mockito.clearInvocations(arquivos);
        Mockito.doAnswer(chamada -> {
            java.nio.file.Files.write(chamada.<Path>getArgument(1), br.gov.mt.sema.rastro.testsupport.PdfsDeTeste.bytes(1, "novo"));
            return 1;
        }).when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));

        String resposta = continuar("gestor.demo", id, "TENTAR_NOVAMENTE").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        verify(arquivos, Mockito.times(1)).baixar(anyString(), any(Path.class));
        verify(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.processados").value(2))
                .andExpect(jsonPath("$.falhas", hasSize(0)));
        for (String segredo : SEGREDOS) {
            assertThat(resposta).doesNotContain(segredo);
        }
    }

    @Test
    void removerEContinuarMontaSemBaixarDeNovo() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String id = criarComSucesso("gestor.demo");
        Mockito.clearInvocations(arquivos);

        continuar("gestor.demo", id, "REMOVER_E_CONTINUAR").andExpect(status().isAccepted());

        verify(arquivos, never()).baixar(anyString(), any(Path.class));
        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.itens[1].situacao").value("REMOVIDO"));
    }

    @Test
    void continuacaoSemDecisaoPendenteEh409() throws Exception {
        String id = criarComSucesso("gestor.demo");

        continuar("gestor.demo", id, "TENTAR_NOVAMENTE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_SEM_DECISAO_PENDENTE"));
    }

    @Test
    void continuacaoComAcaoInvalidaEh400() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String id = criarComSucesso("gestor.demo");

        continuar("gestor.demo", id, "REINICIAR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void continuacaoDePedidoDeOutroUsuarioEh404ParaOperador() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String id = criarComSucesso("gestor.demo");

        continuar("operador.demo", id, "TENTAR_NOVAMENTE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("PENDENTE_DECISAO"));
    }

    @Test
    void pedidoDeOutroUsuarioNaoExisteParaOperadorMasGestorVe() throws Exception {
        String idDoGestor = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + idDoGestor).header("Authorization", bearer("operador.demo")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
        mvc.perform(get("/api/dossies/" + idDoGestor + "/arquivo").header("Authorization", bearer("operador.demo")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/dossies/" + idDoGestor).header("Authorization", bearer("operador.demo")))
                .andExpect(status().isNotFound());

        String idDoOperador = idDe(criar("operador.demo", 101, "COMPLEMENTACAO", "900118").andExpect(status().isAccepted()));
        mvc.perform(get("/api/dossies/" + idDoOperador).header("Authorization", bearer("operador.demo"))).andExpect(status().isOk());
        mvc.perform(get("/api/dossies/" + idDoOperador).header("Authorization", bearer("gestor.demo"))).andExpect(status().isOk());
        mvc.perform(get("/api/dossies/" + idDoOperador).header("Authorization", bearer("admin.demo"))).andExpect(status().isOk());
    }

    @Test
    void idInexistenteEh404() throws Exception {
        mvc.perform(get("/api/dossies/" + UUID.randomUUID()).header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isNotFound());
    }

    // --- GET arquivo

    @Test
    void arquivoInlineDevolvePdfComONomeDoRf18EMantemOArquivo() throws Exception {
        String id = criarComSucesso("gestor.demo");

        MvcResult resultado = mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=inline")
                .header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("dossie-processo-2825-2025.pdf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(resultado.getResponse().getContentLength()).isEqualTo(pdf.length);
        // inline não consome o arquivo: pode ser lido de novo
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk());
    }

    @Test
    void pdfDoOperadorRegistraOsNaoIncluidosComMotivoSemRevelarOSigilosoNemChaves() throws Exception {
        String id = idDe(criar("operador.demo", 101, "DOCUMENTO_PROCESSO", "804328").andExpect(status().isAccepted()));

        byte[] pdf = mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("operador.demo")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        try (org.apache.pdfbox.pdmodel.PDDocument documento = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texto = new org.apache.pdfbox.text.PDFTextStripper().getText(documento);
            assertThat(texto).contains("1 documento incluído de 6 listados", "Documentos não incluídos",
                    "não selecionado pelo usuário", "indisponível (chave do arquivo inválida)",
                    "não é PDF: aguardando conversão", "sigiloso: o perfil de quem gerou não tem acesso",
                    "Documento sigiloso", "antigo.pdf", "oficio.pdf", "calculo.xlsx", "perdido.pdf");
            assertThat(texto).doesNotContain("Laudo reservado", "laudo-reservado");
            for (String segredo : SEGREDOS) {
                assertThat(texto).doesNotContain(segredo);
            }
        }
    }

    // --- ticket de download (S2.2, D49)

    private String emitirTicket(String usuario, String id) throws Exception {
        String resposta = mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.ticket").isString())
                .andExpect(jsonPath("$.expiraEm").isString())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("ticket").asText();
    }

    @Test
    void ticketBaixaSemCabecalhoDeSessaoUmaVezSoEDepoisOArquivoSome() throws Exception {
        String id = criarComSucesso("gestor.demo");
        String ticket = emitirTicket("gestor.demo", id);

        MvcResult resultado = mvc.perform(get("/api/dossies/" + id + "/arquivo")
                        .param("disposicao", "attachment").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        byte[] pdf = resultado.getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(resultado.getResponse().getContentLength()).isEqualTo(pdf.length);

        mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "attachment").param("ticket", ticket))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TICKET_INVALIDO"));
        // D77: na janela de nova tentativa o arquivo ainda está lá; ao fim dela, 410.
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk());
        encerrarJanela(id);
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isGone());
    }

    @Test
    void ticketDeOutroPedidoOuInventadoEh403ESemTicketContinuaExigindoSessao() throws Exception {
        String primeiro = criarComSucesso("gestor.demo");
        String segundo = criarComSucesso("gestor.demo");
        String ticket = emitirTicket("gestor.demo", primeiro);

        mvc.perform(get("/api/dossies/" + segundo + "/arquivo").param("disposicao", "attachment").param("ticket", ticket))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("TICKET_INVALIDO"));
        mvc.perform(get("/api/dossies/" + primeiro + "/arquivo").param("disposicao", "attachment").param("ticket", "inventado"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/dossies/" + primeiro + "/arquivo").param("disposicao", "attachment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketApresentadoComASessaoDeOutroUsuarioEh403() throws Exception {
        String id = criarComSucesso("gestor.demo");
        String ticket = emitirTicket("gestor.demo", id);

        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("admin.demo"))
                        .param("disposicao", "attachment").param("ticket", ticket))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("TICKET_INVALIDO"));
    }

    @Test
    void ticketSoServeParaAttachment() throws Exception {
        String id = criarComSucesso("gestor.demo");
        String ticket = emitirTicket("gestor.demo", id);

        mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "inline").param("ticket", ticket))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operadorNaoEmiteTicketDePedidoAlheio() throws Exception {
        String id = criarComSucesso("gestor.demo");

        mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket").header("Authorization", bearer("operador.demo")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/dossies/" + id + "/arquivo/ticket"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oTicketSoApareceNaRespostaDoProprioPost() throws Exception {
        String id = criarComSucesso("gestor.demo");
        String ticket = emitirTicket("gestor.demo", id);

        String andamento = mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.entregaPorTicket").value(false))
                .andExpect(jsonPath("$.tamanhoBytes").isNumber())
                .andReturn().getResponse().getContentAsString();
        MvcResult arquivo = mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "attachment")
                .param("ticket", ticket)).andReturn();
        String repetido = mvc.perform(get("/api/dossies/" + id + "/arquivo").param("disposicao", "attachment")
                .param("ticket", ticket)).andReturn().getResponse().getContentAsString();

        assertThat(andamento).doesNotContain(ticket);
        assertThat(repetido).doesNotContain(ticket);
        assertThat(new String(arquivo.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1)).doesNotContain(ticket);
        arquivo.getResponse().getHeaderNames().forEach(nome ->
                assertThat(arquivo.getResponse().getHeader(nome)).doesNotContain(ticket));
    }

    @Test
    void arquivoAttachmentBaixaDeNovoNaJanelaEDepoisEh410() throws Exception {
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk());
        // "Tentar novamente" (D77): o navegador pode não ter gravado; o arquivo fica 5 minutos.
        mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=attachment").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("dossie-processo-2825-2025.pdf")));

        encerrarJanela(id);
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("DOSSIE_EXPIRADO"));
        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("EXPIRADO"));
    }

    @Test
    void disposicaoInvalidaEh400() throws Exception {
        String id = criarComSucesso("gestor.demo");

        mvc.perform(get("/api/dossies/" + id + "/arquivo?disposicao=outra").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
    }

    // --- DELETE

    @Test
    void deleteDescartaOArquivoECancela() throws Exception {
        String id = criarComSucesso("gestor.demo");

        mvc.perform(delete("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(jsonPath("$.status").value("CANCELADO"));
        mvc.perform(get("/api/dossies/" + id + "/arquivo").header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/dossies/" + id).header("Authorization", bearer("gestor.demo")))
                .andExpect(status().isNoContent());
    }

    // --- nada vaza

    @Test
    void nenhumaRespostaDeDossieTrazChaveOuToken() throws Exception {
        Mockito.doThrow(new FalhaNoDocumentoException("arquivo não encontrado no repositório"))
                .when(arquivos).baixar(Mockito.eq(CHAVE_2), any(Path.class));
        String bom = criarComSucesso("gestor.demo");
        String ok = idDe(criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "804328").andExpect(status().isAccepted()));

        String[] rotas = {"/api/dossies/" + bom, "/api/dossies/" + ok};
        for (String rota : rotas) {
            String corpoDaResposta = mvc.perform(get(rota).header("Authorization", bearer("gestor.demo")))
                    .andReturn().getResponse().getContentAsString();
            for (String segredo : SEGREDOS) {
                assertThat(corpoDaResposta).doesNotContain(segredo);
            }
        }
        String erro = criar("gestor.demo", 101, "DOCUMENTO_PROCESSO", "999999").andReturn().getResponse().getContentAsString();
        for (String segredo : SEGREDOS) {
            assertThat(erro).doesNotContain(segredo);
        }
    }
}
