package br.gov.mt.sema.rastro.painel.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel.LinhaDeAnalista;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import org.junit.jupiter.api.Test;

/** Lista por analista do painel (S11, bloco 9): os totais batem com as barras da carga, em todos os casos. */
class ListarPorResponsavelTest {

    private static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    private static final CatalogoDeGrupos GRUPOS = id -> Optional.empty();
    private static final String HIDRICO = "procOutorgaAguaSupSub";
    private static final byte[] SAL = "sal-de-teste-com-mais-de-16-bytes".getBytes();

    private static RequerimentoDaBase req(long id, String interessado) {
        return new RequerimentoDaBase(id, Optional.of("RECURSO_HIDRICO"), Optional.of("EM_ANALISE"),
                Optional.of("REQ-" + id), Optional.ofNullable(interessado), Optional.of(LocalDate.of(2026, 3, 1)));
    }

    private static TarefaAtiva tarefa(long id, String idTarefa, ResponsavelDaTarefa r, String nome) {
        return new TarefaAtiva(id, HIDRICO, idTarefa, r, Optional.ofNullable(nome), List.of());
    }

    private record Cenario(List<RequerimentoDaBase> base, LeituraDasTarefas leitura) {
    }

    /** 17 servidores (os 15 maiores aparecem; 2 viram "Outros"), mais categorias sem nome e um paralelo. */
    private static Cenario cenario() {
        List<RequerimentoDaBase> base = new ArrayList<>();
        List<TarefaAtiva> tarefas = new ArrayList<>();
        long id = 100;
        for (int s = 1; s <= 17; s++) {
            int quantos = 20 - s; // 19 ... 3: o 16 e o 17 ficam de fora dos 15 maiores
            for (int k = 0; k < quantos; k++) {
                base.add(req(id, "Interessado Exemplo " + s));
                tarefas.add(tarefa(id, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR,
                        "Servidor Exemplo " + String.format("%02d", s)));
                id++;
            }
        }
        // categorias sem nome
        base.add(req(1, "Interessado Exemplo A"));
        tarefas.add(tarefa(1, "taskAnalisarRequerimento", ResponsavelDaTarefa.NAO_ATRIBUIDA, null));
        base.add(req(2, "Interessado Exemplo B"));
        tarefas.add(tarefa(2, "taskAnalisarRequerimento", ResponsavelDaTarefa.INTERESSADO, null));
        // um requerimento em duas etapas paralelas do mesmo servidor 01: conta uma vez
        base.add(req(3, "Interessado Exemplo C"));
        tarefas.add(tarefa(3, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"));
        tarefas.add(tarefa(3, "taskRevisarParecerTecnico", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"));
        // fora do catálogo, sem instância e concluído: nunca entram
        base.add(new RequerimentoDaBase(4, Optional.of("TRIFASICO"), Optional.of("EM_ANALISE")));
        base.add(req(5, "Interessado Exemplo D"));
        base.add(new RequerimentoDaBase(6, Optional.of("RECURSO_HIDRICO"), Optional.of("CONCLUIDO")));
        tarefas.add(tarefa(6, "taskAnalisarRequerimento", ResponsavelDaTarefa.SERVIDOR, "Servidor Exemplo 01"));
        return new Cenario(base, new LeituraDasTarefas(tarefas, Set.of(5L), false));
    }

    @Test
    void totalDeCadaLinhaEhOMesmoDaBarraComOutrosEAsCategorias() {
        Cenario c = cenario();
        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, c.base(), c.leitura(), true);

        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, c.base(), c.leitura(), carga, SAL);

        assertThat(lista.analistas()).hasSameSizeAs(carga.linhas());
        for (int i = 0; i < carga.linhas().size(); i++) {
            LinhaDeCarga barra = carga.linhas().get(i);
            LinhaDeAnalista linha = lista.analistas().get(i);
            assertThat(linha.rotulo()).isEqualTo(barra.rotulo());
            assertThat(linha.total()).isEqualTo(barra.total());
            // a lista tem exatamente tantos requerimentos quanto a barra diz
            assertThat(lista.requerimentos(linha.chave(), Optional.empty(), "")).hasSize((int) barra.total());
        }
        assertThat(lista.analistas()).extracting(LinhaDeAnalista::tipo).contains(TipoDeCarga.OUTROS,
                TipoDeCarga.NAO_ATRIBUIDA, TipoDeCarga.INTERESSADO);
        LinhaDeAnalista outros = lista.analistas().stream().filter(l -> l.tipo() == TipoDeCarga.OUTROS).findFirst()
                .orElseThrow();
        assertThat(outros.total()).isEqualTo(3 + 4); // servidores 16 e 17 (4 e 3 requerimentos)
        assertThat(lista.requerimentos(outros.chave(), Optional.empty(), "")).hasSize(7);
    }

    @Test
    void requerimentoEmEtapasParalelasContaUmaVezEMostraAsDuasEtapas() {
        Cenario c = cenario();
        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, c.base(), c.leitura(), true);
        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, c.base(), c.leitura(), carga, SAL);
        String servidor01 = lista.analistas().stream().filter(l -> l.rotulo().equals("Servidor Exemplo 01"))
                .findFirst().orElseThrow().chave();

        var paralelos = lista.requerimentos(servidor01, Optional.empty(), "Interessado Exemplo C");

        assertThat(paralelos).hasSize(1);
        assertThat(paralelos.get(0).etapas()).hasSize(2);
        assertThat(paralelos.get(0).numero()).contains("REQ-3");
        assertThat(paralelos.get(0).protocolo()).contains(LocalDate.of(2026, 3, 1));
        // o filtro por etapa acha as duas
        assertThat(lista.requerimentos(servidor01, Optional.of(paralelos.get(0).etapas().get(1)), "")).extracting(
                ListaPorResponsavel.RequerimentoDaLista::id).contains(3L);
        // concluído nunca entra, mesmo com tarefa
        assertThat(lista.requerimentos(servidor01, Optional.empty(), "REQ-6")).isEmpty();
    }

