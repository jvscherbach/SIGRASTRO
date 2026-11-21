package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Um ponto ativo do fluxo resolvido pelo catálogo: uma etapa (com as tarefas dela), uma espera, uma automática ou uma
 * emissão (uma por instância filha).
 *
 * @param etapa            vazia = "Etapa não mapeada"
 * @param idTecnico        id do BPMN (ou chave da filha); só para o Administrador (bloco 6)
 * @param desde            início do ponto: criação da tarefa, início do ciclo da multi-instância, assinatura da espera
 *                         ou início da filha; nulo quando não se sabe
 * @param desdeUltimaEntrada a tarefa é recriada a cada volta (ex.: análise depois de correção): o "desde" é o da
 *                         última entrada nesta etapa, não o da primeira
 * @param tarefas          tarefas ativas do ponto (multi-instância: uma por analista/signatário)
 * @param multiInstancia   resumo "x de y" quando a etapa é multi-instância
 * @param processoFilho    chave da filha, quando o ponto é uma emissão
 */
public record ElementoDaPosicao(
        Optional<Etapa> etapa,
        String idTecnico,
        LocalDateTime desde,
        boolean desdeUltimaEntrada,
        List<LeituraDoFluxo.Tarefa> tarefas,
        Optional<MultiInstancia> multiInstancia,
        Optional<String> processoFilho) {

    public ElementoDaPosicao {
        etapa = Objects.requireNonNullElse(etapa, Optional.empty());
        tarefas = List.copyOf(Objects.requireNonNullElse(tarefas, List.of()));
        multiInstancia = Objects.requireNonNullElse(multiInstancia, Optional.empty());
        processoFilho = Objects.requireNonNullElse(processoFilho, Optional.empty());
    }

    public String rotulo() {
        return etapa.map(Etapa::rotulo).orElse(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA);
    }

    public boolean mapeado() {
        return etapa.isPresent();
    }

    /**
     * "x de y concluídos" do ciclo atual da multi-instância.
     *
     * @param concluidos tarefas do ciclo já concluídas; vazio quando o início do ciclo não pôde ser determinado
     * @param ativas     tarefas ativas do ciclo
     */
    public record MultiInstancia(Optional<Integer> concluidos, int ativas) {

        public MultiInstancia {
            concluidos = Objects.requireNonNullElse(concluidos, Optional.empty());
        }

        /** Total do ciclo (concluídas + ativas); vazio se o ciclo não é conhecido: o Rastro não inventa o "y". */
        public Optional<Integer> total() {
            return concluidos.map(c -> c + ativas);
        }
    }
}
