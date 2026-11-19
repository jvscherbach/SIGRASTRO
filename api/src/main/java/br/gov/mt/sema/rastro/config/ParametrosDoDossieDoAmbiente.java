package br.gov.mt.sema.rastro.config;

import java.nio.file.Path;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros do dossiê: o ambiente é o padrão; a tela de configurações sobrepõe os editáveis (gerações simultâneas só na subida). */
@Component
class ParametrosDoDossieDoAmbiente implements ParametrosDoDossie {

    private final int ttlMinutos;
    private final int threads;
    private final int downloadTimeoutSegundos;
    private final int downloadTotalMaximoSegundos;
    private final long espacoMinimoMb;
    private final long blobMaxMb;
    private final int historicoHoras;
    private final boolean sumario;
    private final boolean marcadores;
    private final boolean numeracao;
    private final Path diretorio;
    private final ParametrosEmVigor vigentes;

    ParametrosDoDossieDoAmbiente(
            @Value("${rastro.dossie.ttl-minutos:30}") int ttlMinutos,
            @Value("${rastro.dossie.threads:2}") int threads,
            @Value("${rastro.dossie.download-timeout-segundos:120}") int downloadTimeoutSegundos,
            @Value("${rastro.dossie.download-total-maximo-segundos:900}") int downloadTotalMaximoSegundos,
            @Value("${rastro.dossie.espaco-minimo-mb:2048}") long espacoMinimoMb,
            @Value("${rastro.dossie.blob-max-mb:150}") long blobMaxMb,
            @Value("${rastro.dossie.historico-horas:24}") int historicoHoras,
            @Value("${rastro.dossie.sumario:true}") boolean sumario,
            @Value("${rastro.dossie.marcadores:true}") boolean marcadores,
            @Value("${rastro.dossie.numeracao:true}") boolean numeracao,
            @Value("${rastro.dossie.diretorio:/var/tmp/vistas}") String diretorio, ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
        this.ttlMinutos = ttlMinutos;
        this.threads = threads;
        this.downloadTimeoutSegundos = downloadTimeoutSegundos;
        this.downloadTotalMaximoSegundos = downloadTotalMaximoSegundos;
        this.espacoMinimoMb = espacoMinimoMb;
        this.blobMaxMb = blobMaxMb;
        this.historicoHoras = historicoHoras;
        this.sumario = sumario;
        this.marcadores = marcadores;
        this.numeracao = numeracao;
        this.diretorio = Path.of(diretorio);
    }

    @Override
    public int ttlMinutos() {
        return vigentes.inteiro(CatalogoDeParametros.DOSSIE_TTL, ttlMinutos);
    }

    @Override
    public int historicoHoras() {
        return historicoHoras;
    }

    @Override
    public int threads() {
        return threads;
    }

    @Override
    public int downloadTimeoutSegundos() {
        return vigentes.inteiro(CatalogoDeParametros.DOWNLOAD_TIMEOUT, downloadTimeoutSegundos);
    }

    @Override
    public int downloadTotalMaximoSegundos() {
        return downloadTotalMaximoSegundos;
    }

    @Override
    public long espacoMinimoMb() {
        return espacoMinimoMb;
    }

    @Override
    public long blobMaxMb() {
        return blobMaxMb;
    }

    @Override
    public boolean sumario() {
        return vigentes.booleano(CatalogoDeParametros.DOSSIE_SUMARIO, sumario);
    }

    @Override
    public boolean marcadores() {
        return vigentes.booleano(CatalogoDeParametros.DOSSIE_MARCADORES, marcadores);
    }

    @Override
    public boolean numeracao() {
        return vigentes.booleano(CatalogoDeParametros.DOSSIE_NUMERACAO, numeracao);
    }

    @Override
    public Path diretorio() {
        return diretorio;
    }
}
