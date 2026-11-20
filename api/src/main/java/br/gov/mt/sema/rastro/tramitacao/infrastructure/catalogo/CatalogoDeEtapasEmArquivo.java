package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Catálogo lido dos arquivos {@code rastro/etapas/*.yml} do classpath, uma vez, na subida. Qualquer problema de conteúdo
 * derruba a subida com a lista completa ({@link ModeloDeFluxoInvalido}): catálogo errado contamina toda posição mostrada.
 * Além das regras de cada modelo, confere entre arquivos: chave do Camunda e instância do requerimento não se repetem.
 */
@Component
class CatalogoDeEtapasEmArquivo implements CatalogoDeEtapas {

    static final String LOCAL_PADRAO = "classpath*:rastro/etapas/*.yml";
    private static final Logger LOG = LoggerFactory.getLogger(CatalogoDeEtapasEmArquivo.class);

    private final List<ModeloDeFluxo> modelos;
    private final Map<String, ModeloDeFluxo> porChave = new HashMap<>();
    private final Map<String, ModeloDeFluxo> porInstancia = new HashMap<>();
    private final String versao;

    CatalogoDeEtapasEmArquivo() {
        this(recursos(LOCAL_PADRAO));
    }

    /** Para teste: lê o conteúdo dado, como {@code nome -> YAML}. */
    CatalogoDeEtapasEmArquivo(Map<String, String> arquivos) {
        List<String> nomes = arquivos.keySet().stream().sorted().toList();
        if (nomes.isEmpty()) {
            throw new ModeloDeFluxoInvalido("rastro/etapas", List.of("nenhum arquivo de modelo encontrado"));
        }
        List<ModeloDeFluxo> lidos = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        MessageDigest resumo = sha256();
        for (String nome : nomes) {
            String conteudo = arquivos.get(nome);
            resumo.update(nome.getBytes(StandardCharsets.UTF_8));
            resumo.update(conteudo.getBytes(StandardCharsets.UTF_8));
            ModeloDeFluxo modelo = LeitorDoCatalogo.ler(nome, conteudo);
            if (porChave.putIfAbsent(modelo.chave(), modelo) != null) {
                problemas.add("chave '" + modelo.chave() + "' em mais de um arquivo");
            }
            for (String instancia : modelo.instancias()) {
                ModeloDeFluxo outro = porInstancia.putIfAbsent(instancia, modelo);
                if (outro != null) {
                    problemas.add("instância '" + instancia + "' em '" + outro.chave() + "' e '" + modelo.chave() + "'");
                }
            }
            lidos.add(modelo);
        }
        if (!problemas.isEmpty()) {
            throw new ModeloDeFluxoInvalido("rastro/etapas", problemas);
        }
        this.modelos = List.copyOf(lidos);
        this.versao = HexFormat.of().formatHex(resumo.digest(), 0, 6);
        LOG.info("Catálogo de etapas carregado: {} modelo(s) {}, versão {}", modelos.size(),
                modelos.stream().map(ModeloDeFluxo::chave).toList(), versao);
    }

    private static Map<String, String> recursos(String padrao) {
        try {
            Resource[] encontrados = new PathMatchingResourcePatternResolver().getResources(padrao);
            Map<String, String> arquivos = new HashMap<>();
            for (Resource recurso : Arrays.stream(encontrados).sorted(Comparator.comparing(Resource::getFilename)).toList()) {
                String nome = recurso.getFilename();
                if (arquivos.containsKey(nome)) {
                    throw new ModeloDeFluxoInvalido("rastro/etapas", List.of("arquivo '" + nome + "' repetido no classpath"));
                }
                arquivos.put(nome, recurso.getContentAsString(StandardCharsets.UTF_8));
            }
            return arquivos;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o catálogo de etapas (" + padrao + ")", e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<ModeloDeFluxo> modelos() {
        return modelos;
    }

    @Override
    public Optional<ModeloDeFluxo> modelo(String chave) {
        return Optional.ofNullable(chave).map(porChave::get);
    }

    @Override
    public Optional<ModeloDeFluxo> modeloDaInstancia(String instanciaDoRequerimento) {
        return Optional.ofNullable(instanciaDoRequerimento).map(String::strip).map(porInstancia::get);
    }

    @Override
    public String versao() {
        return versao;
    }
}
