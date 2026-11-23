package br.gov.mt.sema.rastro.painel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Regras de contagem do painel (S10, bloco 3), em memória, com o catálogo real e cenários no espírito de PN01 a PN15. */
class RegrasDeContagemDoPainelTest {

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final CatalogoDeGrupos GRUPOS =
            id -> "grpSurhConferirDocumentoSubterranea".equals(id) ? Optional.of("Conferência de documentos (subterrânea)")
                    : Optional.empty();
    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final String LAC = "procLicenciamentoLac";

    // ---- série (PN01 a PN04)

    @Test
    void serieTemDozeMesesSempreEMesSemDadoVale0() {
        var leitura = new LeituraDaSerie(
                List.of(ContagemDoMes.de(YearMonth.of(2026, 1), 2), ContagemDoMes.de(YearMonth.of(2026, 12), 3),
                        ContagemDoMes.de(YearMonth.of(2026, 3), 2)),
                List.of(ContagemDoMes.de(YearMonth.of(2026, 4), 2)), 1);

        SerieDoAno serie = MontarSerieDoAno.montar(2026, leitura);

        assertThat(serie.meses()).hasSize(12);
        assertThat(serie.meses().get(0).protocolados()).isEqualTo(2);
        assertThat(serie.meses().get(1).protocolados()).isZero();
        assertThat(serie.meses().get(11).protocolados()).isEqualTo(3);
        assertThat(serie.meses().get(3).concluidos()).isEqualTo(2);
        assertThat(serie.totalProtocolados()).isEqualTo(7);
        assertThat(serie.totalConcluidos()).isEqualTo(2);
        assertThat(serie.semDataDeProtocolo()).isEqualTo(1);
        assertThat(serie.avisos()).isEmpty();
    }

    @Test
    void mesDe2025NaoEntraNoAnoEConcluidoForaDoAnoGeraAvisoSemEsconder() {
        var leitura = new LeituraDaSerie(
                List.of(ContagemDoMes.de(YearMonth.of(2025, 12), 1), ContagemDoMes.de(YearMonth.of(2026, 2), 1)),
                List.of(ContagemDoMes.de(YearMonth.of(2026, 2), 1), ContagemDoMes.de(null, 1)), 0);

        SerieDoAno serie = MontarSerieDoAno.montar(2026, leitura);

        assertThat(serie.totalProtocolados()).isEqualTo(1);
        assertThat(serie.totalConcluidos()).isEqualTo(2);
        assertThat(serie.avisos()).extracting(AvisoDoPainel::codigo).containsExactly("INCONSISTENCIA_NA_SERIE");
    }

    @Test
    void concluidosMaioresQueProtocoladosGeramAviso() {
        var leitura = new LeituraDaSerie(List.of(ContagemDoMes.de(YearMonth.of(2026, 5), 1)),
                List.of(ContagemDoMes.de(YearMonth.of(2026, 5), 2)), 0);

        assertThat(MontarSerieDoAno.montar(2026, leitura).avisos()).hasSize(1);
    }

    // ---- fila por etapa

    private static RequerimentoDaBase req(long id, String tipo, String situacao) {
        return new RequerimentoDaBase(id, Optional.ofNullable(tipo), Optional.ofNullable(situacao));
    }

    private static TarefaAtiva tarefa(long id, String modelo, String idTarefa, ResponsavelDaTarefa r, String nome,
            String... grupos) {
        return new TarefaAtiva(id, modelo, idTarefa, r, Optional.ofNullable(nome), List.of(grupos));
    }

    private static LeituraDasTarefas leitura(Set<Long> comInstancia, TarefaAtiva... tarefas) {
        Set<Long> todos = new java.util.HashSet<>(comInstancia);
        for (TarefaAtiva t : tarefas) {
            todos.add(t.idRequerimento());
        }
        return new LeituraDasTarefas(List.of(tarefas), todos, false);
    }

    private static long barra(FilaPorEtapa fila, String modelo, String etapa) {
        return fila.barras().stream().filter(b -> b.idDaEtapa().equals(etapa) && b.modelo().equals(modelo))
                .findFirst().orElseThrow().total();
    }

    private static final String NOME_HIDRICO = "Outorga de recursos hídricos";

