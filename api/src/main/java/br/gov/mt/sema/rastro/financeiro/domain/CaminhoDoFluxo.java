package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * O caminho <b>exato</b> da tramitação visto pelo financeiro (S9, blocos 4 e 5), na ordem das passagens. Sem caminho
 * exato (Camunda fora, plano B, tipo não suportado), {@link #NENHUM}: o financeiro perde só a etiqueta "da correção", a
 * data de apoio da isenção e as marcas nas passagens.
 *
 * <p>Os ids de etapa abaixo são os do catálogo (`resources/rastro/etapas/*.yml`); mudou o catálogo, mude aqui.
 */
public record CaminhoDoFluxo(List<PassagemDoFluxo> passagens) {

    public static final CaminhoDoFluxo NENHUM = new CaminhoDoFluxo(List.of());

    static final String ISENCAO = "isencao_confirmada";
    static final Set<String> EMISSOES = Set.of("emissao_da_taxa", "emissao_da_taxa_correcao");
    static final Set<String> ESPERAS = Set.of("aguardando_pagamento", "aguardando_pagamento_correcao");

    public CaminhoDoFluxo {
        passagens = List.copyOf(passagens);
    }

    /** Entradas das passagens "Isenção confirmada", em ordem. */
    public List<LocalDateTime> entradasDeIsencao() {
        return passagens.stream().filter(p -> p.eh(ISENCAO)).map(PassagemDoFluxo::entrada).sorted().toList();
    }

    /** Entrada da primeira passagem do ramo de correção; vazia se não houve correção. */
    public Optional<LocalDateTime> entradaNaCorrecao() {
        return passagens.stream().filter(PassagemDoFluxo::correcao).map(PassagemDoFluxo::entrada).sorted().findFirst();
    }
}
