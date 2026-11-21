package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * O caminho percorrido em etapas simplificadas (S8, bloco 3), pronto para a linha do tempo e o fluxograma. O final (bola
 * preta) é do bloco 4; o plano B é do bloco 5.
 *
 * @param passagens       ordenadas por entrada
 * @param complementacoes complementações paralelas (instâncias independentes), por início
 * @param taxaPrincipal   taxável ou isento pela <b>rota</b> do ramo principal
 * @param taxaDaCorrecao  o ramo da correção teve nova taxa? vazio = não houve correção
 * @param idsNaoMapeados  ids técnicos fora do catálogo vistos no caminho (só para o Administrador)
 * @param ignoradas       atividades de ids ignorados pelo catálogo (suporte, ruído), fora do caminho
 * @param diagnosticos    divergências só para o Administrador ({@link Diagnostico})
 * @param truncado        alguma instância passou do limite de linhas: o caminho está incompleto
 * @param instanciasRaiz  quantas instâncias raiz o requerimento tem (mais de uma: vale a mais recente)
 */
public record CaminhoReconstruido(
        List<PassagemPelaEtapa> passagens,
        List<Complementacao> complementacoes,
        ClassificacaoDaTaxa taxaPrincipal,
        Optional<ClassificacaoDaTaxa> taxaDaCorrecao,
        Set<String> idsNaoMapeados,
        int ignoradas,
        Set<Diagnostico> diagnosticos,
        boolean truncado,
        int instanciasRaiz) {

    public CaminhoReconstruido {
        passagens = List.copyOf(Objects.requireNonNullElse(passagens, List.of()));
        complementacoes = List.copyOf(Objects.requireNonNullElse(complementacoes, List.of()));
        Objects.requireNonNull(taxaPrincipal, "taxaPrincipal");
        taxaDaCorrecao = Objects.requireNonNullElse(taxaDaCorrecao, Optional.empty());
        idsNaoMapeados = Set.copyOf(Objects.requireNonNullElse(idsNaoMapeados, Set.of()));
        diagnosticos = Set.copyOf(Objects.requireNonNullElse(diagnosticos, Set.of()));
    }

    public static CaminhoReconstruido vazio() {
        return new CaminhoReconstruido(List.of(), List.of(), ClassificacaoDaTaxa.INDETERMINADA, Optional.empty(), Set.of(),
                0, Set.of(), false, 0);
    }

    /** Sem os nomes dos servidores em nenhuma passagem. */
    public CaminhoReconstruido semNomes() {
        return new CaminhoReconstruido(passagens.stream().map(PassagemPelaEtapa::semNomes).toList(), complementacoes,
                taxaPrincipal, taxaDaCorrecao, idsNaoMapeados, ignoradas, diagnosticos, truncado, instanciasRaiz);
    }

    /** Complementação paralela (regra técnica 7): fora da linha do caminho, ao lado dela. */
    public record Complementacao(LocalDateTime entrada, Optional<LocalDateTime> saida) {
        public Complementacao {
            saida = Objects.requireNonNullElse(saida, Optional.empty());
        }
    }

    /**
     * Classificação pela rota: {@code TAXAVEL} = passou (ou está) na espera de pagamento; {@code ISENTA} = passou pela
     * isenção confirmada sem a espera; {@code SEM_NOVA_TAXA} = correção sem espera própria; {@code INDETERMINADA} = ainda não
     * chegou ao ponto que decide.
     */
    public enum ClassificacaoDaTaxa { TAXAVEL, ISENTA, SEM_NOVA_TAXA, INDETERMINADA }

    /** Só para o diagnóstico do Administrador. */
    public enum Diagnostico {
        /** O {@code statusConferencia} da época diz o contrário da rota; a rota vence (regra técnica 11). */
        ROTA_DIVERGE_DO_STATUS
    }
}
