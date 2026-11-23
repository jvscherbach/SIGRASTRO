package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * O aviso {@link CodigoDoAviso#TAXA_VENCIDA} da posição (S9, bloco 5): "Taxa vencida em dd/mm/aaaa." e, quando a taxa está
 * vencida só pela data (o SIGA ainda mostra "aguardando pagamento"), a explicação da causa. Junto com
 * {@link CodigoDoAviso#FLUXO_ENCERRADO_SEM_STATUS} os dois aparecem: um explica o fim do fluxo, o outro a taxa.
 */
public final class AvisoDeTaxaVencida {

    public static final String EXPLICACAO_POR_DATA =
            " O SIGA ainda não marca a taxa como vencida; o Rastro compara a data de vencimento com hoje.";
    private static final DateTimeFormatter DIA_MES_ANO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private AvisoDeTaxaVencida() {
    }

    public static Aviso de(CatalogoDoConfronto catalogo, Optional<LocalDate> vencidaEm, boolean porData) {
        Aviso base = catalogo.aviso(CodigoDoAviso.TAXA_VENCIDA,
                vencidaEm.map(d -> " em " + d.format(DIA_MES_ANO)).orElse(""));
        return new Aviso(base.codigo(), porData ? base.texto() + EXPLICACAO_POR_DATA : base.texto(), base.gravidade());
    }
}
