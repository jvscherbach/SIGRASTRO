package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ACEITE;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ANALISE;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.ATRIBUICAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.AUTORIZACOES;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CADASTRO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CONFERENCIA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CORRECAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.CORRECAO_STATUS;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.EMITIR_TAXA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.OUTROS_ATOS;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PAGAMENTO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PORTARIA;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PROTOCOLO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.PUBLICACAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.REVISAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.VALIDACAO;
import static br.gov.mt.sema.rastro.integration.siga.CenariosFicticiosDaS8.VERIFICACAO;

import java.time.Clock;
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
 * População fictícia da caixa do analista (S11, bloco 1): cenários CX01 a CX17, ids <b>15001 a 15099</b>, e o volume
 * (1.200 requerimentos, ids <b>16001 a 17200</b>) para provar a leitura em lotes. Pessoas (interessados) em <b>15001 a
 * 15099</b> (a tabela {@code PESSOA} tem a sua própria numeração). Testes da S11 que criem cenário próprio usam 18000 em
 * diante. Tabela do cenário (estado esperado, aviso e origem) em {@code docs/progresso/s11.md}.
 *
 * <p>Como a população da S10, esta <b>não é gravada ao subir o Oracle de teste</b>: {@code SigaOracleTeste.cenariosDaCaixa()}
 * e {@code SigaOracleTeste.volumeDaCaixa()} gravam sob demanda. <b>Todas as datas ficam em 2027 e 2028</b> (o "hoje" é
 * {@link #HOJE}, 2028-06-15), longe de 2026: assim a população não entra na base do ano do painel da S10 nem nos testes
 * de 2025 e 2026.
 *
 * <p>Analistas (CPFs fictícios inválidos, "Servidor Exemplo 01 a 03" do recorte; os usuários Analista "Analista Exemplo NN"
 * vivem na memória do sistema): {@link #A1}, {@link #A2} e {@link #A3}; {@link #A4} é o usuário Analista <b>sem CPF
 * cadastrado</b> (não existe no Camunda). Conferência 10, atribuição 11, revisão 04 (chefia) e publicação 08 são outros
 * servidores. Hipóteses NÃO CONFIRMADAS (docs/10): o valor das variáveis de devolução ({@code isRevisaoAnalise},
 * {@code isRevisaoParecer}, {@code decisaoValidacaoParecerTecnico} = RA) e a devolução da emissão do ato direto para a
 * análise (CX17).
 */
public final class CenariosFicticiosDaS11 {

    // Analistas
    public static final String A1 = cpfDoServidor(1);
    public static final String A2 = cpfDoServidor(2);
    public static final String A3 = cpfDoServidor(3);
    /** Usuário Analista sem CPF cadastrado: não tem tarefa no Camunda e vê a caixa vazia com a mensagem de vínculo. */
    public static final String A4 = null;
    public static final String NOME_DO_A1 = "Analista Exemplo 01";
    public static final String NOME_DO_A2 = "Analista Exemplo 02";
    public static final String NOME_DO_A3 = "Analista Exemplo 03";
    public static final String NOME_DO_A4 = "Analista Exemplo 04 (sem CPF)";

    // Interessados (PESSOA): o mesmo campo guarda pessoa e empresa
    public static final long P1 = 15001;
    public static final long P2 = 15002;
    public static final long P3 = 15003;
    public static final long P4 = 15004;
    public static final long P_VOLUME = 15010;
    public static final String NOME_DO_P1 = "Interessado Exemplo 01";
    public static final String NOME_DO_P2 = "Interessado Exemplo LTDA";
    public static final String NOME_DO_P3 = "Interessado Exemplo 03";
    public static final String NOME_DO_P4 = "Cooperativa Exemplo 04";
    public static final String NOME_DO_P_VOLUME = "Interessado Exemplo Volume";

    // Requerimentos
    public static final long CX01 = 15001;
    public static final long CX02 = 15002;
    public static final long CX03 = 15003;
    public static final long CX04 = 15004;
    public static final long CX05 = 15005;
    public static final long CX06 = 15006;
    public static final long CX07 = 15007;
    public static final long CX08 = 15008;
    public static final long CX09 = 15009;
    public static final long CX10 = 15010;
    public static final long CX11 = 15011;
    public static final long CX12 = 15012;
    public static final long CX13 = 15013;
    public static final long CX14 = 15014;
    public static final long CX15 = 15015;
    public static final long CX16 = 15016;
    public static final long CX17 = 15017;

    // Volume (leitura em lotes de 500: 1.200 = 3 lotes)
    public static final long VOLUME_PRIMEIRO = 16001;
    public static final int VOLUME_TOTAL = 1200;
    /** O volume é repartido entre A1, A2 e A3: 400 para cada um, todos na análise. */
    public static final int VOLUME_POR_ANALISTA = VOLUME_TOTAL / 3;

    // Estados de posse (ids do catálogo de etapas do Hídrico) e do "último visto"
    public static final String COM_ANALISTA_ANALISE = "COM_ANALISTA(analise_do_processo)";
    public static final String COM_ANALISTA_REVISAO = "COM_ANALISTA(revisao_do_parecer_tecnico)";
    public static final String COM_INTERESSADO = "COM_INTERESSADO";
    public static final String FORA = "FORA";
    /** O requerimento não constava do "último visto" do analista. */
    public static final String INEXISTENTE = "INEXISTENTE";

    // Origens do retorno (rótulos que a lista de mudanças mostra)
    public static final String VOLTOU_DA_CORRECAO = "Voltou da correção";
    public static final String VOLTOU_DA_VALIDACAO = "Voltou da validação do parecer";
    public static final String VOLTOU_DA_EMISSAO = "Voltou da emissão do ato";
    public static final String VOLTOU_DA_REVISAO = "Voltou da revisão";
    public static final String NOVO_NA_CAIXA = "Novo na sua caixa";

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");
    /** O "hoje" dos testes da caixa: 2028-06-15, 12h em Cuiabá. */
    public static final LocalDate HOJE = LocalDate.of(2028, 6, 15);
    public static final Clock RELOGIO = Clock.fixed(HOJE.atTime(12, 0).atZone(FUSO).toInstant(), FUSO);

    private static final String CONFERIDOR = cpfDoServidor(10);
    private static final String ATRIBUIDOR = cpfDoServidor(11);
    private static final String CHEFIA = cpfDoServidor(4);
    private static final String PUBLICADOR = cpfDoServidor(8);

    /**
     * Uma linha da tabela do cenário. {@code analista} é o dono da posse que o caso exercita (A1, A2 ou A3; {@code null}
     * quando o requerimento não é de nenhum analista). {@code estadoNoUltimoVisto} é o que o "visto" do analista guardava
     * para o requerimento <i>antes</i> da atualização que o teste do bloco 5 simula. {@code diasNaEtapa} é do início da
     * tarefa atual até {@link #RELOGIO} (nulo quando não há tarefa atual, como no concluído).
     */
    public record Caso(String codigo, long id, String analista, String interessado, String estadoEsperado,
            String estadoNoUltimoVisto, boolean aviso, String origem, Integer diasNaEtapa) {

        public boolean daPosse() {
            return estadoEsperado.startsWith("COM_ANALISTA");
        }
    }

    /** A tabela do cenário, na ordem dos códigos. */
    public static final List<Caso> CASOS = List.of(
            new Caso("CX01", CX01, "A1", NOME_DO_P1, COM_ANALISTA_ANALISE, COM_ANALISTA_ANALISE, false, null, 20),
            new Caso("CX02", CX02, "A2", NOME_DO_P2, COM_ANALISTA_REVISAO, COM_ANALISTA_REVISAO, false, null, 8),
            new Caso("CX03", CX03, "A1", NOME_DO_P3, COM_ANALISTA_REVISAO, COM_ANALISTA_ANALISE, false, null, 6),
            new Caso("CX04", CX04, "A1", NOME_DO_P4, COM_INTERESSADO, COM_ANALISTA_ANALISE, false, null, 12),
            new Caso("CX05", CX05, "A1", NOME_DO_P3, COM_ANALISTA_ANALISE, COM_INTERESSADO, true, VOLTOU_DA_CORRECAO, 10),
            new Caso("CX06", CX06, "A1", NOME_DO_P1, COM_ANALISTA_REVISAO, FORA, true, VOLTOU_DA_EMISSAO, 4),
            new Caso("CX07", CX07, "A1", NOME_DO_P2, COM_ANALISTA_ANALISE, FORA, true, VOLTOU_DA_VALIDACAO, 9),
            new Caso("CX08", CX08, "A1", NOME_DO_P4, COM_ANALISTA_ANALISE, FORA, true, VOLTOU_DA_REVISAO, 7),
            new Caso("CX09", CX09, "A1", NOME_DO_P3, COM_ANALISTA_ANALISE, INEXISTENTE, true, NOVO_NA_CAIXA, 2),
            new Caso("CX10", CX10, "A1", NOME_DO_P1, FORA, COM_ANALISTA_REVISAO, false, null, 18),
            new Caso("CX11", CX11, "A1", NOME_DO_P2, FORA, FORA, false, null, 10),
            new Caso("CX12", CX12, "A1", NOME_DO_P4, FORA, FORA, false, null, null),
            new Caso("CX13", CX13, "A2", NOME_DO_P2, COM_INTERESSADO, COM_ANALISTA_ANALISE, false, null, 9),
            new Caso("CX14", CX14, "A1", NOME_DO_P2, COM_ANALISTA_ANALISE, COM_ANALISTA_ANALISE, false, null, 400),
            new Caso("CX15", CX15, null, NOME_DO_P3, FORA, INEXISTENTE, false, null, 28),
            new Caso("CX16", CX16, "A3", NOME_DO_P1, COM_ANALISTA_ANALISE, COM_ANALISTA_ANALISE, false, null, 15),
            new Caso("CX17", CX17, "A1", NOME_DO_P3, COM_ANALISTA_ANALISE, FORA, true, VOLTOU_DA_EMISSAO, 3));

    private CenariosFicticiosDaS11() {
    }

    /** Ids dos cenários CX01 a CX17, na ordem de gravação. */
    public static List<Long> ids() {
        return CASOS.stream().map(Caso::id).toList();
    }

    /** {@code n} dias antes de {@link #HOJE}, às 9h (o "agora" é 12h: {@code a(n)} dá {@code n} dias na etapa). */
    static LocalDateTime a(int n) {
        return HOJE.atTime(9, 0).minusDays(n);
    }

    private static Processo hidrico(CamundaFicticioBuilder camunda, long id, String situacao, long pessoa, int ano) {
        return camunda.processo(Fluxo.HIDRICO, id).situacao(situacao).numero(id + "/" + ano).comInteressado(pessoa);
    }

    /**
     * Cadastro até o aceite pelo analista {@code aceitante} (taxável): a análise começa em {@code ia}, oito dias depois do
     * cadastro. O protocolo fica cinco dias depois do cadastro (a data de protocolo preenchida mantém a população fora do
     * aviso "sem data" do painel).
     */
    private static Processo ate(Processo p, LocalDateTime ia, String aceitante) {
        LocalDateTime t0 = ia.minusDays(8);
        return p.passou(CADASTRO, t0, t0.plusDays(1))
                .passou(CONFERENCIA, t0.plusDays(1), t0.plusDays(2), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", t0.plusDays(2))
                .passou(EMITIR_TAXA, t0.plusDays(2), t0.plusDays(2).plusHours(1))
                .passouPelaEspera(PAGAMENTO, t0.plusDays(2).plusHours(1), t0.plusDays(5))
                .passou(PROTOCOLO, t0.plusDays(5), t0.plusDays(5).plusHours(1))
                .passou(ATRIBUICAO, t0.plusDays(5).plusHours(1), t0.plusDays(6), ATRIBUIDOR)
                .passouEmCiclo(ACEITE, t0.plusDays(6), ia, aceitante)
                .comDatas(t0.plusDays(5), null, null, null);
    }

    /** Análise concluída por {@code analista} de {@code ia} a {@code fim}, e a revisão da chefia até {@code fimDaRevisao}. */
    private static Processo ateARevisao(Processo p, LocalDateTime ia, String analista, LocalDateTime fimDaAnalise,
            LocalDateTime fimDaRevisao) {
        return ate(p, ia, analista).passouEmCiclo(ANALISE, ia, fimDaAnalise, analista)
                .passou(REVISAO, fimDaAnalise, fimDaRevisao, CHEFIA);
    }

    /** Análise encerrada pelo pedido de correção do analista e a correção de {@code inicio} em diante (a tarefa fica ativa). */
    private static Resultado emCorrecao(Processo p, LocalDateTime ia, String analista, int diasAtrasDoPedido) {
        LocalDateTime pedido = a(diasAtrasDoPedido);
        return ate(p, ia, analista).passouEmCicloEncerradoPor(ANALISE, ia, pedido, analista)
                .passou(CORRECAO_STATUS, pedido, pedido.plusHours(1))
                .naEtapa(CORRECAO).doInteressado().desde(pedido.plusHours(1)).gravar();
    }

    /** Grava as pessoas e os cenários CX01 a CX17; devolve o resultado de cada um por {@code "CXnn/<id>"}. */
    public static Map<String, Resultado> carregar(CamundaFicticioBuilder camunda) {
        camunda.pessoaFicticia(P1, NOME_DO_P1, "00000000101");
        camunda.pessoaFicticia(P2, NOME_DO_P2, "00000000000102");
        camunda.pessoaFicticia(P3, NOME_DO_P3, "00000000103");
        camunda.pessoaFicticia(P4, NOME_DO_P4, "00000000000104");

        Map<String, Resultado> r = new LinkedHashMap<>();

        // CX01: análise de A1, sem mudança desde o último visto.
        r.put("CX01/" + CX01, ate(hidrico(camunda, CX01, "EM_ANALISE", P1, 2028), a(20), A1)
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(20)).gravar());

        // CX02: revisão do parecer com A2 (a revisão também está na posse do analista a quem foi atribuída).
        r.put("CX02/" + CX02, ate(hidrico(camunda, CX02, "EM_ANALISE", P2, 2028), a(30), A2)
                .passouEmCiclo(ANALISE, a(30), a(8), A2)
                .naEtapa(REVISAO).atribuidoA(A2).desde(a(8)).gravar());

        // CX03: mudou de etapa DENTRO da posse (análise para revisão, ambas de A1): sem aviso.
        r.put("CX03/" + CX03, ate(hidrico(camunda, CX03, "EM_ANALISE", P3, 2028), a(25), A1)
                .passouEmCiclo(ANALISE, a(25), a(6), A1)
                .naEtapa(REVISAO).atribuidoA(A1).desde(a(6)).gravar());

        // CX04: correção com o interessado, última análise de A1: o requerimento continua sendo dele (posse "com o
        // interessado"). Saiu da análise para a correção: saída da posse não gera aviso.
        r.put("CX04/" + CX04, emCorrecao(hidrico(camunda, CX04, "EM_CORRECAO", P4, 2028), a(40), A1, 12));

        // CX05: a correção voltou para A1 (análise de novo): aviso "Voltou da correção".
        LocalDateTime pedido = a(20);
        r.put("CX05/" + CX05, ate(hidrico(camunda, CX05, "EM_ANALISE", P3, 2028), a(50), A1)
                .passouEmCicloEncerradoPor(ANALISE, a(50), pedido, A1)
                .passou(CORRECAO_STATUS, pedido, pedido.plusHours(1))
                .passou(CORRECAO, pedido.plusHours(1), a(10))
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(10)).gravar());

        // CX06: foi até a emissão do ato, a filha foi devolvida e voltou para a REVISÃO de A1: "Voltou da emissão do ato".
        r.put("CX06/" + CX06, ateARevisao(hidrico(camunda, CX06, "EM_ANALISE", P1, 2028), a(60), A1, a(30), a(14))
                .passouEmCiclo(VALIDACAO, a(14), a(6), A2)
                .passouPelaEmissaoComDevolucao(a(6), a(4), OUTROS_ATOS, AUTORIZACOES)
                .variavel("isRevisaoParecer", "true", a(4))
                .naEtapa(REVISAO).atribuidoA(A1).desde(a(4)).gravar());

        // CX07: a validação devolveu para a ANÁLISE de A1: "Voltou da validação do parecer".
        r.put("CX07/" + CX07, ateARevisao(hidrico(camunda, CX07, "EM_ANALISE", P2, 2028), a(70), A1, a(40), a(38))
                .passouEmCiclo(VALIDACAO, a(38), a(9), A2)
                .variavel("decisaoValidacaoParecerTecnico", "RA", a(9))
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(9)).gravar());

        // CX08: a revisão da chefia devolveu para a ANÁLISE de A1: "Voltou da revisão".
        r.put("CX08/" + CX08, ateARevisao(hidrico(camunda, CX08, "EM_ANALISE", P4, 2028), a(80), A1, a(30), a(7))
                .variavel("isRevisaoAnalise", "true", a(7))
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(7)).gravar());

        // CX09: processo novo distribuído a A1 (aceite há 2 dias): "Novo na sua caixa".
        r.put("CX09/" + CX09, ate(hidrico(camunda, CX09, "EM_ANALISE", P3, 2028), a(2), A1)
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(2)).gravar());

        // CX10: na validação, mesmo com a tarefa atribuída a A1, sai da caixa. Saiu da posse: sem aviso.
        r.put("CX10/" + CX10, ateARevisao(hidrico(camunda, CX10, "EM_ANALISE", P1, 2028), a(50), A1, a(20), a(18))
                .naEtapa(VALIDACAO).atribuidoA(A1).desde(a(18)).gravar());

        // CX11: na emissão do ato (filhas ativas): fora da caixa.
        r.put("CX11/" + CX11, ateARevisao(hidrico(camunda, CX11, "EM_ANALISE", P2, 2028), a(45), A1, a(20), a(16))
                .passouEmCiclo(VALIDACAO, a(16), a(10), A2)
                .comFilhas(AUTORIZACOES).desde(a(10)).gravar());

        // CX12: concluído (final do catálogo): fora da caixa.
        r.put("CX12/" + CX12, ateARevisao(hidrico(camunda, CX12, "CONCLUIDO", P4, 2028), a(90), A1, a(60), a(58))
                .passouEmCiclo(VALIDACAO, a(58), a(56), A2)
                .passouPelaEmissao(a(56), a(54), AUTORIZACOES, PORTARIA)
                .passou(PUBLICACAO, a(54), a(40), PUBLICADOR)
                .passou(VERIFICACAO, a(40), a(39), cpfDoServidor(10))
                .encerradoEm("Event_1wiuc48").desde(a(39)).gravar());

        // CX13: correção com última análise de A2: só aparece na caixa de A2 (A1 não vê).
        r.put("CX13/" + CX13, emCorrecao(hidrico(camunda, CX13, "EM_CORRECAO", P2, 2028), a(35), A2, 9));

        // CX14: de ano anterior (2027), na análise de A1: a caixa não usa o recorte do ano.
        r.put("CX14/" + CX14, ate(hidrico(camunda, CX14, "EM_ANALISE", P2, 2027), a(400), A1)
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(400)).gravar());

        // CX15: correção sem nenhuma análise antes (ainda no cadastro): não é de nenhum analista.
        r.put("CX15/" + CX15, hidrico(camunda, CX15, "EM_CORRECAO", P3, 2028)
                .passou(CADASTRO, a(30), a(29))
                .passou(CONFERENCIA, a(29), a(28), CONFERIDOR)
                .comDatas(a(29), null, null, null)
                .naEtapa(CORRECAO).doInteressado().desde(a(28)).gravar());

        // CX16: análise de A3, do mesmo interessado do CX01 (dois requerimentos com o mesmo interessado).
        r.put("CX16/" + CX16, ate(hidrico(camunda, CX16, "EM_ANALISE", P1, 2028), a(15), A3)
                .naEtapa(ANALISE).atribuidoA(A3).desde(a(15)).gravar());

        // CX17: a emissão do ato devolveu para a ANÁLISE de A1: "Voltou da emissão do ato".
        r.put("CX17/" + CX17, ateARevisao(hidrico(camunda, CX17, "EM_ANALISE", P3, 2028), a(55), A1, a(25), a(23))
                .passouEmCiclo(VALIDACAO, a(23), a(8), A2)
                .passouPelaEmissaoComDevolucao(a(8), a(3), OUTROS_ATOS, PORTARIA)
                .variavel("isRevisaoAnalise", "true", a(3))
                .naEtapa(ANALISE).atribuidoA(A1).desde(a(3)).gravar());
        return r;
    }

    /**
     * Grava o volume: 1.200 requerimentos na análise, 400 de cada analista (A1, A2 e A3), do mesmo interessado, sem
     * histórico (só a tarefa ativa). Devolve o resultado de cada um por {@code "VOL/<id>"}.
     */
    public static Map<String, Resultado> carregarVolume(CamundaFicticioBuilder camunda) {
        camunda.pessoaFicticia(P_VOLUME, NOME_DO_P_VOLUME, "00000000105");
        Map<String, Resultado> r = new LinkedHashMap<>();
        String[] analistas = {A1, A2, A3};
        for (int i = 0; i < VOLUME_TOTAL; i++) {
            long id = VOLUME_PRIMEIRO + i;
            LocalDateTime desde = a(1 + i % 60);
            r.put("VOL/" + id, camunda.processo(Fluxo.HIDRICO, id).situacao("EM_ANALISE").numero(id + "/2028")
                    .comInteressado(P_VOLUME).comDatas(desde.minusDays(10), null, null, null)
                    .naEtapa(ANALISE).atribuidoA(analistas[i % 3]).desde(desde).gravar());
        }
        return r;
    }
}
