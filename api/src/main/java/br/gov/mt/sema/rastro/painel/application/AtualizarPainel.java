package br.gov.mt.sema.rastro.painel.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import br.gov.mt.sema.rastro.painel.application.port.ResumoDoAnoNoSiga;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.AvisoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.CargaPorResponsavel;
import br.gov.mt.sema.rastro.painel.domain.ContarCargaPorResponsavel;
import br.gov.mt.sema.rastro.painel.domain.ContarFilaPorEtapa;
import br.gov.mt.sema.rastro.painel.domain.FilaPorEtapa;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.LeituraDasTarefas;
import br.gov.mt.sema.rastro.painel.domain.MontarSerieDoAno;
import br.gov.mt.sema.rastro.painel.domain.RequerimentoDaBase;
import br.gov.mt.sema.rastro.painel.domain.SerieDoAno;
import br.gov.mt.sema.rastro.painel.domain.SituacoesForaDaFila;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Atualiza o instantâneo do painel (S10, bloco 4): lê a série, a base e as tarefas em lotes, monta com as regras do
 * domínio e <b>troca o instantâneo de uma vez</b>. Uma atualização por vez (single flight), em segundo plano; o
 * tempo total é um só para as três leituras; falha em qualquer ponto <b>não troca</b> o instantâneo (só marca
 * DESATUALIZADO ou INDISPONIVEL) e o log leva só a classe da exceção (sem mensagem, que poderia trazer dado).
 * Os parâmetros são lidos a cada ciclo.
 */
@Component
public class AtualizarPainel {

    private static final Logger log = LoggerFactory.getLogger(AtualizarPainel.class);

    private final ResumoDoAnoNoSiga resumo;
    private final LerTarefasEmLotes tarefas;
    private final CatalogoDeEtapas catalogo;
    private final CatalogoDeGrupos grupos;
    private final ParametrosDoPainel parametros;
    private final GuardarInstantaneo guardar;
    private final Clock relogio;
    private final Executor executor;
    private final LongSupplier agoraEmNanos;

    private final AtomicBoolean emAndamento = new AtomicBoolean();
    private volatile Instant ultimoInicio;

    @Autowired
    public AtualizarPainel(ResumoDoAnoNoSiga resumo, LerTarefasEmLotes tarefas, CatalogoDeEtapas catalogo,
            CatalogoDeGrupos grupos, ParametrosDoPainel parametros, GuardarInstantaneo guardar, Clock relogio) {
        this(resumo, tarefas, catalogo, grupos, parametros, guardar, relogio, executorEmSegundoPlano(), System::nanoTime);
    }

    /** Para teste: executor e relógio monotônico controlados. */
    AtualizarPainel(ResumoDoAnoNoSiga resumo, LerTarefasEmLotes tarefas, CatalogoDeEtapas catalogo,
            CatalogoDeGrupos grupos, ParametrosDoPainel parametros, GuardarInstantaneo guardar, Clock relogio,
            Executor executor, LongSupplier agoraEmNanos) {
        this.resumo = resumo;
        this.tarefas = tarefas;
        this.catalogo = catalogo;
        this.grupos = grupos;
        this.parametros = parametros;
        this.guardar = guardar;
        this.relogio = relogio;
        this.executor = executor;
        this.agoraEmNanos = agoraEmNanos;
    }