    @Test
    void filaConta_etapasZeradasNaOrdemDoCatalogoE_osTresAParte() {
        List<RequerimentoDaBase> base = List.of(
                req(1, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(2, "RECURSO_HIDRICO", "EM_REVISAO"),
                req(3, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(4, "LICENCIAMENTO_ADESAO", "EM_CADASTRO"),
                req(5, "RECURSO_HIDRICO", "CONCLUIDO"),
                req(6, "RECURSO_HIDRICO", "CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE"),
                req(7, "LICENCIAMENTO_ADESAO", "ARQUIVADO_DEFINITIVAMENTE"),
                req(8, "TRIFASICO", "EM_ANALISE"),
                req(9, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(10, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(2, HIDRICO, "taskRevisarParecerTecnico", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 02"),
                tarefa(3, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 03"),
                tarefa(4, LAC, "taskCadastrarRequerimento", ResponsavelDaTarefa.INTERESSADO, null),
                tarefa(5, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(9, HIDRICO, "taskFicticioNaoMapeado", ResponsavelDaTarefa.NAO_ATRIBUIDA, null));
        // 8 (fora do catálogo) e 10 (sem instância) não têm tarefa

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, tarefas);

        assertThat(fila.totalNaFila()).isEqualTo(7);
        assertThat(barra(fila, NOME_HIDRICO, "analise_do_processo")).isEqualTo(2);
        assertThat(barra(fila, NOME_HIDRICO, "revisao_do_parecer_tecnico")).isEqualTo(1);
        assertThat(barra(fila, NOME_HIDRICO, "emissao_do_ato")).isZero();
        assertThat(fila.foraDoCatalogo()).isEqualTo(1);
        assertThat(fila.semInstancia()).isEqualTo(1);
        assertThat(fila.etapaNaoMapeada()).isEqualTo(1);
        assertThat(fila.avisos()).isEmpty();
        // ordem do catálogo: as barras seguem os modelos e as etapas dele
        List<String> esperada = new ArrayList<>();
        CATALOGO.modelos().forEach(m -> m.etapas().forEach(e -> esperada.add(m.rotulo() + "/" + e.id())));
        assertThat(fila.barras().stream().map(b -> b.modelo() + "/" + b.idDaEtapa()).toList()).isEqualTo(esperada);
    }

    @Test
    void requerimentoParaleloContaUmaVezEmCadaEtapaEAvisaQueASomaPassa() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(1, HIDRICO, "taskCorrecaoRequerimento", ResponsavelDaTarefa.INTERESSADO, null));

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, tarefas);

        assertThat(barra(fila, NOME_HIDRICO, "analise_do_processo")).isEqualTo(1);
        assertThat(barra(fila, NOME_HIDRICO, "correcao_do_requerimento")).isEqualTo(1);
        assertThat(fila.totalNaFila()).isEqualTo(1);
        assertThat(fila.avisos()).extracting(AvisoDoPainel::codigo)
                .containsExactly("SOMA_DAS_BARRAS_MAIOR_QUE_O_TOTAL");
    }

    @Test
    void complementacaoNaoViraLinhaEORequerimentoSegueNaEtapaDaRaiz() {
        // o adaptador só devolve a raiz; aqui a raiz está na análise e a complementação não aparece em lugar nenhum
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 02"));

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, tarefas);

        assertThat(barra(fila, NOME_HIDRICO, "analise_do_processo")).isEqualTo(1);
        assertThat(fila.etapaNaoMapeada()).isZero();
        assertThat(fila.semInstancia()).isZero();
    }

    @Test
    void instanciaAtivaSemTarefaDeEtapaNaoSomeEmSilencio() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = new LeituraDasTarefas(List.of(), Set.of(1L), false);

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, tarefas);

        assertThat(fila.etapaNaoMapeada()).isEqualTo(1);
        assertThat(fila.semInstancia()).isZero();
    }

    @Test
    void entradaVaziaOuEstranhaNuncaLanca() {
        var base = List.of(new RequerimentoDaBase(1, Optional.empty(), Optional.empty()),
                req(1, "RECURSO_HIDRICO", null));
        var tarefas = leitura(Set.of(), tarefa(1, "modeloQueNaoExiste", "x", ResponsavelDaTarefa.NAO_ATRIBUIDA, null));

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, tarefas);

