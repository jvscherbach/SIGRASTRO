package br.gov.mt.sema.rastro.documento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.gov.mt.sema.rastro.testsupport.BaseHttpDeDocumentos;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Contrato de GET /api/requerimentos/{id}/documentos (docs/07). */
class DocumentoHttpTest extends BaseHttpDeDocumentos {

    @Test
    void gestorRecebeItensETotaisComIdEmTextoEBloqueios() throws Exception {
        mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", bearer("gestor")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.itens", hasSize(6)))
                // ordem cronológica: o substituído (31/03) vem primeiro
                .andExpect(jsonPath("$.itens[0].id").value("11"))
                .andExpect(jsonPath("$.itens[0].situacao").value("SUBSTITUIDO"))
                .andExpect(jsonPath("$.itens[0].selecionavelPorPadrao").value(true))
                .andExpect(jsonPath("$.itens[1].origem").value("DOCUMENTO_PROCESSO"))
                .andExpect(jsonPath("$.itens[1].id").value("804328"))
                .andExpect(jsonPath("$.itens[1].titulo").value("Requerimento assinado"))
                .andExpect(jsonPath("$.itens[1].nomeArquivo").value("requerimento.pdf"))
                .andExpect(jsonPath("$.itens[1].origemDescricao").value("Documento do processo"))
                .andExpect(jsonPath("$.itens[1].extensao").value("pdf"))
                .andExpect(jsonPath("$.itens[1].tipoArquivo").value("PDF"))
                .andExpect(jsonPath("$.itens[1].situacao").value("VIGENTE"))
                .andExpect(jsonPath("$.itens[1].selecionavelPorPadrao").value(true))
                .andExpect(jsonPath("$.itens[1].bloqueio").doesNotExist())
                .andExpect(jsonPath("$.itens[1].idRequerimento").value(101))
                .andExpect(jsonPath("$.itens[2].titulo").value("oficio.pdf"))
                .andExpect(jsonPath("$.itens[3].sigiloso").value(true))
                .andExpect(jsonPath("$.itens[3].acessivel").value(true))
                .andExpect(jsonPath("$.itens[3].titulo").value("Laudo reservado"))
                .andExpect(jsonPath("$.itens[3].selecionavelPorPadrao").value(true))
                .andExpect(jsonPath("$.itens[4].tipoArquivo").value("PLANILHA"))
                // conversão ligada (padrão, S3): a planilha é convertível, sem bloqueio e pré-selecionada
                .andExpect(jsonPath("$.itens[4].bloqueio").doesNotExist())
                .andExpect(jsonPath("$.itens[4].selecionavelPorPadrao").value(true))
                .andExpect(jsonPath("$.itens[5].situacao").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.itens[5].bloqueio").value("INDISPONIVEL"))
                .andExpect(jsonPath("$.totais.total").value(6))
                .andExpect(jsonPath("$.totais.vigentes").value(4))
                .andExpect(jsonPath("$.totais.semEfeito").value(1))
                .andExpect(jsonPath("$.totais.naoPdf").value(1))
                .andExpect(jsonPath("$.totais.tipoNaoInformado").value(0))
                .andExpect(jsonPath("$.totais.indisponiveis").value(1))
                .andExpect(jsonPath("$.totais.sigilosos").value(1))
                .andExpect(jsonPath("$.totais.convertiveis").value(1));
    }

    @Test
    void operadorVeOSigilosoMascaradoEBloqueado() throws Exception {
        String corpo = mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", bearer("operador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[3].sigiloso").value(true))
                .andExpect(jsonPath("$.itens[3].acessivel").value(false))
                .andExpect(jsonPath("$.itens[3].titulo").value("Documento sigiloso"))
                .andExpect(jsonPath("$.itens[3].nomeArquivo").doesNotExist())
                .andExpect(jsonPath("$.itens[3].extensao").doesNotExist())
                .andExpect(jsonPath("$.itens[3].bloqueio").value("SIGILOSO"))
                .andExpect(jsonPath("$.itens[3].selecionavelPorPadrao").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("Laudo reservado", "laudo-reservado");
    }

    @Test
    void operadorDeOutroInteressadoRecebe403ENaoLeOsDocumentos() throws Exception {
        mvc.perform(get("/api/requerimentos/202/documentos").header("Authorization", bearer("operador")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("NAO_INTERESSADO"))
                .andExpect(jsonPath("$.correlationId", not(emptyOrNullString())));

        org.mockito.Mockito.verify(documentos, org.mockito.Mockito.never()).listar(202);
    }

    @Test
    void gestorListaDeQualquerInteressado() throws Exception {
        mvc.perform(get("/api/requerimentos/202/documentos").header("Authorization", bearer("gestor")))
                .andExpect(status().isOk());
        verify(documentos).listar(202);
    }

    @Test
    void requerimentoInexistenteEh404() throws Exception {
        mvc.perform(get("/api/requerimentos/404/documentos").header("Authorization", bearer("gestor")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NAO_ENCONTRADO"));
    }

    @Test
    void semTokenEh401() throws Exception {
        mvc.perform(get("/api/requerimentos/101/documentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NAO_AUTENTICADO"));
        verifyNoInteractions(documentos);
    }

    @Test
    void aRespostaNuncaTrazAChaveDoArquivoNemOToken() throws Exception {
        for (String usuario : new String[] {"gestor", "operador", "admin"}) {
            String corpo = mvc.perform(get("/api/requerimentos/101/documentos").header("Authorization", bearer(usuario)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

            for (String segredo : SEGREDOS) {
                assertThat(corpo).as("resposta de %s", usuario).doesNotContain(segredo);
            }
            assertThat(corpo).doesNotContainIgnoringCase("chave");
        }
    }
}
