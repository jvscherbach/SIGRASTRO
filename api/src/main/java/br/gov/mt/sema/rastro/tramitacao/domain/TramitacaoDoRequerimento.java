package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A tramitação completa de um requerimento, pronta para a API (S8; o contrato HTTP é do bloco 6). Ou o caminho exato do
 * Camunda ({@link #caminho()} + {@link #desfecho()}), ou o histórico aproximado do plano B ({@link #planoB()}), nunca os
 * dois: o plano B só existe no estado {@code SEM_INSTANCIA}.
 *
 * @param estado    {@code ATIVO}, {@code ENCERRADO}, {@code SEM_INSTANCIA}, {@code NAO_SUPORTADO} ou {@code INDISPONIVEL}
 * @param modelo    chave do modelo do catálogo, quando o tipo é suportado
 * @param situacao  {@code SITUACAO_PROCESSO}, quando lida (mesmo no indisponível, se já tinha sido lida)
 * @param avisos    do desfecho (finais, divergências), {@code VARIAS_INSTANCIAS} e {@code HISTORICO_APROXIMADO}
 */
public record TramitacaoDoRequerimento(
        Estado estado,
        Optional<String> modelo,
        Optional<String> situacao,
        CaminhoReconstruido caminho,
        Optional<DesfechoDoCaminho> desfecho,
        Optional<HistoricoAproximado> planoB,
        List<Aviso> avisos) {

    public enum Estado { ATIVO, ENCERRADO, SEM_INSTANCIA, NAO_SUPORTADO, INDISPONIVEL }

    public TramitacaoDoRequerimento {
        Objects.requireNonNull(estado, "estado");
        modelo = Objects.requireNonNullElse(modelo, Optional.empty());
        situacao = Objects.requireNonNullElse(situacao, Optional.empty());
        caminho = Objects.requireNonNullElse(caminho, CaminhoReconstruido.vazio());
        desfecho = Objects.requireNonNullElse(desfecho, Optional.empty());
        planoB = Objects.requireNonNullElse(planoB, Optional.empty());
        avisos = List.copyOf(Objects.requireNonNullElse(avisos, List.of()));
        if (planoB.isPresent() && estado != Estado.SEM_INSTANCIA) {
            throw new IllegalArgumentException("plano B só existe sem instância no Camunda");
        }
    }

    /** Sem o nome dos servidores em nenhuma passagem nem participante (RN12, parâmetro de nome). */
    public TramitacaoDoRequerimento semNomes() {
        return new TramitacaoDoRequerimento(estado, modelo, situacao, caminho.semNomes(), desfecho, planoB, avisos);
    }

    /** Dado aproximado: só no plano B. */
    public boolean aproximado() {
        return planoB.isPresent();
    }

    public static TramitacaoDoRequerimento naoSuportado(Optional<String> situacao) {
        return new TramitacaoDoRequerimento(Estado.NAO_SUPORTADO, Optional.empty(), situacao, null, Optional.empty(),
                Optional.empty(), List.of());
    }

    public static TramitacaoDoRequerimento indisponivel(Optional<String> modelo, Optional<String> situacao) {
        return new TramitacaoDoRequerimento(Estado.INDISPONIVEL, modelo, situacao, null, Optional.empty(),
                Optional.empty(), List.of());
    }
}
