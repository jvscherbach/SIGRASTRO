package br.gov.mt.sema.rastro.caixa.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * O que o Camunda e o SIGA dizem sobre um requerimento que pode estar na posse de um analista (S11, bloco 4), cru: quem
 * decide o estado de posse é o domínio da caixa (bloco 5). Não tem onde guardar CPF: o responsável é sempre a chave opaca
 * e só aparece quando é um dos analistas pedidos.
 *
 * @param idRequerimento  {@code REQUERIMENTO.ID}
 * @param numero          número do requerimento
 * @param chaveDoModelo   chave da definição do Camunda
 * @param tarefasAtivas   tarefas ativas da raiz nas atividades de posse ou de correção (a análise multi-instância tem uma por analista)
 * @param ultimaDeAnalise última tarefa de análise ou revisão concluída (decide a posse na correção); vazia se não houve
 * @param interessado     só o nome (pessoa) ou a razão social (empresa), nunca CPF/CNPJ
 * @param tipo            descrição do tipo de processo
 */
public record PosseLida(long idRequerimento, Optional<String> numero, String chaveDoModelo,
        List<TarefaAtivaDaPosse> tarefasAtivas, Optional<UltimaTarefaDeAnalise> ultimaDeAnalise,
        Optional<String> interessado, Optional<String> tipo) {

    public PosseLida {
        Objects.requireNonNull(numero);
        Objects.requireNonNull(chaveDoModelo);
        tarefasAtivas = List.copyOf(tarefasAtivas);
        Objects.requireNonNull(ultimaDeAnalise);
        Objects.requireNonNull(interessado);
        Objects.requireNonNull(tipo);
    }

    /**
     * @param atividade   {@code TASK_DEF_KEY_} (o bloco 5 resolve a etapa pelo catálogo)
     * @param inicio      criação da tarefa, relógio de parede de Cuiabá; nulo se o banco não trouxe
     * @param responsavel chave do analista dono da tarefa; vazia quando a tarefa é de outra pessoa (ou de grupo)
     */
    public record TarefaAtivaDaPosse(String atividade, LocalDateTime inicio, Optional<ChaveDoResponsavel> responsavel) {

        public TarefaAtivaDaPosse {
            Objects.requireNonNull(atividade);
            Objects.requireNonNull(responsavel);
        }
    }

    /**
     * @param atividade   {@code TASK_DEF_KEY_} da tarefa de análise ou revisão
     * @param responsavel chave do analista que a concluiu; vazia quando foi outra pessoa
     * @param fim         quando foi concluída
     */
    public record UltimaTarefaDeAnalise(String atividade, Optional<ChaveDoResponsavel> responsavel, LocalDateTime fim) {

        public UltimaTarefaDeAnalise {
            Objects.requireNonNull(atividade);
            Objects.requireNonNull(responsavel);
        }
    }
}