    private static Executor executorEmSegundoPlano() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "painel-atualizacao");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Dispara a atualização em segundo plano, se não houver outra em curso.
     *
     * @return {@code true} se esta chamada iniciou a atualização; {@code false} se já havia uma em curso
     */
    public boolean iniciarEmSegundoPlano() {
        if (!emAndamento.compareAndSet(false, true)) {
            return false;
        }
        ultimoInicio = relogio.instant();
        try {
            executor.execute(this::rodar);
        } catch (RuntimeException e) {
            log.warn("Atualização do painel não pôde ser iniciada: {}", e.getClass().getSimpleName());
            guardar.marcarFalha(relogio.instant());
            emAndamento.set(false);
            return false;
        }
        return true;
    }

    public boolean emAndamento() {
        return emAndamento.get();
    }

    /** Quando a última atualização começou (vazio: nunca houve). */
    public Optional<Instant> ultimoInicio() {
        return Optional.ofNullable(ultimoInicio);
    }

    /** Intervalo da atualização automática, lido agora (vale no próximo ciclo). */
    public Duration intervaloAutomatico() {
        return Duration.ofMinutes(parametros.atualizacaoMinutos());
    }

    /** Disparo periódico: só depois da primeira leitura e passado o intervalo; nunca no início do sistema. */
    public void atualizarSeVencida() {
        Instant inicio = ultimoInicio;
        if (inicio == null || emAndamento.get()) {
            return;
        }
        if (!relogio.instant().isBefore(inicio.plus(intervaloAutomatico()))) {
            iniciarEmSegundoPlano();
        }
    }

    private void rodar() {
        try {
            guardar.trocar(montar());
        } catch (RuntimeException e) {
            log.warn("Atualização do painel falhou: {}", e.getClass().getSimpleName());
            guardar.marcarFalha(relogio.instant());
        } finally {
            emAndamento.set(false);
        }
    }

    private InstantaneoDoPainel montar() {
        Duration total = Duration.ofSeconds(parametros.timeoutSegundos());
        long prazo = agoraEmNanos.getAsLong() + total.toNanos();
        AnoDoPainel ano = AnoDoPainel.doParametro(parametros.ano(), relogio);

        SerieDoAno serie = MontarSerieDoAno.montar(ano.ano(), resumo.lerSerie(ano, restante(prazo)));
        BaseDoAno base = resumo.lerBase(ano, parametros.limiteRequerimentos(), restante(prazo));

        List<Long> ids = new ArrayList<>();
        for (RequerimentoDaBase r : base.requerimentos()) {
            boolean noCatalogo = r.tipoDeProcesso().flatMap(catalogo::modeloDaInstancia).isPresent();
            if (noCatalogo && !SituacoesForaDaFila.estaForaDaFila(r.situacao())) {
                ids.add(r.id());
            }
        }
        List<String> chaves = catalogo.modelos().stream().map(ModeloDeFluxo::chave).toList();
        LeituraDasTarefas leitura = tarefas.ler(ids, chaves, restante(prazo));

        FilaPorEtapa fila = ContarFilaPorEtapa.contar(catalogo, base.requerimentos(), leitura);
        CargaPorResponsavel comNomes =
                ContarCargaPorResponsavel.contar(catalogo, grupos, base.requerimentos(), leitura, true);
        CargaPorResponsavel semNomes =
                ContarCargaPorResponsavel.contar(catalogo, grupos, base.requerimentos(), leitura, false);

        boolean truncado = base.truncado() || leitura.alemDoLimite();
        List<AvisoDoPainel> avisos = new ArrayList<>(serie.avisos());
        if (base.truncado()) {
            avisos.add(new AvisoDoPainel(AvisoDoPainel.TRUNCADO,
                    "Mostrando os " + base.requerimentos().size() + " primeiros requerimentos do ano: "
                            + "a fila por etapa e a carga por responsável ficam parciais."));
        }
        if (leitura.alemDoLimite()) {
            avisos.add(new AvisoDoPainel(AvisoDoPainel.LEITURA_PARCIAL,
                    "Parte das tarefas não coube na leitura: a fila por etapa e a carga por responsável ficam parciais."));
        }
        avisos.addAll(fila.avisos());
        return InstantaneoDoPainel.pronto(ano.ano(), relogio.instant(), truncado, serie, fila, comNomes, semNomes, avisos);
    }

    private Duration restante(long prazo) {
        Duration restante = Duration.ofNanos(prazo - agoraEmNanos.getAsLong());
        if (restante.isNegative() || restante.isZero()) {
            throw new LerTarefasEmLotes.TempoDoPainelEsgotado();
        }
        return restante;
    }
}
