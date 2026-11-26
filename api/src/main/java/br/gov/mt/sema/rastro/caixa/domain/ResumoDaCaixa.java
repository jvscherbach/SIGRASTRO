package br.gov.mt.sema.rastro.caixa.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;

/**
 * Resumo da caixa (S11, bloco 5): total na posse, quantos por etapa (as etapas de posse do catálogo, inclusive as zeradas) e
 * quantos estão com o interessado em correção.
 *
 * @param total          todos os itens da caixa (na análise ou revisão e com o interessado)
 * @param porEtapa       análise e revisão, na ordem do catálogo
 * @param comInteressado em correção, com o interessado, e a última análise foi do analista
 */
public record ResumoDaCaixa(int total, List<EtapaDoResumo> porEtapa, int comInteressado) {

    /** Uma etapa de posse com a contagem. */
    public record EtapaDoResumo(String id, String rotulo, int quantidade) {
    }

    public ResumoDaCaixa {
        porEtapa = List.copyOf(porEtapa);
    }

    public static ResumoDaCaixa de(List<ItemDaCaixa> itens, CatalogoDeEtapas catalogo) {
        List<EtapaDoResumo> etapas = new ArrayList<>();
        for (String id : DefinicaoDaPosse.ETAPAS_DE_POSSE) {
            int quantos = (int) itens.stream().filter(i -> i.estado().etapa().filter(id::equals).isPresent()).count();
            etapas.add(new EtapaDoResumo(id, rotulo(id, catalogo), quantos));
        }
        int comInteressado = (int) itens.stream().filter(i -> !i.estado().comAnalista()).count();
        return new ResumoDaCaixa(itens.size(), etapas, comInteressado);
    }

    /** Rótulo do catálogo; sem a etapa, o próprio id (nunca inventa texto). */
    private static String rotulo(String id, CatalogoDeEtapas catalogo) {
        for (ModeloDeFluxo modelo : catalogo.modelos()) {
            Optional<Etapa> etapa = modelo.etapa(id);
            if (etapa.isPresent()) {
                return etapa.get().rotulo();
            }
        }
        return id;
    }
}
