package br.gov.mt.sema.rastro.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.financeiro.application.FinanceiroAberto;
import br.gov.mt.sema.rastro.financeiro.domain.AvisoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.HistoricoFinanceiro;
import br.gov.mt.sema.rastro.financeiro.domain.Inconsistencia;
import br.gov.mt.sema.rastro.financeiro.domain.LancamentoAvaliado;
import br.gov.mt.sema.rastro.financeiro.domain.MotivoDoIgnorado;
import br.gov.mt.sema.rastro.financeiro.domain.RegistroDeIsencao;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Histórico financeiro de um requerimento (S9, bloco 6; docs/07, "Financeiro"): as taxas emitidas, o número do DAR, o
 * vencimento, o pagamento, as isenções ("Isento de Taxa" com data) e os avisos, na ordem de emissão.
 *
 * <p>Nunca sai a chave do arquivo da guia, a mensagem nem a titulação (o Rastro não as lê), CPF, nem a situação trocada: a
 * situação é a do Licenciamento e a "taxa vencida por data" é uma marca à parte. Valor, desconto e UPF só saem com
 * {@code RASTRO_MOSTRAR_VALORES} ligado. Para quem não é Administrador, nenhum id de lançamento e nenhum diagnóstico
 * (ignorados, origem da data, status bruto): esses campos <b>não existem</b> na resposta.
 */
@Schema(description = "Taxas, DAR, vencimento, pagamento e isenção do requerimento")
public record FinanceiroResponse(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        @Schema(description = "ATIVO ou INDISPONIVEL (banco do SIGA fora do ar, lento ou não configurado)") String estado,
        @Schema(description = "SEM_TAXA, ISENTO, EM_ABERTO, QUITADO, VENCIDO ou INDETERMINADO; vazio quando INDISPONIVEL")
        String resumo,
        @Schema(description = "Valor, desconto e UPF saem nesta resposta (RASTRO_MOSTRAR_VALORES)") boolean mostraValores,
        List<LancamentoResponse> lancamentos,
        List<IsencaoResponse> isencoes,
        List<AvisoResponse> avisos,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Só para o Administrador: lançamentos ignorados pela regra de validade, por motivo")
        Map<String, Integer> ignoradosPorMotivo) {

    public static FinanceiroResponse de(FinanceiroAberto a) {
        HistoricoFinanceiro h = a.historico();
        if (h == null) {
            return new FinanceiroResponse(a.idRequerimento(), a.numeroRequerimento(), a.numeroProcesso(), a.estado().name(),
                    null, a.mostrarValores(), List.of(), List.of(), List.of(), null);
        }
        boolean diagnostico = a.diagnostico();
        return new FinanceiroResponse(a.idRequerimento(), a.numeroRequerimento(), a.numeroProcesso(), a.estado().name(),
                h.resumo().name(), a.mostrarValores(),
                h.lancamentos().stream().map(l -> LancamentoResponse.de(l, a.mostrarValores(), diagnostico)).toList(),
                h.isencoes().stream().map(i -> IsencaoResponse.de(i, diagnostico)).toList(),
                h.avisos().stream().map(AvisoResponse::de).toList(),
                diagnostico ? ignorados(h) : null);
    }

    private static Map<String, Integer> ignorados(HistoricoFinanceiro h) {
        Map<String, Integer> contagem = new java.util.LinkedHashMap<>();
        for (Map.Entry<MotivoDoIgnorado, Integer> e : h.classificados().ignoradosPorMotivo().entrySet()) {
            contagem.put(e.getKey().name(), e.getValue());
        }
        return contagem;
    }

    @Schema(description = "Um lançamento de taxa mantido pela regra de validade")
    public record LancamentoResponse(
            @Schema(description = "Posição na lista, por emissão (1, 2, ...)") int ordem,
            @Schema(description = "1ª taxa, 2ª taxa ou Taxa da correção; vazio na isenção e no status desconhecido")
            String rotulo,
            @Schema(description = "A do Licenciamento, como está: EMITINDO, AGUARDANDO_PAGAMENTO, PAGA, VENCIDA, ISENTA, "
                    + "CANCELADA ou DESCONHECIDA") String situacao,
            @Schema(description = "Conta como vencida no resumo (por data ou já marcada VENCIDA pelo SIGA)") boolean vencida,
            @Schema(description = "Vencida só pela data de vencimento: o SIGA ainda não a marcou. Só esta ganha o aviso "
                    + "TAXA_VENCIDA") boolean vencidaPorData,
            @Schema(description = "Dados incompletos ou fora do padrão no SIGA; mostrada como está") boolean inconsistente,
            LocalDate emitidaEm,
            LocalDate venceEm,
            LocalDate pagaEm,
            String numeroDar,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só com RASTRO_MOSTRAR_VALORES; ausente é \"não informado\", nunca zero") BigDecimal valor,
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal desconto,
            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal upf,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador") Long idTecnico,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador") String statusProcessado,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador; semântica não confirmada, nunca regra")
            Boolean passouPelaReaprovacaoDaCorrecao,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador") List<String> inconsistencias) {

        static LancamentoResponse de(LancamentoAvaliado l, boolean mostrarValores, boolean diagnostico) {
            return new LancamentoResponse(l.ordem(), l.rotulo().orElse(null), l.situacao().name(), l.vencida(),
                    l.vencidaPorData(), !l.mantido().consistente(), l.emitidaEm().orElse(null), l.venceEm().orElse(null),
                    l.pagaEm().orElse(null), l.numeroDar().orElse(null),
                    mostrarValores ? l.valor().orElse(null) : null,
                    mostrarValores ? l.desconto().orElse(null) : null,
                    mostrarValores ? l.upf().orElse(null) : null,
                    diagnostico ? l.lancamento().id() : null,
                    diagnostico ? l.lancamento().statusProcessado() : null,
                    diagnostico ? l.lancamento().passouPelaReaprovacaoDaCorrecao() : null,
                    diagnostico ? l.mantido().inconsistencias().stream().map(Inconsistencia::name).sorted().toList() : null);
        }
    }

    @Schema(description = "\"Isento de Taxa\": uma entrada por isenção, em ordem")
    public record IsencaoResponse(
            @Schema(description = "Sempre \"Isento de Taxa\"") String texto,
            @Schema(description = "Ordem do lançamento isento na lista") int ordem,
            @Schema(description = "Data pela cadeia de apoio (provisória); vazia quando nenhuma fonte a tem") LocalDate data,
            @Schema(description = "A data não é a do próprio lançamento (ou não existe): a tela mostra \"data a confirmar\"")
            boolean dataAConfirmar,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador: LANCAMENTO, FLUXO ou AUSENTE") String origemDaData,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador: STATUS_PROCESSADO = N no lançamento isento")
            Boolean naoProcessado) {

        static IsencaoResponse de(RegistroDeIsencao i, boolean diagnostico) {
            return new IsencaoResponse(i.texto(), i.lancamento().ordem(), i.data().orElse(null), i.dataAConfirmar(),
                    diagnostico ? i.origemDaData().name() : null, diagnostico ? i.naoProcessado() : null);
        }
    }

    public record AvisoResponse(String codigo, String texto, String gravidade) {

        static AvisoResponse de(AvisoFinanceiro a) {
            return new AvisoResponse(a.codigo().name(), a.texto(), a.gravidade().name());
        }
    }
}
