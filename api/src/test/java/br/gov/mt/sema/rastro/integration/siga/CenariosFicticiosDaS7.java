package br.gov.mt.sema.rastro.integration.siga;

import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.CPF_SEM_SERVIDOR;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.GRUPO_DESCONHECIDO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.GRUPO_ZERO;
import static br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.cpfDoServidor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Resultado;

/**
 * Cenários fictícios obrigatórios da S7 (prompt, bloco 2), um requerimento cada, número {@code <id>/2026} (o H21 é de
 * 2022) e interessado "Interessado Fictício S7". Carregados por {@link SigaOracleTeste} ao subir o Oracle de teste; os
 * testes dos blocos 3 a 7 consultam por estes ids. Tabela completa (situação, etapa, responsável) em docs/15.
 *
 * <p>Detalhes de propósito: o H04 não passa por {@code gtwAguardarPagamento}; o H19 só tem a tarefa Suporte ativa; a
 * complementação do H20 tem o id {@value #H01}, o mesmo do requerimento H01 (colisão de {@code BUSINESS_KEY_}, separada
 * pela chave da definição); H07, H15 e H21 estão no mesmo processo {@value #PROCESSO_COMPARTILHADO} (ativo, encerrado e
 * sem instância). G01 a G03 testam o fallback do responsável (grupo desconhecido, grupo {@code 0} e CPF sem servidor).
 */
public final class CenariosFicticiosDaS7 {

    public static final long H01 = 7001;
    public static final long H02 = 7002;
    public static final long H03 = 7003;
    public static final long H04 = 7004;
    public static final long H05 = 7005;
    public static final long H06 = 7006;
    public static final long H07 = 7007;
    public static final long H08 = 7008;
    public static final long H09 = 7009;
    public static final long H10 = 7010;
    public static final long H11 = 7011;
    public static final long H12 = 7012;
    public static final long H13 = 7013;
    public static final long H14 = 7014;
    public static final long H15 = 7015;
    public static final long H16 = 7016;
    public static final long H17 = 7017;
    public static final long H18 = 7018;
    public static final long H19 = 7019;
    public static final long H20 = 7020;
    public static final long H21 = 7021;
    public static final long L01 = 7051;
    public static final long L02 = 7052;
    public static final long L03 = 7053;
    public static final long L04 = 7054;
    public static final long L05 = 7055;
    public static final long L06 = 7056;
    public static final long L07 = 7057;
    public static final long TRIFASICO = 7091;
    public static final long G01 = 7101;
    public static final long G02 = 7102;
    public static final long G03 = 7103;

    /** Id da complementação do H20: igual ao id do requerimento H01, de propósito. */
    public static final long COMPLEMENTACAO_DO_H20 = H01;
    public static final String PROCESSO_COMPARTILHADO = "7900/2022";

    private static final String CADASTRO = "taskCadastrarRequerimento";
    private static final String CONFERENCIA = "taskConferirDocumentosApresentados";
    private static final String EMITIR_TAXA = "taskEmitirTaxaSefaz";

    private CenariosFicticiosDaS7() {
    }

    private static LocalDateTime dia(int mes, int dia) {
        return LocalDateTime.of(2026, mes, dia, 9, 30);
    }

