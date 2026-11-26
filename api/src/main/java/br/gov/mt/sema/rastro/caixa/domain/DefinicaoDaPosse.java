package br.gov.mt.sema.rastro.caixa.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;

/**
 * Quais tarefas do Camunda contam para a posse do analista (S11), tiradas do catálogo de etapas: nada de id inventado aqui.
 *
 * @param chavesDosModelos     chaves de definição dos modelos que têm etapa de posse (o LAC não tem análise: fica de fora)
 * @param atividadesDePosse    {@code TASK_DEF_KEY_} das etapas "Análise do processo" e "Revisão do parecer técnico"
 * @param atividadesDeCorrecao {@code TASK_DEF_KEY_} da etapa "Correção do requerimento" (com o interessado)
 */
public record DefinicaoDaPosse(Set<String> chavesDosModelos, Set<String> atividadesDePosse,
        Set<String> atividadesDeCorrecao) {

    /** Id estável da etapa de correção no catálogo (docs/15). */
    public static final String ETAPA_CORRECAO = "correcao_do_requerimento";
    /** Ids estáveis das etapas em que a tarefa atribuída ao analista é dele. */
    public static final List<String> ETAPAS_DE_POSSE = List.of(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO);

    public DefinicaoDaPosse {
        chavesDosModelos = Set.copyOf(chavesDosModelos);
        atividadesDePosse = Set.copyOf(atividadesDePosse);
        atividadesDeCorrecao = Set.copyOf(atividadesDeCorrecao);
    }

    /** Monta a definição pelo catálogo; etapa que não existir no catálogo simplesmente não conta. */
    public static DefinicaoDaPosse doCatalogo(CatalogoDeEtapas catalogo) {
        Set<String> modelos = new LinkedHashSet<>();
        Set<String> posse = new LinkedHashSet<>();
        Set<String> correcao = new LinkedHashSet<>();
        for (ModeloDeFluxo modelo : catalogo.modelos()) {
            boolean temPosse = false;
            for (String id : ETAPAS_DE_POSSE) {
                Optional<Etapa> etapa = modelo.etapa(id);
                if (etapa.isPresent()) {
                    posse.addAll(etapa.get().idsTecnicos());
                    temPosse = true;
                }
            }
            if (temPosse) {
                modelos.add(modelo.chave());
                modelo.etapa(ETAPA_CORRECAO).ifPresent(e -> correcao.addAll(e.idsTecnicos()));
            }
        }
        return new DefinicaoDaPosse(modelos, posse, correcao);
    }

    /** Sem modelo ou sem atividade de posse não há o que ler. */
    public boolean vazia() {
        return chavesDosModelos.isEmpty() || atividadesDePosse.isEmpty();
    }
}
