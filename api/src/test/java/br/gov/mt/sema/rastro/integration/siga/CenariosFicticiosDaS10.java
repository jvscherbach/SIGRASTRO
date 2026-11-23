package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CPF_SEM_SERVIDOR;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ACEITE;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ANALISE;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ATRIBUICAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CADASTRO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CONFERENCIA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CORRECAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.LAC_ENVIO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.REVISAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.VALIDACAO;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Processo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;

/**
 * População fictícia do painel do Gestor (S10, bloco 1): cenários PN01 a PN16, números {@code <id>/2026} (o de 2025 é
 * {@code <id>/2025}) e interessado "Interessado Fictício S7". Faixa de ids da S10: <b>10001 a 12999</b> (cenários PN01 a PN15
 * em 10001 a 10199; o volume PN16 em 11001 a 12200; testes da S10 que criem cenário próprio usam 13000 em diante).
 *
 * <p>Diferente das populações da S7 a S9, esta <b>não é gravada ao subir o Oracle de teste</b>: o volume de 1.200 linhas só
 * pesa nos testes do painel, que a pedem por {@code SigaOracleTeste.cenariosDoPainel()} (carga única e sob demanda).
 *
 * <p><b>"Hoje" dos testes do painel:</b> {@link #RELOGIO}, 2026-12-31 12:00 em Cuiabá (ano corrente = 2026). Os relógios das
 * viradas ({@link #RELOGIO_VIRADA_PARA_2026_NO_UTC}, {@link #RELOGIO_VIRADA_PARA_2027_NO_UTC}) provam o ano em Cuiabá contra
 * o ano em UTC.
 *
 * <p>Responsáveis (CPFs fictícios inválidos, "Servidor Exemplo NN"): {@code 01}, {@code 02} e {@code 03} nos cenários de
 * etapa; o CPF {@link CamundaFicticioBuilder#CPF_SEM_SERVIDOR} não tem servidor (PN12). Hipóteses NÃO CONFIRMADAS: duas
 * tarefas ativas na raiz do PN07 (como o BPMN real grava o paralelo) e a data de conclusão como {@code DATA_ALTERACAO}.
 */
public final class CenariosFicticiosDaS10 {

    // PN01: protocolados em vários meses (o 1º à meia-noite de 1º/jan; o último no fim do ano; dois em março)
    public static final long PN01_JAN = 10001;
    public static final long PN01_MAR_A = 10002;
    public static final long PN01_MAR_B = 10003;
    public static final long PN01_JUN = 10004;
    public static final long PN01_SET = 10005;
    public static final long PN01_DEZ = 10006;
    // PN02: concluídos em meses diferentes dos protocolados
    public static final long PN02_ABR_A = 10011;
    public static final long PN02_ABR_B = 10012;
    public static final long PN02_AGO = 10013;
    // PN03: fora do recorte (2025, concluído em 2026) e sem data de protocolo
    public static final long PN03_DE_2025 = 10031;
    public static final long PN03_SEM_DATA = 10032;
    // PN04: cancelado e arquivado (fora da fila; contados como protocolados)
    public static final long PN04_CANCELADO = 10021;
    public static final long PN04_ARQUIVADO = 10022;
    // PN05: Hídrico em etapas diferentes; responsáveis Servidor Exemplo 01 a 03
    public static final long PN05_ANALISE_SERVIDOR_01 = 10041;
    public static final long PN05_REVISAO_SERVIDOR_02 = 10042;
    public static final long PN05_VALIDACAO_SERVIDOR_03 = 10043;
    public static final long PN05_ANALISE_SERVIDOR_01_B = 10044;
    public static final long PN05_ACEITE_SERVIDOR_02 = 10045;
    // PN06: LAC
    public static final long PN06_ENVIO_DO_TERMO = 10051;
    public static final long PN06_CONFERENCIA_DE_GRUPO = 10052;
    // PN07 a PN15
    public static final long PN07_PARALELA = 10071;
    public static final long PN08_COMPLEMENTACAO = 10081;
    public static final long PN09_GRUPO = 10091;
    public static final long PN10_INTERESSADO = 10101;
    public static final long PN11_SEM_ATRIBUICAO = 10111;
    public static final long PN12_SEM_SERVIDOR = 10121;
    public static final long PN13_NAO_MAPEADA = 10131;
    public static final long PN14_FORA_DO_CATALOGO = 10141;
    public static final long PN15_SEM_INSTANCIA = 10151;

