package br.gov.mt.sema.rastro.integration.siga;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;

import br.gov.mt.sema.rastro.tramitacao.application.port.CaminhoPercorrido;
import br.gov.mt.sema.rastro.tramitacao.application.port.HistoricoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;
import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.AtividadeBruta;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.InstanciaDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.OrigemDaInstancia;
import br.gov.mt.sema.rastro.tramitacao.domain.VariavelBruta;
import org.springframework.beans.factory.annotation.Autowired;
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
class CamundaPosicaoJdbc implements PosicaoNoFluxo, IdentificacaoDosResponsaveis, CaminhoPercorrido,
        HistoricoDoLicenciamento {

    /** Complementação: instância independente, fora do catálogo (regra técnica 7). */
    static final String CHAVE_COMPLEMENTACAO = "procGestaoComplementacao";
    /** Leitura da posição (bloco 3) sem a identificação dos responsáveis. */
    static final int CONSULTAS_DA_POSICAO = 7;
    static final int CONSULTAS_NO_MAXIMO = CONSULTAS_DA_POSICAO + 1;
    /** Teto da lista em {@code IN} (o Oracle aceita até 1000). */
    static final int MAXIMO_DE_CPFS = 500;
    /** Caminho percorrido (S8, bloco 2): instâncias, filhas, complementações, atividades e 2 de variáveis. Máximo: 6. */
    static final int CONSULTAS_DO_CAMINHO = 6;
    /** Linhas de atividade lidas por instância no caminho percorrido (docs/15; {@code rastro.tramitacao.limite-de-linhas}). */
    static final int LIMITE_PADRAO_DE_LINHAS = 3000;
    /** Plano B (S8, bloco 5): eventos lidos de {@code HISTORICO_REQUERIMENTO} (o SQL pede um a mais). */
    static final int LIMITE_DE_EVENTOS = 500;
    static final int CONSULTAS_DO_PLANO_B = 2;

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
    private final String sqlHistoricoFilhas = SigaSql.carregar(PASTA + "historico-filhas");
    private final String sqlHistoricoComplementacoes = SigaSql.carregar(PASTA + "historico-complementacoes");
    private final String sqlHistoricoAtividades = SigaSql.carregar(PASTA + "historico-atividades");
    private final String sqlHistoricoVariaveisEpoca = SigaSql.carregar(PASTA + "historico-variaveis-epoca");
    private final String sqlHistoricoVariaveisFinais = SigaSql.carregar(PASTA + "historico-variaveis-finais");
    private final String sqlPlanoBEventos = SigaSql.carregar(PASTA + "plano-b-eventos");
    private final String sqlPlanoBRequerimento = SigaSql.carregar(PASTA + "plano-b-requerimento");
    private final int limiteDeLinhas;

    /**
     * S7, bloco 10: o tempo limite vem de {@code ParametrosDoRastro} (ambiente, sobreposto pela tela de Configurações) e é
     * lido a cada consulta: uma alteração vale na próxima leitura, sem reiniciar.
     */
    @Autowired
    CamundaPosicaoJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, ParametrosDoRastro parametros,
            @Value("${rastro.tramitacao.limite-de-linhas:" + LIMITE_PADRAO_DE_LINHAS + "}") int limiteDeLinhas) {
        this(sigaJdbcTemplate, (IntSupplier) parametros::camundaTimeoutSegundos, limiteDeLinhas);
    }

    /** Para teste: tempo limite fixo e limite de linhas padrão. */
    CamundaPosicaoJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, int timeoutSegundos) {
        this(sigaJdbcTemplate, timeoutSegundos, LIMITE_PADRAO_DE_LINHAS);
    }

    /** Para teste: tempo limite e limite de linhas fixos. */
    CamundaPosicaoJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, int timeoutSegundos, int limiteDeLinhas) {
        this(sigaJdbcTemplate, (IntSupplier) () -> timeoutSegundos, limiteDeLinhas);
    }

    private CamundaPosicaoJdbc(NamedParameterJdbcTemplate sigaJdbcTemplate, IntSupplier timeoutSegundos,
            int limiteDeLinhas) {
        this.limiteDeLinhas = Math.max(1, limiteDeLinhas);
        // Template próprio: o tempo limite vale só para a tramitação, sem mudar o das demais consultas do SIGA. O Spring
        // aplica getQueryTimeout() em cada Statement, então o valor é lido de novo a cada consulta.
        JdbcTemplate base = new JdbcTemplate(sigaJdbcTemplate.getJdbcTemplate().getDataSource()) {
            @Override
            public int getQueryTimeout() {
                return Math.max(1, timeoutSegundos.getAsInt());
            }
        };
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

    /** Instâncias raiz do requerimento no modelo, da mais recente para a mais antiga (consulta compartilhada). */
    private List<LeituraDoFluxo.InstanciaRaiz> instanciasRaiz(long idRequerimento, String chaveDoModelo) {
        return jdbc.query(sqlInstancias, new MapSqlParameterSource()
                        .addValue("idRequerimento", idRequerimento).addValue("chaveDoModelo", chaveDoModelo),
                (rs, n) -> new LeituraDoFluxo.InstanciaRaiz(rs.getString("ID"), DataHoraDoSiga.ler(rs, "INICIO"),
                        DataHoraDoSiga.ler(rs, "FIM"), rs.getString("ATIVIDADE_FINAL"), rs.getString("ESTADO"),
                        rs.getInt("COM_MOTIVO") == 1));
    }

    /**
     * S8, bloco 2: o caminho percorrido, cru. No máximo {@value #CONSULTAS_DO_CAMINHO} consultas por requerimento, sem laço
     * por linha: raiz (1), filhas (1), complementações (1), atividades de <b>todas</b> as instâncias numa só consulta com
     * limite por instância (1) e as variáveis da lista branca (2: valor da época e valor final). Sem raiz, 1 consulta.
     * Atividades de uma instância além do limite ficam de fora e a instância sai marcada como truncada.
     */
    @Override
    public CaminhoBruto lerCaminho(long idRequerimento, String chaveDoModelo) {
        List<LeituraDoFluxo.InstanciaRaiz> raizes = instanciasRaiz(idRequerimento, chaveDoModelo);
        if (raizes.isEmpty()) {
            return CaminhoBruto.semInstancia();
        }
        LeituraDoFluxo.InstanciaRaiz raiz = raizes.getFirst();
        List<InstanciaDoHistorico> instancias = new ArrayList<>();
        instancias.add(new InstanciaDoHistorico(raiz.id(), OrigemDaInstancia.RAIZ, chaveDoModelo, raiz.inicio(),
                raiz.fim(), raiz.atividadeFinal(), raiz.estado(), null, false, 0));
        instancias.addAll(jdbc.query(sqlHistoricoFilhas, new MapSqlParameterSource("instancia", raiz.id()),
                (rs, n) -> new InstanciaDoHistorico(rs.getString("ID"), OrigemDaInstancia.FILHA, rs.getString("CHAVE"),
                        DataHoraDoSiga.ler(rs, "INICIO"), DataHoraDoSiga.ler(rs, "FIM"), rs.getString("ATIVIDADE_FINAL"),
                        rs.getString("ESTADO"), rs.getString("MAE"), false, 0)));
        instancias.addAll(jdbc.query(sqlHistoricoComplementacoes, new MapSqlParameterSource()
                        .addValue("idRequerimento", idRequerimento).addValue("chaveDaComplementacao", CHAVE_COMPLEMENTACAO),
                (rs, n) -> new InstanciaDoHistorico(rs.getString("ID"), OrigemDaInstancia.COMPLEMENTACAO,
                        rs.getString("CHAVE"), DataHoraDoSiga.ler(rs, "INICIO"), DataHoraDoSiga.ler(rs, "FIM"),
                        rs.getString("ATIVIDADE_FINAL"), rs.getString("ESTADO"), null, false, 0)));

        List<String> ids = instancias.stream().map(InstanciaDoHistorico::id).distinct().toList();
        Map<String, OrigemDaInstancia> origens = new HashMap<>();
        instancias.forEach(i -> origens.putIfAbsent(i.id(), i.origem()));

        List<AtividadeBruta> atividades = new ArrayList<>();
        Map<String, Integer> lidas = new HashMap<>();
        Set<String> truncadas = new HashSet<>();
        jdbc.query(sqlHistoricoAtividades, new MapSqlParameterSource().addValue("instancias", ids)
                .addValue("limiteMaisUm", limiteDeLinhas + 1), rs -> {
                    String instancia = rs.getString("INSTANCIA");
                    if (rs.getInt("POSICAO") > limiteDeLinhas) {
                        truncadas.add(instancia);
                        return;
                    }
                    lidas.merge(instancia, 1, Integer::sum);
                    atividades.add(atividade(rs, instancia, origens.get(instancia)));
                });
        List<InstanciaDoHistorico> lidasPorInstancia = instancias.stream()
                .map(i -> i.comLeitura(truncadas.contains(i.id()), lidas.getOrDefault(i.id(), 0))).toList();

        MapSqlParameterSource variaveis = new MapSqlParameterSource().addValue("instancias", ids)
                .addValue("nomes", List.copyOf(VariavelBruta.PERMITIDAS));
        List<VariavelBruta> daEpoca = jdbc.query(sqlHistoricoVariaveisEpoca, variaveis, (rs, n) -> variavel(rs));
        List<VariavelBruta> finais = jdbc.query(sqlHistoricoVariaveisFinais, variaveis, (rs, n) -> variavel(rs));
        return new CaminhoBruto(lidasPorInstancia, raizes.size(), atividades, daEpoca, finais);
    }

    /**
     * S8, bloco 5: plano B, 2 consultas (datas e situação; eventos até {@value #LIMITE_DE_EVENTOS}). {@code CPF_USUARIO}
     * nunca é lido.
     */
    @Override
    public LeituraDoPlanoB lerPlanoB(long idRequerimento) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("idRequerimento", idRequerimento);
        record DoRequerimento(Optional<String> situacao, LeituraDoPlanoB.Datas datas) {}
        DoRequerimento requerimento = jdbc.query(sqlPlanoBRequerimento, parametros,
                (rs, n) -> new DoRequerimento(Optional.ofNullable(rs.getString("SITUACAO")),
                        new LeituraDoPlanoB.Datas(DataHoraDoSiga.ler(rs, "ABERTURA"), DataHoraDoSiga.ler(rs, "PROTOCOLO"),
                                DataHoraDoSiga.ler(rs, "CANCELAMENTO"), DataHoraDoSiga.ler(rs, "EXPIRACAO_REQUERIMENTO"),
                                DataHoraDoSiga.ler(rs, "EXPIRACAO_PROCESSO"))))
                .stream().findFirst().orElse(new DoRequerimento(Optional.empty(), LeituraDoPlanoB.Datas.NENHUMA));
        List<LeituraDoPlanoB.Evento> eventos = new ArrayList<>(jdbc.query(sqlPlanoBEventos, parametros,
                (rs, n) -> new LeituraDoPlanoB.Evento(rs.getString("EVENTO"), DataHoraDoSiga.ler(rs, "QUANDO"))));
        boolean truncados = eventos.size() > LIMITE_DE_EVENTOS;
        if (truncados) {
            eventos = eventos.subList(0, LIMITE_DE_EVENTOS);
        }
        return new LeituraDoPlanoB(requerimento.situacao(), requerimento.datas(), eventos, truncados);
    }

    private static AtividadeBruta atividade(ResultSet rs, String instancia, OrigemDaInstancia origem)
            throws SQLException {
        String motivo = rs.getString("TAREFA_MOTIVO");
        Optional<AtividadeBruta.TarefaBruta> tarefa = motivo == null ? Optional.empty()
                : Optional.of(new AtividadeBruta.TarefaBruta(rs.getString("TAREFA_DEFINICAO"),
                        IdentificadorDoResponsavel.de(rs.getString("TAREFA_RESPONSAVEL")),
                        DataHoraDoSiga.ler(rs, "TAREFA_INICIO"), DataHoraDoSiga.ler(rs, "TAREFA_FIM"),
                        AtividadeBruta.MotivoDaTarefa.valueOf(motivo)));
        return new AtividadeBruta(instancia, origem, rs.getString("ID"), rs.getString("ID_PAI"),
                rs.getString("EXECUCAO"), rs.getString("ATIVIDADE"), rs.getString("NOME"), rs.getString("TIPO"),
                DataHoraDoSiga.ler(rs, "INICIO"), DataHoraDoSiga.ler(rs, "FIM"),
                IdentificadorDoResponsavel.de(rs.getString("RESPONSAVEL")), rs.getString("CHAMADA"), tarefa,
                Math.max(1, rs.getInt("REPRESENTA")));
    }

    private static VariavelBruta variavel(ResultSet rs) throws SQLException {
        return new VariavelBruta(rs.getString("INSTANCIA"), rs.getString("NOME"), rs.getString("TIPO"),
                rs.getString("TEXTO"), rs.getObject("NUMERO", Long.class), DataHoraDoSiga.ler(rs, "QUANDO"));
    }

    @Override
    public LeituraDoFluxo ler(long idRequerimento, String chaveDoModelo) {
        List<LeituraDoFluxo.InstanciaRaiz> instancias = instanciasRaiz(idRequerimento, chaveDoModelo);
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
