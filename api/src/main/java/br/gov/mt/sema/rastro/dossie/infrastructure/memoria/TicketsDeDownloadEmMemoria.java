package br.gov.mt.sema.rastro.dossie.infrastructure.memoria;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.dossie.application.port.TicketsDeDownload;
import br.gov.mt.sema.rastro.dossie.domain.TicketDeDownload;
import org.springframework.stereotype.Component;

/** Tickets em memória (D09): somem num reinício. {@code remove} garante o uso único mesmo com pedidos simultâneos. */
@Component
class TicketsDeDownloadEmMemoria implements TicketsDeDownload {

    private final Map<String, TicketDeDownload> tickets = new ConcurrentHashMap<>();

    @Override
    public void guardar(String resumo, TicketDeDownload ticket) {
        tickets.put(resumo, ticket);
    }

    @Override
    public Optional<TicketDeDownload> consumir(String resumo) {
        return Optional.ofNullable(resumo == null ? null : tickets.remove(resumo));
    }

    @Override
    public int descartarVencidos(LocalDateTime agora) {
        int antes = tickets.size();
        tickets.values().removeIf(t -> t.venceu(agora));
        return antes - tickets.size();
    }
}
