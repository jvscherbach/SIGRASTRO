package br.gov.mt.sema.rastro.configuracao.domain;

import static br.gov.mt.sema.rastro.configuracao.domain.ClasseDeAplicacao.EXIGE_REINICIO;
import static br.gov.mt.sema.rastro.configuracao.domain.ClasseDeAplicacao.IMEDIATO;
import static br.gov.mt.sema.rastro.configuracao.domain.ClasseDeAplicacao.PROXIMA_GERACAO;
import static br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro.booleano;
import static br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro.inteiro;
import static br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro.opcao;

import java.util.List;
import java.util.Optional;

/**
 * Tudo o que a tela pode alterar (docs/12). O que não está aqui é recusado: credenciais, URLs, {@code SCORP_SIGILO_PADRAO}
 * (decisão da área, P30) e diretórios não entram nunca.
 */
public final class CatalogoDeParametros {

    public static final String DOSSIE_TTL = "dossie.ttlMinutos";
    public static final String DOSSIE_THREADS = "dossie.threads";
    public static final String DOSSIE_SUMARIO = "dossie.sumario";
    public static final String DOSSIE_MARCADORES = "dossie.marcadores";
    public static final String DOSSIE_NUMERACAO = "dossie.numeracao";
    public static final String DOWNLOAD_TIMEOUT = "conexoes.downloadTimeoutSegundos";
    public static final String CONVERSAO_HABILITADA = "conversao.habilitada";
    public static final String CONVERSAO_TIMEOUT = "conversao.timeoutSegundos";
    public static final String CONVERSAO_PROCESSOS = "conversao.processos";
    public static final String CONVERSAO_TAREFAS = "conversao.tarefasPorProcesso";
    public static final String CONVERSAO_PLANILHA_MODO = "conversao.planilhaModo";
    public static final String CONVERSAO_ABAS_OCULTAS = "conversao.abasOcultas";
    public static final String CONVERSAO_IMAGEM_MEGAPIXELS = "conversao.imagemMaxMegapixels";
    public static final String CONVERSAO_ESCALA_MINIMA = "conversao.planilhaEscalaMinima";
    public static final String CONVERSAO_PAGINAS = "conversao.paginasMaximas";
    public static final String CONVERSAO_SAIDA_MB = "conversao.saidaMaxMb";
    public static final String VISUALIZACAO_TTL = "visualizacao.ttlMinutos";
    public static final String VISUALIZACAO_CACHE_MB = "visualizacao.cacheMaxMb";
    public static final String VISUALIZACAO_CONVERSOES = "visualizacao.conversoesSimultaneas";
    public static final String VISUALIZACAO_FILA = "visualizacao.filaDeConversoes";
    public static final String AUDITORIA_RETENCAO_DOCUMENTOS = "auditoria.retencaoDocumentosAnos";
    public static final String AUDITORIA_RETENCAO_ACESSOS = "auditoria.retencaoAcessosAnos";
    public static final String PREFIXO_ORIGEM = "dossie.origem.";
    /** S7, bloco 10: parâmetros do Rastro (tramitação), todos lidos a cada consulta. */
    public static final String RASTRO_MOSTRAR_NOME = "rastro.mostrarNomeResponsavel";
    public static final String RASTRO_CACHE_POSICAO = "rastro.posicaoCacheSegundos";
    public static final String RASTRO_MAX_NOMES = "rastro.maxNomesPorEquipe";
    public static final String RASTRO_POSICOES_SIMULTANEAS = "rastro.posicoesSimultaneas";
    public static final String RASTRO_CAMUNDA_TIMEOUT = "rastro.camundaTimeoutSegundos";

    private static final String DOSSIE = "DOSSIE";
    private static final String CONVERSAO = "CONVERSAO";
    private static final String VISUALIZACAO = "VISUALIZACAO";
    private static final String CONEXOES = "CONEXOES";
    private static final String AUDITORIA = "AUDITORIA";
    private static final String RASTRO = "RASTRO";
    private static final List<String> RETENCOES = List.of("1", "2", "5", "10");

