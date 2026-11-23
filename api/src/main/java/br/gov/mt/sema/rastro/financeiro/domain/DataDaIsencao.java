package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * "Isento de Taxa" com data (S9, bloco 4). <b>Decisão provisória</b> (docs/09 D87, docs/10 P44): não se sabe qual coluna
 * dá a data da isenção nos dados reais; a homologação da S9 fecha. Até lá, a data sai da {@link #CADEIA}, nesta ordem, e
 * a primeira fonte que tiver data vence:
 *
 * <ol>
 *   <li>{@link OrigemDaData#LANCAMENTO}: {@code DATA_EMISSAO} do lançamento {@code ISENTA};</li>
 *   <li>{@link OrigemDaData#FLUXO}: a entrada da passagem "Isenção confirmada" da tramitação (S8), quando há caminho
 *       exato do Camunda. Pode faltar (Camunda fora, plano B, isenção sem a etapa no fluxo);</li>
 *   <li>nenhuma: {@link OrigemDaData#AUSENTE}, "data não registrada".</li>
 * </ol>
 *
 * <p>Para mudar a ordem ou tirar um elo, mude só {@link #CADEIA}; {@code AUSENTE} é sempre o fim.
 *
 * <p><b>Pareamento com o fluxo:</b> a k-ésima isenção (por emissão) usa a k-ésima passagem "Isenção confirmada" (por
 * entrada) <b>só quando os números batem</b>; com quantidades diferentes não há como saber qual passagem é de qual
 * lançamento, e o elo do fluxo é pulado (nada é adivinhado).
 */
public final class DataDaIsencao {

    /** A cadeia de apoio, em ordem. Único ponto de configuração (constante documentada em docs/15). */
    public static final List<OrigemDaData> CADEIA = List.of(OrigemDaData.LANCAMENTO, OrigemDaData.FLUXO);

    private DataDaIsencao() {
    }

    /** Há isenção sem data no lançamento e o fluxo está na cadeia: só então vale a pena ler a tramitação. */
    public static boolean precisaDoFluxo(List<LancamentoAvaliado> avaliados) {
        return CADEIA.contains(OrigemDaData.FLUXO) && isentos(avaliados).stream()
                .anyMatch(a -> a.lancamento().emitidoEm() == null);
    }

    /**
     * Uma entrada por lançamento {@code ISENTA}, na ordem da lista.
     *
     * @param entradasNoFluxo entradas das passagens "Isenção confirmada" (vazia quando a tramitação não foi lida ou não tem)
     */
    public static List<RegistroDeIsencao> registrar(List<LancamentoAvaliado> avaliados,
            List<LocalDateTime> entradasNoFluxo) {
        List<LancamentoAvaliado> isentos = isentos(avaliados);
        List<LocalDateTime> fluxo = entradasNoFluxo.stream().sorted().toList();
        boolean pareavel = fluxo.size() == isentos.size();
        List<RegistroDeIsencao> registros = new ArrayList<>();
        for (int i = 0; i < isentos.size(); i++) {
            LancamentoAvaliado isento = isentos.get(i);
            Optional<LocalDateTime> doFluxo = pareavel ? Optional.of(fluxo.get(i)) : Optional.empty();
            registros.add(registro(isento, doFluxo));
        }
        return List.copyOf(registros);
    }

    private static RegistroDeIsencao registro(LancamentoAvaliado isento, Optional<LocalDateTime> doFluxo) {
        for (OrigemDaData origem : CADEIA) {
            Optional<LocalDate> data = switch (origem) {
                case LANCAMENTO -> isento.emitidaEm();
                case FLUXO -> doFluxo.map(CalendarioDeCuiaba::data);
                case AUSENTE -> Optional.empty();
            };
            if (data.isPresent()) {
                return new RegistroDeIsencao(isento, data, origem);
            }
        }
        return new RegistroDeIsencao(isento, Optional.empty(), OrigemDaData.AUSENTE);
    }

    private static List<LancamentoAvaliado> isentos(List<LancamentoAvaliado> avaliados) {
        return avaliados.stream().filter(a -> a.situacao() == SituacaoFinanceira.ISENTA).toList();
    }
}
