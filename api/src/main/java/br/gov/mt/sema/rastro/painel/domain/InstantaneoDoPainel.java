package br.gov.mt.sema.rastro.painel.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O retrato do painel em memória (S10): imutável, trocado de uma vez. Uma requisição só lê este objeto; nunca consulta o
 * SIGA nem o Camunda. Guarda a carga nas duas formas (com e sem nomes) para que desligar a exibição de nomes valha já
 * na próxima leitura, sem esperar a próxima atualização (RN12). Nenhum campo de CPF.
 *
 * @param ano       ano considerado (0 enquanto não houve leitura)
 * @param geradoEm  quando o último retrato bom foi gerado
 * @param falhouEm  quando a última tentativa falhou (só em {@code DESATUALIZADO} e {@code INDISPONIVEL})
 * @param truncado  a base passou do teto de requerimentos (ou uma leitura de tarefas ficou parcial): gráficos 2 e 3 parciais
 */
public record InstantaneoDoPainel(EstadoDoPainel estado, int ano, Optional<Instant> geradoEm, Optional<Instant> falhouEm,
        boolean truncado, Optional<SerieDoAno> serie, Optional<FilaPorEtapa> fila,
        Optional<CargaPorResponsavel> cargaComNomes, Optional<CargaPorResponsavel> cargaSemNomes,
        List<AvisoDoPainel> avisos) {

    /** Antes da primeira leitura. */
    public static final InstantaneoDoPainel PREPARANDO = new InstantaneoDoPainel(EstadoDoPainel.PREPARANDO, 0,
            Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), List.of());

    public InstantaneoDoPainel {
        Objects.requireNonNull(estado);
        Objects.requireNonNull(geradoEm);
        Objects.requireNonNull(falhouEm);
        Objects.requireNonNull(serie);
        Objects.requireNonNull(fila);
        Objects.requireNonNull(cargaComNomes);
        Objects.requireNonNull(cargaSemNomes);
        avisos = List.copyOf(avisos);
    }

    /** Um retrato bom, recém-gerado. */
    public static InstantaneoDoPainel pronto(int ano, Instant geradoEm, boolean truncado, SerieDoAno serie,
            FilaPorEtapa fila, CargaPorResponsavel comNomes, CargaPorResponsavel semNomes, List<AvisoDoPainel> avisos) {
        return new InstantaneoDoPainel(EstadoDoPainel.PRONTO, ano, Optional.of(geradoEm), Optional.empty(), truncado,
                Optional.of(serie), Optional.of(fila), Optional.of(comNomes), Optional.of(semNomes), avisos);
    }

    /** A carga que a tela pode mostrar, conforme o parâmetro de exibição de nomes vigente. */
    public Optional<CargaPorResponsavel> carga(boolean mostrarNome) {
        return mostrarNome ? cargaComNomes : cargaSemNomes;
    }

    /** Há um retrato bom guardado (pronto ou desatualizado). */
    public boolean temDadosBons() {
        return estado == EstadoDoPainel.PRONTO || estado == EstadoDoPainel.DESATUALIZADO;
    }

    /** A leitura falhou em {@code quando}: mantém o último bom (DESATUALIZADO) ou fica INDISPONIVEL se nunca houve. */
    public InstantaneoDoPainel aposFalha(Instant quando) {
        if (temDadosBons()) {
            return new InstantaneoDoPainel(EstadoDoPainel.DESATUALIZADO, ano, geradoEm, Optional.of(quando), truncado,
                    serie, fila, cargaComNomes, cargaSemNomes, avisos);
        }
        return new InstantaneoDoPainel(EstadoDoPainel.INDISPONIVEL, 0, Optional.empty(), Optional.of(quando), false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), List.of());
    }
}
