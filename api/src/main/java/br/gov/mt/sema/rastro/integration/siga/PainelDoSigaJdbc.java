package br.gov.mt.sema.rastro.integration.siga;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.painel.application.port.ResumoDoAnoNoSiga;
import br.gov.mt.sema.rastro.painel.application.port.TarefasAtivasEmLote;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.ContagemDoMes;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.RequerimentoDaBase;
import br.gov.mt.sema.rastro.painel.domain.ResponsavelDaTarefa;
import br.gov.mt.sema.rastro.painel.domain.TarefaAtiva;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Único componente que lê o SIGA e o Camunda para o painel do Gestor (S10, bloco 2; risco R04). Três consultas, todas
 * {@code SELECT} com parâmetros nomeados, {@code FETCH FIRST} e tempo limite por consulta (o que o chamador ainda tem do
 * tempo total da atualização), em {@code sql/siga/painel/}:
 * <ol>
 *   <li>{@code protocolados-e-concluidos-por-mes}: o gráfico 1, agregado no banco (1 consulta, independe do teto);</li>
 *   <li>{@code requerimentos-da-base}: ids, tipo e situação do ano, até o teto + 1 (1 consulta);</li>
 *   <li>{@code tarefas-ativas-em-lote}: tarefas ativas das raízes de até {@value TarefasAtivasEmLote#MAXIMO_POR_LOTE}
 *       requerimentos (1 consulta por lote).</li>
 * </ol>
 * Nenhum laço por linha. O CPF do responsável fica dentro do SQL (resolução do nome) e nunca é selecionado; o id técnico
 * da tarefa só serve para agrupar os grupos candidatos e não sai daqui. Não interpreta nada: devolve os fatos crus; quem
 * conta é o domínio do painel.
 */
@Repository
@ConditionalOnExpression(SigaCondicoes.CONFIGURADO)
class PainelDoSigaJdbc implements ResumoDoAnoNoSiga, TarefasAtivasEmLote {

    /** Chave da definição da complementação: instância raiz independente, nunca tarefa do painel. */
    static final String CHAVE_COMPLEMENTACAO = CamundaPosicaoJdbc.CHAVE_COMPLEMENTACAO;
    /** Teto de linhas da série (12 meses de protocolo, os meses de conclusão e a linha "sem data"); passou, falha. */
    static final int MAXIMO_DE_LINHAS_DA_SERIE = 500;
    /** Teto de linhas por requerimento do lote (tarefas x grupos candidatos); passou, o lote vem marcado. */
    static final int LINHAS_POR_REQUERIMENTO = 20;
    /** Teto do parâmetro de requerimentos (o máximo da faixa de {@code rastro.painelLimiteRequerimentos}). */
    static final int MAXIMO_DE_REQUERIMENTOS = 20_000;

    private static final String PASTA = "painel/";

    private final NamedParameterJdbcTemplate sigaJdbcTemplate;
    private final String sqlSerie = SigaSql.carregar(PASTA + "protocolados-e-concluidos-por-mes");
    private final String sqlBase = SigaSql.carregar(PASTA + "requerimentos-da-base");
    private final String sqlTarefas = SigaSql.carregar(PASTA + "tarefas-ativas-em-lote");

    PainelDoSigaJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate) {
        this.sigaJdbcTemplate = sigaJdbcTemplate;
    }

    /**
     * Template com o tempo limite desta consulta: o Spring aplica {@code getQueryTimeout()} em cada {@code Statement}. É
     * leve (só embrulha o mesmo {@code DataSource}) e não muda o tempo limite das outras consultas do SIGA.
     */
    private NamedParameterJdbcTemplate com(Duration tempoLimite) {
        int segundos = segundos(tempoLimite);
        JdbcTemplate base = new JdbcTemplate(sigaJdbcTemplate.getJdbcTemplate().getDataSource()) {
            @Override
            public int getQueryTimeout() {
                return segundos;
            }
        };
        return new NamedParameterJdbcTemplate(base);
    }

    /** Arredonda para cima, no mínimo 1 s (0 no JDBC seria "sem limite"). */
    static int segundos(Duration tempoLimite) {
        long milis = Math.max(0, tempoLimite.toMillis());
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (milis + 999) / 1000));
    }

    private static MapSqlParameterSource doAno(AnoDoPainel ano) {
        return new MapSqlParameterSource().addValue("inicio", ano.inicio()).addValue("fim", ano.fimExclusivo());
    }

    @Override
    public LeituraDaSerie lerSerie(AnoDoPainel ano, Duration tempoLimite) {
        List<ContagemDoMes> protocolados = new ArrayList<>();
        List<ContagemDoMes> concluidos = new ArrayList<>();
        long[] semData = {0};
        int[] linhas = {0};
        com(tempoLimite).query(sqlSerie, doAno(ano).addValue("maximoDeLinhas", MAXIMO_DE_LINHAS_DA_SERIE + 1), rs -> {
            linhas[0]++;
            String medida = rs.getString("MEDIDA");
            int anoDoMes = rs.getInt("ANO");
            boolean semMes = rs.wasNull();
            int mes = rs.getInt("MES");
            semMes |= rs.wasNull();
            long total = rs.getLong("TOTAL");
            YearMonth quando = semMes ? null : YearMonth.of(anoDoMes, mes);
            switch (medida.strip()) {
                case "PROTOCOLADOS" -> protocolados.add(ContagemDoMes.de(quando, total));
                case "CONCLUIDOS" -> concluidos.add(ContagemDoMes.de(quando, total));
                case "SEM_DATA_DE_PROTOCOLO" -> semData[0] += total;
                default -> throw new IllegalStateException("Medida desconhecida na série do painel");
            }
        });
        if (linhas[0] > MAXIMO_DE_LINHAS_DA_SERIE) {
            // Número errado é pior que ausente: série incompleta não vira gráfico.
            throw new IllegalStateException("Série do painel com linhas demais");
        }
        return new LeituraDaSerie(protocolados, concluidos, semData[0]);
    }

    @Override
    public BaseDoAno lerBase(AnoDoPainel ano, int limite, Duration tempoLimite) {
        int teto = Math.max(1, Math.min(MAXIMO_DE_REQUERIMENTOS, limite));
        List<RequerimentoDaBase> lidos = com(tempoLimite).query(sqlBase,
                doAno(ano).addValue("limiteMaisUm", teto + 1),
                (rs, n) -> new RequerimentoDaBase(rs.getLong("ID"), texto(rs.getString("TIPO")),
                        texto(rs.getString("SITUACAO"))));
        boolean truncado = lidos.size() > teto;
        return new BaseDoAno(truncado ? lidos.subList(0, teto) : lidos, truncado);
    }

    @Override
    public LeituraDasTarefas ler(List<Long> idsRequerimento, Collection<String> chavesDosModelos, Duration tempoLimite) {
        List<Long> ids = List.copyOf(new LinkedHashSet<>(idsRequerimento));
        if (ids.size() > MAXIMO_POR_LOTE) {
            throw new IllegalArgumentException("lote do painel acima de " + MAXIMO_POR_LOTE + " requerimentos");
        }
        List<String> modelos = chavesDosModelos.stream().filter(c -> c != null && !c.isBlank())
                .filter(c -> !c.equals(CHAVE_COMPLEMENTACAO)).distinct().toList();
        if (ids.isEmpty() || modelos.isEmpty()) {
            return LeituraDasTarefas.NENHUMA;
        }
        int maximoDeLinhas = ids.size() * LINHAS_POR_REQUERIMENTO;
        record Linha(long requerimento, String modelo, String atividade, String responsavel, String nome, String grupo) {}
        Map<String, List<Linha>> porTarefa = new LinkedHashMap<>();
        Set<Long> comInstancia = new LinkedHashSet<>();
        int[] linhas = {0};
        com(tempoLimite).query(sqlTarefas, new MapSqlParameterSource()
                .addValue("ids", ids)
                .addValue("chaves", ids.stream().map(String::valueOf).toList())
                .addValue("modelos", modelos)
                .addValue("chaveDaComplementacao", CHAVE_COMPLEMENTACAO)
                .addValue("maximoDeLinhas", maximoDeLinhas + 1), rs -> {
                    if (++linhas[0] > maximoDeLinhas) {
                        return;
                    }
                    long requerimento = rs.getLong("REQUERIMENTO");
                    comInstancia.add(requerimento);
                    String tarefa = rs.getString("TAREFA");
                    if (tarefa == null) {
                        return;
                    }
                    porTarefa.computeIfAbsent(tarefa, k -> new ArrayList<>()).add(new Linha(requerimento,
                            rs.getString("MODELO"), rs.getString("ATIVIDADE"), rs.getString("RESPONSAVEL"),
                            rs.getString("NOME_RESPONSAVEL"), rs.getString("GRUPO")));
                });
        List<TarefaAtiva> tarefas = porTarefa.values().stream().map(grupo -> {
            Linha primeira = grupo.getFirst();
            List<String> grupos = grupo.stream().map(Linha::grupo).filter(g -> g != null && !g.isBlank()).distinct()
                    .toList();
            ResponsavelDaTarefa responsavel = responsavel(primeira.responsavel());
            return new TarefaAtiva(primeira.requerimento(), primeira.modelo(),
                    primeira.atividade() == null ? "" : primeira.atividade(), responsavel,
                    texto(primeira.nome()), grupos);
        }).toList();
        return new LeituraDasTarefas(tarefas, comInstancia, linhas[0] > maximoDeLinhas);
    }

    private static ResponsavelDaTarefa responsavel(String valor) {
        if (valor == null) {
            return ResponsavelDaTarefa.NAO_IDENTIFICADO;
        }
        return switch (valor.strip()) {
            case "NAO_ATRIBUIDA" -> ResponsavelDaTarefa.NAO_ATRIBUIDA;
            case "INTERESSADO" -> ResponsavelDaTarefa.INTERESSADO;
            case "SERVIDOR" -> ResponsavelDaTarefa.SERVIDOR;
            default -> ResponsavelDaTarefa.NAO_IDENTIFICADO;
        };
    }

    private static Optional<String> texto(String valor) {
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }
}
