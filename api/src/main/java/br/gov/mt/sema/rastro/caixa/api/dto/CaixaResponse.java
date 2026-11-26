package br.gov.mt.sema.rastro.caixa.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.caixa.application.AbrirCaixa.CaixaAberta;
import br.gov.mt.sema.rastro.caixa.application.CaixaDoAnalista;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato da caixa do analista (S11). Só números, rótulos e o nome do interessado: nenhum campo de CPF ou CNPJ (nem
 * mascarado), nem chave do analista, nem id técnico do Camunda.
 */
@Schema(description = "Caixa de entrada do analista: resumo, lista e estado da leitura.")
public record CaixaResponse(
        String estado, OffsetDateTime geradoEm, OffsetDateTime falhouEm, boolean incompleta,
        boolean semVinculo, String mensagem, int novos, ResumoResponse resumo, List<LinhaResponse> linhas) {

    private static final ZoneId CUIABA = ZoneId.of("America/Cuiaba");

    public static final String MENSAGEM_SEM_VINCULO = "Seu usuário não está vinculado a um servidor. Peça ao Administrador.";

    public record ResumoResponse(int total, int comInteressado, List<EtapaResponse> porEtapa) {
    }

    public record EtapaResponse(String id, String rotulo, int quantidade) {
    }

    @Schema(description = "Um requerimento da caixa. posse: COM_ANALISTA ou COM_INTERESSADO (correção). diasNaEtapa nulo = sem informação.")
    public record LinhaResponse(long id, String numero, String interessado, String tipo, String posse, String etapaId,
            String etapa, Long diasNaEtapa, boolean novo) {
    }

    public static CaixaResponse de(CaixaAberta aberta) {
        CaixaDoAnalista.Visao v = aberta.visao();
        Map<String, String> rotulos = aberta.rotulosDeEtapa();
        return new CaixaResponse(v.estado().name(), data(v.geradoEm()), data(v.falhouEm()), v.incompleta(),
                v.semVinculo(), v.semVinculo() ? MENSAGEM_SEM_VINCULO : null, v.novos(),
                new ResumoResponse(v.resumo().total(), v.resumo().comInteressado(),
                        v.resumo().porEtapa().stream()
                                .map(e -> new EtapaResponse(e.id(), e.rotulo(), e.quantidade())).toList()),
                v.linhas().stream().map(l -> linha(l, rotulos)).toList());
    }

    private static LinhaResponse linha(CaixaDoAnalista.Linha l, Map<String, String> rotulos) {
        ItemDaCaixa i = l.item();
        String etapaId = etapaId(i);
        return new LinhaResponse(i.id(), i.numero().orElse(null), i.interessado().orElse(null), i.tipo().orElse(null),
                i.estado().tipo().name(), etapaId, rotulos.getOrDefault(etapaId, etapaId),
                l.diasNaEtapa().orElse(null), l.novo());
    }

    /** O id da etapa do catálogo: a da posse, ou a correção quando o requerimento está com o interessado. */
    public static String etapaId(ItemDaCaixa item) {
        return item.estado().etapa().orElse(DefinicaoDaPosse.ETAPA_CORRECAO);
    }

    public static OffsetDateTime data(Optional<Instant> instante) {
        return instante.map(x -> OffsetDateTime.ofInstant(x, CUIABA)).orElse(null);
    }
}