    private static final List<DefinicaoDeParametro> DEFINICOES = List.of(
            inteiro(DOSSIE_TTL, "Expiração do dossiê (min)", DOSSIE, "rastro.dossie.ttl-minutos", 30, 5, 240, PROXIMA_GERACAO),
            inteiro(DOSSIE_THREADS, "Gerações simultâneas", DOSSIE, "rastro.dossie.threads", 2, 1, 8, EXIGE_REINICIO),
            booleano(DOSSIE_SUMARIO, "Sumário", DOSSIE, "rastro.dossie.sumario", true, PROXIMA_GERACAO),
            booleano(DOSSIE_MARCADORES, "Marcadores no PDF", DOSSIE, "rastro.dossie.marcadores", true, PROXIMA_GERACAO),
            booleano(DOSSIE_NUMERACAO, "Numeração de páginas", DOSSIE, "rastro.dossie.numeracao", true, PROXIMA_GERACAO),
            origem("documento_processo", "Origem: documento do processo"),
            origem("documento_requerimento", "Origem: documento do requerimento"),
            origem("complementacao", "Origem: complementação"),
            origem("imovel", "Origem: imóvel"),
            origem("representante_legal", "Origem: representante legal"),
            origem("responsavel_tecnico", "Origem: responsável técnico"),
            origem("scorp", "Origem: pessoal (SCORP)"),
            booleano(CONVERSAO_HABILITADA, "Conversão habilitada", CONVERSAO, "rastro.conversao.habilitada", true, PROXIMA_GERACAO),
            opcao(CONVERSAO_PLANILHA_MODO, "Planilhas", CONVERSAO, "rastro.conversao.planilha-modo", "LARGURA",
                    List.of("LARGURA", "PAGINA_POR_ABA"), PROXIMA_GERACAO),
            booleano(CONVERSAO_ABAS_OCULTAS, "Incluir abas ocultas", CONVERSAO, "rastro.conversao.abas-ocultas", false, PROXIMA_GERACAO),
            inteiro(CONVERSAO_TIMEOUT, "Tempo limite de conversão (s)", CONVERSAO, "rastro.conversao.timeout-segundos", 300, 10, 1800, EXIGE_REINICIO),
            inteiro(CONVERSAO_PROCESSOS, "Instâncias do conversor", CONVERSAO, "rastro.conversao.processos", 2, 1, 8, EXIGE_REINICIO),
            inteiro(CONVERSAO_TAREFAS, "Tarefas por instância antes de reiniciar", CONVERSAO, "rastro.conversao.tarefas-por-processo", 50, 1, 500, EXIGE_REINICIO),
            inteiro(CONVERSAO_IMAGEM_MEGAPIXELS, "Imagem: máximo de megapixels", CONVERSAO, "rastro.conversao.imagem-max-megapixels", 100, 1, 500, PROXIMA_GERACAO),
            inteiro(CONVERSAO_ESCALA_MINIMA, "Planilha: escala mínima (%)", CONVERSAO, "rastro.conversao.planilha-escala-minima", 40, 10, 100, PROXIMA_GERACAO),
            inteiro(CONVERSAO_PAGINAS, "Máximo de páginas de uma conversão", CONVERSAO, "rastro.conversao.paginas-maximas", 2000, 1, 10000, PROXIMA_GERACAO),
            inteiro(CONVERSAO_SAIDA_MB, "Tamanho máximo do PDF de uma conversão (MB)", CONVERSAO, "rastro.conversao.saida-max-mb", 500, 1, 2000, PROXIMA_GERACAO),
            inteiro(VISUALIZACAO_TTL, "Tempo de vida do cache (min)", VISUALIZACAO, "rastro.visualizacao.ttl-minutos", 10, 1, 240, IMEDIATO),
            inteiro(VISUALIZACAO_CACHE_MB, "Teto de espaço do cache (MB)", VISUALIZACAO, "rastro.visualizacao.cache-max-mb", 500, 10, 10000, IMEDIATO),
            inteiro(VISUALIZACAO_CONVERSOES, "Conversões do modal em paralelo", VISUALIZACAO, "rastro.visualizacao.conversoes-simultaneas", 2, 1, 8, EXIGE_REINICIO),
            inteiro(VISUALIZACAO_FILA, "Conversões do modal à espera", VISUALIZACAO, "rastro.visualizacao.fila-de-conversoes", 4, 0, 32, EXIGE_REINICIO),
            inteiro(DOWNLOAD_TIMEOUT, "Tempo limite de download (s)", CONEXOES, "rastro.dossie.download-timeout-segundos", 120, 10, 300, PROXIMA_GERACAO),
            opcao(AUDITORIA_RETENCAO_DOCUMENTOS, "Retenção da auditoria de documentos (anos)", AUDITORIA, "rastro.auditoria.retencao-documentos-anos", "5", RETENCOES, IMEDIATO),
            opcao(AUDITORIA_RETENCAO_ACESSOS, "Retenção da auditoria de acessos (anos)", AUDITORIA, "rastro.auditoria.retencao-acessos-anos", "1", RETENCOES, IMEDIATO),
            // S7, bloco 10. O nome do servidor depende de aprovação da SEMA (P32); CPF nunca, em nenhum valor.
            booleano(RASTRO_MOSTRAR_NOME, "Mostrar o nome do responsável", RASTRO, "rastro.tramitacao.mostrar-nome-responsavel", true, IMEDIATO),
            inteiro(RASTRO_CACHE_POSICAO, "Validade do cache da posição (s; 0 desliga)", RASTRO, "rastro.tramitacao.posicao-cache-segundos", 30, 0, 3600, IMEDIATO),
            inteiro(RASTRO_MAX_NOMES, "Máximo de nomes por equipe", RASTRO, "rastro.tramitacao.max-nomes-por-equipe", 10, 1, 100, IMEDIATO),
            inteiro(RASTRO_POSICOES_SIMULTANEAS, "Posições consultadas ao mesmo tempo (lista da busca)", RASTRO, "rastro.tramitacao.posicoes-simultaneas", 4, 1, 20, IMEDIATO),
            inteiro(RASTRO_CAMUNDA_TIMEOUT, "Tempo limite das consultas ao fluxo (s)", RASTRO, "rastro.tramitacao.camunda-timeout-segundos", 10, 1, 120, IMEDIATO));

    private CatalogoDeParametros() {
    }

    private static DefinicaoDeParametro origem(String chave, String rotulo) {
        return booleano(PREFIXO_ORIGEM + chave, rotulo, DOSSIE, "rastro.documentos.origens." + chave, true, IMEDIATO);
    }

    public static List<DefinicaoDeParametro> todos() {
        return DEFINICOES;
    }

    public static Optional<DefinicaoDeParametro> buscar(String nome) {
        return DEFINICOES.stream().filter(d -> d.nome().equals(nome)).findFirst();
    }
}