    @Test
    void foraDoCatalogoSemInstanciaEConcluidosNaoApareceEmLugarNenhum() {
        Cenario c = cenario();
        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, c.base(), c.leitura(), true);
        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, c.base(), c.leitura(), carga, SAL);

        Set<Long> ids = new java.util.HashSet<>();
        lista.porChave().values().forEach(l -> l.forEach(r -> ids.add(r.id())));

        assertThat(ids).doesNotContain(4L, 5L, 6L);
    }

    @Test
    void buscaPorNomeSemAcentoENumeroEFiltroDeEtapa() {
        Cenario c = cenario();
        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, c.base(), c.leitura(), true);
        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, c.base(), c.leitura(), carga, SAL);
        String naoAtribuida = lista.analistas().stream().filter(l -> l.tipo() == TipoDeCarga.NAO_ATRIBUIDA).findFirst()
                .orElseThrow().chave();

        assertThat(lista.requerimentos(naoAtribuida, Optional.empty(), "  INTERESSADO exemplo a ")).hasSize(1);
        assertThat(lista.requerimentos(naoAtribuida, Optional.empty(), "req-1")).hasSize(1);
        assertThat(lista.requerimentos(naoAtribuida, Optional.empty(), "inexistente")).isEmpty();
        assertThat(lista.requerimentos(naoAtribuida, Optional.of("Etapa inexistente"), "")).isEmpty();
        assertThat(lista.etapas()).isNotEmpty().endsWith("Etapa não mapeada");
    }

    @Test
    void nomesOcultosSoTemCategoriasENenhumServidorEAlcancavel() {
        Cenario c = cenario();
        CargaPorResponsavel semNomes = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, c.base(), c.leitura(), false);

        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, c.base(), c.leitura(), semNomes, SAL);

        assertThat(lista.analistas()).extracting(LinhaDeAnalista::tipo)
                .doesNotContain(TipoDeCarga.SERVIDOR, TipoDeCarga.OUTROS);
        assertThat(lista.analistas()).extracting(LinhaDeAnalista::total)
                .containsExactlyElementsOf(semNomes.linhas().stream().map(LinhaDeCarga::total).toList());
        assertThat(lista.porChave().values().stream().flatMap(List::stream).map(ListaPorResponsavel.RequerimentoDaLista::responsavel))
                .noneMatch(r -> r.contains("Servidor Exemplo"));
        assertThat(lista.toString()).doesNotContain("Servidor Exemplo");
    }

    @Test
    void chaveEOpacaEstavelComOMesmoSalEDiferenteComOutro() {
        String a = ListarPorResponsavel.chaveOpaca(TipoDeCarga.SERVIDOR, "Servidor Exemplo 01", SAL);
        String b = ListarPorResponsavel.chaveOpaca(TipoDeCarga.SERVIDOR, "Servidor Exemplo 01", SAL);
        String outroSal = ListarPorResponsavel.chaveOpaca(TipoDeCarga.SERVIDOR, "Servidor Exemplo 01",
                "outro-sal-com-mais-de-16-bytes".getBytes());

        assertThat(a).isEqualTo(b).hasSize(16).matches("[0-9a-f]{16}").doesNotContain("Exemplo");
        assertThat(outroSal).isNotEqualTo(a);
    }

    @Test
    void semDadosNaoQuebra() {
        CargaPorResponsavel carga = ContarCargaPorResponsavel.contar(CATALOGO, GRUPOS, List.of(),
                LeituraDasTarefas.NENHUMA, true);

        ListaPorResponsavel lista = ListarPorResponsavel.listar(CATALOGO, GRUPOS, List.of(), LeituraDasTarefas.NENHUMA,
                carga, SAL);

        assertThat(lista.analistas()).isEmpty();
        assertThat(lista.requerimentos("qualquer", Optional.empty(), "")).isEmpty();
    }
}
