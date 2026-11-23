package br.gov.mt.sema.rastro.integration.siga;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.IdIgnorado;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PapelDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TipoDeEtapa;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Constrói requerimentos FICTÍCIOS com a instância do Camunda coerente, no recorte do SIGA dos testes (Oracle 23 Free por
 * Testcontainers, {@link SigaOracleTeste}). Só código de teste; reutilizável na S8 e na S9.
 *
 * <p>Os ids técnicos são conferidos contra o catálogo de etapas real ({@link CatalogoDeEtapas}, {@code rastro/etapas/*.yml}):
 * um id fora do catálogo, um final que não é final ou um cenário incoerente (ex.: isenção com passagem pelo gateway de
 * pagamento) é recusado na hora, antes de gravar. Assim as fixtures nunca repetem texto nem divergem do catálogo.
 *
 * <p>Grava {@code REQUERIMENTO} e, se houver instância: {@code ACT_RE_PROCDEF}, {@code ACT_HI_PROCINST} (sempre, ativa ou
 * encerrada), e para a ativa {@code ACT_RU_EXECUTION}, {@code ACT_RU_TASK}, {@code ACT_RU_IDENTITYLINK},
 * {@code ACT_RU_EVENT_SUBSCR}, com {@code ACT_HI_ACTINST}/{@code ACT_HI_TASKINST} do que está aberto e do percurso dado em
 * {@link Processo#percorrendo}.
 *
 * <p><b>Caminho histórico (S8, bloco 1).</b> {@link Processo#passou}, {@link Processo#passouPelaEspera},
 * {@link Processo#voltouPara}, {@link Processo#passouEmCiclo}, {@link Processo#passouPelaEmissao} e os métodos de ruído
 * gravam o caminho percorrido com início e fim reais em {@code ACT_HI_ACTINST}, {@code ACT_HI_TASKINST},
 * {@code ACT_HI_PROCINST} (também as filhas e a complementação), {@code ACT_HI_DETAIL} e {@code ACT_HI_VARINST} (só a lista
 * branca de variáveis, {@link #VARIAVEIS_PERMITIDAS}, nunca dado pessoal). É alternativa a {@code percorrendo}: um cenário
 * usa um ou outro. A etapa ativa (ou o final) vem depois, por {@code naEtapa}/{@code comFilhas}/{@code encerradoEm}, e
 * {@code desde} é o início da etapa ativa ou o fim da instância. O plano B (requerimento sem instância) usa
 * {@link Processo#noHistoricoDoLicenciamento} e {@link Processo#comDatas}.
 *
 * <pre>{@code
 * camunda.processo(Fluxo.HIDRICO, 7007).naEtapa("taskAnalisarRequerimento").comAnalistas(3).desde(data).gravar();
 * camunda.processo(Fluxo.HIDRICO, 8001)
 *         .passou("taskCadastrarRequerimento", dias(0), dias(1))
 *         .passouPelaEspera("gtwAguardarPagamento", dias(2), dias(4))
 *         .naEtapa("taskAtribuirRequerimento").desde(dias(5)).gravar();
 * }</pre>
 *
 * Dados pessoais: CPFs inválidos de propósito ({@code 000000000NN}, NN diferente de 00) e nomes evidentes
 * ("Servidor Exemplo 01"), criados em {@code sql/siga/06-camunda-s7.sql}.
 */
public final class CamundaFicticioBuilder {

    /** Fluxo do requerimento: chave de definição do Camunda e tipo de processo fictício de {@code 06-camunda-s7.sql}. */
    public enum Fluxo {
        HIDRICO("procOutorgaAguaSupSub", 71),
        LAC("procLicenciamentoLac", 72),
        /** Fora do catálogo: o requerimento não tem instância e a resposta é "fluxo não suportado". */
        TRIFASICO(null, 73);

        private final String chave;
        private final long idTipoProcesso;

        Fluxo(String chave, long idTipoProcesso) {
            this.chave = chave;
            this.idTipoProcesso = idTipoProcesso;
        }

        public String chave() {
            return chave;
        }
    }

    /** Complementação: instância independente, fora do catálogo de propósito (regra técnica 7). */
    public static final String CHAVE_COMPLEMENTACAO = "procGestaoComplementacao";
    public static final long ID_INTERESSADO = 7000;
    public static final String CPF_DO_INTERESSADO = "00000000091";
    /** CPF fictício sem linha em {@code SERVIDOR}: "Responsável não identificado" (bloco 4). */
    public static final String CPF_SEM_SERVIDOR = "00000000099";
    public static final int SERVIDORES = 12;
    public static final String GRUPO_DESCONHECIDO = "grpFicticioDesconhecido";
    public static final String GRUPO_ZERO = "0";
    public static final String GRUPO_SUPORTE = "suporte";
    public static final String GRUPO_EMISSOR = "GrpProcSurhEmissorTitulos";
    /**
     * Ids de atividade dentro das filhas, da complementação e da assinatura de evento do gateway: NÃO CONFIRMADOS (não há
     * BPMN em docs/referencias). São fictícios e evidentes; o Rastro identifica filha e complementação pela chave da
     * definição, não por estes ids.
     */
    public static final String TAREFA_DA_FILHA = "taskFicticiaDaFilha";
    public static final String TAREFA_DA_COMPLEMENTACAO = "taskFicticiaDaComplementacao";
    public static final String EVENTO_DE_PAGAMENTO = "evtFicticioPagamentoConfirmado";
    public static final LocalDateTime DESDE_PADRAO = LocalDateTime.of(2026, 3, 2, 8, 0);

    /**
     * Variáveis do Camunda que o Rastro pode ler do histórico (lista branca do prompt da S8, bloco 3): nenhuma guarda dado
     * pessoal. O construtor recusa qualquer outro nome, e valor com cara de CPF.
     */
    public static final Set<String> VARIAVEIS_PERMITIDAS = Set.of("decisaoValidacaoParecerTecnico", "revisarRequerimento",
            "decisaoVerificarRevisao", "isRevisaoAnalise", "isRevisaoParecer", "statusConferencia");

    /**
     * Ruído sem id técnico levantado ("Report Kafka" e eventos de repetição por erro de API: NÃO CONFIRMADO, docs/15). Os
     * ids são fictícios e evidentes; o Rastro os trata como "não mapeados", como faria com os reais.
     */
    public static final String REPORT_KAFKA_FICTICIO = "Activity_FicticioReportKafka";
    public static final String REPETICAO_POR_ERRO_FICTICIA = "Event_FicticioRepeticaoPorErroDeApi";
    public static final String CORRECAO_EXPIRADA_FICTICIA = "Event_FicticioCorrecaoExpirada";

    private static final Pattern ONZE_DIGITOS = Pattern.compile("(?<![0-9])[0-9]{11}(?![0-9])");

    /** Prefixo obrigatório do número de DAR fictício (S9): o construtor recusa qualquer outro, para nunca gravar um real. */
    public static final String PREFIXO_DO_DAR_FICTICIO = "FICT-";

    /** {@code LANCAMENTO_TAXA.STATUS} (enum de guia). Um valor fora destes é gravado por {@link Processo.Lancamento#comStatusBruto}. */
    public enum StatusDaGuia {
        AGUARDANDO_EMISSAO, AGUARDANDO_PAGAMENTO, PAGA, VENCIDA, ISENTA, CANCELADA
    }

    private static final String SQL_ATIVIDADE = """
            INSERT INTO SIGA2_CAMUNDA.ACT_HI_ACTINST
                   (ID_, PROC_DEF_KEY_, PROC_DEF_ID_, ROOT_PROC_INST_ID_, PROC_INST_ID_, EXECUTION_ID_, ACT_ID_, TASK_ID_,
                    CALL_PROC_INST_ID_, ACT_NAME_, ACT_TYPE_, ASSIGNEE_, START_TIME_, END_TIME_, SEQUENCE_COUNTER_)
            VALUES (:id, :chave, :procDef, :raiz, :proc, :exec, :atividade, :tarefa, :chamada, :nome, :tipo, :assignee,
                    :inicio, :fim, :seq)""";

    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogoDeEtapas catalogo;
    private final AtomicLong sequencia = new AtomicLong();

    /**
     * @param jdbc     conexão com permissão de escrita no recorte (dono ou {@code system}), nunca o usuário de leitura
     * @param catalogo catálogo real ({@code CatalogoDeEtapasDeTeste.padrao()})
     */
    public CamundaFicticioBuilder(NamedParameterJdbcTemplate jdbc, CatalogoDeEtapas catalogo) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    /** CPF fictício do "Servidor Exemplo NN" (1 a {@value #SERVIDORES}). */
    public static String cpfDoServidor(int numero) {
        if (numero < 1 || numero > SERVIDORES) {
            throw new IllegalArgumentException("servidor fictício vai de 1 a " + SERVIDORES);
        }
        return String.format("%011d", numero);
    }

    /** Instante {@code n} dias depois de {@link #DESDE_PADRAO} (2026-03-02 08:00), para escrever caminhos legíveis. */
    public static LocalDateTime dias(int n) {
        return DESDE_PADRAO.plusDays(n);
    }

    /** Instante {@code n} dias e {@code horas} horas depois de {@link #DESDE_PADRAO}. */
    public static LocalDateTime dias(int n, int horas) {
        return DESDE_PADRAO.plusDays(n).plusHours(horas);
    }

    public Processo processo(Fluxo fluxo, long idRequerimento) {
        return new Processo(fluxo, idRequerimento);
    }

    // --- caminho histórico (S8): passos guardados até o gravar()

    private sealed interface Passo permits Simples, Ciclo, Emissao, Repeticao {
        LocalDateTime inicio();

        LocalDateTime fim();
    }

    /** Tarefa, espera ou automática com início e fim: uma linha em ACT_HI_ACTINST (e em ACT_HI_TASKINST se for tarefa). */
    private record Simples(String id, String tipo, LocalDateTime inicio, LocalDateTime fim, String cpf, String motivo)
            implements Passo {
    }

    /** Uma tarefa de ciclo multi-instância: {@code motivo} é o DELETE_REASON_ ({@code completed} ou {@code deleted}). */
    private record Tarefa(String cpf, String motivo) {
    }

    private record Ciclo(String id, LocalDateTime inicio, LocalDateTime fim, List<Tarefa> tarefas) implements Passo {
    }

    private record Filha(String chave, boolean devolvida) {
    }

    private record Emissao(LocalDateTime inicio, LocalDateTime fim, List<Filha> filhas) implements Passo {
    }

    /** O mesmo script automático várias vezes seguidas (erro de API repetido). */
    private record Repeticao(String id, int vezes, LocalDateTime inicio, LocalDateTime fim, long intervaloSegundos)
            implements Passo {
    }

    private record Variavel(String nome, String valor, LocalDateTime quando) {
    }

    private record Evento(String evento, LocalDateTime quando) {
    }

    /** Tarefa ativa a mais na instância raiz (S10): paralela de outro ramo do catálogo ou id não mapeado. */
    private record TarefaExtra(String id, String nome, String cpf) {
    }

    private record ComplementacaoConcluida(long id, LocalDateTime inicio, LocalDateTime fim) {
    }

    /** O que foi gravado, para o teste conferir contra o banco. */
    public record Resultado(
            long idRequerimento,
            String instancia,
            boolean encerrada,
            int tarefasAtivasNaRaiz,
            int tarefasConcluidasNoCiclo,
            int filhas,
            int assinaturasDeEvento,
            boolean complementacao) {

        public boolean temInstancia() {
            return instancia != null;
        }
    }

    public final class Processo {

        private final Fluxo fluxo;
        private final long id;
        private final ModeloDeFluxo modelo;
        private String numero;
        private String numeroProcesso;
        private String situacao = "EM_ANALISE";
        private LocalDateTime desde = DESDE_PADRAO;
        private Etapa etapa;
        private String atividade;
        private int analistas = 1;
        private int concluidos;
        private final List<String> responsaveis = new ArrayList<>();
        private String grupo;
        private final List<String> filhas = new ArrayList<>();
        private String fim;
        private boolean suporte;
        private Long complementacao;
        private final List<String> percurso = new ArrayList<>();
        private boolean semInstancia;
        private final List<Passo> historico = new ArrayList<>();
        private final List<Variavel> variaveis = new ArrayList<>();
        private final List<Evento> eventosDoLicenciamento = new ArrayList<>();
        private final List<ComplementacaoConcluida> complementacoesConcluidas = new ArrayList<>();
        private LocalDateTime abertura;
        private LocalDateTime dataProtocolo;
        private LocalDateTime dataCancelamento;
        private LocalDateTime dataExpiracaoRequerimento;
        private LocalDateTime dataExpiracaoProcesso;
        private int filhasHistoricas;
        private final List<Lancamento> lancamentos = new ArrayList<>();
        private final List<TarefaExtra> extras = new ArrayList<>();

        private Processo(Fluxo fluxo, long id) {
            this.fluxo = Objects.requireNonNull(fluxo);
            this.id = id;
            this.numero = id + "/2026";
            this.modelo = fluxo.chave == null ? null
                    : catalogo.modelo(fluxo.chave).orElseThrow(
                            () -> new IllegalStateException("modelo " + fluxo.chave + " fora do catálogo de etapas"));
        }

        public Processo numero(String numeroDoRequerimento) {
            this.numero = numeroDoRequerimento;
            return this;
        }

        public Processo numeroProcesso(String numeroDoProcesso) {
            this.numeroProcesso = numeroDoProcesso;
            return this;
        }

        /** {@code REQUERIMENTO.SITUACAO_PROCESSO} (padrão {@code EM_ANALISE}). */
        public Processo situacao(String situacaoDoLicenciamento) {
            this.situacao = situacaoDoLicenciamento;
            return this;
        }

        /** Início da etapa atual (criação da tarefa), ou o fim da instância encerrada. */
        public Processo desde(LocalDateTime quando) {
            this.desde = Objects.requireNonNull(quando);
            return this;
        }

        /** Etapa atual pelo id técnico (tarefa, espera ou automática). Emissão usa {@link #comFilhas}. */
        public Processo naEtapa(String idTecnico) {
            Etapa encontrada = etapaDo(idTecnico);
            if (encontrada.tipo() == TipoDeEtapa.EMISSAO) {
                throw new IllegalArgumentException("emissão é feita por instâncias filhas: use comFilhas(...)");
            }
            definirEtapa(encontrada, idTecnico);
            return this;
        }

        /** Na espera de pagamento do ramo principal (gateway por evento + assinatura em {@code ACT_RU_EVENT_SUBSCR}). */
        public Processo esperandoPagamento() {
            Etapa espera = modelo().etapas().stream()
                    .filter(e -> e.tipo() == TipoDeEtapa.ESPERA && e.ramo() == Ramo.PRINCIPAL)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("modelo sem espera no ramo principal"));
            definirEtapa(espera, espera.idsTecnicos().getFirst());
            return this;
        }

        /** Multi-instância: uma tarefa por analista; os responsáveis padrão são os servidores 1 a n. */
        public Processo comAnalistas(int total) {
            if (total < 1) {
                throw new IllegalArgumentException("ao menos um analista");
            }
            this.analistas = total;
            return this;
        }

        /** Quantos do ciclo já concluíram (ficam em {@code ACT_HI_TASKINST} com {@code DELETE_REASON_ = 'completed'}). */
        public Processo concluidos(int quantos) {
            this.concluidos = quantos;
            return this;
        }

        /** {@code ASSIGNEE_} de cada tarefa, na ordem (na multi-instância, os concluídos primeiro). */
        public Processo atribuidoA(String... cpfs) {
            responsaveis.clear();
            responsaveis.addAll(Arrays.asList(cpfs));
            return this;
        }

        /** Tarefa do interessado (cadastro, correção): {@code ASSIGNEE_} = CPF do cadastrante fictício. */
        public Processo doInteressado() {
            return atribuidoA(CPF_DO_INTERESSADO);
        }

        /** Grupo candidato da tarefa ({@code ACT_RU_IDENTITYLINK.TYPE_ = 'candidate'}). */
        public Processo paraGrupo(String idDoGrupo) {
            this.grupo = idDoGrupo;
            return this;
        }

        /** Emissão: uma instância filha por chave ({@code procSurh*}), em paralelo, ligadas pela instância-mãe. */
        public Processo comFilhas(String... chavesDasFilhas) {
            if (chavesDasFilhas.length == 0) {
                throw new IllegalArgumentException("informe ao menos uma filha");
            }
            Etapa emissao = null;
            for (String chave : chavesDasFilhas) {
                if (!(modelo().resolverProcessoFilho(chave) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e))) {
                    throw new IllegalArgumentException("filha " + chave + " fora do catálogo de " + modelo().chave());
                }
                if (emissao != null && !emissao.equals(e)) {
                    throw new IllegalArgumentException("filhas de etapas diferentes");
                }
                emissao = e;
            }
            filhas.clear();
            filhas.addAll(Arrays.asList(chavesDasFilhas));
            this.etapa = emissao;
            this.atividade = null;
            return this;
        }

        /** Instância encerrada no evento de fim dado (precisa ser um final do catálogo). */
        public Processo encerradoEm(String idDoFinal) {
            if (!(modelo().resolver(idDoFinal, Set.of()) instanceof ResolucaoDeEtapa.Encerrado)) {
                throw new IllegalArgumentException(idDoFinal + " não é um final de " + modelo().chave());
            }
            this.fim = idDoFinal;
            return this;
        }

        /** Tarefa "Suporte" ativa (id de suporte do catálogo, grupo {@code suporte}). */
        public Processo comTarefaDeSuporte() {
            if (modelo().idsDeSuporte().isEmpty()) {
                throw new IllegalStateException(modelo().chave() + " não tem tarefa de suporte no catálogo");
            }
            this.suporte = true;
            return this;
        }

        /**
         * Complementação em andamento: linha em {@code COMPLEMENTACAO} e instância {@value #CHAVE_COMPLEMENTACAO} com
         * {@code BUSINESS_KEY_} = id da complementação (pode colidir de propósito com o id de outro requerimento).
         */
        public Processo comComplementacao(long idDaComplementacao) {
            this.complementacao = idDaComplementacao;
            return this;
        }

        /** Atividades já percorridas, em ordem (histórico mínimo: {@code ACT_HI_ACTINST} encerrada). */
        public Processo percorrendo(String... idsTecnicos) {
            for (String idTecnico : idsTecnicos) {
                ResolucaoDeEtapa r = modelo().resolver(idTecnico, Set.of());
                if (!(r instanceof ResolucaoDeEtapa.NaEtapa) && !(r instanceof ResolucaoDeEtapa.Ignorado)) {
                    throw new IllegalArgumentException(idTecnico + " não é etapa nem id ignorado de " + modelo().chave());
                }
                percurso.add(idTecnico);
            }
            return this;
        }

        /**
         * Segunda tarefa humana ativa na RAIZ, em paralelo à etapa dada por {@link #naEtapa} (S10, painel: "dois ramos
         * ativos"). Precisa ser tarefa humana de uma etapa DIFERENTE da principal e de outro ramo do catálogo (ex.: análise no
         * ramo principal e correção no ramo de correção). {@code cpf} nulo deixa a tarefa sem atribuição. Hipótese da
         * fixture (NÃO CONFIRMADO): como o BPMN real grava duas tarefas ativas na raiz.
         */
        public Processo comTarefaParalela(String idTecnico, String cpf) {
            Etapa paralela = etapaDo(idTecnico);
            if (paralela.tipo() != TipoDeEtapa.TAREFA_HUMANA || paralela.multiInstancia()) {
                throw new IllegalArgumentException(idTecnico + " não é tarefa humana simples");
            }
            if (etapa == null || etapa.equals(paralela) || etapa.ramo() == paralela.ramo()) {
                throw new IllegalStateException("a paralela exige naEtapa(...) antes e uma etapa de OUTRO ramo");
            }
            extras.add(new TarefaExtra(idTecnico, paralela.rotulo(), cpf));
            return this;
        }

        /**
         * Tarefa ativa na raiz com id que o catálogo não conhece (S10: "Etapa não mapeada"). O id precisa levar "Ficticio" e
         * não pode estar no catálogo; {@code cpf} nulo deixa a tarefa sem atribuição.
         */
        public Processo comTarefaNaoMapeada(String idFicticio, String cpf) {
            if (!idFicticio.contains("Ficticio")) {
                throw new IllegalArgumentException("id de tarefa fictícia precisa conter \"Ficticio\"");
            }
            if (!(modelo().resolver(idFicticio, Set.of()) instanceof ResolucaoDeEtapa.NaoMapeado)) {
                throw new IllegalArgumentException(idFicticio + " já é conhecido pelo catálogo de " + modelo().chave());
            }
            extras.add(new TarefaExtra(idFicticio, idFicticio + " (FICTÍCIO)", cpf));
            return this;
        }

        /** Requerimento legado sem instância no Camunda (antes de 2023). */
        public Processo semInstancia() {
            this.semInstancia = true;
            return this;
        }

        // --- caminho histórico (S8)

        /**
         * Etapa de uma tarefa só (ou automática) percorrida: {@code ACT_HI_ACTINST} com início e fim e, se for tarefa,
         * {@code ACT_HI_TASKINST} com {@code DELETE_REASON_ = 'completed'}. O responsável padrão da tarefa humana é o
         * cadastrante fictício (papel do interessado) ou o "Servidor Exemplo 01"; automáticas não têm responsável.
         */
        public Processo passou(String idTecnico, LocalDateTime inicio, LocalDateTime fim) {
            return passou(idTecnico, inicio, fim, responsavelPadrao(idTecnico));
        }

        /** Como {@link #passou(String, LocalDateTime, LocalDateTime)}, com o responsável (CPF fictício) dado. */
        public Processo passou(String idTecnico, LocalDateTime inicio, LocalDateTime fim, String cpf) {
            intervalo(inicio, fim);
            ResolucaoDeEtapa r = modelo().resolver(idTecnico, Set.of());
            if (r instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)) {
                if (e.tipo() == TipoDeEtapa.EMISSAO || e.multiInstancia()) {
                    throw new IllegalArgumentException(
                            idTecnico + " é emissão ou multi-instância: use passouPelaEmissao ou passouEmCiclo");
                }
            } else if (!(r instanceof ResolucaoDeEtapa.Ignorado)) {
                throw new IllegalArgumentException(idTecnico + " não é etapa nem id ignorado de " + modelo().chave());
            }
            String tipo = tipoNoHistorico(idTecnico);
            historico.add(new Simples(idTecnico, tipo, inicio, fim, "userTask".equals(tipo) ? cpf : null, "completed"));
            return this;
        }

        /**
         * No caminho histórico, os ids que o catálogo agrupa numa etapa humana mas que são scripts ({@code taskAtualizar*},
         * {@code taskUpdate*}, {@code taskGerar*}, {@code taskEmitirTaxa*}, {@code taskInformar*}, {@code taskMontar*}) são
         * {@code serviceTask}: só tarefa humana tem linha em {@code ACT_HI_TASKINST}.
         */
        private String tipoNoHistorico(String idTecnico) {
            if (idTecnico.matches("task(Atualizar|Update|Gerar|Informar|Montar|EmitirTaxa).*")) {
                return "serviceTask";
            }
            return tipoDaAtividade(idTecnico);
        }

        /** Espera de pagamento (gateway por evento) já superada: precisa ser etapa do tipo espera. */
        public Processo passouPelaEspera(String idTecnico, LocalDateTime inicio, LocalDateTime fim) {
            if (!(modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e))
                    || e.tipo() != TipoDeEtapa.ESPERA) {
                throw new IllegalArgumentException(idTecnico + " não é espera de " + modelo().chave());
            }
            return passou(idTecnico, inicio, fim, null);
        }

        /** Volta: a etapa já tinha sido percorrida e algo veio depois dela (senão não é volta). */
        public Processo voltouPara(String idTecnico, LocalDateTime inicio, LocalDateTime fim) {
            exigirVolta(idTecnico);
            return passou(idTecnico, inicio, fim);
        }

        /** Volta com responsável (CPF fictício) dado. */
        public Processo voltouPara(String idTecnico, LocalDateTime inicio, LocalDateTime fim, String cpf) {
            exigirVolta(idTecnico);
            return passou(idTecnico, inicio, fim, cpf);
        }

        /**
         * Ciclo multi-instância concluído: uma tarefa por CPF, todas {@code completed}, mais o corpo da multi-instância
         * ({@code <tarefa>#multiInstanceBody}). Os CPFs são os fictícios de {@link #cpfDoServidor}.
         */
        public Processo passouEmCiclo(String idTecnico, LocalDateTime inicio, LocalDateTime fim, String... cpfs) {
            List<Tarefa> tarefas = new ArrayList<>();
            for (String cpf : cpfs) {
                tarefas.add(new Tarefa(cpf, "completed"));
            }
            return ciclo(idTecnico, inicio, fim, tarefas);
        }

        /** Mesmo que {@link #passouEmCiclo}, marcado como volta (a etapa já tinha sido percorrida). */
        public Processo voltouParaEmCiclo(String idTecnico, LocalDateTime inicio, LocalDateTime fim, String... cpfs) {
            exigirVolta(idTecnico);
            return passouEmCiclo(idTecnico, inicio, fim, cpfs);
        }

        /**
         * Ciclo em que o primeiro CPF concluiu pedindo correção/recusando e as demais tarefas foram encerradas pela
         * condição de término ({@code DELETE_REASON_ = 'deleted'}: não contam como percorridas, regra técnica 6).
         */
        public Processo passouEmCicloEncerradoPor(String idTecnico, LocalDateTime inicio, LocalDateTime fim, String cpfQueConcluiu,
                String... cpfsEncerradosSemAcao) {
            List<Tarefa> tarefas = new ArrayList<>();
            tarefas.add(new Tarefa(cpfQueConcluiu, "completed"));
            for (String cpf : cpfsEncerradosSemAcao) {
                tarefas.add(new Tarefa(cpf, "deleted"));
            }
            return ciclo(idTecnico, inicio, fim, tarefas);
        }

        /**
         * Emissão concluída: um callActivity e uma instância filha (encerrada) por chave ({@code procSurh*}), todas
         * começando em {@code inicio} e terminando em momentos diferentes até {@code fim} (paralelas sobrepostas).
         */
        public Processo passouPelaEmissao(LocalDateTime inicio, LocalDateTime fim, String... chavesDasFilhas) {
            return emissao(inicio, fim, null, chavesDasFilhas);
        }

        /** Emissão em que a filha {@code chaveDevolvida} foi devolvida (encerrada sem concluir; tarefa {@code deleted}). */
        public Processo passouPelaEmissaoComDevolucao(LocalDateTime inicio, LocalDateTime fim, String chaveDevolvida,
                String... outrasFilhas) {
            return emissao(inicio, fim, Objects.requireNonNull(chaveDevolvida), outrasFilhas);
        }

        /** Tarefa "Suporte" (id de suporte do catálogo) concluída no meio do caminho: ruído a ignorar. */
        public Processo passouPeloSuporte(LocalDateTime inicio, LocalDateTime fim) {
            if (modelo().idsDeSuporte().isEmpty()) {
                throw new IllegalStateException(modelo().chave() + " não tem tarefa de suporte no catálogo");
            }
            return passou(modelo().idsDeSuporte().iterator().next(), inicio, fim, cpfDoServidor(SERVIDORES));
        }

        /**
         * O mesmo script automático {@code vezes} vezes seguidas, uma linha por tentativa, cada uma com {@code intervaloSegundos}
         * (erro de API repetido, regra técnica 10). O id precisa ser etapa automática do catálogo.
         */
        public Processo scriptRepetido(String idTecnico, int vezes, LocalDateTime inicio, long intervaloSegundos) {
            if (vezes < 2 || intervaloSegundos < 1) {
                throw new IllegalArgumentException("repetição precisa de ao menos 2 vezes e 1 segundo de intervalo");
            }
            if (!(modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e))
                    || e.tipo() != TipoDeEtapa.AUTOMATICA) {
                throw new IllegalArgumentException(idTecnico + " não é etapa automática de " + modelo().chave());
            }
            LocalDateTime fim = inicio.plusSeconds(vezes * intervaloSegundos);
            historico.add(new Repeticao(idTecnico, vezes, inicio, fim, intervaloSegundos));
            return this;
        }

        /**
         * Atividade que o catálogo não conhece, para ruído sem id técnico levantado ({@link #REPORT_KAFKA_FICTICIO},
         * {@link #REPETICAO_POR_ERRO_FICTICIA}, {@link #CORRECAO_EXPIRADA_FICTICIA}). O id precisa levar "Ficticio" e não
         * pode estar no catálogo; {@code tipoDoCamunda} é o {@code ACT_TYPE_} (ex.: {@code serviceTask}).
         */
        public Processo atividadeFicticia(String idFicticio, String tipoDoCamunda, LocalDateTime inicio, LocalDateTime fim) {
            intervalo(inicio, fim);
            if (!idFicticio.contains("Ficticio")) {
                throw new IllegalArgumentException("id de atividade fictícia precisa conter \"Ficticio\"");
            }
            if (!(modelo().resolver(idFicticio, Set.of()) instanceof ResolucaoDeEtapa.NaoMapeado)) {
                throw new IllegalArgumentException(idFicticio + " já é conhecido pelo catálogo de " + modelo().chave());
            }
            historico.add(new Simples(idFicticio, tipoDoCamunda, inicio, fim, null, "completed"));
            return this;
        }

        /**
         * Tarefa encerrada pela expiração do prazo (evento de borda), não concluída: {@code DELETE_REASON_ = 'deleted'}.
         * NÃO CONFIRMADO como o Camunda real grava a expiração da correção (docs/15, P40): hipótese da fixture.
         */
        public Processo expirou(String idTecnico, LocalDateTime inicio, LocalDateTime fim) {
            passou(idTecnico, inicio, fim);
            Simples s = (Simples) historico.removeLast();
            historico.add(new Simples(s.id(), s.tipo(), s.inicio(), s.fim(), s.cpf(), "deleted"));
            return this;
        }

        /**
         * Valor de uma variável do Camunda num instante ({@code ACT_HI_DETAIL}, e a última em {@code ACT_HI_VARINST}).
         * Só a lista branca {@link #VARIAVEIS_PERMITIDAS}; valor com 11 dígitos seguidos é recusado.
         */
        public Processo variavel(String nome, String valor, LocalDateTime quando) {
            if (!VARIAVEIS_PERMITIDAS.contains(nome)) {
                throw new IllegalArgumentException("variável fora da lista branca: " + nome);
            }
            if (valor == null || ONZE_DIGITOS.matcher(valor).find()) {
                throw new IllegalArgumentException("valor de variável nulo ou com cara de CPF");
            }
            variaveis.add(new Variavel(nome, valor, Objects.requireNonNull(quando)));
            return this;
        }

        /** Complementação paralela já concluída (instância independente, {@value #CHAVE_COMPLEMENTACAO}, encerrada). */
        public Processo complementacaoConcluida(long idDaComplementacao, LocalDateTime inicio, LocalDateTime fim) {
            intervalo(inicio, fim);
            complementacoesConcluidas.add(new ComplementacaoConcluida(idDaComplementacao, inicio, fim));
            return this;
        }

        // --- plano B (S8): o que o Licenciamento guarda de um requerimento sem instância

        /** Linha de {@code HISTORICO_REQUERIMENTO} (com {@code CPF_USUARIO} fictício e inválido, que o plano B nunca lê). */
        public Processo noHistoricoDoLicenciamento(String evento, LocalDateTime quando) {
            eventosDoLicenciamento.add(new Evento(Objects.requireNonNull(evento), Objects.requireNonNull(quando)));
            return this;
        }

        /** Datas de {@code REQUERIMENTO} que o plano B usa; {@code null} deixa a coluna vazia. */
        public Processo comDatas(LocalDateTime protocolo, LocalDateTime cancelamento, LocalDateTime expiracaoDoRequerimento,
                LocalDateTime expiracaoDoProcesso) {
            this.dataProtocolo = protocolo;
            this.dataCancelamento = cancelamento;
            this.dataExpiracaoRequerimento = expiracaoDoRequerimento;
            this.dataExpiracaoProcesso = expiracaoDoProcesso;
            return this;
        }

        /** {@code REQUERIMENTO.DATA_ABERTURA} quando não é derivada do caminho (ex.: requerimento sem instância). */
        public Processo abertoEm(LocalDateTime quando) {
            this.abertura = Objects.requireNonNull(quando);
            return this;
        }

        // --- financeiro (S9): lançamentos de taxa em LANCAMENTO_TAXA, independentes do Camunda

        /**
         * Um lançamento de taxa (uma linha de {@code LANCAMENTO_TAXA}) com o {@code STATUS} dado, e a API fluente para
         * completá-lo: {@code .comLancamento(PAGA).comDar("FICT-0001").emitidoEm(...).vencendoEm(...).pagoEm(...)}.
         * Vale também para requerimento sem instância ({@link #semInstancia}); sem nenhum lançamento, o requerimento fica
         * sem taxa (FIN13). Os lançamentos saem em {@code ID = <requerimento> * 100 + ordem}.
         */
        public Lancamento comLancamento(StatusDaGuia status) {
            return comLancamentoBruto(Objects.requireNonNull(status).name());
        }

        /** Como {@link #comLancamento(StatusDaGuia)}, mas com o STATUS em texto livre (valor desconhecido do enum). */
        public Lancamento comLancamentoBruto(String statusEmTexto) {
            if (lancamentos.size() >= 99) {
                throw new IllegalStateException("no máximo 99 lançamentos por requerimento fictício");
            }
            Lancamento novo = new Lancamento(id * 100 + lancamentos.size() + 1, statusEmTexto);
            lancamentos.add(novo);
            return novo;
        }

        /** Uma linha de {@code LANCAMENTO_TAXA}; as colunas que o Rastro nunca lê ficam nulas. */
        public final class Lancamento {

            private final long idDoLancamento;
            private String status;
            private int taxaIsenta;
            private String processado = "P";
            private String dar;
            private LocalDateTime emissao;
            private LocalDateTime vencimento;
            private LocalDateTime pagamento;
            private LocalDateTime dataFim;
            private BigDecimal valorReal;
            private BigDecimal desconto;
            private BigDecimal upf;
            private Integer reaprovacaoDaCorrecao;

            private Lancamento(long idDoLancamento, String status) {
                this.idDoLancamento = idDoLancamento;
                this.status = status;
            }

            /** Troca o STATUS por um texto livre. */
            public Lancamento comStatusBruto(String statusEmTexto) {
                this.status = statusEmTexto;
                return this;
            }

            /** {@code NUM_DAR}; só aceita número fictício evidente ({@value #PREFIXO_DO_DAR_FICTICIO}...). */
            public Lancamento comDar(String numeroFicticio) {
                if (numeroFicticio == null || !numeroFicticio.startsWith(PREFIXO_DO_DAR_FICTICIO)) {
                    throw new IllegalArgumentException("DAR fictício deve começar com " + PREFIXO_DO_DAR_FICTICIO);
                }
                this.dar = numeroFicticio;
                return this;
            }

            public Lancamento emitidoEm(LocalDateTime quando) {
                this.emissao = quando;
                return this;
            }

            /** {@code DATA_VENCIMENTO} à meia-noite do dia (a hora real da coluna é NÃO CONFIRMADA). */
            public Lancamento vencendoEm(LocalDate dia) {
                this.vencimento = dia == null ? null : dia.atStartOfDay();
                return this;
            }

            /** {@code DATA_VENCIMENTO} com hora, para provar que a conversão para data não desloca o dia. */
            public Lancamento vencendoEm(LocalDateTime quando) {
                this.vencimento = quando;
                return this;
            }

            public Lancamento pagoEm(LocalDateTime quando) {
                this.pagamento = quando;
                return this;
            }

            /** {@code DATA_FIM} preenchida: lançamento encerrado (cancelado ou substituído). */
            public Lancamento encerradoEm(LocalDateTime quando) {
                this.dataFim = quando;
                return this;
            }

            /** {@code TAXA_ISENTA = 1}. */
            public Lancamento isenta() {
                this.taxaIsenta = 1;
                return this;
            }

            /** {@code STATUS_PROCESSADO} ({@code N} ou {@code P}; o padrão é {@code P}). */
            public Lancamento processado(char marca) {
                if (marca != 'N' && marca != 'P') {
                    throw new IllegalArgumentException("STATUS_PROCESSADO é N ou P");
                }
                this.processado = String.valueOf(marca);
                return this;
            }

            public Lancamento comValores(String real, String desconto, String upf) {
                this.valorReal = real == null ? null : new BigDecimal(real);
                this.desconto = desconto == null ? null : new BigDecimal(desconto);
                this.upf = upf == null ? null : new BigDecimal(upf);
                return this;
            }

            /** {@code PASSOU_POR_REAPROV_TAXA_CORREC} (semântica NÃO CONFIRMADA; só diagnóstico). */
            public Lancamento passouPelaReaprovacaoDaCorrecao(boolean valor) {
                this.reaprovacaoDaCorrecao = valor ? 1 : 0;
                return this;
            }

            /** Próximo lançamento do mesmo requerimento. */
            public Lancamento comLancamento(StatusDaGuia proximo) {
                return Processo.this.comLancamento(proximo);
            }

            public Lancamento comLancamentoBruto(String proximoEmTexto) {
                return Processo.this.comLancamentoBruto(proximoEmTexto);
            }

            /** Grava o requerimento com todos os lançamentos (o mesmo que {@link Processo#gravar()}). */
            public Resultado gravar() {
                return Processo.this.gravar();
            }

            private void gravarLinha() {
                jdbc.update("""
                        INSERT INTO SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA
                               (ID, ID_REQUERIMENTO, STATUS, TAXA_ISENTA, STATUS_PROCESSADO, NUM_DAR, DATA_EMISSAO,
                                DATA_VENCIMENTO, DATA_PAGAMENTO, DATA_FIM, VALOR_REAL, VALOR_DESCONTO, QTDE_TOTAL_UPF,
                                PASSOU_POR_REAPROV_TAXA_CORREC)
                        VALUES (:id, :requerimento, :status, :isenta, :processado, :dar, :emissao,
                                :vencimento, :pagamento, :fim, :real, :desconto, :upf, :reaprovacao)""",
                        new MapSqlParameterSource()
                                .addValue("id", idDoLancamento).addValue("requerimento", id)
                                .addValue("status", status).addValue("isenta", taxaIsenta)
                                .addValue("processado", processado).addValue("dar", dar)
                                .addValue("emissao", carimbo(emissao)).addValue("vencimento", carimbo(vencimento))
                                .addValue("pagamento", carimbo(pagamento)).addValue("fim", carimbo(dataFim))
                                .addValue("real", valorReal).addValue("desconto", desconto).addValue("upf", upf)
                                .addValue("reaprovacao", reaprovacaoDaCorrecao));
            }
        }

        private void gravarLancamentos() {
            lancamentos.forEach(Lancamento::gravarLinha);
        }

        public Resultado gravar() {
            boolean comInstancia = fluxo.chave != null && !semInstancia;
            validar(comInstancia);
            LocalDateTime inicio = historico.isEmpty()
                    ? desde.minusDays(percurso.size() + 1L)
                    : historico.stream().map(Passo::inicio).min(Comparator.naturalOrder()).orElseThrow();
            gravarRequerimento(abertura != null ? abertura : comInstancia ? inicio : desde);
            gravarDoLicenciamento();
            gravarLancamentos();
            if (!comInstancia) {
                return new Resultado(id, null, false, 0, 0, 0, 0, false);
            }
            String pi = "fict-" + id;
            String procDef = procDef(modelo.chave(), modelo.rotulo());
            inserirHiProcinst(pi, Long.toString(id), modelo.chave(), procDef, inicio, null, pi);
            if (historico.isEmpty()) {
                gravarPercurso(pi, procDef, inicio);
            } else {
                gravarHistorico(pi, procDef);
            }
            gravarVariaveis(pi);
            for (ComplementacaoConcluida c : complementacoesConcluidas) {
                gravarComplementacaoConcluida(pi, c);
            }
            if (fim != null) {
                inserirAtividade(pi, procDef, modelo.chave(), pi, pi, fim, "noneEndEvent", null, null, null, desde, desde);
                jdbc.update("""
                        UPDATE SIGA2_CAMUNDA.ACT_HI_PROCINST
                        SET    END_TIME_ = :fim, END_ACT_ID_ = :atividade, STATE_ = 'COMPLETED'
                        WHERE  ID_ = :pi""", new MapSqlParameterSource()
                        .addValue("fim", Timestamp.valueOf(desde)).addValue("atividade", fim).addValue("pi", pi));
                return new Resultado(id, pi, true, 0, 0, 0, 0, false);
            }
            return gravarAtiva(pi, procDef);
        }

        private void validar(boolean comInstancia) {
            if (!comInstancia) {
                if (etapa != null || fim != null || suporte || complementacao != null || !filhas.isEmpty()
                        || !historico.isEmpty() || !variaveis.isEmpty() || !complementacoesConcluidas.isEmpty()
                        || !extras.isEmpty()) {
                    throw new IllegalStateException(
                            "requerimento sem instância não tem etapa, final, filhas, caminho nem variáveis");
                }
                return;
            }
            if (!historico.isEmpty() && !percurso.isEmpty()) {
                throw new IllegalStateException("use percorrendo(...) OU o caminho histórico (passou...), não os dois");
            }
            if (historico.isEmpty() && !variaveis.isEmpty()) {
                throw new IllegalStateException("variável sem caminho histórico: grave o caminho com passou(...)");
            }
            LocalDateTime ultimoFim = historico.stream().map(Passo::fim).max(Comparator.naturalOrder()).orElse(null);
            if (ultimoFim != null && ultimoFim.isAfter(desde)) {
                throw new IllegalStateException("o caminho termina depois do início da etapa ativa (ou do fim da instância): "
                        + "ajuste desde(...)");
            }
            boolean ativa = etapa != null || suporte || !extras.isEmpty();
            if (fim != null && (ativa || complementacao != null)) {
                throw new IllegalStateException("instância encerrada não tem etapa ativa nem complementação");
            }
            if (fim == null && !ativa) {
                throw new IllegalStateException("cenário sem etapa ativa: use naEtapa, comFilhas, comTarefaDeSuporte ou encerradoEm");
            }
            if (etapa != null && !etapa.aceitaPercurso(idsPercorridos())) {
                throw new IllegalStateException("etapa " + etapa.id() + " não vale com o percurso dado (ex.: isenção com "
                        + "passagem pelo gateway de pagamento)");
            }
            if (analistas > 1 && (etapa == null || !etapa.multiInstancia())) {
                throw new IllegalStateException("comAnalistas só vale em etapa multi-instância");
            }
            if (concluidos < 0 || concluidos >= analistas) {
                throw new IllegalStateException("concluidos deve ficar entre 0 e analistas - 1");
            }
        }

        /** Ids técnicos já percorridos: o percurso mínimo da S7 ou, no caminho histórico, as atividades dos passos. */
        private Set<String> idsPercorridos() {
            if (historico.isEmpty()) {
                return Set.copyOf(percurso);
            }
            Set<String> ids = new java.util.HashSet<>();
            for (Passo p : historico) {
                switch (p) {
                    case Simples s -> ids.add(s.id());
                    case Ciclo c -> ids.add(c.id());
                    case Repeticao r -> ids.add(r.id());
                    case Emissao e -> {
                        Etapa emissao = etapaDaEmissao(e);
                        ids.addAll(emissao.idsTecnicos());
                    }
                }
            }
            return ids;
        }

        private Etapa etapaDaEmissao(Emissao e) {
            return ((ResolucaoDeEtapa.NaEtapa) modelo().resolverProcessoFilho(e.filhas().getFirst().chave())).etapa();
        }

        private void intervalo(LocalDateTime inicio, LocalDateTime fim) {
            if (inicio == null || fim == null || fim.isBefore(inicio)) {
                throw new IllegalArgumentException("passo precisa de início e fim, com o fim depois do início");
            }
        }

        /** Responsável padrão da tarefa humana: cadastrante fictício (interessado) ou o Servidor Exemplo 01; outras, ninguém. */
        private String responsavelPadrao(String idTecnico) {
            String tipo = tipoDaAtividade(idTecnico);
            if (!"userTask".equals(tipo)) {
                return null;
            }
            if (modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)
                    && e.responsavel() == PapelDoResponsavel.INTERESSADO) {
                return CPF_DO_INTERESSADO;
            }
            return cpfDoServidor(1);
        }

        private void exigirVolta(String idTecnico) {
            if (!(modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa alvo))) {
                throw new IllegalArgumentException(idTecnico + " não é etapa de " + modelo().chave());
            }
            for (int i = 0; i < historico.size() - 1; i++) {
                if (etapaDoPasso(historico.get(i)).filter(alvo::equals).isPresent()) {
                    return; // já passou por ela e há passo depois: é volta
                }
            }
            throw new IllegalStateException(idTecnico + " não é volta: a etapa ainda não tinha sido percorrida antes de outra");
        }

        private java.util.Optional<Etapa> etapaDoPasso(Passo passo) {
            return switch (passo) {
                case Simples s -> etapaPorId(s.id());
                case Ciclo c -> etapaPorId(c.id());
                case Repeticao r -> etapaPorId(r.id());
                case Emissao e -> java.util.Optional.of(etapaDaEmissao(e));
            };
        }

        private java.util.Optional<Etapa> etapaPorId(String idTecnico) {
            return modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)
                    ? java.util.Optional.of(e) : java.util.Optional.empty();
        }

        private Processo ciclo(String idTecnico, LocalDateTime inicio, LocalDateTime fim, List<Tarefa> tarefas) {
            intervalo(inicio, fim);
            if (!(modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)) || !e.multiInstancia()) {
                throw new IllegalArgumentException(idTecnico + " não é etapa multi-instância de " + modelo().chave());
            }
            if (tarefas.isEmpty()) {
                throw new IllegalArgumentException("ciclo sem nenhuma tarefa");
            }
            historico.add(new Ciclo(idTecnico, inicio, fim, List.copyOf(tarefas)));
            return this;
        }

        private Processo emissao(LocalDateTime inicio, LocalDateTime fim, String chaveDevolvida, String... outras) {
            intervalo(inicio, fim);
            List<Filha> filhasDaEmissao = new ArrayList<>();
            if (chaveDevolvida != null) {
                filhasDaEmissao.add(new Filha(chaveDevolvida, true));
            }
            for (String chave : outras) {
                filhasDaEmissao.add(new Filha(chave, false));
            }
            if (filhasDaEmissao.isEmpty()) {
                throw new IllegalArgumentException("informe ao menos uma filha");
            }
            Etapa emissao = null;
            for (Filha f : filhasDaEmissao) {
                if (!(modelo().resolverProcessoFilho(f.chave()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e))) {
                    throw new IllegalArgumentException("filha " + f.chave() + " fora do catálogo de " + modelo().chave());
                }
                if (emissao != null && !emissao.equals(e)) {
                    throw new IllegalArgumentException("filhas de etapas diferentes");
                }
                emissao = e;
            }
            historico.add(new Emissao(inicio, fim, List.copyOf(filhasDaEmissao)));
            return this;
        }

        private void definirEtapa(Etapa encontrada, String idTecnico) {
            this.etapa = encontrada;
            this.atividade = idTecnico;
            this.filhas.clear();
        }

        private Etapa etapaDo(String idTecnico) {
            if (modelo().resolver(idTecnico, Set.of()) instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)) {
                return e;
            }
            throw new IllegalArgumentException(idTecnico + " não é etapa de " + modelo().chave());
        }

        private ModeloDeFluxo modelo() {
            if (modelo == null) {
                throw new IllegalStateException("fluxo " + fluxo + " não tem modelo no catálogo");
            }
            return modelo;
        }

        private void gravarRequerimento(LocalDateTime abertura) {
            jdbc.update("""
                    INSERT INTO SIGA2_LICENCIAMENTO.REQUERIMENTO
                           (ID, NUMERO, NUMERO_PROCESSO, DATA_ABERTURA, DATA_ALTERACAO, SITUACAO_PROCESSO, ID_PESSOA, ID_TIPO_PROCESSO)
                    VALUES (:id, :numero, :processo, :abertura, :alteracao, :situacao, :pessoa, :tipo)""",
                    new MapSqlParameterSource()
                            .addValue("id", id).addValue("numero", numero).addValue("processo", numeroProcesso)
                            .addValue("abertura", Timestamp.valueOf(abertura)).addValue("alteracao", Timestamp.valueOf(desde))
                            .addValue("situacao", situacao).addValue("pessoa", ID_INTERESSADO)
                            .addValue("tipo", fluxo.idTipoProcesso));
        }

        private void gravarPercurso(String pi, String procDef, LocalDateTime inicio) {
            for (int i = 0; i < percurso.size(); i++) {
                String idTecnico = percurso.get(i);
                LocalDateTime comeco = inicio.plusDays(i);
                LocalDateTime termino = comeco.plusHours(4);
                String tipo = tipoDaAtividade(idTecnico);
                String tarefa = null;
                if ("userTask".equals(tipo)) {
                    tarefa = pi + "-t" + sequencia.incrementAndGet();
                    inserirHiTarefa(tarefa, idTecnico, modelo.chave(), procDef, pi, pi, pi, null, comeco, termino, "completed");
                }
                inserirAtividade(pi, procDef, modelo.chave(), pi, pi, idTecnico, tipo, tarefa, null, null, comeco, termino);
            }
        }

        /** Datas de REQUERIMENTO do plano B e linhas de HISTORICO_REQUERIMENTO (a tabela do plano B). */
        private void gravarDoLicenciamento() {
            if (dataProtocolo != null || dataCancelamento != null || dataExpiracaoRequerimento != null
                    || dataExpiracaoProcesso != null) {
                jdbc.update("""
                        UPDATE SIGA2_LICENCIAMENTO.REQUERIMENTO
                        SET    DATA_PROTOCOLO_PROCESSO = :protocolo, DATA_CANCELAMENTO = :cancelamento,
                               DATA_EXPIRACAO_REQUERIMENTO = :expReq, DATA_EXPIRACAO_PROCESSO = :expProc
                        WHERE  ID = :id""", new MapSqlParameterSource().addValue("id", id)
                        .addValue("protocolo", carimbo(dataProtocolo)).addValue("cancelamento", carimbo(dataCancelamento))
                        .addValue("expReq", carimbo(dataExpiracaoRequerimento))
                        .addValue("expProc", carimbo(dataExpiracaoProcesso)));
            }
            long ordem = 0;
            for (Evento e : eventosDoLicenciamento) {
                jdbc.update("""
                        INSERT INTO SIGA2_LICENCIAMENTO.HISTORICO_REQUERIMENTO
                               (ID, ID_REQUERIMENTO, DATA_CADASTRO, EVENTO, CPF_USUARIO)
                        VALUES (:id, :requerimento, :quando, :evento, :cpf)""", new MapSqlParameterSource()
                        .addValue("id", id * 100 + ++ordem).addValue("requerimento", id)
                        .addValue("quando", Timestamp.valueOf(e.quando())).addValue("evento", e.evento())
                        .addValue("cpf", cpfDoServidor(3)));
            }
        }

        private void gravarHistorico(String pi, String procDef) {
            for (Passo passo : historico) {
                switch (passo) {
                    case Simples s -> gravarSimples(pi, procDef, s);
                    case Ciclo c -> gravarCiclo(pi, procDef, c);
                    case Emissao e -> gravarEmissao(pi, procDef, e);
                    case Repeticao r -> gravarRepeticao(pi, procDef, r);
                }
            }
        }

        private void gravarSimples(String pi, String procDef, Simples s) {
            String tarefa = null;
            if ("userTask".equals(s.tipo())) {
                tarefa = pi + "-t" + sequencia.incrementAndGet();
                inserirHiTarefa(tarefa, s.id(), modelo.chave(), procDef, pi, pi, pi, s.cpf(), s.inicio(), s.fim(), s.motivo());
            }
            inserirAtividade(pi, procDef, modelo.chave(), pi, pi, s.id(), s.tipo(), tarefa, null, s.cpf(), s.inicio(), s.fim());
        }

        private void gravarCiclo(String pi, String procDef, Ciclo c) {
            String corpo = pi + "-mi" + sequencia.incrementAndGet();
            inserirAtividade(pi, procDef, modelo.chave(), pi, corpo, c.id() + "#multiInstanceBody", "multiInstanceBody", null,
                    null, null, c.inicio(), c.fim());
            Duration total = Duration.between(c.inicio(), c.fim());
            long concluidas = c.tarefas().stream().filter(t -> "completed".equals(t.motivo())).count();
            long vistas = 0;
            for (Tarefa t : c.tarefas()) {
                LocalDateTime termino = c.fim();
                if ("completed".equals(t.motivo())) {
                    vistas++;
                    termino = c.inicio().plus(total.multipliedBy(vistas).dividedBy(concluidas));
                }
                String tarefa = pi + "-t" + sequencia.incrementAndGet();
                inserirHiTarefa(tarefa, c.id(), modelo.chave(), procDef, pi, pi, corpo, t.cpf(), c.inicio(), termino,
                        t.motivo());
                inserirAtividade(pi, procDef, modelo.chave(), pi, corpo, c.id(), "userTask", tarefa, null, t.cpf(), c.inicio(),
                        termino);
            }
        }

        private void gravarEmissao(String pi, String procDef, Emissao e) {
            Etapa emissao = etapaDaEmissao(e);
            Duration total = Duration.between(e.inicio(), e.fim());
            int n = e.filhas().size();
            for (int i = 0; i < n; i++) {
                Filha f = e.filhas().get(i);
                LocalDateTime termino = e.inicio().plus(total.multipliedBy(i + 1L).dividedBy(n));
                String chamada = emissao.idsTecnicos().get(emissao.processosFilhos().indexOf(f.chave()));
                String ci = pi + "-hfilha-" + (++filhasHistoricas);
                String titulo = Long.toString(id * 1000 + filhasHistoricas);
                String procDefFilha = procDef(f.chave(), f.chave() + " (FICTÍCIO)");
                inserirAtividade(pi, procDef, modelo.chave(), pi, pi, chamada, "callActivity", null, ci, null, e.inicio(),
                        termino);
                inserirHiProcinst(ci, titulo, f.chave(), procDefFilha, e.inicio(), pi, pi);
                String tarefa = ci + "-t" + sequencia.incrementAndGet();
                String cpf = cpfDoServidor(9);
                inserirHiTarefa(tarefa, TAREFA_DA_FILHA, f.chave(), procDefFilha, pi, ci, ci, cpf, e.inicio(), termino,
                        f.devolvida() ? "deleted" : "completed");
                inserirAtividade(ci, procDefFilha, f.chave(), pi, ci, TAREFA_DA_FILHA, "userTask", tarefa, null, cpf,
                        e.inicio(), termino);
                encerrarInstancia(ci, termino, null, f.devolvida() ? "INTERNALLY_TERMINATED" : "COMPLETED",
                        f.devolvida() ? "devolvida da emissão (FICTÍCIO)" : null);
            }
        }

        private void gravarRepeticao(String pi, String procDef, Repeticao r) {
            SqlParameterSource[] linhas = new SqlParameterSource[r.vezes()];
            String corpo = pi + "-rep" + sequencia.incrementAndGet();
            for (int i = 0; i < r.vezes(); i++) {
                LocalDateTime comeco = r.inicio().plusSeconds(i * r.intervaloSegundos());
                linhas[i] = parametrosDaAtividade(pi, procDef, modelo.chave(), pi, corpo, r.id(), "serviceTask", null, null,
                        null, comeco, comeco.plusSeconds(r.intervaloSegundos()));
            }
            jdbc.batchUpdate(SQL_ATIVIDADE, linhas);
        }

        private void gravarVariaveis(String pi) {
            List<Variavel> ordenadas = variaveis.stream().sorted(Comparator.comparing(Variavel::quando)).toList();
            for (Variavel v : ordenadas) {
                long n = sequencia.incrementAndGet();
                jdbc.update("""
                        INSERT INTO SIGA2_CAMUNDA.ACT_HI_DETAIL
                               (ID_, TYPE_, PROC_DEF_KEY_, PROC_INST_ID_, EXECUTION_ID_, NAME_, VAR_TYPE_, TIME_, TEXT_,
                                SEQUENCE_COUNTER_)
                        VALUES (:id, 'VariableUpdate', :chave, :pi, :pi, :nome, 'string', :quando, :valor, :seq)""",
                        new MapSqlParameterSource().addValue("id", pi + "-d" + n).addValue("chave", modelo.chave())
                                .addValue("pi", pi).addValue("nome", v.nome()).addValue("quando", Timestamp.valueOf(v.quando()))
                                .addValue("valor", v.valor()).addValue("seq", n));
            }
            // ACT_HI_VARINST guarda só o valor final de cada variável.
            java.util.Map<String, Variavel> finais = new java.util.LinkedHashMap<>();
            ordenadas.forEach(v -> finais.put(v.nome(), v));
            for (Variavel v : finais.values()) {
                jdbc.update("""
                        INSERT INTO SIGA2_CAMUNDA.ACT_HI_VARINST
                               (ID_, PROC_DEF_KEY_, PROC_INST_ID_, EXECUTION_ID_, NAME_, VAR_TYPE_, TEXT_, CREATE_TIME_, STATE_)
                        VALUES (:id, :chave, :pi, :pi, :nome, 'string', :valor, :quando, 'CREATED')""",
                        new MapSqlParameterSource().addValue("id", pi + "-v" + sequencia.incrementAndGet())
                                .addValue("chave", modelo.chave()).addValue("pi", pi).addValue("nome", v.nome())
                                .addValue("valor", v.valor()).addValue("quando", Timestamp.valueOf(v.quando())));
            }
        }

        private void gravarComplementacaoConcluida(String pi, ComplementacaoConcluida c) {
            jdbc.update("""
                    INSERT INTO SIGA2_LICENCIAMENTO.COMPLEMENTACAO (ID, ID_REQUERIMENTO, DATA_INICIO, DATA_ENTREGA, SITUACAO)
                    VALUES (:id, :requerimento, :inicio, :entrega, 'CONCLUIDA_FICTICIA')""",
                    new MapSqlParameterSource().addValue("id", c.id()).addValue("requerimento", id)
                            .addValue("inicio", Timestamp.valueOf(c.inicio())).addValue("entrega", Timestamp.valueOf(c.fim())));
            String ce = pi + "-compl-" + c.id();
            String procDefC = procDef(CHAVE_COMPLEMENTACAO, "Complementação (FICTÍCIO)");
            inserirHiProcinst(ce, Long.toString(c.id()), CHAVE_COMPLEMENTACAO, procDefC, c.inicio(), null, ce);
            String tarefa = ce + "-t" + sequencia.incrementAndGet();
            inserirHiTarefa(tarefa, TAREFA_DA_COMPLEMENTACAO, CHAVE_COMPLEMENTACAO, procDefC, ce, ce, ce, CPF_DO_INTERESSADO,
                    c.inicio(), c.fim(), "completed");
            inserirAtividade(ce, procDefC, CHAVE_COMPLEMENTACAO, ce, ce, TAREFA_DA_COMPLEMENTACAO, "userTask", tarefa, null,
                    CPF_DO_INTERESSADO, c.inicio(), c.fim());
            encerrarInstancia(ce, c.fim(), null, "COMPLETED", null);
        }

        private Timestamp carimbo(LocalDateTime quando) {
            return quando == null ? null : Timestamp.valueOf(quando);
        }

        private Resultado gravarAtiva(String pi, String procDef) {
            int elementos = (etapa != null ? Math.max(1, filhas.size()) : 0) + (suporte ? 1 : 0) + extras.size();
            boolean simples = elementos == 1 && !(etapa != null && etapa.multiInstancia());
            inserirExecucao(pi, pi, null, Long.toString(id), procDef, pi, null, simples ? atividadeSimples() : null,
                    simples, false, true);
            String paiDosElementos = pi;
            int tarefasNaRaiz = 0;
            int assinaturas = 0;
            int filhasGravadas = 0;

            if (etapa != null && etapa.multiInstancia()) {
                String corpo = novaExecucao(pi, procDef, pi, atividade + "#multiInstanceBody", elementos > 1, true, false);
                inserirAtividade(pi, procDef, modelo.chave(), pi, corpo, atividade + "#multiInstanceBody", "multiInstanceBody",
                        null, null, null, desde, null);
                for (int i = 0; i < analistas; i++) {
                    String cpf = responsavel(i, cpfDoServidor(i % SERVIDORES + 1));
                    if (i < concluidos) {
                        String tarefa = pi + "-t" + sequencia.incrementAndGet();
                        LocalDateTime termino = desde.plusHours(i + 1L);
                        inserirHiTarefa(tarefa, atividade, modelo.chave(), procDef, pi, pi, corpo, cpf, desde, termino, "completed");
                        inserirAtividade(pi, procDef, modelo.chave(), pi, corpo, atividade, "userTask", tarefa, null, cpf, desde,
                                termino);
                    } else {
                        String exec = novaExecucao(pi, procDef, corpo, atividade, true, false, true);
                        inserirTarefaAtiva(pi, procDef, modelo.chave(), pi, exec, atividade, etapa.rotulo(), cpf, grupo);
                        tarefasNaRaiz++;
                    }
                }
            } else if (!filhas.isEmpty()) {
                for (String chaveDaFilha : filhas) {
                    String chamada = etapa.idsTecnicos().get(etapa.processosFilhos().indexOf(chaveDaFilha));
                    String exec = elementos > 1 ? novaExecucao(pi, procDef, pi, chamada, true, false, true) : pi;
                    gravarFilha(pi, procDef, exec, chamada, chaveDaFilha, ++filhasGravadas);
                }
            } else if (etapa != null) {
                String exec = elementos > 1 ? novaExecucao(pi, procDef, pi, atividade, true, false, true) : pi;
                switch (etapa.tipo()) {
                    case TAREFA_HUMANA -> {
                        inserirTarefaAtiva(pi, procDef, modelo.chave(), pi, exec, atividade, etapa.rotulo(), responsavel(0, null),
                                grupo);
                        tarefasNaRaiz++;
                    }
                    case ESPERA -> {
                        inserirAtividade(pi, procDef, modelo.chave(), pi, exec, atividade, "eventBasedGateway", null, null, null,
                                desde, null);
                        jdbc.update("""
                                INSERT INTO SIGA2_CAMUNDA.ACT_RU_EVENT_SUBSCR
                                       (ID_, EVENT_TYPE_, EVENT_NAME_, EXECUTION_ID_, PROC_INST_ID_, ACTIVITY_ID_, CREATED_)
                                VALUES (:id, 'message', 'FICTICIO_pagamentoConfirmado', :exec, :pi, :atividade, :criado)""",
                                new MapSqlParameterSource()
                                        .addValue("id", pi + "-s" + sequencia.incrementAndGet()).addValue("exec", exec)
                                        .addValue("pi", pi).addValue("atividade", EVENTO_DE_PAGAMENTO)
                                        .addValue("criado", Timestamp.valueOf(desde)));
                        assinaturas++;
                    }
                    case AUTOMATICA -> inserirAtividade(pi, procDef, modelo.chave(), pi, exec, atividade, "serviceTask", null,
                            null, null, desde, null);
                    default -> throw new IllegalStateException("tipo inesperado: " + etapa.tipo());
                }
            }

            if (suporte) {
                String idSuporte = modelo.idsDeSuporte().iterator().next();
                String exec = elementos > 1 ? novaExecucao(pi, procDef, pi, idSuporte, true, false, true) : pi;
                inserirTarefaAtiva(pi, procDef, modelo.chave(), pi, exec, idSuporte, "Suporte", null, GRUPO_SUPORTE);
                tarefasNaRaiz++;
            }
            for (TarefaExtra extra : extras) {
                String exec = elementos > 1 ? novaExecucao(pi, procDef, pi, extra.id(), true, false, true) : pi;
                inserirTarefaAtiva(pi, procDef, modelo.chave(), pi, exec, extra.id(), extra.nome(), extra.cpf(), null);
                tarefasNaRaiz++;
            }
            if (complementacao != null) {
                gravarComplementacao(pi);
            }
            return new Resultado(id, pi, false, tarefasNaRaiz, concluidos, filhasGravadas, assinaturas,
                    complementacao != null);
        }

        private String atividadeSimples() {
            if (!filhas.isEmpty()) {
                return etapa.idsTecnicos().get(etapa.processosFilhos().indexOf(filhas.getFirst()));
            }
            if (etapa != null) {
                return atividade;
            }
            if (!suporte && !extras.isEmpty()) {
                return extras.getFirst().id();
            }
            return modelo.idsDeSuporte().iterator().next();
        }

        private String responsavel(int indice, String padrao) {
            return indice < responsaveis.size() ? responsaveis.get(indice) : padrao;
        }

        private void gravarFilha(String pi, String procDefMae, String execDaChamada, String chamada, String chaveDaFilha,
                int ordem) {
            String ci = pi + "-filha-" + ordem;
            String procDef = procDef(chaveDaFilha, chaveDaFilha + " (FICTÍCIO)");
            String titulo = Long.toString(id * 10 + ordem);
            inserirAtividade(pi, procDefMae, modelo.chave(), pi, execDaChamada, chamada, "callActivity", null, ci, null, desde,
                    null);
            inserirHiProcinst(ci, titulo, chaveDaFilha, procDef, desde, pi, pi);
            inserirExecucao(ci, ci, null, titulo, procDef, pi, execDaChamada, TAREFA_DA_FILHA, true, false, true);
            inserirTarefaAtiva(ci, procDef, chaveDaFilha, pi, ci, TAREFA_DA_FILHA, "Tarefa da emissão (FICTÍCIO)", null,
                    GRUPO_EMISSOR);
        }

        private void gravarComplementacao(String pi) {
            jdbc.update("""
                    INSERT INTO SIGA2_LICENCIAMENTO.COMPLEMENTACAO (ID, ID_REQUERIMENTO, DATA_INICIO, DATA_ENTREGA, SITUACAO)
                    VALUES (:id, :requerimento, :inicio, NULL, 'EM_ANDAMENTO_FICTICIO')""",
                    new MapSqlParameterSource().addValue("id", complementacao).addValue("requerimento", id)
                            .addValue("inicio", Timestamp.valueOf(desde)));
            String ce = pi + "-compl";
            String procDef = procDef(CHAVE_COMPLEMENTACAO, "Complementação (FICTÍCIO)");
            String chave = Long.toString(complementacao);
            inserirHiProcinst(ce, chave, CHAVE_COMPLEMENTACAO, procDef, desde, null, ce);
            inserirExecucao(ce, ce, null, chave, procDef, ce, null, TAREFA_DA_COMPLEMENTACAO, true, false, true);
            inserirTarefaAtiva(ce, procDef, CHAVE_COMPLEMENTACAO, ce, ce, TAREFA_DA_COMPLEMENTACAO,
                    "Complementação (FICTÍCIO)", CPF_DO_INTERESSADO, null);
        }

        private String tipoDaAtividade(String idTecnico) {
            ResolucaoDeEtapa r = modelo.resolver(idTecnico, Set.of());
            if (r instanceof ResolucaoDeEtapa.NaEtapa(Etapa e)) {
                return switch (e.tipo()) {
                    case TAREFA_HUMANA -> "userTask";
                    case ESPERA -> "eventBasedGateway";
                    case EMISSAO -> "callActivity";
                    default -> "serviceTask";
                };
            }
            if (r instanceof ResolucaoDeEtapa.Ignorado(IdIgnorado ig) && ig.motivo() == IdIgnorado.Motivo.SUPORTE) {
                return "userTask";
            }
            return "serviceTask";
        }

        private void inserirTarefaAtiva(String proc, String procDef, String chave, String raiz, String exec, String chaveDaTarefa,
                String nome, String cpf, String grupoCandidato) {
            String tarefa = proc + "-t" + sequencia.incrementAndGet();
            jdbc.update("""
                    INSERT INTO SIGA2_CAMUNDA.ACT_RU_TASK
                           (ID_, PROC_INST_ID_, PROC_DEF_ID_, NAME_, ASSIGNEE_, CREATE_TIME_, EXECUTION_ID_, TASK_DEF_KEY_)
                    VALUES (:id, :proc, :procDef, :nome, :assignee, :criada, :exec, :chave)""",
                    new MapSqlParameterSource()
                            .addValue("id", tarefa).addValue("proc", proc).addValue("procDef", procDef).addValue("nome", nome)
                            .addValue("assignee", cpf).addValue("criada", Timestamp.valueOf(desde)).addValue("exec", exec)
                            .addValue("chave", chaveDaTarefa));
            if (grupoCandidato != null) {
                jdbc.update("""
                        INSERT INTO SIGA2_CAMUNDA.ACT_RU_IDENTITYLINK (ID_, GROUP_ID_, TYPE_, USER_ID_, TASK_ID_, PROC_DEF_ID_)
                        VALUES (:id, :grupo, 'candidate', NULL, :tarefa, NULL)""",
                        new MapSqlParameterSource().addValue("id", tarefa + "-g").addValue("grupo", grupoCandidato)
                                .addValue("tarefa", tarefa));
            }
            inserirHiTarefa(tarefa, chaveDaTarefa, chave, procDef, raiz, proc, exec, cpf, desde, null, null);
            inserirAtividade(proc, procDef, chave, raiz, exec, chaveDaTarefa, "userTask", tarefa, null, cpf, desde, null);
        }

        private void inserirHiTarefa(String tarefa, String chaveDaTarefa, String chave, String procDef, String raiz, String proc,
                String exec, String cpf, LocalDateTime inicio, LocalDateTime termino, String motivo) {
            jdbc.update("""
                    INSERT INTO SIGA2_CAMUNDA.ACT_HI_TASKINST
                           (ID_, TASK_DEF_KEY_, PROC_DEF_KEY_, PROC_DEF_ID_, ROOT_PROC_INST_ID_, PROC_INST_ID_, EXECUTION_ID_,
                            NAME_, ASSIGNEE_, START_TIME_, END_TIME_, DELETE_REASON_)
                    VALUES (:id, :chaveTarefa, :chave, :procDef, :raiz, :proc, :exec, :nome, :assignee, :inicio, :fim, :motivo)""",
                    new MapSqlParameterSource()
                            .addValue("id", tarefa).addValue("chaveTarefa", chaveDaTarefa).addValue("chave", chave)
                            .addValue("procDef", procDef).addValue("raiz", raiz).addValue("proc", proc).addValue("exec", exec)
                            .addValue("nome", chaveDaTarefa + " (FICTÍCIO)").addValue("assignee", cpf)
                            .addValue("inicio", Timestamp.valueOf(inicio))
                            .addValue("fim", termino == null ? null : Timestamp.valueOf(termino)).addValue("motivo", motivo));
        }

        private String novaExecucao(String pi, String procDef, String pai, String atividadeDaExecucao, boolean concorrente,
                boolean escopo, boolean ativa) {
            String exec = pi + "-e" + sequencia.incrementAndGet();
            inserirExecucao(exec, pi, pai, null, procDef, pi, null, atividadeDaExecucao, ativa, concorrente, escopo);
            return exec;
        }
    }

    private String procDef(String chave, String nome) {
        String procDef = chave + ":1:ficticio";
        jdbc.update("""
                MERGE INTO SIGA2_CAMUNDA.ACT_RE_PROCDEF d
                USING (SELECT :id AS ID_ FROM DUAL) s
                ON (d.ID_ = s.ID_)
                WHEN NOT MATCHED THEN INSERT (ID_, NAME_, KEY_, VERSION_) VALUES (:id, :nome, :chave, 1)""",
                new MapSqlParameterSource().addValue("id", procDef).addValue("nome", nome).addValue("chave", chave));
        return procDef;
    }

    private void inserirHiProcinst(String pi, String businessKey, String chave, String procDef, LocalDateTime inicio,
            String superInstancia, String raiz) {
        jdbc.update("""
                INSERT INTO SIGA2_CAMUNDA.ACT_HI_PROCINST
                       (ID_, PROC_INST_ID_, BUSINESS_KEY_, PROC_DEF_KEY_, PROC_DEF_ID_, START_TIME_, END_TIME_, END_ACT_ID_,
                        SUPER_PROCESS_INSTANCE_ID_, ROOT_PROC_INST_ID_, DELETE_REASON_, STATE_)
                VALUES (:pi, :pi, :bk, :chave, :procDef, :inicio, NULL, NULL, :super, :raiz, NULL, 'ACTIVE')""",
                new MapSqlParameterSource()
                        .addValue("pi", pi).addValue("bk", businessKey).addValue("chave", chave).addValue("procDef", procDef)
                        .addValue("inicio", Timestamp.valueOf(inicio)).addValue("super", superInstancia).addValue("raiz", raiz));
    }

    private void inserirExecucao(String exec, String proc, String pai, String businessKey, String procDef, String raiz,
            String superExec, String atividade, boolean ativa, boolean concorrente, boolean escopo) {
        jdbc.update("""
                INSERT INTO SIGA2_CAMUNDA.ACT_RU_EXECUTION
                       (ID_, PROC_INST_ID_, PARENT_ID_, BUSINESS_KEY_, PROC_DEF_ID_, ROOT_PROC_INST_ID_, SUPER_EXEC_, ACT_ID_,
                        IS_ACTIVE_, IS_CONCURRENT_, IS_SCOPE_)
                VALUES (:id, :proc, :pai, :bk, :procDef, :raiz, :superExec, :atividade, :ativa, :concorrente, :escopo)""",
                new MapSqlParameterSource()
                        .addValue("id", exec).addValue("proc", proc).addValue("pai", pai).addValue("bk", businessKey)
                        .addValue("procDef", procDef).addValue("raiz", raiz).addValue("superExec", superExec)
                        .addValue("atividade", atividade).addValue("ativa", ativa ? 1 : 0)
                        .addValue("concorrente", concorrente ? 1 : 0).addValue("escopo", escopo ? 1 : 0));
    }

    private void inserirAtividade(String proc, String procDef, String chave, String raiz, String exec, String atividade,
            String tipo, String tarefa, String chamada, String cpf, LocalDateTime inicio, LocalDateTime termino) {
        jdbc.update(SQL_ATIVIDADE, parametrosDaAtividade(proc, procDef, chave, raiz, exec, atividade, tipo, tarefa, chamada,
                cpf, inicio, termino));
    }

    /** Id com seis dígitos: a ordem textual dos ids é a ordem de gravação (o caminho tem milhares de linhas). */
    private MapSqlParameterSource parametrosDaAtividade(String proc, String procDef, String chave, String raiz, String exec,
            String atividade, String tipo, String tarefa, String chamada, String cpf, LocalDateTime inicio,
            LocalDateTime termino) {
        long n = sequencia.incrementAndGet();
        return new MapSqlParameterSource()
                .addValue("id", String.format("%s-a%06d", proc, n)).addValue("chave", chave)
                .addValue("procDef", procDef).addValue("raiz", raiz).addValue("proc", proc).addValue("exec", exec)
                .addValue("atividade", atividade).addValue("tarefa", tarefa).addValue("chamada", chamada)
                .addValue("nome", atividade + " (FICTÍCIO)").addValue("tipo", tipo).addValue("assignee", cpf)
                .addValue("inicio", Timestamp.valueOf(inicio))
                .addValue("fim", termino == null ? null : Timestamp.valueOf(termino))
                .addValue("seq", n);
    }

    /** Marca a instância (filha, complementação) como encerrada em {@code quando}. */
    private void encerrarInstancia(String instancia, LocalDateTime quando, String atividadeFinal, String estado,
            String motivo) {
        jdbc.update("""
                UPDATE SIGA2_CAMUNDA.ACT_HI_PROCINST
                SET    END_TIME_ = :fim, END_ACT_ID_ = :atividade, STATE_ = :estado, DELETE_REASON_ = :motivo
                WHERE  ID_ = :pi""", new MapSqlParameterSource().addValue("fim", Timestamp.valueOf(quando))
                .addValue("atividade", atividadeFinal).addValue("estado", estado).addValue("motivo", motivo)
                .addValue("pi", instancia));
    }
}
