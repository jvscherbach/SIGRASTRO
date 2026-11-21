package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fatos crus do caminho percorrido de um requerimento (S8, bloco 2), sem interpretação: quem interpreta é a reconstrução
 * (bloco 3), pelo catálogo.
 *
 * @param instancias        raiz primeiro, depois as filhas e as complementações, cada grupo por início; vazia = sem
 *                          instância (plano B, bloco 5)
 * @param instanciasRaiz    quantas instâncias raiz o requerimento tem no modelo; mais de uma = vale a mais recente (marca
 *                          {@code VARIAS_INSTANCIAS}, como na posição)
 * @param atividades        de todas as instâncias, em ordem de gravação: início e id (o id da atividade carrega um contador
 *                          crescente por instância)
 * @param variaveisDaEpoca  alterações das variáveis da lista branca, na ordem em que ocorreram ({@code ACT_HI_DETAIL})
 * @param variaveisFinais   valor final de cada variável da lista branca ({@code ACT_HI_VARINST})
 */
public record CaminhoBruto(
        List<InstanciaDoHistorico> instancias,
        int instanciasRaiz,
        List<AtividadeBruta> atividades,
        List<VariavelBruta> variaveisDaEpoca,
        List<VariavelBruta> variaveisFinais) {

    public CaminhoBruto {
        instancias = List.copyOf(Objects.requireNonNullElse(instancias, List.of()));
        atividades = List.copyOf(Objects.requireNonNullElse(atividades, List.of()));
        variaveisDaEpoca = List.copyOf(Objects.requireNonNullElse(variaveisDaEpoca, List.of()));
        variaveisFinais = List.copyOf(Objects.requireNonNullElse(variaveisFinais, List.of()));
    }

    /** A raiz não existe em {@code ACT_HI_PROCINST}: o plano B (bloco 5) é de quem chama, nunca por falha do Camunda. */
    public static CaminhoBruto semInstancia() {
        return new CaminhoBruto(List.of(), 0, List.of(), List.of(), List.of());
    }

    public boolean vazio() {
        return instancias.isEmpty();
    }

    public Optional<InstanciaDoHistorico> raiz() {
        return instancias.stream().filter(i -> i.origem() == OrigemDaInstancia.RAIZ).findFirst();
    }

    /** Alguma instância passou do limite de linhas: o histórico está incompleto (aviso {@code HISTORICO_TRUNCADO}). */
    public boolean truncado() {
        return instancias.stream().anyMatch(InstanciaDoHistorico::truncada);
    }

    public List<AtividadeBruta> atividadesDe(String instancia) {
        return atividades.stream().filter(a -> a.instancia().equals(instancia)).toList();
    }
}
