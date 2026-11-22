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

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;
import br.gov.mt.sema.rastro.tramitacao.domain.MapaDeEventosDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Mapa de eventos do plano B lido de {@code rastro/eventos-historico.yaml} na subida (S8, bloco 5), com o construtor
 * seguro do SnakeYAML, e conferido contra o catálogo de etapas. Qualquer problema derruba a subida, com todos de uma vez.
 */
@Component
class MapaDeEventosEmArquivo implements MapaDeEventosDoHistorico {

    static final String ARQUIVO = "rastro/eventos-historico.yaml";
    private static final Set<String> CAMPOS = Set.of("etapa", "marco");
    private static final Set<TipoDeMarco> MARCOS_DE_EVENTO = Set.of(TipoDeMarco.PROTOCOLO, TipoDeMarco.CONCLUSAO);

    private final Map<String, Map<String, Resolucao>> porModelo = new HashMap<>();
    private final List<String> problemas = new ArrayList<>();

    @Autowired
    MapaDeEventosEmArquivo(CatalogoDeEtapas etapas) {
        this(ler(), etapas);
    }

    /** Para teste: conteúdo YAML dado. */
    MapaDeEventosEmArquivo(String conteudo, CatalogoDeEtapas etapas) {
        Object raiz;
        try {
            raiz = new Yaml(new SafeConstructor(new LoaderOptions())).load(conteudo);
        } catch (YAMLException e) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("YAML malformado: " + e.getMessage()));
        }
        if (!(raiz instanceof Map<?, ?> mapa) || mapa.isEmpty()) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("arquivo vazio ou sem o mapa por modelo"));
        }
        mapa.forEach((chave, eventos) -> modelo(String.valueOf(chave), eventos, etapas));
        if (!problemas.isEmpty()) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, problemas);
        }
    }

    private static String ler() {
        try {
            return new ClassPathResource(ARQUIVO).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + ARQUIVO, e);
        }
    }

    private void modelo(String chave, Object eventos, CatalogoDeEtapas etapas) {
        Optional<ModeloDeFluxo> modelo = etapas.modelo(chave);
        if (modelo.isEmpty()) {
            problemas.add("modelo '" + chave + "' não existe no catálogo de etapas");
            return;
        }
        if (!(eventos instanceof Map<?, ?> lista) || lista.isEmpty()) {
            problemas.add(chave + ": mapa de eventos ausente ou vazio");
            return;
        }
        Map<String, Resolucao> doModelo = new HashMap<>();
        lista.forEach((nome, item) -> {
            String evento = String.valueOf(nome).strip();
            String onde = chave + "." + evento;
            if (!evento.matches("[A-Z][A-Z0-9_]*")) {
                problemas.add(onde + ": nome de evento fora do padrão (maiúsculas e sublinhado)");
                return;
            }
            if (!(item instanceof Map<?, ?> m)) {
                problemas.add(onde + ": deve ser {etapa: ...} ou {marco: ...}");
                return;
            }
            m.keySet().stream().map(String::valueOf).filter(c -> !CAMPOS.contains(c))
                    .forEach(c -> problemas.add(onde + ": campo desconhecido '" + c + "'"));
            Object etapa = m.get("etapa");
            Object marco = m.get("marco");
            if ((etapa == null) == (marco == null)) {
                problemas.add(onde + ": informe 'etapa' ou 'marco' (um só)");
            } else if (etapa != null) {
                String id = String.valueOf(etapa).strip();
                if (modelo.get().etapa(id).isEmpty()) {
                    problemas.add(onde + ": etapa '" + id + "' não existe no modelo");
                } else {
                    doModelo.put(evento, new NaEtapa(id));
                }
            } else {
                Optional<TipoDeMarco> tipo = MARCOS_DE_EVENTO.stream()
                        .filter(t -> t.name().equals(String.valueOf(marco).strip())).findFirst();
                if (tipo.isEmpty()) {
                    problemas.add(onde + ": marco '" + marco + "' desconhecido (use PROTOCOLO ou CONCLUSAO)");
                } else {
                    doModelo.put(evento, new Marco(tipo.get()));
                }
            }
        });
        porModelo.put(chave, Map.copyOf(doModelo));
    }

    @Override
    public Resolucao resolver(String chaveDoModelo, String evento) {
        if (chaveDoModelo == null || evento == null) {
            return new Desconhecido();
        }
        return porModelo.getOrDefault(chaveDoModelo, Map.of()).getOrDefault(evento.strip(), new Desconhecido());
    }
}
