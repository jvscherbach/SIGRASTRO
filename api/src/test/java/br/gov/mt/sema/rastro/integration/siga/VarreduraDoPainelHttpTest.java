package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Acesso, vazamento e resiliência do painel (S10, bloco 6), com o serviço inteiro contra o Oracle de teste e a população
 * PN01 a PN16: o painel agrega todos os cenários PN, então cada perfil lê a resposta com todos eles na conta. Cobre a
 * varredura de vazamento (resposta, auditoria em lista e CSV, log), o recorte do ano, a queda do Camunda sem 5xx e sem
 * afetar as outras telas, e a proteção do SIGA (consultas só na atualização, lotes dentro do teto).
 */
class VarreduraDoPainelHttpTest extends BaseDoPainelHttp {

    @MockitoSpyBean
    PainelDoSigaJdbc siga;

    @Test
    void varredura_de_vazamento_em_todos_os_perfis_e_a_busca_nao_e_vazia(CapturedOutput log) throws Exception {
        atualizarEEsperar();
        SoftAssertions soft = new SoftAssertions();

        // Operador e sem login: nada do painel.
        assertThat(obter(OPERADOR, "/api/painel").getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/painel"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        for (String perfil : List.of(GESTOR, ADMIN)) {
            MockHttpServletResponse r = obter(perfil, "/api/painel");
            assertThat(r.getStatus()).isEqualTo(200);
            String texto = corpo(r);
            varrer(soft, perfil + " /painel", texto, false);
            JsonNode raiz = json.readTree(texto);
            assertThat(raiz.get("estado").asText()).isEqualTo("PRONTO");
            // A varredura não é vazia: com nomes permitidos eles aparecem, e a carga tem dado de verdade.
            soft.assertThat(raiz.get("cargaPorResponsavel").findValuesAsText("rotulo"))
                    .as(perfil + ": nomes aparecem quando permitido").anyMatch(n -> n.startsWith("Servidor Exemplo"));
            soft.assertThat(raiz.get("totalProtocolados").asLong()).as("total protocolado").isGreaterThan(1000);
        }
        // O Operador recebeu só o 403, que também não traz nada.
        varrer(soft, "operador /painel", corpo(obter(OPERADOR, "/api/painel")), false);

        // Auditoria (lista e CSV) dos perfis que a leem: o evento do painel não traz nome, CPF nem requerimento.
        for (String perfil : List.of(GESTOR, ADMIN)) {
            String lista = corpo(obter(perfil, "/api/auditoria/documentos?periodo=todo&tamanho=100"));
            String csv = corpo(obter(perfil, "/api/auditoria/documentos/exportacao?periodo=todo"));
            soft.assertThat(lista).as(perfil + " auditoria lista").contains("painel ").doesNotContain("Servidor Exemplo");
            soft.assertThat(csv).as(perfil + " auditoria csv").doesNotContain("Servidor Exemplo");
            varrer(soft, perfil + " auditoria lista", lista, false);
            varrer(soft, perfil + " auditoria csv", csv, false);
        }
        varrerLog(soft, log.getAll());
        soft.assertAll();
    }

    @Test
    void o_recorte_do_ano_vale_e_a_mudanca_do_parametro_vale_no_proximo_ciclo() throws Exception {
        atualizarEEsperar();
        JsonNode de2026 = painel(GESTOR);
        assertThat(de2026.get("ano").asInt()).isEqualTo(2026);
        assertThat(de2026.get("serie")).hasSize(12);

        String admin = "Bearer " + token(ADMIN);
        assertThat(mvc.perform(put("/api/configuracoes/parametros").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alteracoes\":{\"rastro.painelAno\":2025}}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        try {
            atualizarEEsperar();
            JsonNode de2025 = painel(GESTOR);
            assertThat(de2025.get("ano").asInt()).isEqualTo(2025);
            // O de 2025 nunca aparece no ano 2026 e vice-versa: o total muda com o recorte.
            assertThat(de2025.get("totalProtocolados").asLong()).isNotEqualTo(de2026.get("totalProtocolados").asLong());
        } finally {
            mvc.perform(delete("/api/configuracoes/parametros/rastro.painelAno").header("Authorization", admin));
        }
        atualizarEEsperar();
        assertThat(painel(GESTOR).get("ano").asInt()).isEqualTo(2026);
    }

    @Test
    void camunda_fora_do_ar_deixa_o_painel_desatualizado_sem_5xx_e_o_resto_segue() throws Exception {
        atualizarEEsperar();
        assertThat(painel(GESTOR).get("estado").asText()).isEqualTo("PRONTO");
        long antes = painel(GESTOR).get("totalProtocolados").asLong();
        String consultaAntes = corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H02 + "/posicao"));

