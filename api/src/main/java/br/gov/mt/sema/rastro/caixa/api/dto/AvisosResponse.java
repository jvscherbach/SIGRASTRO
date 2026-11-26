package br.gov.mt.sema.rastro.caixa.api.dto;

import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.caixa.application.AbrirCaixa.AvisosAbertos;
import br.gov.mt.sema.rastro.caixa.application.AvisosDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A lista de mudanças da caixa (S11): o que chegou ao analista desde o último visto, com de onde veio. O visto só é gravado
 * por {@code POST /api/caixa/avisos/visto}. Nenhum campo de CPF ou CNPJ, nem chave do analista.
 */
@Schema(description = "Requerimentos que chegaram ao analista desde o último visto, com a origem de cada um.")
public record AvisosResponse(boolean semVinculo, String mensagem, int quantidade, List<AvisoResponse> avisos) {

    @Schema(description = "origemCodigo: NOVO, VOLTOU_DA_CORRECAO, VOLTOU_DA_VALIDACAO, VOLTOU_DA_EMISSAO, VOLTOU_DA_REVISAO "
            + "ou MUDOU_DE_ETAPA (quando o histórico não permite dizer).")
    public record AvisoResponse(long id, String numero, String interessado, String tipo, String etapaId, String etapa,
            String origemCodigo, String origem) {
    }

    public static AvisosResponse de(AvisosAbertos aberto) {
        Map<String, String> rotulos = aberto.rotulosDeEtapa();
        List<AvisoResponse> avisos = aberto.avisos().stream().map(a -> aviso(a, rotulos)).toList();
        return new AvisosResponse(aberto.semVinculo(), aberto.semVinculo() ? CaixaResponse.MENSAGEM_SEM_VINCULO : null,
                avisos.size(), avisos);
    }

    private static AvisoResponse aviso(AvisosDaCaixa.Aviso a, Map<String, String> rotulos) {
        ItemDaCaixa i = a.item();
        String etapaId = CaixaResponse.etapaId(i);
        return new AvisoResponse(i.id(), i.numero().orElse(null), i.interessado().orElse(null), i.tipo().orElse(null),
                etapaId, rotulos.getOrDefault(etapaId, etapaId), a.origem().name(), a.origem().rotulo());
    }
}
