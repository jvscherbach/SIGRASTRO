package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.AGUARDANDO_EMISSAO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.AGUARDANDO_PAGAMENTO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.CANCELADA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.ISENTA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.PAGA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.StatusDaGuia.VENCIDA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.dias;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ANALISE;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ATRIBUICAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CADASTRO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CONFERENCIA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.EMITIR_TAXA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.EMITIR_TAXA_CORRECAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PAGAMENTO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PAGAMENTO_CORRECAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PROTOCOLO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.correcaoAteACorrecao;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.hidrico;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.taxavelAteAAnalise;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Processo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;

/**
 * Cenários fictícios do financeiro da S9 (prompt, bloco 1): um requerimento cada, ids <b>9001 a 9199</b>, números
 * {@code <id>/2026}, interessado "Interessado Fictício S7" e números de DAR evidentes ({@code FICT-...}). Carregados por
 * {@link SigaOracleTeste} ao subir o Oracle de teste, depois dos da S7 e da S8; os testes dos blocos 2 a 11 consultam por
 * estes ids. Tabela completa em docs/15.
 *
 * <p><b>"Hoje" dos testes:</b> {@link #HOJE} (2026-03-20, em Cuiabá) e {@link #RELOGIO}, o {@code Clock} fixo que os testes
 * injetam. Todo vencimento é relativo a {@link #HOJE}: {@link #ONTEM}, {@link #HOJE} e {@link #AMANHA}. Os instantes do
 * Camunda seguem {@link CamundaFicticioBuilder#dias(int)} (2026-03-02 + n dias), e {@code dias(18)} é {@link #HOJE}.
 *
 * <p>Os vencimentos de teste de data (ontem, hoje e amanhã) são os requerimentos {@link #VENCE_ONTEM},
 * {@link #VENCE_HOJE} e {@link #VENCE_AMANHA} (apoio; fora da numeração FIN01 a FIN16 do roteiro). O FIN02 (no prazo) vence
 * amanhã e o FIN03 (vencido por data) venceu ontem.
 */
public final class CenariosFicticiosDaS9 {

