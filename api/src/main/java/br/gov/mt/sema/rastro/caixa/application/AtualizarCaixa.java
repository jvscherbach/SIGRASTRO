package br.gov.mt.sema.rastro.caixa.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import br.gov.mt.sema.rastro.caixa.application.port.VistoDoAnalista;
import br.gov.mt.sema.rastro.caixa.domain.AnalistaParaLeitura;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DecidirPosse;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import br.gov.mt.sema.rastro.caixa.domain.InstantaneoDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.LeituraDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.caixa.domain.RegrasDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Atualiza o instantâneo da caixa (S11, bloco 5), com o mesmo padrão do painel da S10: uma atualização por vez (single
 * flight), em segundo plano; lê a posse em lotes ({@link LerPosseEmLotes}), decide o estado de cada requerimento, lê a origem
 * só dos avisados e <b>troca o instantâneo de uma vez</b>. Falha em qualquer ponto não troca o instantâneo (só marca
 * DESATUALIZADO ou INDISPONIVEL) e o log leva só a classe da exceção. Nunca consulta o Oracle nem o Camunda dentro de uma
 * requisição: quem chama só dispara. Os parâmetros são lidos a cada ciclo.
 *
 * <p>Falha ao ler as origens não perde a atualização: os avisados ficam "Mudou de etapa". Analista sem visto ainda (primeiro
 * acesso) não tem avisados, então não gasta leitura de histórico.
 */
@Component
public class AtualizarCaixa {

    private static final Logger log = LoggerFactory.getLogger(AtualizarCaixa.class);

    private final LerPosseEmLotes posse;
    private final LerOrigensDoRetorno origens;
    private final ChavesDosAnalistas analistas;
    private final CatalogoDeEtapas catalogo;
    private final ParametrosDaCaixa parametros;
    private final VistoDoAnalista visto;
    private final GuardarInstantaneoDaCaixa guardar;
    private final Clock relogio;
    private final Executor executor;
    private final LongSupplier agoraEmNanos;

    private final AtomicBoolean emAndamento = new AtomicBoolean();
    private volatile Instant ultimoInicio;

    @Autowired
    public AtualizarCaixa(LerPosseEmLotes posse, LerOrigensDoRetorno origens, ChavesDosAnalistas analistas,
            CatalogoDeEtapas catalogo, ParametrosDaCaixa parametros, VistoDoAnalista visto,
            GuardarInstantaneoDaCaixa guardar, Clock relogio) {
        this(posse, origens, analistas, catalogo, parametros, visto, guardar, relogio, executorEmSegundoPlano(),
                System::nanoTime);
    }

    /** Para teste: executor e relógio monotônico controlados. */
    AtualizarCaixa(LerPosseEmLotes posse, LerOrigensDoRetorno origens, ChavesDosAnalistas analistas,
            CatalogoDeEtapas catalogo, ParametrosDaCaixa parametros, VistoDoAnalista visto,
            GuardarInstantaneoDaCaixa guardar, Clock relogio, Executor executor, LongSupplier agoraEmNanos) {
        this.posse = posse;
        this.origens = origens;
        this.analistas = analistas;
        this.catalogo = catalogo;
        this.parametros = parametros;
        this.visto = visto;
        this.guardar = guardar;
        this.relogio = relogio;
        this.executor = executor;
        this.agoraEmNanos = agoraEmNanos;
    }

