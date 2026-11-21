package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Etapa simplificada do fluxo, como o usuário a entende ("Análise do processo", não {@code taskAnalisarRequerimento}).
 *
 * @param id               identificador estável, em minúsculas com sublinhado (ex.: {@code analise_do_processo})
 * @param rotulo           texto mostrado ao usuário
 * @param ordem            posição dentro do {@link #ramo()}; única no ramo. Entre etapas ativas, a de menor ordem do ramo
 *                         principal é a principal (docs/15)
 * @param tipo             natureza da etapa (nunca {@link TipoDeEtapa#FINAL}: finais ficam em {@link FinalDoFluxo})
 * @param responsavel      papel de quem responde pela etapa
 * @param ramo             principal ou correção
 * @param multiInstancia   uma tarefa por analista ou signatário (regra técnica 6)
 * @param idsTecnicos      ids de atividade do BPMN ({@code ACT_ID_}/{@code TASK_DEF_KEY_}) que caem nesta etapa
 * @param processosFilhos  chaves de definição de instâncias filhas ({@code callActivity}) que caem nesta etapa
 * @param semPassagemPor   a etapa só vale se o percurso <b>não</b> passou por nenhum destes ids (ex.: isenção confirmada só
 *                         sem passagem pelo gateway de pagamento); vazio = sem condição
 */
public record Etapa(
        String id,
        String rotulo,
        int ordem,
        TipoDeEtapa tipo,
        PapelDoResponsavel responsavel,
        Ramo ramo,
        boolean multiInstancia,
        List<String> idsTecnicos,
        List<String> processosFilhos,
        List<String> semPassagemPor) {

    public Etapa {
        idsTecnicos = List.copyOf(Objects.requireNonNullElse(idsTecnicos, List.of()));
        processosFilhos = List.copyOf(Objects.requireNonNullElse(processosFilhos, List.of()));
        semPassagemPor = List.copyOf(Objects.requireNonNullElse(semPassagemPor, List.of()));
    }

    /** {@code true} se a condição da etapa vale para o percurso dado (ids técnicos já percorridos pela instância). */
    public boolean aceitaPercurso(Set<String> percorridos) {
        return semPassagemPor.stream().noneMatch(percorridos::contains);
    }
}
