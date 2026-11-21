package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Com quem a etapa está, sem CPF (docs/15, "Responsável"). O texto ao usuário é o nome (quando há e o parâmetro deixa),
 * senão a área, senão o papel.
 *
 * @param tipo              servidor, não identificado, interessado, área, equipe, sistema ou vários
 * @param papel             rótulo do papel ("Analista", "Chefia", "Interessado (cadastrante)", "Equipe responsável",
 *                          "Sistema", "Responsável não identificado")
 * @param area              área do grupo candidato, quando conhecida
 * @param nome              nome do servidor; vazio quando não há, não foi identificado ou o parâmetro o esconde
 * @param participantes     multi-instância ou equipe: cada um com o estado (concluiu/pendente)
 * @param maisParticipantes quantos participantes ficaram de fora pelo limite de nomes ("e mais N")
 */
public record ResponsavelDaEtapa(
        Tipo tipo,
        String papel,
        Optional<String> area,
        Optional<String> nome,
        List<Participante> participantes,
        int maisParticipantes) {

    public static final String INTERESSADO = "Interessado (cadastrante)";
    public static final String NAO_IDENTIFICADO = "Responsável não identificado";
    public static final String EQUIPE = "Equipe responsável";
    public static final String SISTEMA = "Sistema";

    public enum Tipo { SERVIDOR, NAO_IDENTIFICADO, INTERESSADO, AREA, EQUIPE, SISTEMA, VARIOS }

    public ResponsavelDaEtapa {
        area = Objects.requireNonNullElse(area, Optional.empty());
        nome = Objects.requireNonNullElse(nome, Optional.empty());
        participantes = List.copyOf(Objects.requireNonNullElse(participantes, List.of()));
    }

    static ResponsavelDaEtapa simples(Tipo tipo, String papel, Optional<String> area, Optional<String> nome) {
        return new ResponsavelDaEtapa(tipo, papel, area, nome, List.of(), 0);
    }

    /** Texto curto de "com quem": nome, senão área, senão papel. */
    public String texto() {
        return nome.or(() -> area).orElse(papel);
    }

    /**
     * Aplica os parâmetros de exibição na leitura (valem na próxima leitura, mesmo com a posição em cache): sem nome, só
     * área e papel; no máximo {@code maximoDeNomes} participantes, o resto em {@link #maisParticipantes()}.
     */
    public ResponsavelDaEtapa conforme(boolean mostrarNome, int maximoDeNomes) {
        int maximo = Math.max(0, maximoDeNomes);
        List<Participante> visiveis = participantes.stream().limit(maximo)
                .map(p -> mostrarNome ? p : p.semNome()).toList();
        return new ResponsavelDaEtapa(tipo, papel, area, mostrarNome ? nome : Optional.empty(), visiveis,
                maisParticipantes + participantes.size() - visiveis.size());
    }

    /**
     * Um analista ou signatário da equipe.
     *
     * @param nome     nome do servidor; vazio se não identificado ou escondido
     * @param papel    papel ou "Responsável não identificado"
     * @param concluiu já concluiu a tarefa do ciclo (senão, pendente)
     */
    public record Participante(Optional<String> nome, String papel, boolean concluiu) {

        public Participante {
            nome = Objects.requireNonNullElse(nome, Optional.empty());
        }

        Participante semNome() {
            return new Participante(Optional.empty(), papel, concluiu);
        }
    }
}
