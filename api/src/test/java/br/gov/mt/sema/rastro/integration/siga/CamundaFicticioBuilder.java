package br.gov.mt.sema.rastro.integration.siga;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.IdIgnorado;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResolucaoDeEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TipoDeEtapa;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

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
 * {@link Processo#percorrendo}. O caminho histórico detalhado é da S8: aqui só o necessário para a posição atual.
 *
 * <pre>{@code
 * camunda.processo(Fluxo.HIDRICO, 7007).naEtapa("taskAnalisarRequerimento").comAnalistas(3).desde(data).gravar();
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

    public Processo processo(Fluxo fluxo, long idRequerimento) {
        return new Processo(fluxo, idRequerimento);
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

        /** Requerimento legado sem instância no Camunda (antes de 2023). */
        public Processo semInstancia() {
            this.semInstancia = true;
            return this;
        }

        public Resultado gravar() {
            boolean comInstancia = fluxo.chave != null && !semInstancia;
            validar(comInstancia);
            LocalDateTime inicio = desde.minusDays(percurso.size() + 1L);
            gravarRequerimento(comInstancia ? inicio : desde);
            if (!comInstancia) {
                return new Resultado(id, null, false, 0, 0, 0, 0, false);
            }
            String pi = "fict-" + id;
            String procDef = procDef(modelo.chave(), modelo.rotulo());
            inserirHiProcinst(pi, Long.toString(id), modelo.chave(), procDef, inicio, null, pi);
            gravarPercurso(pi, procDef, inicio);
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
                if (etapa != null || fim != null || suporte || complementacao != null || !filhas.isEmpty()) {
                    throw new IllegalStateException("requerimento sem instância não tem etapa, final nem filhas");
                }
                return;
            }
            boolean ativa = etapa != null || suporte;
            if (fim != null && (ativa || complementacao != null)) {
                throw new IllegalStateException("instância encerrada não tem etapa ativa nem complementação");
            }
            if (fim == null && !ativa) {
                throw new IllegalStateException("cenário sem etapa ativa: use naEtapa, comFilhas, comTarefaDeSuporte ou encerradoEm");
            }
            if (etapa != null && !etapa.aceitaPercurso(Set.copyOf(percurso))) {
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

        private Resultado gravarAtiva(String pi, String procDef) {
            int elementos = (etapa != null ? Math.max(1, filhas.size()) : 0) + (suporte ? 1 : 0);
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
        jdbc.update("""
                INSERT INTO SIGA2_CAMUNDA.ACT_HI_ACTINST
                       (ID_, PROC_DEF_KEY_, PROC_DEF_ID_, ROOT_PROC_INST_ID_, PROC_INST_ID_, EXECUTION_ID_, ACT_ID_, TASK_ID_,
                        CALL_PROC_INST_ID_, ACT_NAME_, ACT_TYPE_, ASSIGNEE_, START_TIME_, END_TIME_, SEQUENCE_COUNTER_)
                VALUES (:id, :chave, :procDef, :raiz, :proc, :exec, :atividade, :tarefa, :chamada, :nome, :tipo, :assignee,
                        :inicio, :fim, :seq)""",
                new MapSqlParameterSource()
                        .addValue("id", proc + "-a" + sequencia.incrementAndGet()).addValue("chave", chave)
                        .addValue("procDef", procDef).addValue("raiz", raiz).addValue("proc", proc).addValue("exec", exec)
                        .addValue("atividade", atividade).addValue("tarefa", tarefa).addValue("chamada", chamada)
                        .addValue("nome", atividade + " (FICTÍCIO)").addValue("tipo", tipo).addValue("assignee", cpf)
                        .addValue("inicio", Timestamp.valueOf(inicio))
                        .addValue("fim", termino == null ? null : Timestamp.valueOf(termino))
                        .addValue("seq", sequencia.get()));
    }
}
