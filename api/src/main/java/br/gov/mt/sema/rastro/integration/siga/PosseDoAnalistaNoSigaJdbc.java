package br.gov.mt.sema.rastro.integration.siga;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.caixa.application.port.PosseDoAnalistaEmLote;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Único componente que lê o SIGA e o Camunda para a caixa do analista (S11, bloco 4). Duas consultas, ambas {@code SELECT}
 * com parâmetros nomeados, {@code FETCH FIRST} e tempo limite por consulta, em {@code sql/siga/caixa/}:
 * <ol>
 *   <li>{@code candidatos-da-posse}: ids dos requerimentos com tarefa ativa de análise ou revisão dos analistas, ou em
 *       correção cuja última análise ou revisão foi deles (1 consulta por lote de até
 *       {@value PosseDoAnalistaEmLote#MAXIMO_POR_LOTE} analistas);</li>
 *   <li>{@code posse-em-lote}: tarefas ativas, última tarefa de análise ou revisão, interessado e tipo (1 consulta por lote
 *       de até {@value PosseDoAnalistaEmLote#MAXIMO_POR_LOTE} requerimentos).</li>
 * </ol>
 * Nenhum laço por linha. O CPF dos analistas é só parâmetro (o SQL devolve o de um analista pedido e nunca o de outra pessoa)
 * e é trocado aqui mesmo pela chave opaca, que vem pronta de quem chama; nada dele entra em log, erro ou retorno. Não decide
 * a posse: devolve os fatos crus.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class PosseDoAnalistaNoSigaJdbc implements PosseDoAnalistaEmLote {

    /** Teto de linhas por requerimento do lote (tarefas ativas); passou, a leitura volta marcada como incompleta. */
    static final int LINHAS_POR_REQUERIMENTO = PainelDoSigaJdbc.LINHAS_POR_REQUERIMENTO;
    /** Valor que nunca é um id de tarefa: evita {@code IN ()} quando o catálogo não tem a atividade. */
    private static final String NENHUMA = "?nenhuma?";

    private static final String PASTA = "caixa/";

    private final NamedParameterJdbcTemplate sigaJdbcTemplate;
    private final String sqlCandidatos = SigaSql.carregar(PASTA + "candidatos-da-posse");
    private final String sqlPosse = SigaSql.carregar(PASTA + "posse-em-lote");

    PosseDoAnalistaNoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.sigaJdbcTemplate = sigaJdbcTemplate;
    }

    /** Template com o tempo limite desta consulta (o mesmo recurso do painel; não muda o das outras consultas). */
    private NamedParameterJdbcTemplate com(Duration tempoLimite) {
        int segundos = PainelDoSigaJdbc.segundos(tempoLimite);
        JdbcTemplate base = new JdbcTemplate(sigaJdbcTemplate.getJdbcTemplate().getDataSource()) {
            @Override
            public int getQueryTimeout() {
                return segundos;
            }
        };
        return new NamedParameterJdbcTemplate(base);
    }

    private static List<String> valores(Collection<String> valores) {
        List<String> lista = valores.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
        return lista.isEmpty() ? List.of(NENHUMA) : lista;
    }

    private static List<String> cpfs(List<AnalistaParaLeitura> analistas) {
        return analistas.stream().map(a -> a.identificador().valorProtegido()).distinct().toList();
    }

    private static List<AnalistaParaLeitura> exigirLote(List<AnalistaParaLeitura> analistas) {
        List<AnalistaParaLeitura> distintos = List.copyOf(new LinkedHashSet<>(analistas));
        if (distintos.size() > MAXIMO_POR_LOTE) {
            throw new IllegalArgumentException("lote da caixa acima de " + MAXIMO_POR_LOTE + " analistas");
        }
        return distintos;
    }

    @Override
    public List<Long> candidatos(List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao, int maximo,
            Duration tempoLimite) {
        List<AnalistaParaLeitura> lote = exigirLote(analistas);
        if (lote.isEmpty() || definicao.vazia() || maximo < 1) {
            return List.of();
        }
        return com(tempoLimite).query(sqlCandidatos, new MapSqlParameterSource()
                .addValue("modelos", valores(definicao.chavesDosModelos()))
                .addValue("chaveDaComplementacao", PainelDoSigaJdbc.CHAVE_COMPLEMENTACAO)
                .addValue("atividadesDePosse", valores(definicao.atividadesDePosse()))
                .addValue("atividadesDeCorrecao", valores(definicao.atividadesDeCorrecao()))
                .addValue("analistas", cpfs(lote))
                .addValue("maximoDeLinhas", maximo), (rs, n) -> rs.getLong("REQUERIMENTO"));
    }

    @Override
    public LeituraDaPosse ler(List<Long> ids, List<AnalistaParaLeitura> analistas, DefinicaoDaPosse definicao,
            Duration tempoLimite) {
        List<Long> distintos = List.copyOf(new LinkedHashSet<>(ids));
        if (distintos.size() > MAXIMO_POR_LOTE) {
            throw new IllegalArgumentException("lote da caixa acima de " + MAXIMO_POR_LOTE + " requerimentos");
        }
        List<AnalistaParaLeitura> lote = exigirLote(analistas);
        if (distintos.isEmpty() || lote.isEmpty() || definicao.vazia()) {
            return LeituraDaPosse.NENHUMA;
        }
        Map<String, ChaveDoResponsavel> chavePorCpf = new HashMap<>();
        lote.forEach(a -> chavePorCpf.put(a.identificador().valorProtegido(), a.chave()));
        int maximoDeLinhas = distintos.size() * LINHAS_POR_REQUERIMENTO;
        Map<Long, Acumulado> porRequerimento = new LinkedHashMap<>();
        int[] linhas = {0};
        com(tempoLimite).query(sqlPosse, new MapSqlParameterSource()
                .addValue("ids", distintos)
                .addValue("chaves", distintos.stream().map(String::valueOf).toList())
                .addValue("modelos", valores(definicao.chavesDosModelos()))
                .addValue("chaveDaComplementacao", PainelDoSigaJdbc.CHAVE_COMPLEMENTACAO)
                .addValue("atividadesDePosse", valores(definicao.atividadesDePosse()))
                .addValue("atividadesDeCorrecao", valores(definicao.atividadesDeCorrecao()))
                .addValue("analistas", cpfs(lote))
                .addValue("maximoDeLinhas", maximoDeLinhas + 1), rs -> {
                    if (++linhas[0] > maximoDeLinhas) {
                        return;
                    }
                    long id = rs.getLong("REQUERIMENTO");
                    Acumulado acumulado = porRequerimento.get(id);
                    if (acumulado == null) {
                        String atividadeDaUltima = rs.getString("ULTIMA_ATIVIDADE");
                        LocalDateTime fimDaUltima = DataHoraDoSiga.ler(rs, "ULTIMA_FIM");
                        Optional<PosseLida.UltimaTarefaDeAnalise> ultima = atividadeDaUltima == null || fimDaUltima == null
                                ? Optional.empty()
                                : Optional.of(new PosseLida.UltimaTarefaDeAnalise(atividadeDaUltima.strip(),
                                        chave(chavePorCpf, rs.getString("ULTIMA_RESPONSAVEL")), fimDaUltima));
                        acumulado = new Acumulado(id, texto(rs.getString("NUMERO")), rs.getString("MODELO"), ultima,
                                texto(rs.getString("INTERESSADO")), texto(rs.getString("TIPO")));
                        porRequerimento.put(id, acumulado);
                    }
                    String atividade = rs.getString("ATIVIDADE");
                    if (atividade != null) {
                        acumulado.tarefas.add(new PosseLida.TarefaAtivaDaPosse(atividade.strip(),
                                DataHoraDoSiga.ler(rs, "INICIO"), chave(chavePorCpf, rs.getString("RESPONSAVEL"))));
                    }
                });
        List<PosseLida> lidos = new ArrayList<>();
        porRequerimento.values().forEach(a -> lidos.add(new PosseLida(a.id, a.numero, a.modelo, a.tarefas, a.ultima,
                a.interessado, a.tipo)));
        return new LeituraDaPosse(lidos, linhas[0] > maximoDeLinhas);
    }

    /** Requerimento em montagem: as linhas de um mesmo requerimento vêm seguidas. */
    private static final class Acumulado {
        final long id;
        final Optional<String> numero;
        final String modelo;
        final Optional<PosseLida.UltimaTarefaDeAnalise> ultima;
        final Optional<String> interessado;
        final Optional<String> tipo;
        final List<PosseLida.TarefaAtivaDaPosse> tarefas = new ArrayList<>();

        Acumulado(long id, Optional<String> numero, String modelo, Optional<PosseLida.UltimaTarefaDeAnalise> ultima,
                Optional<String> interessado, Optional<String> tipo) {
            this.id = id;
            this.numero = numero;
            this.modelo = modelo;
            this.ultima = ultima;
            this.interessado = interessado;
            this.tipo = tipo;
        }
    }

    private static Optional<ChaveDoResponsavel> chave(Map<String, ChaveDoResponsavel> chavePorCpf, String cpf) {
        return cpf == null ? Optional.empty() : Optional.ofNullable(chavePorCpf.get(cpf.strip()));
    }

    private static Optional<String> texto(String valor) {
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }
}
