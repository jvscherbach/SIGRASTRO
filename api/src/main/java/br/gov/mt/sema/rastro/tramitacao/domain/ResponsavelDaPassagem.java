package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Com quem a etapa esteve numa passagem (S8, bloco 3): uma pessoa ou papel, com início, fim e estado. Nunca o CPF: o nome
 * vem do SIGA pela identificação do bloco 4 da S7, e some com {@link #semNome()} quando o parâmetro o esconde.
 *
 * @param tipo  servidor, interessado, não identificado, equipe ou sistema (como na posição)
 * @param papel "Analista", "Chefia", "Signatário", "Interessado (cadastrante)", "Equipe responsável", "Sistema"...
 * @param fim   vazio = pendente
 */
public record ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo tipo, String papel, Optional<String> nome,
        LocalDateTime inicio, LocalDateTime fim, Estado estado) {

    public ResponsavelDaPassagem {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(estado, "estado");
        nome = Objects.requireNonNullElse(nome, Optional.empty());
    }

    /**
     * {@code ENCERRADA_SEM_ACAO}: a tarefa foi encerrada pela condição de término do ciclo, por borda ou recusa
     * ({@code DELETE_REASON_} diferente de {@code completed}); não conta como percorrida (regra técnica 10).
     */
    public enum Estado { CONCLUIU, PENDENTE, ENCERRADA_SEM_ACAO }

    public ResponsavelDaPassagem semNome() {
        return new ResponsavelDaPassagem(tipo, papel, Optional.empty(), inicio, fim, estado);
    }
}