    private static Executor executorEmSegundoPlano() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "caixa-atualizacao");
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
            log.warn("Atualização da caixa não pôde ser iniciada: {}", e.getClass().getSimpleName());
            guardar.marcarFalha(relogio.instant());
            emAndamento.set(false);
            return false;
        }
        return true;
    }

    /** A primeira leitura: dispara na primeira requisição de um Analista, se nunca houve nenhuma. */
    public void iniciarSeNuncaLeu() {
        if (ultimoInicio == null) {
            iniciarEmSegundoPlano();
        }
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
            log.warn("Atualização da caixa falhou: {}", e.getClass().getSimpleName());
            guardar.marcarFalha(relogio.instant());
        } finally {
            emAndamento.set(false);
        }
    }

    private InstantaneoDaCaixa montar() {
        Duration total = Duration.ofSeconds(parametros.timeoutSegundos());
        long prazo = agoraEmNanos.getAsLong() + total.toNanos();
        InstantaneoDaCaixa anterior = guardar.atual();
        List<AnalistaParaLeitura> quem = analistas.analistas();
        DefinicaoDaPosse definicao = DefinicaoDaPosse.doCatalogo(catalogo);

        LeituraDaPosse leitura = posse.ler(quem, definicao, parametros.limiteRequerimentos(), total);
        Map<ChaveDoResponsavel, List<ItemDaCaixa>> itens = DecidirPosse.agrupar(leitura.requerimentos(), definicao, catalogo);
        Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> origensDosAvisados =
                origensDosAvisados(itens, anterior, Duration.ofNanos(Math.max(0, prazo - agoraEmNanos.getAsLong())));
        return InstantaneoDaCaixa.pronto(relogio.instant(), leitura.incompleta(), itens, origensDosAvisados);
    }

    /**
     * Os avisados de cada analista que já tem visto (primeiro acesso não avisa). A origem de quem já estava avisado e na posse
     * no retrato anterior é reaproveitada; as demais são lidas uma vez por requerimento, com teto.
     */
    private Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> origensDosAvisados(
            Map<ChaveDoResponsavel, List<ItemDaCaixa>> itens, InstantaneoDaCaixa anterior, Duration tempo) {
        Map<ChaveDoResponsavel, List<ItemDaCaixa>> avisados = new HashMap<>();
        Set<Long> aLer = new LinkedHashSet<>();
        Map<ChaveDoResponsavel, Map<Long, OrigemDoRetorno>> resultado = new HashMap<>();
        for (var entrada : itens.entrySet()) {
            ChaveDoResponsavel analista = entrada.getKey();
            Optional<Map<Long, EstadoDePosse>> ultimo = visto.ultimo(analista);
            if (ultimo.isEmpty()) {
                continue;
            }
            List<ItemDaCaixa> novos = RegrasDoAviso.avisados(entrada.getValue(), ultimo.get());
            if (novos.isEmpty()) {
                continue;
            }
            avisados.put(analista, novos);
            Map<Long, OrigemDoRetorno> deste = new HashMap<>();
            for (ItemDaCaixa item : novos) {
                Optional<OrigemDoRetorno> conhecida = origemMantida(anterior, analista, item);
                if (conhecida.isPresent()) {
                    deste.put(item.id(), conhecida.get());
                } else {
                    aLer.add(item.id());
                }
            }
            resultado.put(analista, deste);
        }
        if (aLer.isEmpty()) {
            return resultado;
        }
        Map<Long, OrigemDoRetorno> lidas;
        try {
            lidas = origens.ler(new ArrayList<>(aLer), tempo);
        } catch (RuntimeException e) {
            log.warn("Origens do retorno não lidas: {}", e.getClass().getSimpleName());
            lidas = Map.of();
        }
        for (var entrada : avisados.entrySet()) {
            Map<Long, OrigemDoRetorno> deste = resultado.get(entrada.getKey());
            for (ItemDaCaixa item : entrada.getValue()) {
                if (!deste.containsKey(item.id())) {
                    deste.put(item.id(), lidas.getOrDefault(item.id(), OrigemDoRetorno.MUDOU_DE_ETAPA));
                }
            }
        }
        return resultado;
    }

    /** Já estava na posse do analista no retrato anterior, com origem lida: é a mesma chegada. */
    private static Optional<OrigemDoRetorno> origemMantida(InstantaneoDaCaixa anterior, ChaveDoResponsavel analista,
            ItemDaCaixa item) {
        boolean jaEstava = anterior.itensDe(analista).stream()
                .anyMatch(i -> i.id() == item.id() && i.estado().comAnalista());
        return jaEstava ? anterior.origemDe(analista, item.id()) : Optional.empty();
    }
}
