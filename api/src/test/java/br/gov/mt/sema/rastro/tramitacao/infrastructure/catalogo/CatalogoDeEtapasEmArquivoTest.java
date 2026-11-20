package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.IdIgnorado;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import br.gov.mt.sema.rastro.tramitacao.domain.PapelDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TipoDeEtapa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Catálogo versionado (rastro/etapas/*.yml) contra as tabelas do prompt da S7 (bloco 1) e o formato do arquivo. */
class CatalogoDeEtapasEmArquivoTest {

    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final String LAC = "procLicenciamentoLac";
    private static final CatalogoDeEtapasEmArquivo CATALOGO = new CatalogoDeEtapasEmArquivo();

    private static ResolucaoDeEtapa resolver(String chave, String id) {
        return CATALOGO.resolver(chave, id, Set.of());
    }

    @Test
    void carrega_os_dois_modelos_com_versao() {
        assertThat(CATALOGO.modelos()).extracting(ModeloDeFluxo::chave).containsExactlyInAnyOrder(HIDRICO, LAC);
        assertThat(CATALOGO.versao()).matches("[0-9a-f]{12}");
    }

    @ParameterizedTest(name = "Hídrico: {0} -> {1}")
    @CsvSource({
            "taskCadastrarRequerimento, cadastro_do_requerimento, 1, PRINCIPAL",
            "taskConferirDocumentosApresentados, conferencia_dos_documentos, 2, PRINCIPAL",
            "taskAtualizarStatusRequerimentoArrecadacao, emissao_da_taxa, 3, PRINCIPAL",
            "taskAtualizarStatusRequerimentoEmissaoTaxa1, emissao_da_taxa, 3, PRINCIPAL",
            "taskEmitirTaxaSefaz, emissao_da_taxa, 3, PRINCIPAL",
            "gtwAguardarPagamento, aguardando_pagamento, 4, PRINCIPAL",
            "taskGerarNumeroProtocolo, isencao_confirmada, 5, PRINCIPAL",
            "taskAtribuirRequerimento, atribuicao_ao_analista, 6, PRINCIPAL",
            "taskMontarListaAnalistas, atribuicao_ao_analista, 6, PRINCIPAL",
            "taskAceitarAtribuicao, aceite_pelo_analista, 7, PRINCIPAL",
            "taskAnalisarRequerimento, analise_do_processo, 8, PRINCIPAL",
            "taskRevisarParecerTecnico, revisao_do_parecer_tecnico, 9, PRINCIPAL",
            "taskValidarParecerTecnico, validacao_do_parecer_tecnico, 10, PRINCIPAL",
            "taskAtualizarStatusParecerAssinado, validacao_do_parecer_tecnico, 10, PRINCIPAL",
            "taskEmitirAutorizacoes, emissao_do_ato, 11, PRINCIPAL",
            "taskEmitirOutrosAtos, emissao_do_ato, 11, PRINCIPAL",
            "taskEmitirCertificadoCadastros, emissao_do_ato, 11, PRINCIPAL",
            "taskEmitirPortarias, emissao_do_ato, 11, PRINCIPAL",
            "taskRegistarComprovantePublicacao, registro_do_comprovante_de_publicacao, 12, PRINCIPAL",
            "taskUpdateStatusConcluido, registro_do_comprovante_de_publicacao, 12, PRINCIPAL",
            "taskInformarTitulacoesDisponiveis, registro_do_comprovante_de_publicacao, 12, PRINCIPAL",
            "taskVerificarRevisaoProcesso, verificacao_da_revisao_do_processo, 13, PRINCIPAL",
            "taskAtualizarStatusAguardandoRevisao, correcao_do_requerimento, 1, CORRECAO",
            "taskCorrecaoRequerimento, correcao_do_requerimento, 1, CORRECAO",
            "taskEmitirTaxaSefazCorrecao, emissao_da_taxa_correcao, 2, CORRECAO",
            "gtwAguardarPagamentoRevisao, aguardando_pagamento_correcao, 3, CORRECAO"})
    void cada_id_do_hidrico_resolve_para_a_etapa_esperada(String id, String etapa, int ordem, Ramo ramo) {
        assertThat(resolver(HIDRICO, id)).isInstanceOfSatisfying(ResolucaoDeEtapa.NaEtapa.class, r -> {
            assertThat(r.etapa().id()).isEqualTo(etapa);
            assertThat(r.etapa().ordem()).isEqualTo(ordem);
            assertThat(r.etapa().ramo()).isEqualTo(ramo);
        });
    }

    @ParameterizedTest(name = "LAC: {0} -> {1}")
    @CsvSource({
            "taskCadastrarRequerimento, cadastro_do_requerimento, 1, PRINCIPAL",
            "taskConferirDocumentosApresentados, conferencia_dos_documentos, 2, PRINCIPAL",
            "taskAtualizarStatusEncParaArrecadacao, emissao_da_taxa, 3, PRINCIPAL",
            "taskAtualizarStatusAguardEmissaoTaxa, emissao_da_taxa, 3, PRINCIPAL",
            "taskEmitirTaxaSefaz, emissao_da_taxa, 3, PRINCIPAL",
            "gwtAguardarPagtoOuVencto, aguardando_pagamento, 4, PRINCIPAL",
            "taskGerarNumeroProtocolo, isencao_confirmada, 5, PRINCIPAL",
            "taskGerarTermoAdesaoLAC, termo_de_adesao_gerado, 6, PRINCIPAL",
            "taskEnviarTermoAdesao, envio_do_termo_assinado, 7, PRINCIPAL",
            "taskUpdateStatusConcluido, conclusao, 8, PRINCIPAL",
            "taskEnviarEmailTitulacoes, conclusao, 8, PRINCIPAL",
            "taskCorrecaoRequerimento, correcao_do_requerimento, 1, CORRECAO"})
    void cada_id_do_lac_resolve_para_a_etapa_esperada(String id, String etapa, int ordem, Ramo ramo) {
        assertThat(resolver(LAC, id)).isInstanceOfSatisfying(ResolucaoDeEtapa.NaEtapa.class, r -> {
            assertThat(r.etapa().id()).isEqualTo(etapa);
            assertThat(r.etapa().ordem()).isEqualTo(ordem);
            assertThat(r.etapa().ramo()).isEqualTo(ramo);
        });
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource({
            "procOutorgaAguaSupSub, Event_1wiuc48, Concluído",
            "procOutorgaAguaSupSub, Event_1kq2k0z, Cancelado pelo cadastrante",
            "procOutorgaAguaSupSub, Event_0abm55w, Expirado no cadastro",
            "procOutorgaAguaSupSub, Event_0h1oige, Encerrado por taxa não paga no prazo",
            "procLicenciamentoLac, evtProcessoConcluido, Concluído",
            "procLicenciamentoLac, evtFimRequerimentoAPedidoCadastrante, Cancelado pelo cadastrante",
            "procLicenciamentoLac, evtEncerrarRequerimentoPrazoCadExpirado, Expirado no cadastro",
            "procLicenciamentoLac, evtEncerrarProcessoPrazoCorrecaoExpirado, Expirado na correção",
            "procLicenciamentoLac, evtEncerrarProcessoFaltaPagto, Cancelado por falta de pagamento",
            "procLicenciamentoLac, Event_0x7hz1p, Arquivado (termo não enviado no prazo)"})
    void cada_final_resolve_com_rotulo(String chave, String id, String rotulo) {
        assertThat(resolver(chave, id)).isInstanceOfSatisfying(ResolucaoDeEtapa.Encerrado.class, r -> {
            assertThat(r.fim().rotulo()).isEqualTo(rotulo);
            assertThat(r.fim().tipo()).isEqualTo(TipoDeEtapa.FINAL);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"procSurhEmitirAutorizacoes", "procSurhEmitirOutrosAtos", "procSurhCertificadoCadastro",
            "procSurhEmissaoPortaria"})
    void instancias_filhas_de_emissao_resolvem_para_emissao_do_ato(String filho) {
        ModeloDeFluxo hidrico = CATALOGO.modelo(HIDRICO).orElseThrow();
        assertThat(hidrico.resolverProcessoFilho(filho)).isInstanceOfSatisfying(ResolucaoDeEtapa.NaEtapa.class,
                r -> assertThat(r.etapa().id()).isEqualTo("emissao_do_ato"));
        assertThat(hidrico.resolverProcessoFilho("procGestaoComplementacao"))
                .isEqualTo(new ResolucaoDeEtapa.NaoMapeado("procGestaoComplementacao"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Gateway_aguardando_emtx", "Activity_0miwhxt", "taskAnalisarReducaoTaxa"})
    void ids_ignorados_nunca_viram_etapa(String id) {
        assertThat(resolver(HIDRICO, id)).isInstanceOf(ResolucaoDeEtapa.Ignorado.class);
        assertThat(CATALOGO.modelos()).allSatisfy(m -> assertThat(m.etapas())
                .noneMatch(e -> e.idsTecnicos().contains(id)));
    }

    @Test
    void suporte_e_identificado_no_hidrico() {
        assertThat(CATALOGO.modelo(HIDRICO).orElseThrow().idsDeSuporte()).containsExactly("Activity_0miwhxt");
        assertThat(resolver(HIDRICO, "Activity_0miwhxt")).isInstanceOfSatisfying(ResolucaoDeEtapa.Ignorado.class,
                r -> assertThat(r.ignorado().motivo()).isEqualTo(IdIgnorado.Motivo.SUPORTE));
    }

    @Test
    void id_desconhecido_resolve_para_nao_mapeada_sem_lancar() {
        assertThat(resolver(HIDRICO, "taskQueNaoExiste")).isEqualTo(new ResolucaoDeEtapa.NaoMapeado("taskQueNaoExiste"));
        // Id de um modelo não vale no outro.
        assertThat(resolver(LAC, "taskAnalisarRequerimento"))
                .isEqualTo(new ResolucaoDeEtapa.NaoMapeado("taskAnalisarRequerimento"));
        assertThat(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA).isEqualTo("Etapa não mapeada");
    }

    @Test
    void isencao_so_vale_sem_passagem_pelo_gateway_de_pagamento() {
        assertThat(CATALOGO.resolver(HIDRICO, "taskGerarNumeroProtocolo", Set.of("gtwAguardarPagamento")))
                .isEqualTo(new ResolucaoDeEtapa.NaoMapeado("taskGerarNumeroProtocolo"));
        assertThat(CATALOGO.resolver(LAC, "taskGerarNumeroProtocolo", Set.of("gwtAguardarPagtoOuVencto")))
                .isEqualTo(new ResolucaoDeEtapa.NaoMapeado("taskGerarNumeroProtocolo"));
        assertThat(CATALOGO.resolver(HIDRICO, "taskGerarNumeroProtocolo", Set.of("taskConferirDocumentosApresentados")))
                .isInstanceOf(ResolucaoDeEtapa.NaEtapa.class);
    }

    @Test
    void trifasico_e_las_resolvem_para_nao_suportado() {
        assertThat(resolver("procLicenciamentoTrifasico", "taskCadastrarRequerimento"))
                .isEqualTo(new ResolucaoDeEtapa.NaoSuportado("procLicenciamentoTrifasico"));
        assertThat(resolver(null, "taskCadastrarRequerimento")).isEqualTo(new ResolucaoDeEtapa.NaoSuportado(""));
        assertThat(CATALOGO.modeloDaInstancia("LICENCIAMENTO_TRIFASICO")).isEmpty();
        assertThat(CATALOGO.modeloDaInstancia(null)).isEmpty();
    }

    @Test
    void instancia_do_requerimento_leva_ao_modelo() {
        assertThat(CATALOGO.modeloDaInstancia("RECURSO_HIDRICO")).map(ModeloDeFluxo::chave).contains(HIDRICO);
        assertThat(CATALOGO.modeloDaInstancia("LICENCIAMENTO_ADESAO")).map(ModeloDeFluxo::chave).contains(LAC);
    }

    @Test
    void multi_instancia_e_papeis_conforme_a_tabela() {
        ModeloDeFluxo hidrico = CATALOGO.modelo(HIDRICO).orElseThrow();
        assertThat(hidrico.etapas()).filteredOn(e -> e.multiInstancia()).extracting(e -> e.id())
                .containsExactly("aceite_pelo_analista", "analise_do_processo", "validacao_do_parecer_tecnico");
        assertThat(hidrico.etapa("revisao_do_parecer_tecnico").orElseThrow().responsavel())
                .isEqualTo(PapelDoResponsavel.CHEFIA);
        assertThat(hidrico.etapa("emissao_do_ato").orElseThrow().tipo()).isEqualTo(TipoDeEtapa.EMISSAO);
        assertThat(CATALOGO.modelo(LAC).orElseThrow().etapas()).noneMatch(e -> e.multiInstancia());
    }

    // ---- formato do arquivo ----

    private static final String MINIMO = """
            chave: procTeste
            rotulo: Teste
            instancias: [TESTE]
            etapas:
              - {id: um, rotulo: Um, ordem: 1, tipo: TAREFA_HUMANA, responsavel: GRUPO, idsTecnicos: [taskUm]}
            finais:
              - {id: concluido, rotulo: Concluído, idTecnico: evtFim}
            """;

    @Test
    void arquivo_minimo_carrega() {
        CatalogoDeEtapasEmArquivo catalogo = new CatalogoDeEtapasEmArquivo(Map.of("teste.yml", MINIMO));
        assertThat(catalogo.modelo("procTeste")).isPresent();
        assertThat(catalogo.modelo("procTeste").orElseThrow().etapas().getFirst().ramo()).isEqualTo(Ramo.PRINCIPAL);
    }

    @Test
    void mudar_o_conteudo_muda_a_versao() {
        String outra = new CatalogoDeEtapasEmArquivo(Map.of("teste.yml", MINIMO.replace("rotulo: Um", "rotulo: Um."))).versao();
        assertThat(outra).isNotEqualTo(new CatalogoDeEtapasEmArquivo(Map.of("teste.yml", MINIMO)).versao());
    }

    @Test
    void campo_desconhecido_e_valor_fora_do_enum_derrubam_a_subida() {
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("teste.yml",
                MINIMO.replace("idsTecnicos: [taskUm]", "idsTecnico: [taskUm]").replace("GRUPO", "EQUIPE"))))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("teste.yml")
                .hasMessageContaining("campo desconhecido 'idsTecnico'")
                .hasMessageContaining("valor desconhecido 'EQUIPE'");
    }

    @Test
    void arquivo_vazio_ou_malformado_ou_sem_etapa_derruba_a_subida_com_mensagem_clara() {
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("vazio.yml", "")))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("vazio.yml");
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("ruim.yml", "chave: [")))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("YAML malformado");
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("sem.yml", """
                chave: procTeste
                rotulo: Teste
                finais: [{id: concluido, rotulo: Concluído, idTecnico: evtFim}]
                """)))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("sem etapa");
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of()))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("nenhum arquivo");
    }

    @Test
    void chave_ou_instancia_repetida_entre_arquivos_derruba_a_subida() {
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("a.yml", MINIMO, "b.yml", MINIMO)))
                .isInstanceOf(ModeloDeFluxoInvalido.class)
                .hasMessageContaining("chave 'procTeste' em mais de um arquivo")
                .hasMessageContaining("instância 'TESTE'");
    }

    @Test
    void o_arquivo_nao_instancia_classes() {
        assertThatThrownBy(() -> new CatalogoDeEtapasEmArquivo(Map.of("x.yml", "!!java.io.File [\"/\"]")))
                .isInstanceOf(ModeloDeFluxoInvalido.class).hasMessageContaining("YAML malformado");
    }
}
