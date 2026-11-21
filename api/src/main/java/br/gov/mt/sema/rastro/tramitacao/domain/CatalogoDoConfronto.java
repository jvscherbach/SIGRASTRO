package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Porta do catálogo do confronto (arquivo {@code rastro/confronto.yaml}, validado na subida): textos dos avisos e, por
 * modelo, as situações do Licenciamento aceitas em cada etapa e em cada final, com as divergências conhecidas.
 */
public interface CatalogoDoConfronto {

    /** Aviso pronto; {@code situacao} preenche o marcador {@code {situacao}} do texto, quando houver. */
    Aviso aviso(CodigoDoAviso codigo, String situacao);

    /** Regra de uma etapa ({@link Etapa#id()}) do modelo; vazia = sem tabela, não confronta. */
    Optional<RegraDaSituacao> daEtapa(String chaveDoModelo, String idDaEtapa);

    /** Regra de um final ({@link FinalDoFluxo#id()}) do modelo; vazia = sem tabela, não confronta. */
    Optional<RegraDaSituacao> doFinal(String chaveDoModelo, String idDoFinal);

    /**
     * @param aceitas    situações compatíveis; fora delas, {@link CodigoDoAviso#SITUACAO_NAO_CONFERE}
     * @param conhecidas divergência conhecida: situação → aviso próprio (vence o "não confere")
     */
    record RegraDaSituacao(Set<String> aceitas, Map<String, CodigoDoAviso> conhecidas) {

        public RegraDaSituacao {
            aceitas = Set.copyOf(aceitas);
            conhecidas = Map.copyOf(conhecidas);
        }
    }
}
