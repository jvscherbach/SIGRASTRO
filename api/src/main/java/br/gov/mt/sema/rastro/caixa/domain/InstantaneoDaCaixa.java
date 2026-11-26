package br.gov.mt.sema.rastro.caixa.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * O retrato da caixa em memória (S11, bloco 5): imutável, trocado de uma vez, com a mesma máquina de estados do painel da S10.
 * Uma requisição só lê este objeto; nunca consulta o SIGA nem o Camunda. Não tem campo de CPF: o dono dos itens é a chave
 * opaca.
 *
 * @param estado    {@code PREPARANDO}, {@code PRONTO}, {@code DESATUALIZADO} ou {@code INDISPONIVEL}
 * @param geradoEm  quando o último retrato bom foi gerado
 * @param falhouEm  quando a última tentativa falhou (só em {@code DESATUALIZADO} e {@code INDISPONIVEL})
 * @param incompleta a leitura passou do teto de requerimentos ou de linhas: a caixa pode estar parcial
 * @param itens     por analista, os requerimentos na posse dele (ou com o interessado), na ordem do id
 * @param origens   por analista, a origem dos itens avisados na geração (só os que chegaram depois do último visto)
 */
public record InstantaneoDaCaixa(Estado estado, Optional<Instant> geradoEm, Optional<Instant> falhouEm,
        boolean incompleta, Map<ChaveDoResponsavel, List<ItemDaCaixa>> itens,
        Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> origens) {

    /** Estado do instantâneo da caixa. */
    public enum Estado {
        /** Ainda não houve a primeira leitura. */
        PREPARANDO,
        /** A última leitura deu certo. */
        PRONTO,
        /** A última tentativa falhou; vale o último retrato bom, com a data dele. */
        DESATUALIZADO,
        /** Nunca houve leitura boa. */
        INDISPONIVEL
    }

    public static final InstantaneoDaCaixa PREPARANDO = new InstantaneoDaCaixa(Estado.PREPARANDO, Optional.empty(),
            Optional.empty(), false, Map.of(), Map.of());

    public InstantaneoDaCaixa {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(geradoEm);
        Objects.requireNonNull(falhouEm);
        Map<ChaveDoResponsavel, List<ItemDaCaixa>> copiaItens = new HashMap<>();
        itens.forEach((k, v) -> copiaItens.put(k, List.copyOf(v)));
        itens = Map.copyOf(copiaItens);
        Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> copiaOrigens = new HashMap<>();
        origens.forEach((k, v) -> copiaOrigens.put(k, Map.copyOf(v)));
        origens = Map.copyOf(copiaOrigens);
    }

    /** Um retrato bom, recém-gerado. */
    public static InstantaneoDaCaixa pronto(Instant geradoEm, boolean incompleta,
            Map<ChaveDoResponsavel, List<ItemDaCaixa>> itens, Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> origens) {
        return new InstantaneoDaCaixa(Estado.PRONTO, Optional.of(geradoEm), Optional.empty(), incompleta, itens, origens);
    }

    /** Há um retrato bom guardado (pronto ou desatualizado). */
    public boolean temDadosBons() {
        return estado == Estado.PRONTO || estado == Estado.DESATUALIZADO;
    }

    /** Os itens da caixa do analista; vazio se não há nenhum. */
    public List<ItemDaCaixa> itensDe(ChaveDoResponsavel analista) {
        return itens.getOrDefault(analista, List.of());
    }

    /** A origem do item avisado; vazio se não foi lida (quem mostra diz "Mudou de etapa", nunca inventa). */
    public Optional<OrigemDoRetorno> origemDe(ChaveDoResponsavel analista, long idDoRequerimento) {
        return Optional.ofNullable(origens.getOrDefault(analista, Map.of()).get(idDoRequerimento));
    }

    /** A leitura falhou em {@code quando}: mantém o último bom (DESATUALIZADO) ou fica INDISPONIVEL se nunca houve. */
    public InstantaneoDaCaixa aposFalha(Instant quando) {
        if (temDadosBons()) {
            return new InstantaneoDaCaixa(Estado.DESATUALIZADO, geradoEm, Optional.of(quando), incompleta, itens, origens);
        }
        return new InstantaneoDaCaixa(Estado.INDISPONIVEL, Optional.empty(), Optional.of(quando), false, Map.of(), Map.of());
    }
}
