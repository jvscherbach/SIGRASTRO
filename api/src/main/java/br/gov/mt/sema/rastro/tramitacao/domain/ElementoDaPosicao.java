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
 * @param concluidas       tarefas já concluídas do ciclo atual da multi-instância (participantes que concluíram)
 * @param responsavel      com quem está (bloco 4); vazio até a resolução
 */
public record ElementoDaPosicao(
        Optional<Etapa> etapa,
        String idTecnico,
        LocalDateTime desde,
        boolean desdeUltimaEntrada,
        List<LeituraDoFluxo.Tarefa> tarefas,
        Optional<MultiInstancia> multiInstancia,
        Optional<String> processoFilho,
        List<LeituraDoFluxo.Percorrida> concluidas,
        Optional<ResponsavelDaEtapa> responsavel) {

    public ElementoDaPosicao {
        etapa = Objects.requireNonNullElse(etapa, Optional.empty());
        tarefas = List.copyOf(Objects.requireNonNullElse(tarefas, List.of()));
        multiInstancia = Objects.requireNonNullElse(multiInstancia, Optional.empty());
        processoFilho = Objects.requireNonNullElse(processoFilho, Optional.empty());
        concluidas = List.copyOf(Objects.requireNonNullElse(concluidas, List.of()));
        responsavel = Objects.requireNonNullElse(responsavel, Optional.empty());
    }

    /** Ponto sem concluídas nem responsável (antes da resolução do bloco 4). */
    public ElementoDaPosicao(Optional<Etapa> etapa, String idTecnico, LocalDateTime desde, boolean desdeUltimaEntrada,
            List<LeituraDoFluxo.Tarefa> tarefas, Optional<MultiInstancia> multiInstancia, Optional<String> processoFilho) {
        this(etapa, idTecnico, desde, desdeUltimaEntrada, tarefas, multiInstancia, processoFilho, List.of(),
                Optional.empty());
    }

    public String rotulo() {
        return etapa.map(Etapa::rotulo).orElse(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA);
    }

    public boolean mapeado() {
        return etapa.isPresent();
    }

    ElementoDaPosicao comConcluidas(List<LeituraDoFluxo.Percorrida> doCiclo) {
        return new ElementoDaPosicao(etapa, idTecnico, desde, desdeUltimaEntrada, tarefas, multiInstancia, processoFilho,
                doCiclo, responsavel);
    }

    /**
     * Com o responsável resolvido e sem os {@code ASSIGNEE_} (CPF): depois da resolução, o identificador não segue para
     * cache, resposta nem log.
     */
    public ElementoDaPosicao comResponsavel(ResponsavelDaEtapa resolvido) {
        return new ElementoDaPosicao(etapa, idTecnico, desde, desdeUltimaEntrada,
                tarefas.stream().map(LeituraDoFluxo.Tarefa::semResponsavel).toList(), multiInstancia, processoFilho,
                concluidas.stream().map(LeituraDoFluxo.Percorrida::semResponsavel).toList(), Optional.of(resolvido));
    }

    /** Parâmetros de exibição do responsável aplicados na leitura. */
    public ElementoDaPosicao conforme(boolean mostrarNome, int maximoDeNomes) {
        return new ElementoDaPosicao(etapa, idTecnico, desde, desdeUltimaEntrada, tarefas, multiInstancia, processoFilho,
                concluidas, responsavel.map(r -> r.conforme(mostrarNome, maximoDeNomes)));
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
