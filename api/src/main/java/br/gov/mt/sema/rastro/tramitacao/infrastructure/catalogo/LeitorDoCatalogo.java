package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.IdIgnorado;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxoInvalido;
import br.gov.mt.sema.rastro.tramitacao.domain.PapelDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.TipoDeEtapa;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Lê um arquivo YAML do catálogo (formato em docs/15) e devolve o {@link ModeloDeFluxo} validado. Chave desconhecida, tipo
 * errado e valor fora do enum são problemas de conteúdo, reunidos numa só {@link ModeloDeFluxoInvalido}. Usa o construtor
 * seguro do SnakeYAML (sem instanciar classes a partir do arquivo).
 */
final class LeitorDoCatalogo {

    private static final Set<String> CHAVES_DO_MODELO = Set.of("chave", "rotulo", "instancias", "etapas", "finais", "ignorados");
    private static final Set<String> CHAVES_DA_ETAPA = Set.of("id", "rotulo", "ordem", "tipo", "responsavel", "ramo",
            "multiInstancia", "idsTecnicos", "processosFilhos", "semPassagemPor");
    private static final Set<String> CHAVES_DO_FINAL = Set.of("id", "rotulo", "idTecnico");
    private static final Set<String> CHAVES_DO_IGNORADO = Set.of("idTecnico", "descricao", "motivo");

    private final String origem;
    private final List<String> problemas = new ArrayList<>();

    private LeitorDoCatalogo(String origem) {
        this.origem = origem;
    }

    static ModeloDeFluxo ler(String origem, String conteudo) {
        return new LeitorDoCatalogo(origem).ler(conteudo);
    }

    private ModeloDeFluxo ler(String conteudo) {
        Object raiz;
        try {
            raiz = new Yaml(new SafeConstructor(new LoaderOptions())).load(conteudo);
        } catch (YAMLException e) {
            throw new ModeloDeFluxoInvalido(origem, List.of("YAML malformado: " + e.getMessage()));
        }
        if (!(raiz instanceof Map<?, ?> modelo)) {
            throw new ModeloDeFluxoInvalido(origem, List.of("arquivo vazio ou sem o mapa do modelo"));
        }
        chavesConhecidas(modelo, CHAVES_DO_MODELO, "modelo");
        String chave = texto(modelo, "chave", "modelo");
        String rotulo = texto(modelo, "rotulo", "modelo");
        List<String> instancias = textos(modelo, "instancias", "modelo");
        List<Etapa> etapas = mapas(modelo, "etapas", "modelo").stream().map(this::etapa).toList();
        List<FinalDoFluxo> finais = mapas(modelo, "finais", "modelo").stream().map(this::fim).toList();
        List<IdIgnorado> ignorados = mapas(modelo, "ignorados", "modelo").stream().map(this::ignorado).toList();
        if (!problemas.isEmpty()) {
            throw new ModeloDeFluxoInvalido(origem, problemas);
        }
        return ModeloDeFluxo.criar(origem, chave, rotulo, instancias, etapas, finais, ignorados);
    }

    private Etapa etapa(Map<?, ?> m) {
        String onde = "etapa '" + m.get("id") + "'";
        chavesConhecidas(m, CHAVES_DA_ETAPA, onde);
        Integer ordem = inteiro(m, "ordem", onde);
        Ramo ramo = m.containsKey("ramo") ? valor(Ramo.class, m, "ramo", onde) : Ramo.PRINCIPAL;
        Boolean multi = m.containsKey("multiInstancia") ? logico(m, "multiInstancia", onde) : Boolean.FALSE;
        return new Etapa(texto(m, "id", onde), texto(m, "rotulo", onde), ordem == null ? 0 : ordem,
                valor(TipoDeEtapa.class, m, "tipo", onde), valor(PapelDoResponsavel.class, m, "responsavel", onde), ramo,
                Boolean.TRUE.equals(multi), textos(m, "idsTecnicos", onde), textos(m, "processosFilhos", onde),
                textos(m, "semPassagemPor", onde));
    }

    private FinalDoFluxo fim(Map<?, ?> m) {
        String onde = "final '" + m.get("id") + "'";
        chavesConhecidas(m, CHAVES_DO_FINAL, onde);
        return new FinalDoFluxo(texto(m, "id", onde), texto(m, "rotulo", onde), texto(m, "idTecnico", onde));
    }

    private IdIgnorado ignorado(Map<?, ?> m) {
        String onde = "ignorado '" + m.get("idTecnico") + "'";
        chavesConhecidas(m, CHAVES_DO_IGNORADO, onde);
        return new IdIgnorado(texto(m, "idTecnico", onde), texto(m, "descricao", onde),
                valor(IdIgnorado.Motivo.class, m, "motivo", onde));
    }

    private void chavesConhecidas(Map<?, ?> m, Set<String> aceitas, String onde) {
        for (Object chave : m.keySet()) {
            if (!aceitas.contains(String.valueOf(chave))) {
                problemas.add(onde + ": campo desconhecido '" + chave + "'");
            }
        }
    }

    private String texto(Map<?, ?> m, String campo, String onde) {
        Object valor = m.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s.strip();
        }
        problemas.add(onde + ": '" + campo + "' deve ser texto");
        return null;
    }

    private Integer inteiro(Map<?, ?> m, String campo, String onde) {
        Object valor = m.get(campo);
        if (valor == null) {
            return null;
        }
        if (valor instanceof Integer i) {
            return i;
        }
        problemas.add(onde + ": '" + campo + "' deve ser número inteiro");
        return null;
    }

    private Boolean logico(Map<?, ?> m, String campo, String onde) {
        Object valor = m.get(campo);
        if (valor instanceof Boolean b) {
            return b;
        }
        problemas.add(onde + ": '" + campo + "' deve ser true ou false");
        return Boolean.FALSE;
    }

    private <E extends Enum<E>> E valor(Class<E> tipo, Map<?, ?> m, String campo, String onde) {
        String texto = texto(m, campo, onde);
        if (texto == null) {
            return null;
        }
        try {
            return Enum.valueOf(tipo, texto);
        } catch (IllegalArgumentException e) {
            problemas.add(onde + ": '" + campo + "' com valor desconhecido '" + texto + "'");
            return null;
        }
    }

    private List<String> textos(Map<?, ?> m, String campo, String onde) {
        Object valor = m.get(campo);
        if (valor == null) {
            return List.of();
        }
        if (!(valor instanceof List<?> lista)) {
            problemas.add(onde + ": '" + campo + "' deve ser lista");
            return List.of();
        }
        List<String> textos = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof String s && !s.isBlank()) {
                textos.add(s.strip());
            } else {
                problemas.add(onde + ": '" + campo + "' com item vazio ou que não é texto");
            }
        }
        return textos;
    }

    private List<Map<?, ?>> mapas(Map<?, ?> m, String campo, String onde) {
        Object valor = m.get(campo);
        if (valor == null) {
            return List.of();
        }
        if (!(valor instanceof List<?> lista)) {
            problemas.add(onde + ": '" + campo + "' deve ser lista");
            return List.of();
        }
        List<Map<?, ?>> mapas = new ArrayList<>();
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa) {
                mapas.add(mapa);
            } else {
                problemas.add(onde + ": item de '" + campo + "' deve ser mapa");
            }
        }
        return mapas;
    }
}
