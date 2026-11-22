package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Linha do tempo <b>aproximada</b> do plano B (S8, bloco 5): requerimento anterior ao registro detalhado do fluxo. Sempre
 * com o selo {@link #SELO}; sem fluxograma, sem ciclos, sem motivo de volta, sem aceite, revisão e validação separados e
 * <b>sem responsável</b> (o tipo nem tem esse campo). Dado aproximado nunca pode parecer exato.
 *
 * @param etapas            etapas simplificadas, na ordem das datas dos eventos
 * @param marcos            datas de {@code REQUERIMENTO} e eventos de marco, por data
 * @param situacao          {@code SITUACAO_PROCESSO}, quando lida
 * @param eventosIgnorados  eventos fora do mapa (ignorados, só contados)
 * @param eventosTruncados  havia mais eventos que o limite lido
 */
public record HistoricoAproximado(List<EtapaAproximada> etapas, List<Marco> marcos, Optional<String> situacao,
        int eventosIgnorados, boolean eventosTruncados) {

    /** Selo fixo do plano B (prompt da S8). */
    public static final String SELO = "Histórico aproximado: este requerimento é anterior ao registro detalhado do fluxo";
    /** Texto quando não há nenhum evento mapeado: só situação e datas. */
    public static final String SEM_DETALHAMENTO = "Sem detalhamento disponível";

    public HistoricoAproximado {
        etapas = List.copyOf(Objects.requireNonNullElse(etapas, List.of()));
        marcos = List.copyOf(Objects.requireNonNullElse(marcos, List.of()));
        situacao = Objects.requireNonNullElse(situacao, Optional.empty());
    }

    public boolean aproximado() {
        return true;
    }

    /** Sem nenhuma etapa: a tela mostra {@link #SEM_DETALHAMENTO}, a situação e as datas. */
    public boolean semDetalhamento() {
        return etapas.isEmpty();
    }

    /**
     * Uma etapa da linha aproximada.
     *
     * @param saida   entrada da etapa seguinte, ou o marco de encerramento depois da entrada; vazia se não se sabe
     * @param eventos quantos eventos caíram nesta etapa
     */
    public record EtapaAproximada(Etapa etapa, LocalDateTime entrada, Optional<LocalDateTime> saida, int eventos) {
        public EtapaAproximada {
            Objects.requireNonNull(etapa, "etapa");
            Objects.requireNonNull(entrada, "entrada");
            saida = Objects.requireNonNullElse(saida, Optional.empty());
        }
    }

    public record Marco(TipoDeMarco tipo, LocalDateTime data) {
        public Marco {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(data, "data");
        }
    }

    public enum TipoDeMarco {
        ABERTURA("Abertura do requerimento", false),
        PROTOCOLO("Protocolo", false),
        CONCLUSAO("Conclusão", true),
        CANCELAMENTO("Cancelamento", true),
        EXPIRACAO_DO_REQUERIMENTO("Expiração do requerimento", true),
        EXPIRACAO_DO_PROCESSO("Expiração do processo", true);

        private final String rotulo;
        private final boolean encerra;

        TipoDeMarco(String rotulo, boolean encerra) {
            this.rotulo = rotulo;
            this.encerra = encerra;
        }

        public String rotulo() {
            return rotulo;
        }

        /** Marca o fim do requerimento: serve de saída aproximada da última etapa. */
        public boolean encerra() {
            return encerra;
        }
    }
}
