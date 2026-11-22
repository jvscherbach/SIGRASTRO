package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CORRECAO_EXPIRADA_FICTICIA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPETICAO_POR_ERRO_FICTICIA;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.REPORT_KAFKA_FICTICIO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.dias;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Processo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;

/**
 * Cenários fictícios do caminho percorrido da S8 (prompt, bloco 1), um requerimento cada, números {@code <id>/2026} (os do
 * plano B são de 2021) e interessado "Interessado Fictício S7". Carregados por {@link SigaOracleTeste} ao subir o Oracle de
 * teste, depois dos da S7 ({@link CenariosFicticiosDaS7}, ids 7000 a 7199: esses mantêm o caminho mínimo e a posição atual);
 * os testes dos blocos 2 a 11 consultam por estes ids. Faixa da S8: <b>8001 a 8199</b>. Tabela completa em docs/15.
 *
 * <p>Convenção de responsáveis (CPFs fictícios inválidos, "Servidor Exemplo NN"): conferência 02, atribuição 03, revisão do
 * parecer 04 (chefia), analistas e validadores 05, 06 e 07, publicação 08, emissão 09 (filhas), suporte 12. O
 * cadastrante é o interessado fictício. Os tempos são relativos a {@link CamundaFicticioBuilder#DESDE_PADRAO}.
 *
 * <p>Hipóteses NÃO CONFIRMADAS (docs/15, P40): os valores das variáveis ({@code decisaoValidacaoParecerTecnico} = RA/RP,
 * {@code revisarRequerimento}, {@code isRevisaoAnalise} = true para troca de analista, {@code decisaoVerificarRevisao} =
 * REVISAR, {@code statusConferencia}); a gravação da correção expirada (tarefa {@code deleted} + evento de borda fictício);
 * os ids de "Report Kafka" e de repetição por erro. O bloco 3 (OPUS) confere cada uma contra o BPMN antes de fixar a regra.
 */
public final class CenariosFicticiosDaS8 {

    // Hídrico: caminhos com voltas, ruído e filhas
    public static final long HC01 = 8001;
    public static final long HC02 = 8002;
    public static final long HC03 = 8003;
    public static final long HC04 = 8004;
    public static final long HC05 = 8005;
    public static final long HC06 = 8006;
    public static final long HC07 = 8007;
    public static final long HC08 = 8008;
    public static final long HC09 = 8009;
    public static final long HC10 = 8010;
    public static final long HC11 = 8011;
    public static final long HC12 = 8012;
    public static final long HC13 = 8013;
    public static final long HC14 = 8014;
    public static final long HC15 = 8015;
    // Hídrico: finais
    public static final long HF01 = 8021;
    public static final long HF02 = 8022;
    public static final long HF03 = 8023;
    public static final long HF04 = 8024;
    // LAC
    public static final long LC01 = 8051;
    public static final long LC02 = 8052;
    public static final long LC03 = 8053;
    // LAC: finais (o "concluído" é o LC01)
    public static final long LF01 = 8061;
    public static final long LF02 = 8062;
    public static final long LF03 = 8063;
    public static final long LF04 = 8064;
    public static final long LF05 = 8065;
    // Plano B: sem instância no Camunda
    public static final long PB01 = 8091;
    public static final long PB02 = 8092;
    public static final long PB03 = 8093;

    /** Id da complementação do HC14. */
    public static final long COMPLEMENTACAO_DO_HC14 = 8814;
    /** Quantas tentativas do script repetido no HC15: passa do limite padrão de 3.000 linhas por instância (bloco 2). */
    public static final int REPETICOES_DO_HC15 = 3_500;

