package br.gov.mt.sema.vistas.config;

import java.util.Locale;

import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros da conversão vindos de variáveis de ambiente (protótipo). A S5 troca esta implementação pela tela de configurações. */
@Component
class ParametrosDaConversaoDoAmbiente implements ParametrosDaConversao {

    private final boolean habilitada;
    private final int timeoutSegundos;
    private final int processos;
    private final int tarefasPorProcesso;
    private final PlanilhaModo planilhaModo;
    private final boolean abasOcultas;
    private final int imagemMaxMegapixels;
    private final int saidaMaxMb;

    ParametrosDaConversaoDoAmbiente(
            @Value("${vistas.conversao.habilitada:true}") boolean habilitada,
            @Value("${vistas.conversao.timeout-segundos:300}") int timeoutSegundos,
            @Value("${vistas.conversao.processos:2}") int processos,
            @Value("${vistas.conversao.tarefas-por-processo:50}") int tarefasPorProcesso,
            @Value("${vistas.conversao.planilha-modo:LARGURA}") String planilhaModo,
            @Value("${vistas.conversao.abas-ocultas:false}") boolean abasOcultas,
            @Value("${vistas.conversao.imagem-max-megapixels:100}") int imagemMaxMegapixels,
            @Value("${vistas.conversao.saida-max-mb:500}") int saidaMaxMb) {
        this.habilitada = habilitada;
        this.timeoutSegundos = timeoutSegundos;
        this.processos = processos;
        this.tarefasPorProcesso = tarefasPorProcesso;
        this.planilhaModo = modo(planilhaModo);
        this.abasOcultas = abasOcultas;
        this.imagemMaxMegapixels = imagemMaxMegapixels;
        this.saidaMaxMb = saidaMaxMb;
    }

    /** Valor desconhecido cai no padrão LARGURA, que nunca corta colunas. */
    private static PlanilhaModo modo(String valor) {
        try {
            return PlanilhaModo.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PlanilhaModo.LARGURA;
        }
    }

    @Override
    public boolean habilitada() {
        return habilitada;
    }

    @Override
    public int timeoutSegundos() {
        return timeoutSegundos;
    }

    @Override
    public int processos() {
        return processos;
    }

    @Override
    public int tarefasPorProcesso() {
        return tarefasPorProcesso;
    }

    @Override
    public PlanilhaModo planilhaModo() {
        return planilhaModo;
    }

    @Override
    public boolean abasOcultas() {
        return abasOcultas;
    }

    @Override
    public int imagemMaxMegapixels() {
        return imagemMaxMegapixels;
    }

    @Override
    public int saidaMaxMb() {
        return saidaMaxMb;
    }
}