    /** Id da complementação do PN08: igual ao id do requerimento, de propósito (a chave de negócio colide). */
    public static final long COMPLEMENTACAO_DO_PN08 = PN08_COMPLEMENTACAO;
    /** Id de tarefa do Camunda que o catálogo não conhece (PN13). */
    public static final String TAREFA_NAO_MAPEADA = "taskFicticioNaoMapeadoDoPainel";

    // PN16: volume
    public static final long PN16_PRIMEIRO = 11001;
    public static final int PN16_TOTAL = 1200;
    /** O volume é distribuído por igual: 100 protocolados por mês e 300 em cada uma das quatro etapas. */
    public static final int PN16_POR_MES = PN16_TOTAL / 12;
    public static final int PN16_POR_ETAPA = PN16_TOTAL / 4;

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    public static final int ANO = 2026;
    /** O "hoje" dos testes do painel: 2026-12-31, 12h em Cuiabá (16h UTC). */
    public static final LocalDate HOJE = LocalDate.of(2026, 12, 31);
    public static final Clock RELOGIO = Clock.fixed(HOJE.atTime(12, 0).atZone(FUSO).toInstant(), FUSO);
    /** Em UTC já é 2027-01-01, em Cuiabá ainda é 2026-12-31 (22h): o ano corrente é 2026. */
    public static final Clock RELOGIO_VIRADA_PARA_2027_NO_UTC = Clock.fixed(Instant.parse("2027-01-01T02:00:00Z"), FUSO);
    /** Em UTC já é 2026-01-01, em Cuiabá ainda é 2025-12-31 (22h): o ano corrente é 2025. */
    public static final Clock RELOGIO_VIRADA_PARA_2026_NO_UTC = Clock.fixed(Instant.parse("2026-01-01T02:00:00Z"), FUSO);

    /** Protocolados por mês (janeiro a dezembro) só dos cenários PN01 a PN15, sem o volume PN16. */
    public static final int[] PROTOCOLADOS_POR_MES_SEM_VOLUME = {2, 3, 2, 2, 2, 1, 2, 2, 3, 3, 3, 2};
    /** Concluídos por mês (mês aproximado: {@code DATA_ALTERACAO}) dos requerimentos protocolados em 2026. */
    public static final int[] CONCLUIDOS_POR_MES = {0, 0, 0, 2, 0, 0, 0, 1, 0, 0, 0, 0};
    public static final int PROTOCOLADOS_SEM_VOLUME = 27;
    public static final int CONCLUIDOS = 3;
    /** Requerimentos de 2026 na base: PN01 a PN15 (27) mais o volume. */
    public static final int BASE_DO_ANO = PROTOCOLADOS_SEM_VOLUME + PN16_TOTAL;
    /** Requerimentos da base (PN01 a PN15) fora da fila: 3 concluídos (PN02), 1 cancelado e 1 arquivado (PN04). */
    public static final int FORA_DA_FILA_SEM_VOLUME = 5;

    private static final String GRUPO_CONFERENCIA = "grpSurhConferirDocumentoSubterranea";
    private static final String GRUPO_ATRIBUICAO = "grpSurhAtribuirRequerimentoSuperficial";
    private static final String GRUPO_LAC = "GrpProcResponsavelAtendimentoLac";

    private CenariosFicticiosDaS10() {
    }

    private static LocalDateTime em(int mes, int dia) {
        return LocalDateTime.of(ANO, mes, dia, 10, 0);
    }

