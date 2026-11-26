package br.gov.mt.sema.rastro.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.stereotype.Component;

/** R1, bloco 5 (D74): o prefixo antigo vistas.* continua valendo como alias de rastro.*; a antiga vence. */
class AliasDasPropriedadesAntigasTest {

    private static final String VALOR_SECRETO = "valor-secreto-de-teste";

    private final List<String> avisos = new ArrayList<>();
    private final AliasDasPropriedadesAntigas alias = new AliasDasPropriedadesAntigas(destino -> new NoOpLog() {
        @Override
        public void warn(Object mensagem) {
            avisos.add(String.valueOf(mensagem));
        }
    });

    private StandardEnvironment ambienteCom(Map<String, Object> propriedades) {
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addLast(new MapPropertySource("teste", propriedades));
        alias.postProcessEnvironment(ambiente, new SpringApplication());
        return ambiente;
    }

    @Test
    void antigaSozinhaContinuaValendoEAvisaSemOValor() {
        StandardEnvironment ambiente = ambienteCom(Map.of("vistas.arquivos.token", VALOR_SECRETO));

        assertThat(ambiente.getProperty("rastro.arquivos.token")).isEqualTo(VALOR_SECRETO);
        assertThat(avisos).singleElement().asString()
                .contains("vistas.arquivos.token", "rastro.arquivos.token", "depreciada")
                .doesNotContain(VALOR_SECRETO);
    }

    @Test
    void novaSozinhaFuncionaSemAviso() {
        StandardEnvironment ambiente = ambienteCom(Map.of("rastro.dossie.ttl-minutos", "45"));

        assertThat(ambiente.getProperty("rastro.dossie.ttl-minutos", Integer.class)).isEqualTo(45);
        assertThat(avisos).isEmpty();
    }

    @Test
    void comAsDuasVenceAAntigaEOAvisoDizIsso() {
        StandardEnvironment ambiente = ambienteCom(Map.of(
                "rastro.arquivos.token", "token-novo-de-teste",
                "vistas.arquivos.token", VALOR_SECRETO));

        assertThat(ambiente.getProperty("rastro.arquivos.token")).isEqualTo(VALOR_SECRETO);
        assertThat(avisos).singleElement().asString()
                .contains("vale a antiga", "depreciada", "vistas.arquivos.token", "rastro.arquivos.token")
                .doesNotContain(VALOR_SECRETO, "token-novo-de-teste");
    }

    @Test
    void placeholderNaAntigaEResolvido() {
        StandardEnvironment ambiente = ambienteCom(Map.of(
                "vistas.dossie.diretorio", "${base}/dossies", "base", "/tmp/rastro-teste"));

        assertThat(ambiente.getProperty("rastro.dossie.diretorio")).isEqualTo("/tmp/rastro-teste/dossies");
        assertThat(ambiente.resolvePlaceholders("${rastro.dossie.diretorio:padrao}")).isEqualTo("/tmp/rastro-teste/dossies");
    }

    @Test
    void variavelDeAmbienteComNomeAntigoNaoViraAliasNemAviso() {
        // VISTAS_ARQUIVOS_TOKEN continua sendo lida pelo placeholder do application.yml; não é a propriedade antiga.
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.getPropertySources().addFirst(new SystemEnvironmentPropertySource("ambienteDeTeste",
                Map.of("VISTAS_ARQUIVOS_TOKEN", VALOR_SECRETO)));
        ambiente.getPropertySources().addLast(new MapPropertySource("yml",
                Map.of("rastro.arquivos.token", "${VISTAS_ARQUIVOS_TOKEN:}")));
        alias.postProcessEnvironment(ambiente, new SpringApplication());

        assertThat(ambiente.getProperty("rastro.arquivos.token")).isEqualTo(VALOR_SECRETO);
        assertThat(avisos).isEmpty();
    }

    @Test
    void registradoNoSpringBootValeParaValueEParaAsPropriedadesDaLinhaDeComando() {
        SpringApplication aplicacao = new SpringApplication(Contexto.class);
        aplicacao.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext contexto = aplicacao.run(
                "--vistas.dossie.ttl-minutos=17",
                "--rastro.conversao.processos=3",
                "--vistas.geometrias.max-arquivos=11", "--rastro.geometrias.max-arquivos=99")) {
            Leitor leitor = contexto.getBean(Leitor.class);
            assertThat(leitor.ttl).isEqualTo(17);
            assertThat(leitor.processos).isEqualTo(3);
            assertThat(leitor.maxArquivos).as("a antiga vence").isEqualTo(11);
            assertThat(contexto.getEnvironment().getProperty("rastro.dossie.ttl-minutos")).isEqualTo("17");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @org.springframework.context.annotation.Import(Leitor.class)
    static class Contexto {
    }

    @Component
    static class Leitor {
        final int ttl;
        final int processos;
        final int maxArquivos;

        Leitor(@Value("${rastro.dossie.ttl-minutos:0}") int ttl,
                @Value("${rastro.conversao.processos:0}") int processos,
                @Value("${rastro.geometrias.max-arquivos:0}") int maxArquivos) {
            this.ttl = ttl;
            this.processos = processos;
            this.maxArquivos = maxArquivos;
        }
    }
}