    /** Grava todos os cenários; devolve o resultado de cada um pelo código (H01, L01, TRIFASICO, G01...). */
    public static Map<String, Resultado> carregar(CamundaFicticioBuilder camunda) {
        Map<String, Resultado> r = new LinkedHashMap<>();

        // Hídrico
        r.put("H01", camunda.processo(Fluxo.HIDRICO, H01).situacao("EM_CADASTRO")
                .naEtapa(CADASTRO).doInteressado().desde(dia(3, 2)).gravar());
        r.put("H02", camunda.processo(Fluxo.HIDRICO, H02).situacao("EM_CONFERENCIA")
                .percorrendo(CADASTRO).naEtapa(CONFERENCIA).paraGrupo("grpSurhConferirDocumentoSubterranea")
                .desde(dia(3, 3)).gravar());
        r.put("H03", camunda.processo(Fluxo.HIDRICO, H03).situacao("AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .percorrendo(CADASTRO, CONFERENCIA, EMITIR_TAXA).esperandoPagamento().desde(dia(3, 4)).gravar());
        // Isento: sem passagem por gtwAguardarPagamento; parado na geração do protocolo, a caminho da atribuição.
        r.put("H04", camunda.processo(Fluxo.HIDRICO, H04).situacao("AGUARDANDO_ATRIBUICAO")
                .percorrendo(CADASTRO, CONFERENCIA).naEtapa("taskGerarNumeroProtocolo").desde(dia(3, 5)).gravar());
        r.put("H05", camunda.processo(Fluxo.HIDRICO, H05).situacao("AGUARDANDO_ATRIBUICAO")
                .percorrendo(CADASTRO, CONFERENCIA, "taskGerarNumeroProtocolo")
                .naEtapa("taskAtribuirRequerimento").paraGrupo("grpSurhAtribuirRequerimentoSuperficial")
                .desde(dia(3, 6)).gravar());
        r.put("H06", camunda.processo(Fluxo.HIDRICO, H06).situacao("AGUARDANDO_ACEITE_ATRIBUICAO")
                .naEtapa("taskAceitarAtribuicao").comAnalistas(3).concluidos(2).desde(dia(3, 7)).gravar());
        r.put("H07", camunda.processo(Fluxo.HIDRICO, H07).situacao("EM_ANALISE").numeroProcesso(PROCESSO_COMPARTILHADO)
                .naEtapa("taskAnalisarRequerimento").comAnalistas(3).desde(dia(3, 8)).gravar());
        r.put("H08", camunda.processo(Fluxo.HIDRICO, H08).situacao("EM_ANALISE")
                .naEtapa("taskRevisarParecerTecnico").atribuidoA(cpfDoServidor(4)).desde(dia(3, 9)).gravar());
        r.put("H09", camunda.processo(Fluxo.HIDRICO, H09).situacao("EM_ANALISE")
                .naEtapa("taskValidarParecerTecnico").comAnalistas(3)
                .atribuidoA(cpfDoServidor(5), cpfDoServidor(6), cpfDoServidor(7)).desde(dia(3, 10)).gravar());
        r.put("H10", camunda.processo(Fluxo.HIDRICO, H10).situacao("EM_ANALISE")
                .comFilhas("procSurhEmitirAutorizacoes", "procSurhEmissaoPortaria").desde(dia(3, 11)).gravar());
        r.put("H11", camunda.processo(Fluxo.HIDRICO, H11).situacao("AGUARDANDO_PUBLICACAO")
                .naEtapa("taskRegistarComprovantePublicacao").paraGrupo("grpSurhRegistrarPublicacaoBarramento")
                .desde(dia(3, 12)).gravar());
        // CONCLUIDO é gravado antes da verificação da revisão (regra técnica 8); a etapa funciona como arquivamento.
        r.put("H12", camunda.processo(Fluxo.HIDRICO, H12).situacao("CONCLUIDO")
                .percorrendo("taskRegistarComprovantePublicacao", "taskUpdateStatusConcluido")
                .naEtapa("taskVerificarRevisaoProcesso").paraGrupo("grpSurhRevisarDecisaoMultidisciplinar")
                .desde(dia(3, 13)).gravar());
        r.put("H13", camunda.processo(Fluxo.HIDRICO, H13).situacao("EM_CORRECAO")
                .percorrendo(CADASTRO, CONFERENCIA, "taskAtualizarStatusAguardandoRevisao")
                .naEtapa("taskCorrecaoRequerimento").doInteressado().desde(dia(3, 14)).gravar());
        r.put("H14", camunda.processo(Fluxo.HIDRICO, H14).situacao("AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .percorrendo("taskCorrecaoRequerimento", "taskEmitirTaxaSefazCorrecao")
                .naEtapa("gtwAguardarPagamentoRevisao").desde(dia(3, 15)).gravar());
        r.put("H15", camunda.processo(Fluxo.HIDRICO, H15).situacao("CONCLUIDO").numeroProcesso(PROCESSO_COMPARTILHADO)
                .percorrendo("taskRegistarComprovantePublicacao", "taskVerificarRevisaoProcesso")
                .encerradoEm("Event_1wiuc48").desde(dia(3, 16)).gravar());
        r.put("H16", camunda.processo(Fluxo.HIDRICO, H16).situacao("CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE")
                .percorrendo(CADASTRO).encerradoEm("Event_1kq2k0z").desde(dia(3, 17)).gravar());
        r.put("H17", camunda.processo(Fluxo.HIDRICO, H17).situacao("CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO")
                .percorrendo(CADASTRO).encerradoEm("Event_0abm55w").desde(dia(3, 18)).gravar());
        // Bug de origem: taxa vencida encerra a instância sem atualizar a situação (regra técnica 8).
        r.put("H18", camunda.processo(Fluxo.HIDRICO, H18).situacao("AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .percorrendo(CADASTRO, CONFERENCIA, EMITIR_TAXA, "gtwAguardarPagamento")
                .encerradoEm("Event_0h1oige").desde(dia(3, 19)).gravar());
        // Só a tarefa Suporte ativa: a posição é a última etapa real percorrida (conferência).
        r.put("H19", camunda.processo(Fluxo.HIDRICO, H19).situacao("EM_CONFERENCIA")
                .percorrendo(CADASTRO, CONFERENCIA).comTarefaDeSuporte().desde(dia(3, 20)).gravar());
        r.put("H20", camunda.processo(Fluxo.HIDRICO, H20).situacao("EM_ANALISE")
                .naEtapa("taskAnalisarRequerimento").atribuidoA(cpfDoServidor(8))
                .comComplementacao(COMPLEMENTACAO_DO_H20).desde(dia(3, 21)).gravar());
        r.put("H21", camunda.processo(Fluxo.HIDRICO, H21).numero(H21 + "/2022").situacao("CONCLUIDO")
                .numeroProcesso(PROCESSO_COMPARTILHADO).semInstancia().desde(LocalDateTime.of(2022, 1, 10, 9, 0))
                .gravar());

        // LAC
        r.put("L01", camunda.processo(Fluxo.LAC, L01).situacao("EM_CONFERENCIA")
                .percorrendo(CADASTRO).naEtapa(CONFERENCIA).paraGrupo("GrpProcResponsavelAtendimentoLac")
                .desde(dia(4, 1)).gravar());
        r.put("L02", camunda.processo(Fluxo.LAC, L02).situacao("AGUARDANDO_PAGAMENTO_DE_TAXAS")
                .percorrendo(CADASTRO, CONFERENCIA, EMITIR_TAXA).esperandoPagamento().desde(dia(4, 2)).gravar());
        r.put("L03", camunda.processo(Fluxo.LAC, L03).situacao("EM_CORRECAO")
                .percorrendo(CADASTRO, CONFERENCIA).naEtapa("taskCorrecaoRequerimento").doInteressado()
                .desde(dia(4, 3)).gravar());
        r.put("L04", camunda.processo(Fluxo.LAC, L04).situacao("AGUARDANDO_ASSINATURA_LAC")
                .percorrendo(CADASTRO, CONFERENCIA, "taskGerarNumeroProtocolo", "taskGerarTermoAdesaoLAC")
                .naEtapa("taskEnviarTermoAdesao").doInteressado().desde(dia(4, 4)).gravar());
        r.put("L05", camunda.processo(Fluxo.LAC, L05).situacao("CONCLUIDO")
                .percorrendo("taskEnviarTermoAdesao", "taskUpdateStatusConcluido")
                .encerradoEm("evtProcessoConcluido").desde(dia(4, 5)).gravar());
        r.put("L06", camunda.processo(Fluxo.LAC, L06).situacao("ARQUIVADO_DEFINITIVAMENTE")
                .percorrendo("taskGerarTermoAdesaoLAC").encerradoEm("Event_0x7hz1p").desde(dia(4, 6)).gravar());
        // No LAC, "expirado na correção" grava a mesma situação do expirado no cadastro (regra técnica 8).
        r.put("L07", camunda.processo(Fluxo.LAC, L07).situacao("CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO")
                .percorrendo(CADASTRO, CONFERENCIA, "taskCorrecaoRequerimento")
                .encerradoEm("evtEncerrarProcessoPrazoCorrecaoExpirado").desde(dia(4, 7)).gravar());

        // Fora do catálogo
        r.put("TRIFASICO", camunda.processo(Fluxo.TRIFASICO, TRIFASICO).situacao("EM_ANALISE").desde(dia(4, 8)).gravar());

        // Fallback do responsável (bloco 4)
        r.put("G01", camunda.processo(Fluxo.HIDRICO, G01).situacao("EM_CONFERENCIA")
                .naEtapa(CONFERENCIA).paraGrupo(GRUPO_DESCONHECIDO).desde(dia(4, 9)).gravar());
        r.put("G02", camunda.processo(Fluxo.HIDRICO, G02).situacao("EM_CONFERENCIA")
                .naEtapa(CONFERENCIA).paraGrupo(GRUPO_ZERO).desde(dia(4, 10)).gravar());
        r.put("G03", camunda.processo(Fluxo.HIDRICO, G03).situacao("EM_ANALISE")
                .naEtapa("taskRevisarParecerTecnico").atribuidoA(CPF_SEM_SERVIDOR).desde(dia(4, 11)).gravar());
        return r;
    }
}
