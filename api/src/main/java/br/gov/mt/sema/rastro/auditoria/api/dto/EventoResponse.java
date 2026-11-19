package br.gov.mt.sema.rastro.auditoria.api.dto;

import java.time.ZonedDateTime;

import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;

/** Linha da lista. O vínculo do interessado nunca sai (RN07, RNF09). */
public record EventoResponse(long id, ZonedDateTime dataHora, String tipo, String rotulo, UsuarioDoEventoResponse usuario,
        Long idRequerimento, String numeroProcesso, String numeroRequerimento, boolean sigiloso, String arquivo,
        String idPedido, String detalhe) {

    public static EventoResponse de(EventoDeAuditoria evento) {
        return new EventoResponse(evento.id(), evento.dataHora(), evento.tipo().name(), evento.tipo().rotulo(),
                UsuarioDoEventoResponse.de(evento.usuario()), evento.idRequerimento(), evento.numeroProcesso(),
                evento.numeroRequerimento(), evento.sigiloso(), evento.arquivo(), evento.idPedido(), evento.detalhe());
    }
}
