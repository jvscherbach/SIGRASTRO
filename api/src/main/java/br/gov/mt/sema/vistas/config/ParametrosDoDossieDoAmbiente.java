package br.gov.mt.sema.vistas.config;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros do dossiê vindos de variáveis de ambiente (protótipo). A S5 troca esta implementação pela tela de configurações. */
@Component
class ParametrosDoDossieDoAmbiente implements ParametrosDoDossie {

    private final int ttlMinutos;
    private final int threads;
    private final int downloadTimeoutSegundos;
    private final boolean sumario;
    private final boolean marcadores;
    private final boolean numeracao;
    private final Path diretorio;

    ParametrosDoDossieDoAmbiente(
            @Value("${vistas.dossie.ttl-minutos:30}") int ttlMinutos,
            @Value("${vistas.dossie.threads:2}") int threads,
            @Value("${vistas.dossie.download-timeout-segundos:60}") int downloadTimeoutSegundos,
            @Value("${vistas.dossie.sumario:true}") boolean sumario,
            @Value("${vistas.dossie.marcadores:true}") boolean marcadores,
            @Value("${vistas.dossie.numeracao:true}") boolean numeracao,
            @Value("${vistas.dossie.diretorio:/var/tmp/vistas}") String diretorio) {
        this.ttlMinutos = ttlMinutos;
        this.threads = threads;
        this.downloadTimeoutSegundos = downloadTimeoutSegundos;
        this.sumario = sumario;
        this.marcadores = marcadores;
        this.numeracao = numeracao;
        this.diretorio = Path.of(diretorio);
    }

    @Override
    public int ttlMinutos() {
        return ttlMinutos;
    }

    @Override
    public int threads() {
        return threads;
    }

    @Override
    public int downloadTimeoutSegundos() {
        return downloadTimeoutSegundos;
    }

    @Override
    public boolean sumario() {
        return sumario;
    }

    @Override
    public boolean marcadores() {
        return marcadores;
    }

    @Override
    public boolean numeracao() {
        return numeracao;
    }

    @Override
    public Path diretorio() {
        return diretorio;
    }
}