    /** Requerimento com situação, protocolo e última alteração controlados (a alteração é o {@code desde}). */
    private static Processo base(CamundaFicticioBuilder camunda, Fluxo fluxo, long id, String situacao,
            LocalDateTime protocolo, LocalDateTime alteracao) {
        Processo p = camunda.processo(fluxo, id).situacao(situacao).desde(alteracao);
        if (protocolo != null) {
            p.comDatas(protocolo, null, null, null).abertoEm(protocolo.minusDays(1));
        }
        return p;
    }

    private static Processo hidrico(CamundaFicticioBuilder camunda, long id, LocalDateTime protocolo, LocalDateTime alteracao) {
        return base(camunda, Fluxo.HIDRICO, id, "EM_ANALISE", protocolo, alteracao);
    }

    /** Grava todos os cenários; devolve o resultado de cada um por {@code "PNxx/<id>"}. */
    public static Map<String, Resultado> carregar(CamundaFicticioBuilder camunda) {
        Map<String, Resultado> r = new LinkedHashMap<>();

        // PN01: protocolados em vários meses, parados na conferência (tarefa de grupo). Janeiro é o limite do intervalo.
        r.put("PN01/" + PN01_JAN, conferencia(hidrico(camunda, PN01_JAN, LocalDateTime.of(ANO, 1, 1, 0, 0), em(1, 8))));
        r.put("PN01/" + PN01_MAR_A, conferencia(hidrico(camunda, PN01_MAR_A, em(3, 10), em(3, 12))));
        r.put("PN01/" + PN01_MAR_B, conferencia(hidrico(camunda, PN01_MAR_B, em(3, 25), em(3, 26))));
        r.put("PN01/" + PN01_JUN, conferencia(hidrico(camunda, PN01_JUN, em(6, 15), em(6, 16))));
        r.put("PN01/" + PN01_SET, conferencia(hidrico(camunda, PN01_SET, em(9, 20), em(9, 21))));
        r.put("PN01/" + PN01_DEZ, conferencia(hidrico(camunda, PN01_DEZ, LocalDateTime.of(ANO, 12, 31, 8, 0),
                LocalDateTime.of(ANO, 12, 31, 9, 0))));

        // PN02: concluídos (CONCLUIDO, final do catálogo) em meses diferentes dos protocolados.
        r.put("PN02/" + PN02_ABR_A, concluido(base(camunda, Fluxo.HIDRICO, PN02_ABR_A, "CONCLUIDO", em(2, 5), em(4, 20))));
        r.put("PN02/" + PN02_ABR_B, concluido(base(camunda, Fluxo.HIDRICO, PN02_ABR_B, "CONCLUIDO", em(2, 12), em(4, 28))));
        r.put("PN02/" + PN02_AGO, concluido(base(camunda, Fluxo.HIDRICO, PN02_AGO, "CONCLUIDO", em(5, 3), em(8, 14))));

        // PN03: o de 2025 (um segundo antes de 2026) foi concluído em 2026: fica fora da base e dos concluídos do painel.
        r.put("PN03/" + PN03_DE_2025, concluido(base(camunda, Fluxo.HIDRICO, PN03_DE_2025, "CONCLUIDO",
                LocalDateTime.of(2025, 12, 31, 23, 59, 59), em(2, 10)).numero(PN03_DE_2025 + "/2025")));
        // PN03: sem data de protocolo (a coluna fica vazia): só entra no aviso "sem data de protocolo".
        r.put("PN03/" + PN03_SEM_DATA, analise(base(camunda, Fluxo.HIDRICO, PN03_SEM_DATA, "EM_ANALISE", null, em(5, 5)),
                cpfDoServidor(1)));

        // PN04: cancelado (Hídrico) e arquivado (LAC): protocolados, mas sem tarefa ativa (fora da fila).
        r.put("PN04/" + PN04_CANCELADO, base(camunda, Fluxo.HIDRICO, PN04_CANCELADO,
                "CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE", em(7, 7), em(7, 20))
                .percorrendo(CADASTRO).encerradoEm("Event_1kq2k0z").gravar());
        r.put("PN04/" + PN04_ARQUIVADO, base(camunda, Fluxo.LAC, PN04_ARQUIVADO, "ARQUIVADO_DEFINITIVAMENTE", em(7, 9),
                em(10, 1)).percorrendo(CADASTRO, CONFERENCIA).encerradoEm("Event_0x7hz1p").gravar());

        // PN05: Hídrico em etapas diferentes; cada tarefa atribuída a um dos três servidores (01 tem duas, 02 tem duas).
        r.put("PN05/" + PN05_ANALISE_SERVIDOR_01, analise(hidrico(camunda, PN05_ANALISE_SERVIDOR_01, em(4, 6), em(4, 9)),
                cpfDoServidor(1)));
        r.put("PN05/" + PN05_REVISAO_SERVIDOR_02, hidrico(camunda, PN05_REVISAO_SERVIDOR_02, em(4, 13), em(4, 16))
                .naEtapa(REVISAO).atribuidoA(cpfDoServidor(2)).gravar());
        r.put("PN05/" + PN05_VALIDACAO_SERVIDOR_03, hidrico(camunda, PN05_VALIDACAO_SERVIDOR_03, em(5, 11), em(5, 14))
                .naEtapa(VALIDACAO).atribuidoA(cpfDoServidor(3)).gravar());
        r.put("PN05/" + PN05_ANALISE_SERVIDOR_01_B, analise(hidrico(camunda, PN05_ANALISE_SERVIDOR_01_B, em(10, 5),
                em(10, 8)), cpfDoServidor(1)));
        r.put("PN05/" + PN05_ACEITE_SERVIDOR_02, hidrico(camunda, PN05_ACEITE_SERVIDOR_02, em(11, 9), em(11, 12))
                .naEtapa(ACEITE).atribuidoA(cpfDoServidor(2)).gravar());

        // PN06: LAC em etapas do LAC (envio do termo pelo interessado e conferência por grupo de atendimento).
        r.put("PN06/" + PN06_ENVIO_DO_TERMO, base(camunda, Fluxo.LAC, PN06_ENVIO_DO_TERMO, "AGUARDANDO_ASSINATURA_LAC",
                em(8, 3), em(8, 6)).naEtapa(LAC_ENVIO).doInteressado().gravar());
        r.put("PN06/" + PN06_CONFERENCIA_DE_GRUPO, base(camunda, Fluxo.LAC, PN06_CONFERENCIA_DE_GRUPO, "EM_CONFERENCIA",
                em(8, 17), em(8, 19)).naEtapa(CONFERENCIA).paraGrupo(GRUPO_LAC).gravar());

        // PN07: etapa paralela: análise (ramo principal, Servidor 01) e correção (ramo de correção, interessado).
        r.put("PN07/" + PN07_PARALELA, hidrico(camunda, PN07_PARALELA, em(11, 16), em(11, 18))
                .naEtapa(ANALISE).atribuidoA(cpfDoServidor(1))
                .comTarefaParalela(CORRECAO, CamundaFicticioBuilder.CPF_DO_INTERESSADO).gravar());

        // PN08: complementação em andamento (instância independente, mesma chave de negócio); a raiz segue na análise.
        r.put("PN08/" + PN08_COMPLEMENTACAO, hidrico(camunda, PN08_COMPLEMENTACAO, em(10, 19), em(10, 22))
                .naEtapa(ANALISE).atribuidoA(cpfDoServidor(2)).comComplementacao(COMPLEMENTACAO_DO_PN08).gravar());

        // PN09: tarefa de grupo (área). PN10: tarefa do interessado. PN11: sem atribuição nem grupo.
        r.put("PN09/" + PN09_GRUPO, hidrico(camunda, PN09_GRUPO, em(9, 7), em(9, 8))
                .naEtapa(ATRIBUICAO).paraGrupo(GRUPO_ATRIBUICAO).gravar());
        r.put("PN10/" + PN10_INTERESSADO, hidrico(camunda, PN10_INTERESSADO, em(9, 14), em(9, 15))
                .naEtapa(CADASTRO).doInteressado().gravar());
        r.put("PN11/" + PN11_SEM_ATRIBUICAO, hidrico(camunda, PN11_SEM_ATRIBUICAO, em(10, 26), em(10, 27))
                .naEtapa(REVISAO).gravar());

        // PN12: ASSIGNEE_ com CPF sem servidor cadastrado (Não identificado).
        r.put("PN12/" + PN12_SEM_SERVIDOR, hidrico(camunda, PN12_SEM_SERVIDOR, em(11, 2), em(11, 3))
                .naEtapa(REVISAO).atribuidoA(CPF_SEM_SERVIDOR).gravar());

        // PN13: tarefa ativa com id técnico que o catálogo não conhece (Etapa não mapeada).
        r.put("PN13/" + PN13_NAO_MAPEADA, hidrico(camunda, PN13_NAO_MAPEADA, em(12, 7), em(12, 8))
                .comTarefaNaoMapeada(TAREFA_NAO_MAPEADA, null).gravar());

        // PN14: tipo de processo fora do catálogo (Trifásico): sem instância e sem modelo.
        r.put("PN14/" + PN14_FORA_DO_CATALOGO, base(camunda, Fluxo.TRIFASICO, PN14_FORA_DO_CATALOGO, "EM_ANALISE", em(1, 12),
                em(1, 14)).gravar());

        // PN15: requerimento do Hídrico sem instância no Camunda (legado).
        r.put("PN15/" + PN15_SEM_INSTANCIA, base(camunda, Fluxo.HIDRICO, PN15_SEM_INSTANCIA, "EM_ANALISE", em(2, 16),
                em(2, 18)).semInstancia().gravar());

        // PN16: volume de 2026, 100 por mês e 300 por etapa (conferência, atribuição, revisão e cadastro), para os lotes.
        for (int i = 0; i < PN16_TOTAL; i++) {
            long id = PN16_PRIMEIRO + i;
            LocalDateTime protocolo = em(1 + i % 12, 1 + (i / 12) % 28);
            Processo p = hidrico(camunda, id, protocolo, protocolo.plusDays(1));
            Resultado resultado = switch (i % 4) {
                case 0 -> conferencia(p);
                case 1 -> p.naEtapa(ATRIBUICAO).paraGrupo(GRUPO_ATRIBUICAO).gravar();
                case 2 -> p.naEtapa(REVISAO).atribuidoA(cpfDoServidor(1 + (i / 4) % 3)).gravar();
                default -> p.naEtapa(CADASTRO).doInteressado().gravar();
            };
            r.put("PN16/" + id, resultado);
        }
        return r;
    }

