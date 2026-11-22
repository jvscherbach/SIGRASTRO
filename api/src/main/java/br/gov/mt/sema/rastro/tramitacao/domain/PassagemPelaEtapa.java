package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma passagem do requerimento por uma etapa simplificada (S8, bloco 3; docs/15, "Reconstrução do caminho"). Só existe para
 * o que <b>de fato</b> aconteceu: nenhuma etapa futura vira passagem.
 *
 * @param etapa             etapa do catálogo; vazia = "Etapa não mapeada" (tarefa humana fora do catálogo)
 * @param idTecnico         id técnico da primeira atividade da passagem; só para o diagnóstico do Administrador
 * @param ciclo             1 na primeira passagem pela etapa, 2 na segunda...
 * @param ramo              principal ou correção (da etapa; não mapeada = principal)
 * @param saida             vazia = passagem atual (aberta)
 * @param responsaveis      com quem esteve: participantes da multi-instância, a pessoa da tarefa, os signatários da emissão
 *                          ou o papel da etapa (sistema, interessado)
 * @param multiInstancia    passagem de um ciclo multi-instância (uma tarefa por analista ou signatário)
 * @param atos              só na "Emissão do ato": cada instância filha
 * @param motivoDaVolta     só a partir do 2º ciclo e só se a regra casa; senão vazio
 * @param emParalelo        a passagem se sobrepõe no tempo a outra passagem ou a uma complementação
 * @param repeticoes        quantas atividades consecutivas foram juntadas nesta passagem (diagnóstico)
 * @param processoConcluido "Verificação da revisão do processo": selo "Processo concluído" (regra técnica 9)
 */
public record PassagemPelaEtapa(
        Optional<Etapa> etapa,
        String idTecnico,
        int ciclo,
        Ramo ramo,
        LocalDateTime entrada,
        Optional<LocalDateTime> saida,
        List<ResponsavelDaPassagem> responsaveis,
        boolean multiInstancia,
        List<AtoDaEmissao> atos,
        Optional<MotivoDaVolta> motivoDaVolta,
        boolean emParalelo,
        int repeticoes,
        boolean processoConcluido) {

    public PassagemPelaEtapa {
        etapa = Objects.requireNonNullElse(etapa, Optional.empty());
        Objects.requireNonNull(entrada, "entrada");
        saida = Objects.requireNonNullElse(saida, Optional.empty());
        responsaveis = List.copyOf(Objects.requireNonNullElse(responsaveis, List.of()));
        atos = List.copyOf(Objects.requireNonNullElse(atos, List.of()));
        motivoDaVolta = Objects.requireNonNullElse(motivoDaVolta, Optional.empty());
        Objects.requireNonNull(ramo, "ramo");
    }

    /** A etapa em que o requerimento está agora. */
    public boolean atual() {
        return saida.isEmpty();
    }

    public Optional<Duration> duracao() {
        return saida.map(s -> Duration.between(entrada, s));
    }

    public String rotulo() {
        return etapa.map(Etapa::rotulo).orElse(ResolucaoDeEtapa.ROTULO_NAO_MAPEADA);
    }

    public Optional<String> idDaEtapa() {
        return etapa.map(Etapa::id);
    }

    /** Sem os nomes dos servidores ({@code RASTRO_MOSTRAR_NOME_RESPONSAVEL} desligado): fica o papel. */
    public PassagemPelaEtapa semNomes() {
        return new PassagemPelaEtapa(etapa, idTecnico, ciclo, ramo, entrada, saida,
                responsaveis.stream().map(ResponsavelDaPassagem::semNome).toList(), multiInstancia, atos, motivoDaVolta,
                emParalelo, repeticoes, processoConcluido);
    }

    PassagemPelaEtapa comMotivo(Optional<MotivoDaVolta> motivo) {
        return new PassagemPelaEtapa(etapa, idTecnico, ciclo, ramo, entrada, saida, responsaveis, multiInstancia, atos,
                motivo, emParalelo, repeticoes, processoConcluido);
    }
}
