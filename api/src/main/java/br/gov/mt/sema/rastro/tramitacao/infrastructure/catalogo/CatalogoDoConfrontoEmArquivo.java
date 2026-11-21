package br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDoConfronto;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
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
 * Catálogo do confronto lido de {@code rastro/confronto.yaml} na subida e conferido contra o catálogo de etapas: todo
 * código de aviso tem texto, toda situação citada é uma das conhecidas, todo modelo, etapa e final existe, e a
 * divergência conhecida só aponta para aviso próprio de divergência. Qualquer problema derruba a subida.
 */
@Component
class CatalogoDoConfrontoEmArquivo implements CatalogoDoConfronto {

    static final String ARQUIVO = "rastro/confronto.yaml";
    static final String MARCADOR = "{situacao}";
    private static final Set<String> RAIZ = Set.of("situacoes", "avisos", "compatibilidade");
    private static final Set<String> CAMPOS_DO_AVISO = Set.of("codigo", "gravidade", "texto");
    private static final Set<String> SECOES = Set.of("etapas", "finais");
    private static final Set<String> CAMPOS_DA_REGRA = Set.of("aceitas", "conhecidas");

    private record Lugar(String chave, String secao, String id) {}

    private final Map<CodigoDoAviso, Aviso> avisos = new EnumMap<>(CodigoDoAviso.class);
    private final Map<Lugar, RegraDaSituacao> regras = new HashMap<>();
    private final List<String> problemas = new ArrayList<>();

    @Autowired
    CatalogoDoConfrontoEmArquivo(CatalogoDeEtapas etapas) {
        this(ler(), etapas);
    }

