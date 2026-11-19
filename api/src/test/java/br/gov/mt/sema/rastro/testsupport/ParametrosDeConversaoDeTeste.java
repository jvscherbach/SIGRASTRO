package br.gov.mt.sema.rastro.testsupport;

import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaConversao;

/** Parâmetros de conversão fixos para testes, com os padrões da S3. */
public record ParametrosDeConversaoDeTeste(boolean habilitada) implements ParametrosDaConversao {

    public static ParametrosDeConversaoDeTeste ligada() {
        return new ParametrosDeConversaoDeTeste(true);
    }

    public static ParametrosDeConversaoDeTeste desligada() {
        return new ParametrosDeConversaoDeTeste(false);
    }

    @Override
    public int timeoutSegundos() {
        return 300;
    }

    @Override
    public int processos() {
        return 2;
    }

    @Override
    public int tarefasPorProcesso() {
        return 50;
    }

    @Override
    public PlanilhaModo planilhaModo() {
        return PlanilhaModo.LARGURA;
    }

    @Override
    public boolean abasOcultas() {
        return false;
    }

    @Override
    public int imagemMaxMegapixels() {
        return 100;
    }

    @Override
    public int planilhaEscalaMinima() {
        return 40;
    }

    @Override
    public int paginasMaximas() {
        return 2000;
    }

    @Override
    public int saidaMaxMb() {
        return 500;
    }
}
