package br.gov.mt.sema.rastro.consulta.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Regra binária da prioridade: pelo menos um critério cadastrado é atendimento prioritário (docs/04, P01). */
public record PrioridadeAtendimento(Tipo tipo, List<CriterioPrioridade> criterios) {

    public enum Tipo {
        NORMAL("Atendimento normal"),
        PRIORITARIO("Atendimento prioritário");

        private final String descricao;

        Tipo(String descricao) {
            this.descricao = descricao;
        }

        public String descricao() {
            return descricao;
        }
    }

    public PrioridadeAtendimento {
        criterios = List.copyOf(criterios);
    }

    /** Monta a prioridade a partir dos códigos de critério lidos do SIGA (repetidos contam uma vez). */
    public static PrioridadeAtendimento de(Collection<Integer> codigos) {
        List<CriterioPrioridade> criterios = new LinkedHashSet<>(codigos).stream()
                .map(CriterioPrioridade::de).toList();
        return new PrioridadeAtendimento(criterios.isEmpty() ? Tipo.NORMAL : Tipo.PRIORITARIO, criterios);
    }

    public String descricao() {
        return tipo.descricao();
    }
}