    static final String CADASTRO = "taskCadastrarRequerimento";
    static final String CONFERENCIA = "taskConferirDocumentosApresentados";
    static final String EMITIR_TAXA = "taskEmitirTaxaSefaz";
    static final String PAGAMENTO = "gtwAguardarPagamento";
    static final String PROTOCOLO = "taskGerarNumeroProtocolo";
    static final String ATRIBUICAO = "taskAtribuirRequerimento";
    static final String ACEITE = "taskAceitarAtribuicao";
    static final String ANALISE = "taskAnalisarRequerimento";
    static final String REVISAO = "taskRevisarParecerTecnico";
    static final String VALIDACAO = "taskValidarParecerTecnico";
    static final String PUBLICACAO = "taskRegistarComprovantePublicacao";
    static final String VERIFICACAO = "taskVerificarRevisaoProcesso";
    static final String CORRECAO_STATUS = "taskAtualizarStatusAguardandoRevisao";
    static final String CORRECAO = "taskCorrecaoRequerimento";
    static final String EMITIR_TAXA_CORRECAO = "taskEmitirTaxaSefazCorrecao";
    static final String PAGAMENTO_CORRECAO = "gtwAguardarPagamentoRevisao";
    static final String AUTORIZACOES = "procSurhEmitirAutorizacoes";
    static final String OUTROS_ATOS = "procSurhEmitirOutrosAtos";
    static final String PORTARIA = "procSurhEmissaoPortaria";
    static final String GRUPO_REVISAO = "grpSurhRevisarDecisaoMultidisciplinar";

    // LAC
    static final String LAC_PAGAMENTO = "gwtAguardarPagtoOuVencto";
    static final String LAC_TERMO = "taskGerarTermoAdesaoLAC";
    static final String LAC_ENVIO = "taskEnviarTermoAdesao";
    static final String LAC_CONCLUSAO = "taskUpdateStatusConcluido";

    private static final String CONFERIDOR = cpfDoServidor(2);
    private static final String ATRIBUIDOR = cpfDoServidor(3);
    private static final String CHEFIA = cpfDoServidor(4);
    private static final String ANALISTA_1 = cpfDoServidor(5);
    private static final String ANALISTA_2 = cpfDoServidor(6);
    private static final String ANALISTA_3 = cpfDoServidor(7);
    private static final String PUBLICADOR = cpfDoServidor(8);

    private CenariosFicticiosDaS8() {
    }

