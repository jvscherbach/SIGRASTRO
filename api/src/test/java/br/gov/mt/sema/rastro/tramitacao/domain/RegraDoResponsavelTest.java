package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Percorrida;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo.Tarefa;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa.Tipo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Regra do responsável (S7, bloco 4) sem banco. CPFs fictícios {@code 000000000NN}, inválidos de propósito; nomes
 * evidentes ("Servidor Exemplo NN").
 */
class RegraDoResponsavelTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 9, 9, 30);
    private static final String INTERESSADO = "00000000091";
    private static final String SEM_SERVIDOR = "00000000099";

    private static final CatalogoDeGrupos GRUPOS = id -> "grpSurhConferirDocumentoSubterranea".equals(id)
            ? Optional.of("Conferência de documentos (subterrânea)")
            : Optional.empty();

    private static String cpf(int n) {
        return String.format("%011d", n);
    }

    private static IdentificadorDoResponsavel id(String valor) {
        return IdentificadorDoResponsavel.de(valor).orElseThrow();
    }

    private static Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados() {
        Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> m = new HashMap<>();
        for (int n = 1; n <= 12; n++) {
            m.put(id(cpf(n)), new IdentificacaoDoResponsavel(Optional.of(String.format("Servidor Exemplo %02d", n)), false));
        }
        m.put(id(INTERESSADO), new IdentificacaoDoResponsavel(Optional.empty(), true));
        return m;
    }

    private static Etapa etapa(TipoDeEtapa tipo, PapelDoResponsavel papel, boolean multi) {
        return new Etapa("e", "Etapa", 1, tipo, papel, Ramo.PRINCIPAL, multi, List.of("taskE"), List.of(), List.of());
    }

    private static Tarefa tarefa(String assignee, String... grupos) {
        return new Tarefa("pi", "taskE", T0, IdentificadorDoResponsavel.de(assignee), List.of(grupos));
    }

    private static ElementoDaPosicao ponto(Etapa etapa, List<Tarefa> tarefas) {
        return new ElementoDaPosicao(Optional.ofNullable(etapa), "taskE", T0, true, tarefas, Optional.empty(),
                Optional.empty());
    }

    private static ResponsavelDaEtapa resolver(ElementoDaPosicao ponto) {
        return RegraDoResponsavel.resolver(ponto, identificados(), GRUPOS);
    }

    @Test
    void servidor_pelo_cpf_com_papel_e_area() {
        ResponsavelDaEtapa r = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.CHEFIA, false),
                List.of(tarefa(cpf(4), "grpSurhConferirDocumentoSubterranea"))));
        assertThat(r.tipo()).isEqualTo(Tipo.SERVIDOR);
        assertThat(r.nome()).contains("Servidor Exemplo 04");
        assertThat(r.papel()).isEqualTo("Chefia");
        assertThat(r.area()).contains("Conferência de documentos (subterrânea)");
        assertThat(r.texto()).isEqualTo("Servidor Exemplo 04");
    }

    @Test
    void cpf_do_interessado_vira_interessado_mesmo_em_etapa_de_grupo() {
        ResponsavelDaEtapa r = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.INTERESSADO, false),
                List.of(tarefa(INTERESSADO))));
        assertThat(r.tipo()).isEqualTo(Tipo.INTERESSADO);
        assertThat(r.texto()).isEqualTo("Interessado (cadastrante)");
        assertThat(r.nome()).isEmpty();
    }

    @Test
    void cpf_sem_servidor_e_valor_que_nao_e_cpf_viram_nao_identificado() {
        for (String assignee : List.of(SEM_SERVIDOR, "analista.teste")) {
            ResponsavelDaEtapa r = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, false),
                    List.of(tarefa(assignee))));
            assertThat(r.tipo()).isEqualTo(Tipo.NAO_IDENTIFICADO);
            assertThat(r.texto()).isEqualTo("Responsável não identificado");
        }
    }

    @Test
    void grupo_conhecido_vira_area_e_desconhecido_ou_zero_vira_equipe() {
        assertThat(resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.GRUPO, false),
                List.of(tarefa(null, "grpSurhConferirDocumentoSubterranea")))))
                .satisfies(r -> {
                    assertThat(r.tipo()).isEqualTo(Tipo.AREA);
                    assertThat(r.texto()).isEqualTo("Conferência de documentos (subterrânea)");
                });
        for (String grupo : List.of("grpFicticioDesconhecido", "0")) {
            ResponsavelDaEtapa r = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.GRUPO, false),
                    List.of(tarefa(null, grupo))));
            assertThat(r.tipo()).isEqualTo(Tipo.EQUIPE);
            assertThat(r.texto()).isEqualTo("Equipe responsável");
        }
    }

    @Test
    void sem_tarefa_vale_o_papel_da_etapa() {
        assertThat(resolver(ponto(etapa(TipoDeEtapa.ESPERA, PapelDoResponsavel.INTERESSADO, false), List.of())).tipo())
                .isEqualTo(Tipo.INTERESSADO);
        assertThat(resolver(ponto(etapa(TipoDeEtapa.AUTOMATICA, PapelDoResponsavel.SISTEMA, false), List.of())).tipo())
                .isEqualTo(Tipo.SISTEMA);
        assertThat(resolver(ponto(null, List.of())).tipo()).isEqualTo(Tipo.SISTEMA);
        assertThat(resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.GRUPO, false), List.of())).tipo())
                .isEqualTo(Tipo.EQUIPE);
        // Tarefa sem grupo nem ASSIGNEE_ numa etapa do interessado.
        assertThat(resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.INTERESSADO, false),
                List.of(tarefa(null)))).tipo()).isEqualTo(Tipo.INTERESSADO);
    }

    @Test
    void multi_instancia_lista_quem_concluiu_e_quem_esta_pendente() {
        ElementoDaPosicao analise = new ElementoDaPosicao(
                Optional.of(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, true)), "taskE", T0, true,
                List.of(tarefa(cpf(3)), tarefa(SEM_SERVIDOR)),
                Optional.of(new ElementoDaPosicao.MultiInstancia(Optional.of(2), 2)), Optional.empty(),
                List.of(new Percorrida("taskE", T0, T0.plusHours(1), true, IdentificadorDoResponsavel.de(cpf(1))),
                        new Percorrida("taskE", T0, T0.plusHours(2), true, IdentificadorDoResponsavel.de(cpf(2)))),
                Optional.empty());
        ResponsavelDaEtapa r = resolver(analise);
        assertThat(r.tipo()).isEqualTo(Tipo.VARIOS);
        assertThat(r.papel()).isEqualTo("Analista");
        assertThat(r.participantes()).extracting(p -> p.nome().orElse(p.papel()), ResponsavelDaEtapa.Participante::concluiu)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Servidor Exemplo 01", true),
                        org.assertj.core.groups.Tuple.tuple("Servidor Exemplo 02", true),
                        org.assertj.core.groups.Tuple.tuple("Servidor Exemplo 03", false),
                        org.assertj.core.groups.Tuple.tuple("Responsável não identificado", false));
    }

    @Test
    void equipe_de_12_analistas_mostra_10_e_mais_2() {
        List<Tarefa> doze = new ArrayList<>();
        for (int n = 1; n <= 12; n++) {
            doze.add(tarefa(cpf(n)));
        }
        ElementoDaPosicao analise = new ElementoDaPosicao(
                Optional.of(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, true)), "taskE", T0, true, doze,
                Optional.of(new ElementoDaPosicao.MultiInstancia(Optional.of(0), 12)), Optional.empty());
        ResponsavelDaEtapa completo = resolver(analise);
        assertThat(completo.participantes()).hasSize(12);

        ResponsavelDaEtapa limitado = completo.conforme(true, 10);
        assertThat(limitado.participantes()).hasSize(10);
        assertThat(limitado.maisParticipantes()).isEqualTo(2);
        assertThat(limitado.participantes().getLast().nome()).contains("Servidor Exemplo 10");
    }

    @Test
    void parametro_desligado_tira_todos_os_nomes() {
        ResponsavelDaEtapa servidor = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.CHEFIA, false),
                List.of(tarefa(cpf(4))))).conforme(false, 10);
        assertThat(servidor.nome()).isEmpty();
        assertThat(servidor.texto()).isEqualTo("Chefia");

        List<Tarefa> tres = List.of(tarefa(cpf(5)), tarefa(cpf(6)), tarefa(cpf(7)));
        ResponsavelDaEtapa equipe = resolver(ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, true),
                tres)).conforme(false, 10);
        assertThat(equipe.participantes()).hasSize(3).allSatisfy(p -> {
            assertThat(p.nome()).isEmpty();
            assertThat(p.papel()).isEqualTo("Analista");
        });
        assertThat(equipe.toString()).doesNotContain("Servidor Exemplo");
    }

    @Test
    void so_cpf_de_11_digitos_vai_para_a_consulta_sem_repeticao() {
        List<ElementoDaPosicao> pontos = List.of(
                ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, false),
                        List.of(tarefa(cpf(1)), tarefa(cpf(1)), tarefa("analista.teste"), tarefa(null))));
        assertThat(RegraDoResponsavel.paraConsultar(pontos)).containsExactly(id(cpf(1)));
    }

    @Test
    void json_e_texto_da_posicao_resolvida_nao_tem_cpf() throws Exception {
        ElementoDaPosicao comCpf = ponto(etapa(TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.ANALISTA, true),
                List.of(tarefa(cpf(1)), tarefa(SEM_SERVIDOR), tarefa(INTERESSADO)));
        ElementoDaPosicao resolvido = comCpf.comResponsavel(resolver(comCpf));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(resolvido);
        for (String texto : List.of(json, resolvido.toString())) {
            assertThat(texto).doesNotContain(cpf(1), SEM_SERVIDOR, INTERESSADO).doesNotContainPattern("\\d{11}");
        }
        assertThat(json).contains("Servidor Exemplo 01");
    }
}
