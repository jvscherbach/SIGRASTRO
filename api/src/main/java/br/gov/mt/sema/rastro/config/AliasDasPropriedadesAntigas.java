package br.gov.mt.sema.rastro.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Alias das propriedades antigas (R1, bloco 5; D74): o prefixo {@code vistas.*} virou {@code rastro.*} e o antigo
 * continua valendo. {@code vistas.x} responde por {@code rastro.x}; se as duas existirem, <b>vale a antiga</b>, para
 * que uma configuração já em uso não mude de valor sem aviso. Cada nome antigo em uso gera um aviso de depreciação no
 * log, só com os nomes, nunca com o valor (pode ser senha ou token).
 *
 * <p>As variáveis de ambiente não mudam (decisão da R1) e entram pelos placeholders do {@code application.yml}
 * ({@code ${VISTAS_DOSSIE_DIRETORIO}}). Por isso o alias ignora as fontes de variáveis de
 * ambiente: a ligação relaxada do Spring leria {@code VISTAS_DOSSIE_DIRETORIO} como {@code vistas.dossie.diretorio} e
 * daria um aviso falso de depreciação.
 *
 * <p>O alias é uma fonte de propriedades viva, consultada a cada leitura: vê também as propriedades que os testes
 * acrescentam depois desta etapa. Registrado em {@code META-INF/spring.factories}.
 */
public class AliasDasPropriedadesAntigas implements EnvironmentPostProcessor, Ordered {

    static final String PREFIXO_ANTIGO = "vistas.";
    static final String PREFIXO_NOVO = "rastro.";
    static final String NOME_DA_FONTE = "aliasDasPropriedadesVistas";
    /** Nome da fonte que o Spring Boot acrescenta por cima das outras (ConfigurationPropertySources). */
    private static final String FONTE_ANEXADA_DO_BOOT = "configurationProperties";

    private final Log log;

    public AliasDasPropriedadesAntigas(DeferredLogFactory logs) {
        this.log = logs.getLog(AliasDasPropriedadesAntigas.class);
    }

    @Override
    public int getOrder() {
        // Depois do ConfigDataEnvironmentPostProcessor: os application*.yml já estão carregados.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment ambiente, SpringApplication aplicacao) {
        MutablePropertySources fontes = ambiente.getPropertySources();
        if (fontes.contains(NOME_DA_FONTE)) {
            return;
        }
        Fonte alias = new Fonte(fontes);
        fontes.addFirst(alias);
        for (String antiga : alias.nomesAntigos()) {
            // O application.yml sempre define rastro.*, então não dá para saber se a nova também foi definida de
            // propósito: o aviso diz a regra em vez de adivinhar.
            String nova = PREFIXO_NOVO + antiga.substring(PREFIXO_ANTIGO.length());
            log.warn("Propriedade " + antiga + " está depreciada: use " + nova
                    + ". Enquanto existir, vale a antiga, mesmo com a nova definida.");
        }
    }

    /** Fonte que responde {@code rastro.x} com o valor de {@code vistas.x}, quando houver. */
    static final class Fonte extends EnumerablePropertySource<MutablePropertySources> {

        Fonte(MutablePropertySources fontes) {
            super(NOME_DA_FONTE, fontes);
        }

        @Override
        public String[] getPropertyNames() {
            return nomesAntigos().stream()
                    .map(antiga -> PREFIXO_NOVO + antiga.substring(PREFIXO_ANTIGO.length()))
                    .toArray(String[]::new);
        }

        @Override
        public Object getProperty(String nome) {
            if (!nome.startsWith(PREFIXO_NOVO)) {
                return null;
            }
            String antiga = PREFIXO_ANTIGO + nome.substring(PREFIXO_NOVO.length());
            for (PropertySource<?> fonte : getSource()) {
                if (consultavel(fonte)) {
                    Object valor = fonte.getProperty(antiga);
                    if (valor != null) {
                        return valor;
                    }
                }
            }
            return null;
        }

        Set<String> nomesAntigos() {
            Set<String> nomes = new LinkedHashSet<>();
            for (PropertySource<?> fonte : getSource()) {
                if (consultavel(fonte) && fonte instanceof EnumerablePropertySource<?> enumeravel) {
                    for (String nome : enumeravel.getPropertyNames()) {
                        if (nome.startsWith(PREFIXO_ANTIGO)) {
                            nomes.add(nome);
                        }
                    }
                }
            }
            return nomes;
        }

        private boolean consultavel(PropertySource<?> fonte) {
            return fonte != this
                    && !FONTE_ANEXADA_DO_BOOT.equals(fonte.getName())
                    && !(fonte instanceof SystemEnvironmentPropertySource);
        }
    }
}
