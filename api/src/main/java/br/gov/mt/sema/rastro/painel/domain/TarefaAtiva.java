package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma tarefa ativa da instância <b>raiz</b> de um requerimento (a complementação, instância à parte, não entra). O tipo
 * não tem onde guardar CPF.
 *
 * @param idRequerimento {@code REQUERIMENTO.ID}
 * @param chaveDoModelo  chave da definição do Camunda ({@code procOutorgaAguaSupSub}, {@code procLicenciamentoLac})
 * @param idDaTarefa     {@code TASK_DEF_KEY_}, o id técnico que o catálogo de etapas resolve (nunca sai em resposta)
 * @param responsavel    categoria do responsável
 * @param nome           só para {@link ResponsavelDaTarefa#SERVIDOR}
 * @param grupos         grupos candidatos ({@code ACT_RU_IDENTITYLINK}), para a área pelo catálogo de grupos
 */
public record TarefaAtiva(long idRequerimento, String chaveDoModelo, String idDaTarefa, ResponsavelDaTarefa responsavel,
        Optional<String> nome, List<String> grupos) {

    public TarefaAtiva {
        Objects.requireNonNull(chaveDoModelo);
        Objects.requireNonNull(idDaTarefa);
        Objects.requireNonNull(responsavel);
        Objects.requireNonNull(nome);
        grupos = List.copyOf(grupos);
        if (responsavel != ResponsavelDaTarefa.SERVIDOR) {
            nome = Optional.empty();
        }
    }
}
