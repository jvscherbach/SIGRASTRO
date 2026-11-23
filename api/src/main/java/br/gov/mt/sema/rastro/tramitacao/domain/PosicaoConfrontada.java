package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * A posição do fluxo, intacta, mais a situação do Licenciamento e os avisos do confronto (S7, bloco 5). A etapa do fluxo
 * sempre prevalece: a situação só aparece ao lado, e as divergências viram avisos.
 *
 * @param posicao                 a posição exatamente como o montador a entregou
 * @param situacaoDoLicenciamento {@code REQUERIMENTO.SITUACAO_PROCESSO}, quando lida
 * @param avisos                  avisos em ordem: divergências do confronto, depois os fatos da leitura
 */
public record PosicaoConfrontada(PosicaoAtual posicao, Optional<String> situacaoDoLicenciamento, List<Aviso> avisos) {

    public PosicaoConfrontada {
        Objects.requireNonNull(posicao);
        situacaoDoLicenciamento = Objects.requireNonNullElse(situacaoDoLicenciamento, Optional.empty());
        avisos = List.copyOf(Objects.requireNonNullElse(avisos, List.of()));
    }

    /** Mesma situação e avisos, com a posição transformada (ex.: parâmetros de exibição na leitura). */
    public PosicaoConfrontada comPosicao(UnaryOperator<PosicaoAtual> transformar) {
        return new PosicaoConfrontada(transformar.apply(posicao), situacaoDoLicenciamento, avisos);
    }

    /** Mesma posição e situação, com um aviso a mais no fim (S9: taxa vencida). Nada mais muda. */
    public PosicaoConfrontada comAviso(Aviso aviso) {
        java.util.List<Aviso> todos = new java.util.ArrayList<>(avisos);
        todos.add(Objects.requireNonNull(aviso));
        return new PosicaoConfrontada(posicao, situacaoDoLicenciamento, todos);
    }
}