    public static final long FIN01 = 9001;
    public static final long FIN02 = 9002;
    public static final long FIN03 = 9003;
    public static final long FIN04 = 9004;
    public static final long FIN05 = 9005;
    public static final long FIN06 = 9006;
    public static final long FIN07 = 9007;
    public static final long FIN08 = 9008;
    public static final long FIN09 = 9009;
    public static final long FIN10 = 9010;
    public static final long FIN11 = 9011;
    public static final long FIN12 = 9012;
    public static final long FIN13 = 9013;
    public static final long FIN14 = 9014;
    public static final long FIN15 = 9015;
    public static final long FIN16 = 9016;
    /** Apoio para os testes de data: vencimento ontem, hoje e amanhã (a taxa segue {@code AGUARDANDO_PAGAMENTO}). */
    public static final long VENCE_ONTEM = 9101;
    public static final long VENCE_HOJE = 9102;
    public static final long VENCE_AMANHA = 9103;

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    /** O "hoje" dos testes de financeiro, em Cuiabá. */
    public static final LocalDate HOJE = LocalDate.of(2026, 3, 20);
    public static final LocalDate ONTEM = HOJE.minusDays(1);
    public static final LocalDate AMANHA = HOJE.plusDays(1);
    /** Relógio fixo em {@link #HOJE} às 10h de Cuiabá (14h UTC), para os testes de data injetarem. */
    public static final Clock RELOGIO = Clock.fixed(HOJE.atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
    /** Relógio fixo logo depois da meia-noite UTC do dia seguinte: em Cuiabá ainda é {@link #HOJE} (virada UTC x Cuiabá). */
    public static final Clock RELOGIO_NA_VIRADA_UTC = Clock.fixed(
            Instant.parse("2026-03-21T01:30:00Z"), FUSO);

    /** Quantos lançamentos "falsos" (CANCELADA sem DAR) o FIN14 grava ao lado do válido. */
    public static final int FALSOS_DO_FIN14 = 10;

    private static final String DAR_FIN01 = "FICT-0001";
    private static final String DAR_FIN02 = "FICT-0002";
    private static final String DAR_FIN03 = "FICT-0003";
    private static final String DAR_FIN04 = "FICT-0004";
    private static final String DAR_FIN07 = "FICT-0007";
    private static final String DAR_FIN08 = "FICT-0008";
    private static final String DAR_FIN11_INICIAL = "FICT-0011";
    private static final String DAR_FIN11_CORRECAO = "FICT-0111";
    private static final String DAR_FIN12 = "FICT-0012";
    private static final String DAR_FIN14 = "FICT-0014";
    private static final String DAR_FIN15 = "FICT-0015";
    private static final String DAR_FIN16 = "FICT-0016";

    private CenariosFicticiosDaS9() {
    }

    /** Grava todos os cenários; devolve o resultado de cada um pelo código (FIN01, VENCE_HOJE...). */
    public static Map<String, Resultado> carregar(CamundaFicticioBuilder camunda) {
        Map<String, Resultado> r = new LinkedHashMap<>();

        // FIN01: taxa paga (emitida, vencida em data futura e paga a tempo).
        r.put("FIN01", pagoEmAnalise(camunda, FIN01)
                .comLancamento(PAGA).comDar(DAR_FIN01).emitidoEm(dias(2)).vencendoEm(dias(12).toLocalDate())
                .pagoEm(dias(5)).comValores("1250.50", "0.00", "35.7286").gravar());

        // FIN02: aguardando pagamento, no prazo (vence amanhã).
        r.put("FIN02", aguardandoPagamento(camunda, FIN02)
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN02).emitidoEm(dias(2)).vencendoEm(AMANHA)
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN03: aguardando pagamento VENCIDO POR DATA: o status ainda é AGUARDANDO (o SIGA não atualiza), venceu ontem.
        r.put("FIN03", aguardandoPagamento(camunda, FIN03)
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN03).emitidoEm(dias(2)).vencendoEm(ONTEM)
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN04: já marcada VENCIDA pelo SIGA.
        r.put("FIN04", aguardandoPagamento(camunda, FIN04)
                .comLancamento(VENCIDA).comDar(DAR_FIN04).emitidoEm(dias(2)).vencendoEm(dias(12).toLocalDate())
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN05: isento (ISENTA sem DAR, TAXA_ISENTA = 1, processado P). Sem taxa nem espera no Camunda.
        r.put("FIN05", isento(camunda, FIN05)
                .comLancamento(ISENTA).isenta().processado('P').emitidoEm(dias(2)).gravar());

        // FIN06: ISENTA com STATUS_PROCESSADO = N (sem tratamento especial; só nota no diagnóstico).
        r.put("FIN06", isento(camunda, FIN06)
                .comLancamento(ISENTA).isenta().processado('N').emitidoEm(dias(2)).gravar());

        // FIN07: CANCELADA SEM DAR (registro "falso", ignorado) ao lado de um lançamento válido (aguardando, no prazo).
        r.put("FIN07", aguardandoPagamento(camunda, FIN07)
                .comLancamento(CANCELADA).emitidoEm(dias(2)).encerradoEm(dias(2, 1))
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN07).emitidoEm(dias(2, 2)).vencendoEm(AMANHA)
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN08: CANCELADA COM DAR e DATA_FIM (cancelada de verdade: válida).
        r.put("FIN08", aguardandoPagamento(camunda, FIN08)
                .comLancamento(CANCELADA).comDar(DAR_FIN08).emitidoEm(dias(2)).vencendoEm(HOJE.plusDays(5))
                .encerradoEm(dias(6)).comValores("980.00", "0.00", "28.0000").gravar());

        // FIN09: AGUARDANDO_EMISSAO sem DAR (válido: a guia ainda vai ser emitida).
        r.put("FIN09", aguardandoPagamento(camunda, FIN09)
                .comLancamento(AGUARDANDO_EMISSAO).gravar());

        // FIN10: ruído: AGUARDANDO_EMISSAO com TAXA_ISENTA = 1 (ignorado por ora, docs/10).
        r.put("FIN10", aguardandoPagamento(camunda, FIN10)
                .comLancamento(AGUARDANDO_EMISSAO).isenta().gravar());

        // FIN11: taxa inicial paga e taxa da correção aguardando pagamento (o Camunda fica na espera da correção).
        r.put("FIN11", correcaoComNovaTaxa(camunda, FIN11)
                .comLancamento(PAGA).comDar(DAR_FIN11_INICIAL).emitidoEm(dias(2)).vencendoEm(dias(12).toLocalDate())
                .pagoEm(dias(5)).comValores("1250.50", "0.00", "35.7286")
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN11_CORRECAO).emitidoEm(dias(14))
                .vencendoEm(HOJE.plusDays(10)).comValores("310.25", "0.00", "8.8643")
                .passouPelaReaprovacaoDaCorrecao(true).gravar());

        // FIN12: pago que virou isento na correção: taxa paga (com DAR) e depois ISENTA, os dois à vista (o destaque é da S11).
        r.put("FIN12", correcaoComNovaTaxa(camunda, FIN12)
                .comLancamento(PAGA).comDar(DAR_FIN12).emitidoEm(dias(2)).vencendoEm(dias(12).toLocalDate())
                .pagoEm(dias(5)).comValores("1250.50", "0.00", "35.7286")
                .comLancamento(ISENTA).isenta().processado('P').emitidoEm(dias(14)).gravar());

        // FIN13: requerimento sem nenhum lançamento.
        r.put("FIN13", aguardandoPagamento(camunda, FIN13).gravar());

        // FIN14: volume: 10 cancelados sem DAR e 1 válido.
        Processo fin14 = aguardandoPagamento(camunda, FIN14);
        for (int i = 0; i < FALSOS_DO_FIN14; i++) {
            fin14.comLancamento(CANCELADA).emitidoEm(dias(2).plusMinutes(i)).encerradoEm(dias(2).plusMinutes(i + 1));
        }
        r.put("FIN14", fin14.comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN14).emitidoEm(dias(3)).vencendoEm(AMANHA)
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN15: o H18 da S7: instância encerrada em Event_0h1oige (pagamento vencido), situação AGUARDANDO_PAGAMENTO_DE_TAXAS,
        // e a taxa vencida POR DATA (o SIGA não a marcou vencida).
        r.put("FIN15", hidrico(camunda, FIN15, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), cpfDoServidor(2))
                .variavel("statusConferencia", "taxavel", dias(2))
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(PAGAMENTO, dias(2, 1), dias(17))
                .encerradoEm("Event_0h1oige").desde(dias(17))
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar(DAR_FIN15).emitidoEm(dias(2)).vencendoEm(ONTEM)
                .comValores("980.00", "0.00", "28.0000").gravar());

        // FIN16: requerimento SEM instância (plano B) com a taxa paga.
        r.put("FIN16", camunda.processo(Fluxo.HIDRICO, FIN16).situacao("EM_ANALISE").semInstancia()
                .abertoEm(LocalDateTime.of(2026, 3, 2, 8, 0)).desde(dias(10))
                .comLancamento(PAGA).comDar(DAR_FIN16).emitidoEm(dias(2)).vencendoEm(dias(12).toLocalDate())
                .pagoEm(dias(5)).comValores("1250.50", "0.00", "35.7286").gravar());

        // Vencimentos em torno de "hoje", para os testes de data.
        r.put("VENCE_ONTEM", aguardandoPagamento(camunda, VENCE_ONTEM)
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar("FICT-0101").emitidoEm(dias(2)).vencendoEm(ONTEM).gravar());
        r.put("VENCE_HOJE", aguardandoPagamento(camunda, VENCE_HOJE)
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar("FICT-0102").emitidoEm(dias(2)).vencendoEm(HOJE).gravar());
        r.put("VENCE_AMANHA", aguardandoPagamento(camunda, VENCE_AMANHA)
                .comLancamento(AGUARDANDO_PAGAMENTO).comDar("FICT-0103").emitidoEm(dias(2)).vencendoEm(AMANHA).gravar());
        return r;
    }