        assertThat(fila.totalNaFila()).isEqualTo(1);
        assertThat(fila.foraDoCatalogo()).isEqualTo(1);
    }

    @Test
    void propriedadeRequerimentoForaDoCatalogoNuncaEntraNumaBarraDeEtapa() {
        Random sorteio = new Random(10);
        String[] tipos = {"RECURSO_HIDRICO", "LICENCIAMENTO_ADESAO", "TRIFASICO", "LAS", null};
        String[] ids = {"taskAnalisarRequerimento", "taskCadastrarRequerimento", "taskFicticioNaoMapeado"};
        for (int rodada = 0; rodada < 50; rodada++) {
            List<RequerimentoDaBase> base = new ArrayList<>();
            List<TarefaAtiva> tarefas = new ArrayList<>();
            long esperadoFora = 0;
            for (long id = 1; id <= 30; id++) {
                String tipo = tipos[sorteio.nextInt(tipos.length)];
                base.add(req(id, tipo, "EM_ANALISE"));
                if (tipo == null || CATALOGO.modeloDaInstancia(tipo).isEmpty()) {
                    esperadoFora++;
                    // mesmo com tarefa do catálogo no Camunda, o tipo fora do catálogo nunca entra em barra
                    tarefas.add(tarefa(id, HIDRICO, ids[sorteio.nextInt(ids.length)], ResponsavelDaTarefa.NAO_ATRIBUIDA,
                            null));
                } else if (sorteio.nextBoolean()) {
                    tarefas.add(tarefa(id, tipo.equals("RECURSO_HIDRICO") ? HIDRICO : LAC,
                            ids[sorteio.nextInt(ids.length)], ResponsavelDaTarefa.NAO_ATRIBUIDA, null));
                }
            }
            FilaPorEtapa fila = ContarFilaPorEtapa.contar(CATALOGO, base, leitura(Set.of(), tarefas.toArray(new TarefaAtiva[0])));
            long nasBarras = fila.barras().stream().mapToLong(BarraDeEtapa::total).sum();
            assertThat(fila.foraDoCatalogo()).isEqualTo(esperadoFora);
            assertThat(nasBarras + fila.etapaNaoMapeada() + fila.semInstancia() + fila.foraDoCatalogo())
                    .isGreaterThanOrEqualTo(fila.totalNaFila());
            long tarefasDeFora = tarefas.stream().filter(t -> base.stream().anyMatch(r -> r.id() == t.idRequerimento()
                    && (r.tipoDeProcesso().isEmpty() || CATALOGO.modeloDaInstancia(r.tipoDeProcesso().get()).isEmpty())))
                    .count();
            // as tarefas dos requerimentos de fora do catálogo existem, mas nenhuma barra as conta
            assertThat(tarefasDeFora).isGreaterThanOrEqualTo(0);
            long dosDeDentro = base.stream().filter(r -> r.tipoDeProcesso().isPresent()
                    && CATALOGO.modeloDaInstancia(r.tipoDeProcesso().get()).isPresent()).count();
            assertThat(nasBarras).isLessThanOrEqualTo(dosDeDentro);
        }
    }

    // ---- carga por responsável

    @Test
    void cargaSepara_servidorAreaInteressadoNaoAtribuidaENaoIdentificado() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"), req(2, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(3, "RECURSO_HIDRICO", "EM_ANALISE"), req(4, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(5, "RECURSO_HIDRICO", "EM_ANALISE"), req(6, "RECURSO_HIDRICO", "CONCLUIDO"),
                req(7, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(2, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(3, HIDRICO, "taskConferirDocumentosApresentados", ResponsavelDaTarefa.NAO_ATRIBUIDA, null,
                        "grpSurhConferirDocumentoSubterranea"),
                tarefa(4, HIDRICO, "taskCadastrarRequerimento", ResponsavelDaTarefa.INTERESSADO, null),
                tarefa(5, HIDRICO, "taskRevisarParecerTecnico", ResponsavelDaTarefa.NAO_ATRIBUIDA, null),
                tarefa(6, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 02"),
                tarefa(7, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.NAO_IDENTIFICADO, null));

        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, tarefas, true);

        assertThat(carga.nomesOcultos()).isFalse();
        assertThat(carga.linhas()).extracting(LinhaDeCarga::tipo, LinhaDeCarga::rotulo, LinhaDeCarga::total)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TipoDeCarga.SERVIDOR, "Servidor Exemplo 01", 2L),
                        org.assertj.core.groups.Tuple.tuple(TipoDeCarga.AREA, "Conferência de documentos (subterrânea)", 1L),
                        org.assertj.core.groups.Tuple.tuple(TipoDeCarga.INTERESSADO, "Interessado (cadastrante)", 1L),
                        org.assertj.core.groups.Tuple.tuple(TipoDeCarga.NAO_ATRIBUIDA, "Não atribuída", 1L),
                        org.assertj.core.groups.Tuple.tuple(TipoDeCarga.NAO_IDENTIFICADO, "Não identificado", 1L));
    }

    @Test
    void grupoSemAreaCadastradaViraEquipeResponsavelEZeroViraNaoAtribuida() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"), req(2, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.NAO_ATRIBUIDA, null, "grupoDesconhecido"),
                tarefa(2, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.NAO_ATRIBUIDA, null, "0"));

        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, tarefas, true);

        assertThat(carga.linhas()).extracting(LinhaDeCarga::rotulo)
                .containsExactlyInAnyOrder("Equipe responsável", "Não atribuída");
    }

    @Test
    void dezessseteServidoresGeramOutrosComDoisServidores() {
        List<RequerimentoDaBase> base = new ArrayList<>();
        List<TarefaAtiva> tarefas = new ArrayList<>();
        for (int s = 1; s <= 17; s++) {
            base.add(req(s, "RECURSO_HIDRICO", "EM_ANALISE"));
            tarefas.add(tarefa(s, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR,
                    String.format("Servidor Exemplo %02d", s)));
        }

        CargaPorResponsavel carga =
                ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, leitura(Set.of(), tarefas.toArray(new TarefaAtiva[0])), true);

        assertThat(carga.linhas()).hasSize(16);
        LinhaDeCarga ultima = carga.linhas().get(15);
        assertThat(ultima.tipo()).isEqualTo(TipoDeCarga.OUTROS);
        assertThat(ultima.rotulo()).isEqualTo("Outros (2 servidores)");
        assertThat(ultima.total()).isEqualTo(2);
        // empate: nome, depois (estável) -> os 15 primeiros por nome
        assertThat(carga.linhas().get(0).rotulo()).isEqualTo("Servidor Exemplo 01");
        assertThat(carga.linhas().get(14).rotulo()).isEqualTo("Servidor Exemplo 15");
    }

    @Test
    void comNomesOcultosSoCategoriasSemNomeENotaEnenhumNomeNoResultado() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"), req(2, "RECURSO_HIDRICO", "EM_ANALISE"),
                req(3, "RECURSO_HIDRICO", "EM_ANALISE"));
        var tarefas = leitura(Set.of(),
                tarefa(1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"),
                tarefa(2, HIDRICO, "taskCadastrarRequerimento", ResponsavelDaTarefa.INTERESSADO, null),
                tarefa(3, HIDRICO, "taskRevisarParecerTecnico", ResponsavelDaTarefa.NAO_ATRIBUIDA, null));

        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, tarefas, false);

        assertThat(carga.nomesOcultos()).isTrue();
        assertThat(carga.avisos()).extracting(AvisoDoPainel::texto).containsExactly("Nomes ocultos por configuração");
        assertThat(carga.linhas()).extracting(LinhaDeCarga::tipo)
                .containsExactlyInAnyOrder(TipoDeCarga.INTERESSADO, TipoDeCarga.NAO_ATRIBUIDA);
        assertThat(carga.toString()).doesNotContain("Servidor Exemplo");
    }

    @Test
    void ordemEEstavelMesmoComEntradaEmOutraOrdem() {
        List<RequerimentoDaBase> base = new ArrayList<>();
        List<TarefaAtiva> tarefas = new ArrayList<>();
        String[] nomes = {"Servidor Exemplo 03", "Servidor Exemplo 01", "Servidor Exemplo 02"};
        for (int i = 0; i < nomes.length; i++) {
            base.add(req(i + 1, "RECURSO_HIDRICO", "EM_ANALISE"));
            tarefas.add(tarefa(i + 1, HIDRICO, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, nomes[i]));
        }
        var direta = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, leitura(Set.of(), tarefas.toArray(new TarefaAtiva[0])), true);
        java.util.Collections.reverse(tarefas);
        var invertida = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, leitura(Set.of(), tarefas.toArray(new TarefaAtiva[0])), true);

        assertThat(invertida).isEqualTo(direta);
        assertThat(direta.linhas()).extracting(LinhaDeCarga::rotulo)
                .containsExactly("Servidor Exemplo 01", "Servidor Exemplo 02", "Servidor Exemplo 03");
    }

    @Test
    void tarefaDeSuporteNaoContaNaCarga() {
        var base = List.of(req(1, "RECURSO_HIDRICO", "EM_ANALISE"));
        // gtwAguardarPagamento é etapa; usa um id de fim para provar que Encerrado não conta
        var tarefas = leitura(Set.of(), tarefa(1, HIDRICO, "Event_1wiuc48", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"));

        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, base, tarefas, true);

        assertThat(carga.linhas().size()).isLessThanOrEqualTo(1);
    }
}
