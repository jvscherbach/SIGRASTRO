package br.gov.mt.sema.rastro.tramitacao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Regras de conteúdo do catálogo (S7, bloco 1). Ids técnicos fictícios, no formato do BPMN. */
class ValidadorDeModeloTest {

    private static Etapa etapa(String id, int ordem, Ramo ramo, String... idsTecnicos) {
        return new Etapa(id, "Rótulo " + id, ordem, TipoDeEtapa.TAREFA_HUMANA, PapelDoResponsavel.GRUPO, ramo, false,
                List.of(idsTecnicos), List.of(), List.of());
    }

    private static final FinalDoFluxo FIM = new FinalDoFluxo("concluido", "Concluído", "evtFim");

    private static List<String> problemas(List<Etapa> etapas, List<FinalDoFluxo> finais, List<IdIgnorado> ignorados) {
        return ValidadorDeModelo.problemas("procTeste", "Modelo de teste", etapas, finais, ignorados);
    }

    @Test
    void modelo_valido_nao_tem_problema() {
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskUm"), etapa("dois", 1, Ramo.CORRECAO, "taskDois")),
                List.of(FIM), List.of(new IdIgnorado("Activity_x", "Suporte", IdIgnorado.Motivo.SUPORTE)))).isEmpty();
    }

    @Test
    void id_tecnico_em_duas_etapas_e_erro() {
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskX"), etapa("dois", 2, Ramo.PRINCIPAL, "taskX")),
                List.of(FIM), List.of()))
                .anySatisfy(p -> assertThat(p).contains("id técnico 'taskX' em mais de um lugar"));
    }

    @Test
    void id_tecnico_repetido_na_mesma_etapa_e_erro() {
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskX", "taskX")), List.of(FIM), List.of()))
                .anySatisfy(p -> assertThat(p).contains("'taskX' em mais de um lugar"));
    }

    @Test
    void id_tecnico_em_etapa_e_em_final_ou_ignorado_e_erro() {
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "evtFim")), List.of(FIM), List.of()))
                .anySatisfy(p -> assertThat(p).contains("'evtFim' em mais de um lugar"));
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskUm")), List.of(FIM),
                List.of(new IdIgnorado("taskUm", "Ruído", IdIgnorado.Motivo.RUIDO))))
                .anySatisfy(p -> assertThat(p).contains("'taskUm' em mais de um lugar"));
    }

    @Test
    void ordem_repetida_no_mesmo_ramo_e_erro_e_em_ramos_diferentes_nao() {
        assertThat(problemas(List.of(etapa("um", 3, Ramo.PRINCIPAL, "taskUm"), etapa("dois", 3, Ramo.PRINCIPAL, "taskDois")),
                List.of(FIM), List.of()))
                .anySatisfy(p -> assertThat(p).contains("ordem 3 repetida no ramo PRINCIPAL"));
        assertThat(problemas(List.of(etapa("um", 3, Ramo.PRINCIPAL, "taskUm"), etapa("dois", 3, Ramo.CORRECAO, "taskDois")),
                List.of(FIM), List.of())).isEmpty();
    }

    @Test
    void final_sem_rotulo_e_erro() {
        assertThat(problemas(List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskUm")),
                List.of(new FinalDoFluxo("cancelado", " ", "evtCancelado")), List.of()))
                .contains("final 'cancelado' sem rótulo");
    }

    @Test
    void modelo_sem_etapa_e_erro_claro_e_criar_lanca() {
        assertThat(problemas(List.of(), List.of(FIM), List.of())).contains("modelo 'procTeste' sem etapa");
        assertThatThrownBy(() -> ModeloDeFluxo.criar("teste.yml", "procTeste", "Modelo", List.of(), List.of(),
                List.of(FIM), List.of()))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("teste.yml")
                .hasMessageContaining("sem etapa");
    }

    @Test
    void modelo_sem_final_sem_chave_ou_sem_rotulo_e_erro() {
        assertThat(ValidadorDeModelo.problemas(" ", null, List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskUm")), List.of(),
                List.of()))
                .contains("modelo sem chave", "modelo ' ' sem rótulo", "modelo ' ' sem final");
    }

    @Test
    void id_estavel_fora_do_padrao_ou_repetido_e_erro() {
        assertThat(problemas(List.of(etapa("Analise-Processo", 1, Ramo.PRINCIPAL, "taskUm")), List.of(FIM), List.of()))
                .anySatisfy(p -> assertThat(p).contains("fora do padrão"));
        assertThat(problemas(List.of(etapa("concluido", 1, Ramo.PRINCIPAL, "taskUm")), List.of(FIM), List.of()))
                .contains("id 'concluido' repetido no modelo");
    }

    @Test
    void etapa_final_multi_instancia_automatica_sem_id_tecnico_e_condicao_desconhecida_sao_erros() {
        List<Etapa> etapas = new ArrayList<>();
        etapas.add(new Etapa("fim", "Fim", 1, TipoDeEtapa.FINAL, PapelDoResponsavel.SISTEMA, Ramo.PRINCIPAL, false,
                List.of("taskFim"), List.of(), List.of()));
        etapas.add(new Etapa("auto", "Automática", 2, TipoDeEtapa.AUTOMATICA, PapelDoResponsavel.SISTEMA, Ramo.PRINCIPAL,
                true, List.of("taskAuto"), List.of(), List.of("gtwInexistente")));
        etapas.add(new Etapa("vazia", "Vazia", 3, TipoDeEtapa.ESPERA, null, Ramo.PRINCIPAL, false, List.of(), List.of(),
                List.of()));
        assertThat(problemas(etapas, List.of(FIM), List.of())).contains(
                "etapa 'fim' com tipo FINAL (finais ficam na lista 'finais')",
                "etapa 'auto' multi-instância sem ser tarefa humana",
                "etapa 'auto' com condição sobre id técnico desconhecido 'gtwInexistente'",
                "etapa 'vazia' sem responsável",
                "etapa 'vazia' sem id técnico");
    }

    @Test
    void ordem_menor_que_um_e_processo_filho_repetido_sao_erros() {
        Etapa a = new Etapa("a", "A", 0, TipoDeEtapa.EMISSAO, PapelDoResponsavel.GRUPO, Ramo.PRINCIPAL, false, List.of(),
                List.of("procFilho"), List.of());
        Etapa b = new Etapa("b", "B", 2, TipoDeEtapa.EMISSAO, PapelDoResponsavel.GRUPO, Ramo.PRINCIPAL, false, List.of(),
                List.of("procFilho"), List.of());
        assertThat(problemas(List.of(a, b), List.of(FIM), List.of()))
                .contains("etapa 'a' com ordem menor que 1")
                .anySatisfy(p -> assertThat(p).contains("processo filho 'procFilho' em mais de um lugar"));
    }

    @Test
    void condicao_de_percurso_nao_atendida_vira_nao_mapeada() {
        Etapa espera = new Etapa("espera", "Espera", 1, TipoDeEtapa.ESPERA, PapelDoResponsavel.INTERESSADO, Ramo.PRINCIPAL,
                false, List.of("gtwPagar"), List.of(), List.of());
        Etapa isencao = new Etapa("isencao", "Isenção", 2, TipoDeEtapa.AUTOMATICA, PapelDoResponsavel.SISTEMA,
                Ramo.PRINCIPAL, false, List.of("taskProtocolo"), List.of(), List.of("gtwPagar"));
        ModeloDeFluxo modelo = ModeloDeFluxo.criar("teste", "procTeste", "Teste", List.of(), List.of(espera, isencao),
                List.of(FIM), List.of());
        assertThat(modelo.resolver("taskProtocolo", Set.of("taskCadastro")))
                .isEqualTo(new ResolucaoDeEtapa.NaEtapa(isencao));
        assertThat(modelo.resolver("taskProtocolo", Set.of("gtwPagar")))
                .isEqualTo(new ResolucaoDeEtapa.NaoMapeado("taskProtocolo"));
    }

    @Test
    void id_vazio_ou_nulo_vira_nao_mapeada_sem_lancar() {
        ModeloDeFluxo modelo = ModeloDeFluxo.criar("teste", "procTeste", "Teste", null,
                List.of(etapa("um", 1, Ramo.PRINCIPAL, "taskUm")), List.of(FIM), null);
        assertThat(modelo.resolver(null, null)).isEqualTo(new ResolucaoDeEtapa.NaoMapeado(""));
        assertThat(modelo.resolver("  ", Set.of())).isEqualTo(new ResolucaoDeEtapa.NaoMapeado(""));
        assertThat(modelo.resolverProcessoFilho(null)).isEqualTo(new ResolucaoDeEtapa.NaoMapeado(""));
    }
}
