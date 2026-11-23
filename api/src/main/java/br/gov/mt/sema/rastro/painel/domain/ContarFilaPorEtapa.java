package br.gov.mt.sema.rastro.painel.domain;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gráfico 2 (função pura, nunca lança). Regras:
 * <ul>
 *   <li>só entram os requerimentos fora de concluído, cancelado e arquivado;</li>
 *   <li>tipo de processo fora do catálogo: "Fora do catálogo" (e nada mais);</li>
 *   <li>sem instância ativa no Camunda: "Sem instância";</li>
 *   <li>cada tarefa é resolvida pelo catálogo; conta requerimentos <b>distintos</b> por etapa, na ordem do catálogo, com as
 *       barras zeradas; um requerimento em etapas paralelas conta uma vez em cada;</li>
 *   <li>id sem etapa, modelo desconhecido, ou instância ativa sem nenhuma tarefa de etapa: "Etapa não mapeada";</li>
 *   <li>tarefa de suporte e de fim (ignoradas pelo catálogo) não viram barra; a complementação nem chega aqui (o adaptador
 *       só devolve a instância raiz).</li>
 * </ul>
 */
public final class ContarFilaPorEtapa {

    private ContarFilaPorEtapa() {
    }

    public static FilaPorEtapa contar(CatalogoDeEtapas catalogo, List<RequerimentoDaBase> base, LeituraDasTarefas leitura) {
        Map<Long, List<TarefaAtiva>> tarefasPorRequerimento = new HashMap<>();
        for (TarefaAtiva t : leitura.tarefas()) {
            tarefasPorRequerimento.computeIfAbsent(t.idRequerimento(), k -> new ArrayList<>()).add(t);
        }
        Map<String, Set<Long>> porEtapa = new HashMap<>();
        long naFila = 0;
        long foraDoCatalogo = 0;
        long semInstancia = 0;
        long naoMapeada = 0;
        Set<Long> jaContados = new HashSet<>();
        for (RequerimentoDaBase req : base) {
            if (SituacoesForaDaFila.estaForaDaFila(req.situacao()) || !jaContados.add(req.id())) {
                continue;
            }
            naFila++;
            boolean noCatalogo = req.tipoDeProcesso().flatMap(catalogo::modeloDaInstancia).isPresent();
            if (!noCatalogo) {
                foraDoCatalogo++;
                continue;
            }
            List<TarefaAtiva> tarefas = tarefasPorRequerimento.getOrDefault(req.id(), List.of());
            if (tarefas.isEmpty() && !leitura.comInstancia().contains(req.id())) {
                semInstancia++;
                continue;
            }
            boolean algumaBarra = false;
            boolean algumaNaoMapeada = false;
            for (TarefaAtiva t : tarefas) {
                ResolucaoDeEtapa r = catalogo.resolver(t.chaveDoModelo(), t.idDaTarefa(), Set.of());
                if (r instanceof ResolucaoDeEtapa.NaEtapa na) {
                    porEtapa.computeIfAbsent(chaveDaBarra(t.chaveDoModelo(), na.etapa().id()), k -> new HashSet<>())
                            .add(req.id());
                    algumaBarra = true;
                } else if (r instanceof ResolucaoDeEtapa.NaoMapeado || r instanceof ResolucaoDeEtapa.NaoSuportado) {
                    algumaNaoMapeada = true;
                }
            }
            if (algumaNaoMapeada || !algumaBarra) {
                naoMapeada++;
            }
        }
        Map<String, BarraDeEtapa> barras = new LinkedHashMap<>();
        long somaDasBarras = 0;
        for (ModeloDeFluxo modelo : catalogo.modelos()) {
            for (var etapa : modelo.etapas()) {
                String chave = chaveDaBarra(modelo.chave(), etapa.id());
                long total = porEtapa.getOrDefault(chave, Set.of()).size();
                somaDasBarras += total;
                barras.put(chave, new BarraDeEtapa(modelo.rotulo(), etapa.id(), etapa.rotulo(), total));
            }
        }
        List<AvisoDoPainel> avisos = new ArrayList<>();
        if (somaDasBarras > naFila) {
            avisos.add(new AvisoDoPainel(AvisoDoPainel.SOMA_DAS_BARRAS_MAIOR_QUE_O_TOTAL,
                    "A soma das barras passa do total de requerimentos na fila: um requerimento em etapas paralelas "
                            + "conta uma vez em cada etapa."));
        }
        return new FilaPorEtapa(new ArrayList<>(barras.values()), foraDoCatalogo, semInstancia, naoMapeada, naFila, avisos);
    }

    private static String chaveDaBarra(String modelo, String etapa) {
        return modelo + "/" + etapa;
    }
}