    private static Resultado conferencia(Processo p) {
        return p.naEtapa(CONFERENCIA).paraGrupo(GRUPO_CONFERENCIA).gravar();
    }

    private static Resultado analise(Processo p, String cpf) {
        return p.naEtapa(ANALISE).atribuidoA(cpf).gravar();
    }

    private static Resultado concluido(Processo p) {
        return p.percorrendo(CADASTRO, CONFERENCIA).encerradoEm("Event_1wiuc48").gravar();
    }

    /** Ids dos cenários PN01 a PN15 (sem o volume), na ordem de gravação. */
    public static List<Long> idsSemVolume() {
        return List.of(PN01_JAN, PN01_MAR_A, PN01_MAR_B, PN01_JUN, PN01_SET, PN01_DEZ, PN02_ABR_A, PN02_ABR_B, PN02_AGO,
                PN03_DE_2025, PN03_SEM_DATA, PN04_CANCELADO, PN04_ARQUIVADO, PN05_ANALISE_SERVIDOR_01,
                PN05_REVISAO_SERVIDOR_02, PN05_VALIDACAO_SERVIDOR_03, PN05_ANALISE_SERVIDOR_01_B, PN05_ACEITE_SERVIDOR_02,
                PN06_ENVIO_DO_TERMO, PN06_CONFERENCIA_DE_GRUPO, PN07_PARALELA, PN08_COMPLEMENTACAO, PN09_GRUPO,
                PN10_INTERESSADO, PN11_SEM_ATRIBUICAO, PN12_SEM_SERVIDOR, PN13_NAO_MAPEADA, PN14_FORA_DO_CATALOGO,
                PN15_SEM_INSTANCIA);
    }
}
