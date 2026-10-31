package br.gov.mt.sema.vistas.configuracao.application.port;

import java.util.Map;

/** Valores que o Administrador sobrepôs aos do ambiente, por nome de parâmetro. */
public interface RepositorioDeSobreposicoes {

    /** Retrato imutável e consistente. */
    Map<String, String> todas();

    /** Troca o conjunto inteiro de uma vez: quem lê vê tudo antes ou tudo depois. */
    void substituir(Map<String, String> sobreposicoes);
}
