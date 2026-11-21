package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Catálogo de grupos lido de {@code rastro/grupos.yaml} uma vez, na subida. Problema de conteúdo (id repetido, rótulo
 * vazio, id {@code 0}, campo desconhecido, lista vazia) derruba a subida com a lista completa.
 */
@Component
class CatalogoDeGruposEmArquivo implements CatalogoDeGrupos {

    static final String ARQUIVO = "rastro/grupos.yaml";
    private static final Set<String> CAMPOS = Set.of("id", "rotulo");

    private final Map<String, String> areas;

    CatalogoDeGruposEmArquivo() {
        this(ler());
    }

    /** Para teste: conteúdo YAML dado. */
    CatalogoDeGruposEmArquivo(String conteudo) {
        this.areas = Map.copyOf(validar(conteudo));
    }

    private static String ler() {
        try {
            return new ClassPathResource(ARQUIVO).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, e);
        }
    }

    private static Map<String, String> validar(String conteudo) {
        Object raiz;
        try {
            raiz = new Yaml(new SafeConstructor(new LoaderOptions())).load(conteudo);
        } catch (YAMLException e) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("YAML malformado: " + e.getMessage()));
        }
        List<String> problemas = new ArrayList<>();
        Map<String, String> areas = new HashMap<>();
        if (!(raiz instanceof Map<?, ?> mapa) || !(mapa.get("grupos") instanceof List<?> lista) || lista.isEmpty()) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("arquivo sem a lista 'grupos'"));
        }
        for (Object chave : mapa.keySet()) {
            if (!"grupos".equals(chave)) {
                problemas.add("campo desconhecido '" + chave + "'");
            }
        }
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> grupo)) {
                problemas.add("item de 'grupos' deve ser mapa");
                continue;
            }
            grupo.keySet().stream().filter(c -> !CAMPOS.contains(String.valueOf(c)))
                    .forEach(c -> problemas.add("grupo '" + grupo.get("id") + "': campo desconhecido '" + c + "'"));
            String id = grupo.get("id") instanceof String s ? s.strip() : null;
            String rotulo = grupo.get("rotulo") instanceof String s ? s.strip() : null;
            if (id == null || id.isEmpty()) {
                problemas.add("grupo sem id");
            } else if (GRUPO_SEM_AREA.equals(id)) {
                problemas.add("grupo '0' é reservado (sem área)");
            } else if (rotulo == null || rotulo.isEmpty()) {
                problemas.add("grupo '" + id + "' sem rótulo");
            } else if (areas.putIfAbsent(id, rotulo) != null) {
                problemas.add("grupo '" + id + "' repetido");
            }
        }
        if (!problemas.isEmpty()) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, problemas);
        }
        return areas;
    }

    @Override
    public Optional<String> area(String idDoGrupo) {
        return idDoGrupo == null ? Optional.empty() : Optional.ofNullable(areas.get(idDoGrupo.strip()));
    }
}
