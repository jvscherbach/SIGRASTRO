package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Onde o requerimento está agora, pelo Camunda e pelo catálogo (docs/15). Não deriva nada da situação do Licenciamento:
 * o confronto (bloco 5) só acrescenta avisos, sem alterar esta posição.
 *
 * @param estado            ativo, encerrado, sem instância, não suportado ou indisponível
 * @param chaveDoModelo     chave do Camunda; vazia quando o modelo não é suportado
 * @param rotuloDoModelo    rótulo do modelo ("Outorga de recursos hídricos"); vazio quando não suportado
 * @param principal         a etapa principal (menor ordem entre as ativas do ramo principal; ver {@link MontadorDaPosicao})
 * @param emParalelo        os demais pontos ativos ("+N em paralelo"), sem a complementação
 * @param complementacao    complementação em andamento (instância independente), listada à parte
 * @param fim               final do fluxo, quando encerrado e mapeado
 * @param encerradaEm       fim da instância, quando encerrada
 * @param emSuporteDesde    início da tarefa "Suporte" ativa, quando houver
 * @param marcas            fatos que o bloco 5 transforma em avisos
 * @param idsNaoMapeados    ids técnicos sem etapa no catálogo; só para o Administrador
 * @param diagnostico       {@code STATE_} da instância e se havia motivo de exclusão; interno, nunca na resposta
 */
public record PosicaoAtual(
        EstadoDaPosicao estado,
        Optional<String> chaveDoModelo,
        Optional<String> rotuloDoModelo,
        Optional<ElementoDaPosicao> principal,
        List<ElementoDaPosicao> emParalelo,
        Optional<ComplementacaoEmAndamento> complementacao,
        Optional<FinalDoFluxo> fim,
        Optional<LocalDateTime> encerradaEm,
        Optional<LocalDateTime> emSuporteDesde,
        Set<MarcaDaPosicao> marcas,
        List<String> idsNaoMapeados,
        Optional<String> diagnostico) {

    public PosicaoAtual {
        chaveDoModelo = Objects.requireNonNullElse(chaveDoModelo, Optional.empty());
        rotuloDoModelo = Objects.requireNonNullElse(rotuloDoModelo, Optional.empty());
        principal = Objects.requireNonNullElse(principal, Optional.empty());
        emParalelo = List.copyOf(Objects.requireNonNullElse(emParalelo, List.of()));
        complementacao = Objects.requireNonNullElse(complementacao, Optional.empty());
        fim = Objects.requireNonNullElse(fim, Optional.empty());
        encerradaEm = Objects.requireNonNullElse(encerradaEm, Optional.empty());
        emSuporteDesde = Objects.requireNonNullElse(emSuporteDesde, Optional.empty());
        marcas = marcas == null || marcas.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(marcas));
        idsNaoMapeados = List.copyOf(Objects.requireNonNullElse(idsNaoMapeados, List.of()));
        diagnostico = Objects.requireNonNullElse(diagnostico, Optional.empty());
    }

    public static PosicaoAtual naoSuportado() {
        return semPontos(EstadoDaPosicao.NAO_SUPORTADO, null, Set.of());
    }

    public static PosicaoAtual indisponivel(ModeloDeFluxo modelo) {
        return semPontos(EstadoDaPosicao.INDISPONIVEL, modelo, Set.of());
    }

    public static PosicaoAtual semInstancia(ModeloDeFluxo modelo) {
        return semPontos(EstadoDaPosicao.SEM_INSTANCIA, modelo, Set.of(MarcaDaPosicao.SEM_INSTANCIA));
    }

    private static PosicaoAtual semPontos(EstadoDaPosicao estado, ModeloDeFluxo modelo, Set<MarcaDaPosicao> marcas) {
        return new PosicaoAtual(estado, Optional.ofNullable(modelo).map(ModeloDeFluxo::chave),
                Optional.ofNullable(modelo).map(ModeloDeFluxo::rotulo), Optional.empty(), List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), marcas, List.of(), Optional.empty());
    }

    /** A mesma posição com cada ponto transformado (ex.: responsável resolvido, parâmetros de exibição). */
    public PosicaoAtual comPontos(java.util.function.UnaryOperator<ElementoDaPosicao> transformar) {
        return new PosicaoAtual(estado, chaveDoModelo, rotuloDoModelo, principal.map(transformar),
                emParalelo.stream().map(transformar).toList(), complementacao, fim, encerradaEm, emSuporteDesde, marcas,
                idsNaoMapeados, diagnostico);
    }

    /** Todos os pontos ativos: a principal e os paralelos. */
    public List<ElementoDaPosicao> pontos() {
        return java.util.stream.Stream.concat(principal.stream(), emParalelo.stream()).toList();
    }

    /** Complementação: quantas instâncias ativas e desde quando (a mais antiga). */
    public record ComplementacaoEmAndamento(int quantidade, LocalDateTime desde) {}
}
