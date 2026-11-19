package br.gov.mt.sema.rastro.auditoria.infrastructure.memoria;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.auditoria.application.port.ParametrosDaAuditoria;
import br.gov.mt.sema.rastro.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.rastro.auditoria.application.port.RetencaoDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import org.springframework.stereotype.Repository;

/**
 * Eventos em memória (D09): somem ao reiniciar. Índice por grupo e data e, à parte, por id. A escrita é serializada, para
 * o id seguir a ordem do tempo; a leitura não trava e vê um retrato fracamente consistente. Descarte só pelo teto e pela
 * retenção.
 */
@Repository
class RepositorioDeEventosEmMemoria implements RepositorioDeEventos, RetencaoDeEventos {

    private record Chave(Instant instante, long id) implements Comparable<Chave> {

        private static final Comparator<Chave> ORDEM = Comparator.comparing(Chave::instante).thenComparingLong(Chave::id);

        private static Chave de(EventoDeAuditoria evento) {
            return new Chave(evento.dataHora().toInstant(), evento.id());
        }

        @Override
        public int compareTo(Chave outra) {
            return ORDEM.compare(this, outra);
        }
    }

    private final Map<GrupoDeEvento, ConcurrentSkipListMap<Chave, EventoDeAuditoria>> porGrupo =
            new EnumMap<>(GrupoDeEvento.class);
    private final Map<Long, EventoDeAuditoria> porId = new ConcurrentHashMap<>();
    private final ParametrosDaAuditoria parametros;
    private final Clock relogio;
    private long ultimoId;

    RepositorioDeEventosEmMemoria(ParametrosDaAuditoria parametros, Clock relogio) {
        this.parametros = parametros;
        this.relogio = relogio;
        for (GrupoDeEvento grupo : GrupoDeEvento.values()) {
            porGrupo.put(grupo, new ConcurrentSkipListMap<>());
        }
    }

    @Override
    public synchronized EventoDeAuditoria incluir(NovoEvento novo) {
        EventoDeAuditoria evento = EventoDeAuditoria.de(ultimoId + 1,
                relogio.instant().atZone(EventoDeAuditoria.FUSO), novo);
        ultimoId = evento.id();
        porId.put(evento.id(), evento);
        porGrupo.get(evento.grupo()).put(Chave.de(evento), evento);
        int teto = parametros.maximoDeEventos();
        while (porId.size() > teto) {
            descartarMaisAntigo();
        }
        return evento;
    }

    @Override
    public Stream<EventoDeAuditoria> doPeriodo(GrupoDeEvento grupo, Instant desde, Instant ate) {
        NavigableMap<Chave, EventoDeAuditoria> eventos = porGrupo.get(grupo);
        if (desde != null) {
            eventos = eventos.tailMap(new Chave(desde, Long.MIN_VALUE), true);
        }
        if (ate != null) {
            eventos = eventos.headMap(new Chave(ate, Long.MIN_VALUE), false);
        }
        return eventos.descendingMap().values().stream();
    }

    @Override
    public Optional<EventoDeAuditoria> porId(GrupoDeEvento grupo, long id) {
        return Optional.ofNullable(porId.get(id)).filter(evento -> evento.grupo() == grupo);
    }

    @Override
    public synchronized int aplicar() {
        int descartados = 0;
        for (Map.Entry<GrupoDeEvento, ConcurrentSkipListMap<Chave, EventoDeAuditoria>> grupo : porGrupo.entrySet()) {
            Instant limite = relogio.instant().atZone(EventoDeAuditoria.FUSO)
                    .minusYears(parametros.retencaoEmAnos(grupo.getKey())).toInstant();
            ConcurrentSkipListMap<Chave, EventoDeAuditoria> eventos = grupo.getValue();
            while (!eventos.isEmpty() && eventos.firstKey().instante().isBefore(limite)) {
                porId.remove(eventos.pollFirstEntry().getKey().id());
                descartados++;
            }
        }
        return descartados;
    }

    private void descartarMaisAntigo() {
        porGrupo.values().stream()
                .filter(eventos -> !eventos.isEmpty())
                .min(Comparator.comparing(ConcurrentSkipListMap::firstKey))
                .map(ConcurrentSkipListMap::pollFirstEntry)
                .filter(Objects::nonNull)
                .ifPresent(maisAntigo -> porId.remove(maisAntigo.getKey().id()));
    }
}