    /**
     * Requerimento taxável esperando o pagamento, como o HC03 da S8: taxa emitida no dia 2 e espera a partir do dia 2 + 1h.
     * Os lançamentos dos cenários usam a emissão {@code dias(2)}.
     */
    private static Processo aguardandoPagamento(CamundaFicticioBuilder camunda, long id) {
        return hidrico(camunda, id, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), cpfDoServidor(2))
                .variavel("statusConferencia", "taxavel", dias(2))
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .esperandoPagamento().desde(dias(2, 1));
    }

    /** Requerimento taxável com a taxa paga no dia 5 (como o HC01 da S8, até a análise). */
    private static Processo pagoEmAnalise(CamundaFicticioBuilder camunda, long id) {
        return taxavelAteAAnalise(hidrico(camunda, id, "EM_ANALISE"))
                .naEtapa(ANALISE).comAnalistas(3).desde(dias(8));
    }

    /** Requerimento isento como o HC02 da S8: sem emissão de taxa nem espera; o protocolo é gerado direto no dia 2. */
    private static Processo isento(CamundaFicticioBuilder camunda, long id) {
        return hidrico(camunda, id, "AGUARDANDO_ATRIBUICAO")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), cpfDoServidor(2))
                .variavel("statusConferencia", "isento", dias(2))
                .passou(PROTOCOLO, dias(2), dias(2, 1))
                .naEtapa(ATRIBUICAO).paraGrupo("grpSurhAtribuirRequerimentoSuperficial").desde(dias(2, 1));
    }

    /**
     * Correção com nova taxa, como o HC07 da S8, mas parado na espera do pagamento da correção: a taxa inicial foi paga no
     * dia 5, a correção começou no dia 12 e a nova taxa foi emitida no dia 14.
     */
    private static Processo correcaoComNovaTaxa(CamundaFicticioBuilder camunda, long id) {
        return correcaoAteACorrecao(hidrico(camunda, id, "AGUARDANDO_PAGAMENTO_DE_TAXAS"), 12)
                .passou(EMITIR_TAXA_CORRECAO, dias(14), dias(14, 1))
                .naEtapa(PAGAMENTO_CORRECAO).desde(dias(14, 1));
    }
}