    /** Para teste: conteúdo YAML dado. */
    CatalogoDoConfrontoEmArquivo(String conteudo, CatalogoDeEtapas etapas) {
        Object raiz;
        try {
            raiz = new Yaml(new SafeConstructor(new LoaderOptions())).load(conteudo);
        } catch (YAMLException e) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("YAML malformado: " + e.getMessage()));
        }
        if (!(raiz instanceof Map<?, ?> mapa)) {
            throw new ModeloDeFluxoInvalido(ARQUIVO, List.of("arquivo vazio ou sem o mapa do confronto"));
        }
        desconhecidos(mapa, RAIZ, "raiz");
        Set<String> situacoes = situacoes(mapa.get("situacoes"));
        avisos(mapa.get("avisos"));
        compatibilidade(mapa.get("compatibilidade"), situacoes, etapas);
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

    private Set<String> situacoes(Object valor) {
        Set<String> situacoes = new LinkedHashSet<>();
        if (!(valor instanceof List<?> lista) || lista.isEmpty()) {
            problemas.add("lista 'situacoes' ausente ou vazia");
            return situacoes;
        }
        for (Object item : lista) {
            if (!(item instanceof String s) || s.isBlank()) {
                problemas.add("situação vazia ou que não é texto");
            } else if (!situacoes.add(s.strip())) {
                problemas.add("situação '" + s + "' repetida");
            }
        }
        return situacoes;
    }

    private void avisos(Object valor) {
        if (!(valor instanceof List<?> lista)) {
            problemas.add("lista 'avisos' ausente");
            return;
        }
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> m)) {
                problemas.add("item de 'avisos' deve ser mapa");
                continue;
            }
            desconhecidos(m, CAMPOS_DO_AVISO, "aviso '" + m.get("codigo") + "'");
            CodigoDoAviso codigo = valor(CodigoDoAviso.class, m.get("codigo"), "código de aviso");
            Aviso.Gravidade gravidade = valor(Aviso.Gravidade.class, m.get("gravidade"), "gravidade");
            String texto = m.get("texto") instanceof String s ? s.strip() : "";
            if (texto.isEmpty()) {
                problemas.add("aviso '" + m.get("codigo") + "' sem texto");
            }
            if (codigo != null && gravidade != null && !texto.isEmpty()
                    && avisos.putIfAbsent(codigo, new Aviso(codigo, texto, gravidade)) != null) {
                problemas.add("aviso '" + codigo + "' repetido");
            }
        }
        for (CodigoDoAviso codigo : CodigoDoAviso.values()) {
            if (!avisos.containsKey(codigo)) {
                problemas.add("aviso '" + codigo + "' sem texto no arquivo");
            }
        }
    }

    private void compatibilidade(Object valor, Set<String> situacoes, CatalogoDeEtapas etapas) {
        if (!(valor instanceof Map<?, ?> porModelo)) {
            problemas.add("mapa 'compatibilidade' ausente");
            return;
        }
        porModelo.forEach((chave, secoes) -> {
            Optional<ModeloDeFluxo> modelo = etapas.modelo(String.valueOf(chave));
            if (modelo.isEmpty()) {
                problemas.add("modelo '" + chave + "' fora do catálogo de etapas");
                return;
            }
            if (!(secoes instanceof Map<?, ?> m)) {
                problemas.add("modelo '" + chave + "' deve ser mapa com 'etapas' e 'finais'");
                return;
            }
            desconhecidos(m, SECOES, "modelo '" + chave + "'");
            Set<String> idsDeEtapa = new HashSet<>(modelo.get().etapas().stream().map(Etapa::id).toList());
            Set<String> idsDeFinal = new HashSet<>(modelo.get().finais().stream().map(FinalDoFluxo::id).toList());
            regras(modelo.get().chave(), "etapas", m.get("etapas"), idsDeEtapa, situacoes);
            regras(modelo.get().chave(), "finais", m.get("finais"), idsDeFinal, situacoes);
        });
    }

    private void regras(String chave, String secao, Object valor, Set<String> ids, Set<String> situacoes) {
        if (valor == null) {
            return;
        }
        if (!(valor instanceof Map<?, ?> porId)) {
            problemas.add(chave + "." + secao + " deve ser mapa");
            return;
        }
        porId.forEach((id, corpo) -> {
            String onde = chave + "." + secao + "." + id;
            if (!ids.contains(String.valueOf(id))) {
                problemas.add(onde + ": id fora do catálogo de etapas");
                return;
            }
            if (!(corpo instanceof Map<?, ?> regra)) {
                problemas.add(onde + " deve ser mapa");
                return;
            }
            desconhecidos(regra, CAMPOS_DA_REGRA, onde);
            Set<String> aceitas = new HashSet<>();
            if (regra.get("aceitas") instanceof List<?> lista) {
                lista.forEach(s -> aceitas.add(situacao(s, situacoes, onde)));
            } else if (regra.get("aceitas") != null) {
                problemas.add(onde + ": 'aceitas' deve ser lista");
            }
            Map<String, CodigoDoAviso> conhecidas = new HashMap<>();
            if (regra.get("conhecidas") instanceof Map<?, ?> mapa) {
                mapa.forEach((s, c) -> {
                    CodigoDoAviso codigo = valor(CodigoDoAviso.class, c, onde + ": aviso da divergência");
                    if (codigo != null && !codigo.divergenciaConhecida()) {
                        problemas.add(onde + ": '" + codigo + "' não é aviso de divergência conhecida");
                    }
                    String daSituacao = situacao(s, situacoes, onde);
                    if (codigo != null) {
                        conhecidas.put(daSituacao, codigo);
                    }
                });
            } else if (regra.get("conhecidas") != null) {
                problemas.add(onde + ": 'conhecidas' deve ser mapa");
            }
            regras.put(new Lugar(chave, secao, String.valueOf(id)), new RegraDaSituacao(aceitas, conhecidas));
        });
    }

    private String situacao(Object valor, Set<String> situacoes, String onde) {
        String s = String.valueOf(valor).strip();
        if (!situacoes.contains(s)) {
            problemas.add(onde + ": situação desconhecida '" + s + "'");
        }
        return s;
    }

    private <E extends Enum<E>> E valor(Class<E> tipo, Object valor, String oQue) {
        try {
            return Enum.valueOf(tipo, String.valueOf(valor).strip());
        } catch (IllegalArgumentException e) {
            problemas.add(oQue + " desconhecido '" + valor + "'");
            return null;
        }
    }

    private void desconhecidos(Map<?, ?> mapa, Set<String> aceitos, String onde) {
        mapa.keySet().stream().filter(k -> !aceitos.contains(String.valueOf(k)))
                .forEach(k -> problemas.add(onde + ": campo desconhecido '" + k + "'"));
    }

    @Override
    public Aviso aviso(CodigoDoAviso codigo, String situacao) {
        Aviso modelo = avisos.get(codigo);
        String texto = modelo.texto().replace(MARCADOR, situacao == null ? "" : situacao);
        return new Aviso(codigo, texto, modelo.gravidade());
    }

    @Override
    public Optional<RegraDaSituacao> daEtapa(String chaveDoModelo, String idDaEtapa) {
        return Optional.ofNullable(regras.get(new Lugar(chaveDoModelo, "etapas", idDaEtapa)));
    }

    @Override
    public Optional<RegraDaSituacao> doFinal(String chaveDoModelo, String idDoFinal) {
        return Optional.ofNullable(regras.get(new Lugar(chaveDoModelo, "finais", idDoFinal)));
    }
}