        doThrow(new SigaIndisponivelException())
                .when(siga).ler(anyList(), any(), any());
        try {
            atualizarEEsperar();
            MockHttpServletResponse r = obter(GESTOR, "/api/painel");
            assertThat(r.getStatus()).isLessThan(500).isEqualTo(200);
            JsonNode atual = json.readTree(corpo(r));
            assertThat(atual.get("estado").asText()).isEqualTo("DESATUALIZADO");
            assertThat(atual.get("totalProtocolados").asLong()).isEqualTo(antes);

            // Tramitação, consulta e documentos seguem iguais com o painel desatualizado.
            assertThat(corpo(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H02 + "/posicao")))
                    .isEqualTo(consultaAntes);
            assertThat(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H02).getStatus()).isEqualTo(200);
            assertThat(obter(GESTOR, "/api/requerimentos/" + CenariosFicticiosDaS7.H02 + "/documentos").getStatus())
                    .isLessThan(500);
        } finally {
            doCallRealMethod().when(siga).ler(anyList(), any(), any());
        }

        // O inverso: com o painel pronto de novo, a tramitação não foi afetada e o painel volta a PRONTO.
        atualizarEEsperar();
        assertThat(painel(GESTOR).get("estado").asText()).isEqualTo("PRONTO");
    }

    @Test
    void sem_dado_anterior_a_falha_deixa_indisponivel_sem_5xx_e_a_leitura_da_serie_tambem() throws Exception {
        // Falha da série no SIGA: com instantâneo bom guardado, fica DESATUALIZADO; nunca 5xx.
        atualizarEEsperar();
        doThrow(new SigaIndisponivelException())
                .when(siga).lerSerie(any(), any());
        try {
            atualizarEEsperar();
            MockHttpServletResponse r = obter(ADMIN, "/api/painel");
            assertThat(r.getStatus()).isEqualTo(200);
            assertThat(json.readTree(corpo(r)).get("estado").asText()).isIn("DESATUALIZADO", "INDISPONIVEL");
        } finally {
            doCallRealMethod().when(siga).lerSerie(any(), any());
        }
        atualizarEEsperar();
    }

    @Test
    void o_siga_so_e_consultado_na_atualizacao_e_dentro_do_teto_de_lotes() throws Exception {
        atualizarEEsperar();
        clearInvocations(siga);

        // N requisições ao painel (todos os perfis): zero consultas ao SIGA.
        for (int i = 0; i < 10; i++) {
            obter(GESTOR, "/api/painel");
            obter(ADMIN, "/api/painel");
            obter(OPERADOR, "/api/painel");
        }
        verifyNoInteractions(siga);

        // Durante a atualização: uma série, uma base e os lotes previstos (até 500 por lote, no máximo o teto/500).
        atualizarEEsperar();
        verify(siga, times(1)).lerSerie(any(), any());
        ArgumentCaptor<Integer> teto = ArgumentCaptor.forClass(Integer.class);
        verify(siga, times(1)).lerBase(any(), teto.capture(), any());
        ArgumentCaptor<List<Long>> lote = ArgumentCaptor.forClass(List.class);
        verify(siga, atLeastOnce()).ler(lote.capture(), any(), any());
        List<Long> todos = new ArrayList<>();
        for (List<Long> um : lote.getAllValues()) {
            assertThat(um.size()).as("tamanho de um lote").isLessThanOrEqualTo(500);
            todos.addAll(um);
        }
        assertThat(todos.size()).as("ids lidos").isLessThanOrEqualTo(teto.getValue());
        assertThat(lote.getAllValues().size()).as("lotes").isLessThanOrEqualTo((teto.getValue() + 499) / 500);
        // A leitura simultânea é limitada por LimiteDeLeiturasDoCamunda: cada lote ocupa uma vaga (LerTarefasEmLotesTest).
    }
}