    /** Grava todos os cenários; devolve o resultado de cada um pelo código (HC01, LC01, PB01...). */
    public static Map<String, Resultado> carregar(CamundaFicticioBuilder camunda) {
        Map<String, Resultado> r = new LinkedHashMap<>();

        // HC01: caminho feliz taxável, até a verificação da revisão ativa (processo concluído pela publicação).
        r.put("HC01", validacaoAteAEmissao(taxavelAteAAnalise(hidrico(camunda, HC01, "CONCLUIDO")))
                .passouPelaEmissao(dias(24), dias(26), AUTORIZACOES, PORTARIA)
                .passou(PUBLICACAO, dias(26), dias(30), PUBLICADOR)
                .naEtapa(VERIFICACAO).paraGrupo(GRUPO_REVISAO).desde(dias(30)).gravar());

        // HC02: isento. Sem emissão da taxa nem espera; o protocolo é gerado direto (isenção confirmada).
        r.put("HC02", hidrico(camunda, HC02, "EM_ANALISE")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "isento", dias(2))
                .passou(PROTOCOLO, dias(2), dias(2, 1))
                .passou(ATRIBUICAO, dias(2, 1), dias(3), ATRIBUIDOR)
                .passouEmCiclo(ACEITE, dias(3), dias(5), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .naEtapa(ANALISE).comAnalistas(3).desde(dias(5)).gravar());

        // HC03: a conferência reprova, volta ao cadastro (2ª passagem) e o statusConferencia é regravado.
        r.put("HC03", hidrico(camunda, HC03, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "reprovado", dias(2))
                .voltouPara(CADASTRO, dias(2), dias(3))
                .voltouPara(CONFERENCIA, dias(3), dias(4), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", dias(4))
                .passou(EMITIR_TAXA, dias(4), dias(4, 1))
                .esperandoPagamento().desde(dias(4, 1)).gravar());

        // HC04: validação devolvida RA (volta à análise) e depois RP (volta à revisão do parecer).
        r.put("HC04", validacaoAteAEmissao(taxavelAteAAnalise(hidrico(camunda, HC04, "EM_ANALISE")))
                .variavel("decisaoValidacaoParecerTecnico", "RA", dias(24))
                .voltouParaEmCiclo(ANALISE, dias(24), dias(26), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .voltouPara(REVISAO, dias(26), dias(27), CHEFIA)
                .voltouParaEmCiclo(VALIDACAO, dias(27), dias(28), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .variavel("decisaoValidacaoParecerTecnico", "RP", dias(28))
                .voltouPara(REVISAO, dias(28), dias(29), CHEFIA)
                .naEtapa(VALIDACAO).comAnalistas(3).atribuidoA(ANALISTA_1, ANALISTA_2, ANALISTA_3).desde(dias(29)).gravar());

        // HC05: troca de analista na revisão e na validação: devolve sempre à análise.
        r.put("HC05", taxavelAteAAnalise(hidrico(camunda, HC05, "EM_ANALISE"))
                .passouEmCiclo(ANALISE, dias(8), dias(12), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passou(REVISAO, dias(12), dias(13), CHEFIA)
                .variavel("isRevisaoAnalise", "true", dias(13))
                .voltouParaEmCiclo(ANALISE, dias(13), dias(15), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .voltouPara(REVISAO, dias(15), dias(16), CHEFIA)
                .passouEmCiclo(VALIDACAO, dias(16), dias(17), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .variavel("isRevisaoAnalise", "true", dias(17))
                .voltouParaEmCiclo(ANALISE, dias(17), dias(18), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .naEtapa(REVISAO).atribuidoA(CHEFIA).desde(dias(18)).gravar());

        // HC06: recusa no aceite (as outras tarefas do ciclo são encerradas) e depois redistribuição (volta à atribuição).
        r.put("HC06", hidrico(camunda, HC06, "EM_ANALISE")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", dias(2))
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(PAGAMENTO, dias(2, 1), dias(5))
                .passou(PROTOCOLO, dias(5), dias(5, 1))
                .passou(ATRIBUICAO, dias(5, 1), dias(6), ATRIBUIDOR)
                .passouEmCicloEncerradoPor(ACEITE, dias(6), dias(7), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .voltouPara(ATRIBUICAO, dias(7), dias(8), ATRIBUIDOR)
                .voltouParaEmCiclo(ACEITE, dias(8), dias(10), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passouEmCiclo(ANALISE, dias(10), dias(14), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .voltouPara(ATRIBUICAO, dias(14), dias(15), ATRIBUIDOR)
                .naEtapa(ACEITE).comAnalistas(3).atribuidoA(ANALISTA_1, ANALISTA_2, ANALISTA_3).desde(dias(15)).gravar());

        // HC07: correção com nova taxa (emissão da taxa e espera próprias) e retorno direto à análise, sem nova atribuição.
        r.put("HC07", correcaoAteACorrecao(hidrico(camunda, HC07, "EM_ANALISE"), 12)
                .passou(EMITIR_TAXA_CORRECAO, dias(14), dias(14, 1))
                .passouPelaEspera(PAGAMENTO_CORRECAO, dias(14, 1), dias(17))
                .naEtapa(ANALISE).comAnalistas(3).desde(dias(17)).gravar());

        // HC08: correção sem nova taxa; volta direto à análise.
        r.put("HC08", correcaoAteACorrecao(hidrico(camunda, HC08, "EM_ANALISE"), 12)
                .naEtapa(ANALISE).comAnalistas(3).desde(dias(14)).gravar());

        // HC09: correção EXPIRADA no Hídrico: segue para a análise, não encerra (hipótese da gravação: P40).
        r.put("HC09", taxavelAteAAnalise(hidrico(camunda, HC09, "EM_ANALISE"))
                .passouEmCicloEncerradoPor(ANALISE, dias(8), dias(12), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passou(CORRECAO_STATUS, dias(12), dias(12, 1))
                .expirou(CORRECAO, dias(12, 1), dias(42))
                .atividadeFicticia(CORRECAO_EXPIRADA_FICTICIA, "boundaryTimer", dias(42), dias(42))
                .naEtapa(ANALISE).comAnalistas(3).desde(dias(42, 1)).gravar());

        // HC10: um analista pede correção; as outras duas tarefas do ciclo ficam 'deleted' (não contam como percorridas).
        r.put("HC10", taxavelAteAAnalise(hidrico(camunda, HC10, "EM_CORRECAO"))
                .passouEmCicloEncerradoPor(ANALISE, dias(8), dias(12), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passou(CORRECAO_STATUS, dias(12), dias(12, 1))
                .naEtapa(CORRECAO).doInteressado().desde(dias(12, 1)).gravar());

        // HC11: ruído: suporte, "Report Kafka", script repetido 4 vezes por erro de API e o evento de repetição.
        r.put("HC11", hidrico(camunda, HC11, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passouPeloSuporte(dias(1), dias(1, 2))
                .passou(CONFERENCIA, dias(1, 2), dias(2), CONFERIDOR)
                .atividadeFicticia(REPORT_KAFKA_FICTICIO, "serviceTask", dias(2), dias(2, 0))
                .scriptRepetido(EMITIR_TAXA, 4, dias(2, 1), 30)
                .atividadeFicticia(REPETICAO_POR_ERRO_FICTICIA, "intermediateTimer", dias(2, 2), dias(2, 2))
                .esperandoPagamento().desde(dias(2, 3)).gravar());

        // HC12: emissão com três filhas, uma devolvida para a revisão do parecer (isRevisaoParecer); a emissão é refeita.
        r.put("HC12", validacaoAteAEmissao(taxavelAteAAnalise(hidrico(camunda, HC12, "EM_ANALISE")))
                .passouPelaEmissaoComDevolucao(dias(24), dias(25), OUTROS_ATOS, AUTORIZACOES, PORTARIA)
                .variavel("isRevisaoParecer", "true", dias(25))
                .voltouPara(REVISAO, dias(25), dias(26), CHEFIA)
                .voltouParaEmCiclo(VALIDACAO, dias(26), dias(27), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passouPelaEmissao(dias(27), dias(28), OUTROS_ATOS, AUTORIZACOES, PORTARIA)
                .naEtapa(PUBLICACAO).paraGrupo("grpSurhRegistrarPublicacaoBarramento").desde(dias(28)).gravar());

        // HC13: REVISAR na verificação (recria a emissão): caso raro, mas o modelo permite.
        r.put("HC13", validacaoAteAEmissao(taxavelAteAAnalise(hidrico(camunda, HC13, "CONCLUIDO")))
                .passouPelaEmissao(dias(24), dias(26), AUTORIZACOES)
                .passou(PUBLICACAO, dias(26), dias(30), PUBLICADOR)
                .passou(VERIFICACAO, dias(30), dias(31), cpfDoServidor(10))
                .variavel("decisaoVerificarRevisao", "REVISAR", dias(31))
                .passouPelaEmissao(dias(31), dias(32), OUTROS_ATOS)
                .naEtapa(PUBLICACAO).paraGrupo("grpSurhRegistrarPublicacaoBarramento").desde(dias(32)).gravar());

        // HC14: complementação paralela concluída durante a análise (instância independente, mesma chave de requerimento).
        r.put("HC14", taxavelAteAAnalise(hidrico(camunda, HC14, "EM_ANALISE"))
                .passouEmCiclo(ANALISE, dias(8), dias(20), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .complementacaoConcluida(COMPLEMENTACAO_DO_HC14, dias(10), dias(13))
                .naEtapa(REVISAO).atribuidoA(CHEFIA).desde(dias(20)).gravar());

        // HC15: milhares de atividades (script repetido por erro): passa do limite de linhas por instância (bloco 2).
        r.put("HC15", hidrico(camunda, HC15, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .scriptRepetido(EMITIR_TAXA, REPETICOES_DO_HC15, dias(2), 5)
                .esperandoPagamento().desde(dias(3)).gravar());

        // Finais do Hídrico. A situação é a que o Licenciamento grava (o final vem do evento, nunca da situação).
        r.put("HF01", validacaoAteAEmissao(taxavelAteAAnalise(hidrico(camunda, HF01, "CONCLUIDO")))
                .passouPelaEmissao(dias(24), dias(26), AUTORIZACOES, PORTARIA)
                .passou(PUBLICACAO, dias(26), dias(30), PUBLICADOR)
                .passou(VERIFICACAO, dias(30), dias(31), cpfDoServidor(10))
                .encerradoEm("Event_1wiuc48").desde(dias(31)).gravar());
        r.put("HF02", hidrico(camunda, HF02, "CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .encerradoEm("Event_1kq2k0z").desde(dias(3)).gravar());
        r.put("HF03", hidrico(camunda, HF03, "CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO")
                .expirou(CADASTRO, dias(0), dias(30))
                .encerradoEm("Event_0abm55w").desde(dias(30)).gravar());
        // Bug de origem: a taxa não paga encerra a instância sem atualizar a situação (fica AGUARDANDO_PAGAMENTO_DE_TAXAS).
        r.put("HF04", hidrico(camunda, HF04, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(PAGAMENTO, dias(2, 1), dias(62))
                .encerradoEm("Event_0h1oige").desde(dias(62)).gravar());

        // LAC
        // LC01: conferência -> emissão da taxa -> pagamento -> termo -> termo assinado -> concluído (evtProcessoConcluido).
        r.put("LC01", lac(camunda, LC01, "CONCLUIDO")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", dias(2))
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(LAC_PAGAMENTO, dias(2, 1), dias(4))
                .passou(PROTOCOLO, dias(4), dias(4, 1))
                .passou(LAC_TERMO, dias(4, 1), dias(4, 2))
                .passou(LAC_ENVIO, dias(4, 2), dias(6))
                .passou(LAC_CONCLUSAO, dias(6), dias(6, 1))
                .encerradoEm("evtProcessoConcluido").desde(dias(6, 1)).gravar());
        // LC02: isento (sem emissão da taxa nem espera), parado no envio do termo assinado.
        r.put("LC02", lac(camunda, LC02, "AGUARDANDO_ASSINATURA_LAC")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "isento", dias(2))
                .passou(PROTOCOLO, dias(2), dias(2, 1))
                .passou(LAC_TERMO, dias(2, 1), dias(2, 2))
                .naEtapa(LAC_ENVIO).doInteressado().desde(dias(2, 2)).gravar());
        // LC03: reprova na conferência, correção e volta à conferência, sem nova taxa na correção.
        r.put("LC03", lac(camunda, LC03, "AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "reprovado", dias(2))
                .passou(CORRECAO, dias(2), dias(4))
                .voltouPara(CONFERENCIA, dias(4), dias(5), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", dias(5))
                .passou(EMITIR_TAXA, dias(5), dias(5, 1))
                .esperandoPagamento().desde(dias(5, 1)).gravar());

        // Finais do LAC (o "concluído" é o LC01). "Expirado na correção" usa a MESMA situação do expirado no cadastro.
        r.put("LF01", lac(camunda, LF01, "CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE")
                .passou(CADASTRO, dias(0), dias(1))
                .encerradoEm("evtFimRequerimentoAPedidoCadastrante").desde(dias(2)).gravar());
        r.put("LF02", lac(camunda, LF02, "CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO")
                .expirou(CADASTRO, dias(0), dias(30))
                .encerradoEm("evtEncerrarRequerimentoPrazoCadExpirado").desde(dias(30)).gravar());
        r.put("LF03", lac(camunda, LF03, "CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .expirou(CORRECAO, dias(2), dias(32))
                .encerradoEm("evtEncerrarProcessoPrazoCorrecaoExpirado").desde(dias(32)).gravar());
        r.put("LF04", lac(camunda, LF04, "CANCELADO_POR_FALTA_DE_PAGAMENTO")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(LAC_PAGAMENTO, dias(2, 1), dias(32))
                .encerradoEm("evtEncerrarProcessoFaltaPagto").desde(dias(32)).gravar());
        r.put("LF05", lac(camunda, LF05, "ARQUIVADO_DEFINITIVAMENTE")
                .passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .passou(PROTOCOLO, dias(2), dias(2, 1))
                .passou(LAC_TERMO, dias(2, 1), dias(2, 2))
                .passou(LAC_ENVIO, dias(2, 2), dias(32))
                .encerradoEm("Event_0x7hz1p").desde(dias(32)).gravar());

        // Plano B: requerimentos de 2021 SEM instância no Camunda. Eventos de HISTORICO_REQUERIMENTO (a tabela real tem
        // CPF_USUARIO: aqui vai um CPF fictício inválido que o plano B nunca lê) e as datas de REQUERIMENTO.
        LocalDateTime a2021 = LocalDateTime.of(2021, 5, 10, 9, 0);
        // PB01: histórico completo, concluído.
        Processo pb01 = camunda.processo(Fluxo.HIDRICO, PB01).numero(PB01 + "/2021").situacao("CONCLUIDO")
                .semInstancia().abertoEm(a2021).desde(a2021.plusDays(120))
                .comDatas(a2021.plusDays(12), null, null, null);
        String[] eventosCompletos = {"CRIACAO_REQUERIMENTO", "AGUARDANDO_CONFERENCIA", "CHECKLIST_APROVADO",
                "AGUARDANDO_PAGAMENTO_TAXA", "PAGAMENTO_CONFIRMADO", "PROTOCOLO", "EM_ANALISE", "VALIDAR_PARECER_TECNICO",
                "TITULOS_EMITIDOS", "TITULACOES_DISPONIVEIS", "CONCLUSAO_REQUERIMENTO"};
        int[] diasDoEvento = {0, 1, 3, 4, 10, 12, 14, 60, 90, 100, 120};
        for (int i = 0; i < eventosCompletos.length; i++) {
            pb01.noHistoricoDoLicenciamento(eventosCompletos[i], a2021.plusDays(diasDoEvento[i]));
        }
        r.put("PB01", pb01.gravar());
        // PB02: cadastro reprovado e expirado, com um evento que o Rastro não conhece (ignorado com contagem).
        r.put("PB02", camunda.processo(Fluxo.HIDRICO, PB02).numero(PB02 + "/2021")
                .situacao("CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO").semInstancia().abertoEm(a2021)
                .desde(a2021.plusDays(40)).comDatas(null, null, a2021.plusDays(40), null)
                .noHistoricoDoLicenciamento("CRIACAO_REQUERIMENTO", a2021)
                .noHistoricoDoLicenciamento("AGUARDANDO_CONFERENCIA", a2021.plusDays(1))
                .noHistoricoDoLicenciamento("EVENTO_FICTICIO_DESCONHECIDO", a2021.plusDays(2))
                .noHistoricoDoLicenciamento("CHECKLIST_REPROVADO", a2021.plusDays(5))
                .gravar());
        // PB03: nenhum evento: só situação e datas ("Sem detalhamento disponível").
        r.put("PB03", camunda.processo(Fluxo.HIDRICO, PB03).numero(PB03 + "/2021").situacao("EM_ANALISE")
                .semInstancia().abertoEm(a2021).desde(a2021.plusDays(30)).gravar());
        return r;
    }

    private static Processo hidrico(CamundaFicticioBuilder camunda, long id, String situacao) {
        return camunda.processo(Fluxo.HIDRICO, id).situacao(situacao);
    }

    private static Processo lac(CamundaFicticioBuilder camunda, long id, String situacao) {
        return camunda.processo(Fluxo.LAC, id).situacao(situacao);
    }

    /** Cadastro até o aceite pelos três analistas, taxável (emissão da taxa, espera, protocolo após o pagamento). A análise começa no dia 8. */
    private static Processo taxavelAteAAnalise(Processo p) {
        return p.passou(CADASTRO, dias(0), dias(1))
                .passou(CONFERENCIA, dias(1), dias(2), CONFERIDOR)
                .variavel("statusConferencia", "taxavel", dias(2))
                .passou(EMITIR_TAXA, dias(2), dias(2, 1))
                .passouPelaEspera(PAGAMENTO, dias(2, 1), dias(5))
                .passou(PROTOCOLO, dias(5), dias(5, 1))
                .passou(ATRIBUICAO, dias(5, 1), dias(6), ATRIBUIDOR)
                .passouEmCiclo(ACEITE, dias(6), dias(8), ANALISTA_1, ANALISTA_2, ANALISTA_3);
    }

    /** Análise (dias 8 a 20), revisão do parecer (20 a 22) e validação pelos três (22 a 24); parte do fim do aceite. */
    private static Processo validacaoAteAEmissao(Processo p) {
        return p.passouEmCiclo(ANALISE, dias(8), dias(20), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passou(REVISAO, dias(20), dias(22), CHEFIA)
                .passouEmCiclo(VALIDACAO, dias(22), dias(24), ANALISTA_1, ANALISTA_2, ANALISTA_3);
    }

    /**
     * Do cadastro até a correção do requerimento pedida por um analista (análise encerrada por pedido de correção) e a
     * tarefa de correção concluída pelo interessado. Termina no instante {@code dias(inicioDaCorrecao + 2)}.
     */
    private static Processo correcaoAteACorrecao(Processo p, int inicioDaCorrecao) {
        return taxavelAteAAnalise(p)
                .passouEmCicloEncerradoPor(ANALISE, dias(8), dias(inicioDaCorrecao), ANALISTA_1, ANALISTA_2, ANALISTA_3)
                .passou(CORRECAO_STATUS, dias(inicioDaCorrecao), dias(inicioDaCorrecao, 1))
                .passou(CORRECAO, dias(inicioDaCorrecao, 1), dias(inicioDaCorrecao + 2));
    }
}
