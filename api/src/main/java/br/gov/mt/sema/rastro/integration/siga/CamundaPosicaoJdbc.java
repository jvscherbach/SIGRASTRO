package br.gov.mt.sema.rastro.integration.siga;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Único componente que lê o Camunda para a posição atual (S7, bloco 3; risco R04). Só {@code SELECT}, com parâmetros
 * nomeados, {@code FETCH FIRST n ROWS ONLY} e tempo limite por consulta ({@code RASTRO_CAMUNDA_TIMEOUT_SEGUNDOS}).
 *
 * <p>Número de consultas por requerimento, fixo e sem laço por linha: 1 ({@code requerimento}) + 1 ({@code instancias})
 * quando não há instância ou ela está encerrada; mais 5 quando está ativa ({@code execucoes}, {@code filhas},
 * {@code tarefas}, {@code complementacoes}, {@code percorridas}), e mais 1 ({@code responsaveis}, bloco 4) quando há
 * {@code ASSIGNEE_} com cara de CPF. <b>Máximo: 8.</b>
 *
 * <p>Não interpreta nada: devolve os fatos crus; quem resolve etapa, principal, paralelos e responsável é o domínio. O
 * {@code ASSIGNEE_} (CPF) sai daqui só protegido ({@link IdentificadorDoResponsavel}).
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class CamundaPosicaoJdbc implements PosicaoNoFluxo, IdentificacaoDosResponsaveis {

    /** Complementação: instância independente, fora do catálogo (regra técnica 7). */
    static final String CHAVE_COMPLEMENTACAO = "procGestaoComplementacao";
    /** Leitura da posição (bloco 3) sem a identificação dos responsáveis. */
    static final int CONSULTAS_DA_POSICAO = 7;
    static final int CONSULTAS_NO_MAXIMO = CONSULTAS_DA_POSICAO + 1;
    /** Teto da lista em {@code IN} (o Oracle aceita até 1000). */
    static final int MAXIMO_DE_CPFS = 500;

    private static final String PASTA = "tramitacao/";

    private final NamedParameterJdbcTemplate jdbc;
    private final String sqlRequerimento = SigaSql.carregar(PASTA + "requerimento");
    private final String sqlInstancias = SigaSql.carregar(PASTA + "instancias");
    private final String sqlExecucoes = SigaSql.carregar(PASTA + "execucoes");
    private final String sqlFilhas = SigaSql.carregar(PASTA + "filhas");
    private final String sqlTarefas = SigaSql.carregar(PASTA + "tarefas");
    private final String sqlComplementacoes = SigaSql.carregar(PASTA + "complementacoes");
    private final String sqlPercorridas = SigaSql.carregar(PASTA + "percorridas");
    private final String sqlResponsaveis = SigaSql.carregar(PASTA + "responsaveis");

    CamundaPosicaoJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate,
            @Value("${rastro.tramitacao.camunda-timeout-segundos:10}") int timeoutSegundos) {
        // Template próprio: o tempo limite vale só para a tramitação, sem mudar o das demais consultas do SIGA.
        JdbcTemplate base = new JdbcTemplate(sigaJdbcTemplate.getJdbcTemplate().getDataSource());
        base.setQueryTimeout(Math.max(1, timeoutSegundos));
        this.jdbc = new NamedParameterJdbcTemplate(base);
    }

    /**
     * Bloco 4: uma consulta para todos os responsáveis do requerimento. O CPF vai só como parâmetro e volta só para casar
     * com o identificador que já se tinha; a resposta é indexada pelo próprio identificador recebido.
     */
    @Override
    public Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificar(long idRequerimento,
            Collection<IdentificadorDoResponsavel> responsaveis) {
        Map<String, IdentificadorDoResponsavel> porValor = new LinkedHashMap<>();
        responsaveis.stream().limit(MAXIMO_DE_CPFS).forEach(r -> porValor.putIfAbsent(r.valorProtegido(), r));
        if (porValor.isEmpty()) {
            return Map.of();
        }
        Map<IdentificadorDoResponsavel, String> nomes = new HashMap<>();
        Map<IdentificadorDoResponsavel, Boolean> interessado = new HashMap<>();
        jdbc.query(sqlResponsaveis, new MapSqlParameterSource()
                .addValue("idRequerimento", idRequerimento).addValue("cpfs", List.copyOf(porValor.keySet())), rs -> {
                    IdentificadorDoResponsavel id = porValor.get(rs.getString("CPF"));
                    if (id == null) {
                        return;
                    }
                    if (rs.getInt("INTERESSADO") == 1) {
                        interessado.put(id, Boolean.TRUE);
                    } else if (rs.getString("NOME") != null) {
                        nomes.put(id, rs.getString("NOME"));
                    }
                });
        Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> resultado = new HashMap<>();
        porValor.values().forEach(id -> {
            if (nomes.containsKey(id) || interessado.containsKey(id)) {
                resultado.put(id, new IdentificacaoDoResponsavel(Optional.ofNullable(nomes.get(id)),
                        interessado.getOrDefault(id, false)));
            }
        });
        return resultado;
    }

    @Override
    public Optional<RequerimentoNoFluxo> requerimento(long idRequerimento) {
        return jdbc.query(sqlRequerimento, new MapSqlParameterSource("idRequerimento", idRequerimento),
                (rs, n) -> new RequerimentoNoFluxo(rs.getString("INSTANCIA"), rs.getString("SITUACAO")))
                .stream().findFirst();
    }

    @Override
    public LeituraDoFluxo ler(long idRequerimento, String chaveDoModelo) {
        List<LeituraDoFluxo.InstanciaRaiz> instancias = jdbc.query(sqlInstancias, new MapSqlParameterSource()
                        .addValue("idRequerimento", idRequerimento).addValue("chaveDoModelo", chaveDoModelo),
                (rs, n) -> new LeituraDoFluxo.InstanciaRaiz(rs.getString("ID"), DataHoraDoSiga.ler(rs, "INICIO"),
                        DataHoraDoSiga.ler(rs, "FIM"), rs.getString("ATIVIDADE_FINAL"), rs.getString("ESTADO"),
                        rs.getInt("COM_MOTIVO") == 1));
        if (instancias.isEmpty()) {
            return LeituraDoFluxo.semInstancia();
        }
        LeituraDoFluxo.InstanciaRaiz raiz = instancias.getFirst();
        if (raiz.encerrada()) {
            return LeituraDoFluxo.encerrada(instancias);
        }
        MapSqlParameterSource daRaiz = new MapSqlParameterSource("instancia", raiz.id());

        List<LeituraDoFluxo.Execucao> execucoes = jdbc.query(sqlExecucoes, daRaiz,
                (rs, n) -> new LeituraDoFluxo.Execucao(rs.getString("ATIVIDADE"), DataHoraDoSiga.ler(rs, "DESDE"),
                        DataHoraDoSiga.ler(rs, "ASSINADA_EM")));
        List<LeituraDoFluxo.Filha> filhas = jdbc.query(sqlFilhas, daRaiz,
                (rs, n) -> new LeituraDoFluxo.Filha(rs.getString("ID"), rs.getString("CHAVE"),
                        DataHoraDoSiga.ler(rs, "INICIO")));
        List<String> instanciasComTarefa = new ArrayList<>();
        instanciasComTarefa.add(raiz.id());
        filhas.forEach(f -> instanciasComTarefa.add(f.id()));
        List<LeituraDoFluxo.Tarefa> tarefas = tarefas(instanciasComTarefa);
        List<LeituraDoFluxo.Complementacao> complementacoes = jdbc.query(sqlComplementacoes, new MapSqlParameterSource()
                        .addValue("idRequerimento", idRequerimento).addValue("chaveDaComplementacao", CHAVE_COMPLEMENTACAO),
                (rs, n) -> new LeituraDoFluxo.Complementacao(rs.getString("ID"), DataHoraDoSiga.ler(rs, "INICIO")));
        List<LeituraDoFluxo.Percorrida> percorridas = jdbc.query(sqlPercorridas, daRaiz,
                (rs, n) -> new LeituraDoFluxo.Percorrida(rs.getString("ATIVIDADE"), DataHoraDoSiga.ler(rs, "INICIO"),
                        DataHoraDoSiga.ler(rs, "FIM"), rs.getInt("TAREFA") == 1,
                        IdentificadorDoResponsavel.de(rs.getString("RESPONSAVEL"))));
        return new LeituraDoFluxo(instancias, execucoes, tarefas, filhas, complementacoes, percorridas);
    }

    /** Uma consulta para todas as instâncias (raiz e filhas); uma linha por tarefa e grupo, agrupada aqui. */
    private List<LeituraDoFluxo.Tarefa> tarefas(List<String> instancias) {
        record Linha(String id, String instancia, String atividade, String responsavel, java.time.LocalDateTime criadaEm,
                String grupo) {}
        List<Linha> linhas = jdbc.query(sqlTarefas, new MapSqlParameterSource("instancias", instancias),
                (rs, n) -> new Linha(rs.getString("ID"), rs.getString("INSTANCIA"), rs.getString("ATIVIDADE"),
                        rs.getString("RESPONSAVEL"), DataHoraDoSiga.ler(rs, "CRIADA_EM"), rs.getString("GRUPO")));
        Map<String, List<Linha>> porTarefa = new LinkedHashMap<>();
        linhas.forEach(l -> porTarefa.computeIfAbsent(l.id(), k -> new ArrayList<>()).add(l));
        return porTarefa.values().stream().map(grupo -> {
            Linha primeira = grupo.getFirst();
            List<String> grupos = grupo.stream().map(Linha::grupo).filter(g -> g != null && !g.isBlank())
                    .distinct().toList();
            return new LeituraDoFluxo.Tarefa(primeira.instancia(), primeira.atividade(), primeira.criadaEm(),
                    IdentificadorDoResponsavel.de(primeira.responsavel()), grupos);
        }).toList();
    }
}
