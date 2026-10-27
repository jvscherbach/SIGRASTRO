package br.gov.mt.sema.vistas.documento.application.port;

/**
 * Parâmetros da conversão de planilhas, textos e imagens para PDF (S3). No protótipo vêm de variáveis de ambiente
 * (implementação em {@code config}); a S5 troca a implementação por parâmetros administráveis sem mudar quem os consome.
 */
public interface ParametrosDaConversao {

    /** Chave de segurança: desligada, vale o comportamento da S2.2 (só PDF; o resto é "não é PDF"). */
    boolean habilitada();

    /** Tempo máximo de uma conversão, em segundos. */
    int timeoutSegundos();

    /** Instâncias do conversor em paralelo. */
    int processos();

    /** Tarefas que cada instância executa antes de ser reiniciada. */
    int tarefasPorProcesso();

    /** Como cada aba de planilha ocupa o PDF. */
    PlanilhaModo planilhaModo();

    /** Abas ocultas entram no PDF? Por padrão não. */
    boolean abasOcultas();

    /** Acima disto (megapixels) a imagem é recusada, sem decodificar. */
    int imagemMaxMegapixels();

    /** Planilha no modo LARGURA: escala (%) abaixo da qual a aba vai para uma página única, por legibilidade. */
    int planilhaEscalaMinima();

    /** Máximo de páginas do PDF gerado por uma conversão; acima, falha do documento. */
    int paginasMaximas();

    /** Tamanho máximo (MB) do PDF gerado por uma conversão; acima, falha do documento. */
    int saidaMaxMb();

    enum PlanilhaModo {
        /** Cada aba ajustada a uma página de largura. */
        LARGURA,
        /** Uma página, de tamanho personalizado, por aba. */
        PAGINA_POR_ABA
    }
}
