package br.gov.mt.sema.rastro.config;

import java.util.Locale;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaConversao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros da conversão: o ambiente é o padrão; a tela sobrepõe os editáveis (tempo, instâncias e tarefas só na subida). */
@Component
class ParametrosDaConversaoDoAmbiente implements ParametrosDaConversao {

    private final boolean habilitada;
    private final int timeoutSegundos;
    private final int processos;
    private final int tarefasPorProcesso;
    private final PlanilhaModo planilhaModo;
    private final boolean abasOcultas;
    private final int imagemMaxMegapixels;
    private final int planilhaEscalaMinima;
    private final int paginasMaximas;
    private final int saidaMaxMb;
    private final ParametrosEmVigor vigentes;

    ParametrosDaConversaoDoAmbiente(
            @Value("${rastro.conversao.habilitada:true}") boolean habilitada,
            @Value("${rastro.conversao.timeout-segundos:300}") int timeoutSegundos,
            @Value("${rastro.conversao.processos:2}") int processos,
            @Value("${rastro.conversao.tarefas-por-processo:50}") int tarefasPorProcesso,
            @Value("${rastro.conversao.planilha-modo:LARGURA}") String planilhaModo,
            @Value("${rastro.conversao.abas-ocultas:false}") boolean abasOcultas,
            @Value("${rastro.conversao.imagem-max-megapixels:100}") int imagemMaxMegapixels,
            @Value("${rastro.conversao.planilha-escala-minima:40}") int planilhaEscalaMinima,
            @Value("${rastro.conversao.paginas-maximas:2000}") int paginasMaximas,
            @Value("${rastro.conversao.saida-max-mb:500}") int saidaMaxMb, ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
        this.habilitada = habilitada;
        this.timeoutSegundos = timeoutSegundos;
        this.processos = processos;
        this.tarefasPorProcesso = tarefasPorProcesso;
        this.planilhaModo = modo(planilhaModo);
        this.abasOcultas = abasOcultas;
        this.imagemMaxMegapixels = imagemMaxMegapixels;
        this.planilhaEscalaMinima = planilhaEscalaMinima;
        this.paginasMaximas = paginasMaximas;
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
        return vigentes.booleano(CatalogoDeParametros.CONVERSAO_HABILITADA, habilitada);
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
        return modo(vigentes.texto(CatalogoDeParametros.CONVERSAO_PLANILHA_MODO, planilhaModo.name()));
    }

    @Override
    public boolean abasOcultas() {
        return vigentes.booleano(CatalogoDeParametros.CONVERSAO_ABAS_OCULTAS, abasOcultas);
    }

    @Override
    public int imagemMaxMegapixels() {
        return vigentes.inteiro(CatalogoDeParametros.CONVERSAO_IMAGEM_MEGAPIXELS, imagemMaxMegapixels);
    }

    @Override
    public int planilhaEscalaMinima() {
        return vigentes.inteiro(CatalogoDeParametros.CONVERSAO_ESCALA_MINIMA, planilhaEscalaMinima);
    }

    @Override
    public int paginasMaximas() {
        return vigentes.inteiro(CatalogoDeParametros.CONVERSAO_PAGINAS, paginasMaximas);
    }

    @Override
    public int saidaMaxMb() {
        return vigentes.inteiro(CatalogoDeParametros.CONVERSAO_SAIDA_MB, saidaMaxMb);
    }
}
