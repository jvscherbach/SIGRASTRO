package br.gov.mt.sema.rastro.auditoria.api.dto;

import java.time.ZonedDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;

/** Gaveta de detalhe: a linha da lista mais a origem, a correlação, os documentos e o antes e depois. */
public record EventoDetalheResponse(long id, ZonedDateTime dataHora, String tipo, String rotulo,
        UsuarioDoEventoResponse usuario, Long idRequerimento, String numeroProcesso, String numeroRequerimento,
        boolean sigiloso, String arquivo, String idPedido, String detalhe, String origem, String correlationId,
        List<Documento> documentos, List<Alteracao> alteracoes) {

    public record Documento(String origem, String id, String titulo, String nomeDoArquivo, boolean sigiloso,
            Integer ordem) {

        static Documento de(DocumentoDoEvento d) {
            return new Documento(d.origem(), d.id(), d.titulo(), d.nomeDoArquivo(), d.sigiloso(), d.ordem());
        }
    }

    public record Alteracao(String item, String valorAnterior, String valorNovo) {

        static Alteracao de(AlteracaoDoEvento a) {
            return new Alteracao(a.item(), a.valorAnterior(), a.valorNovo());
        }
    }

    public static EventoDetalheResponse de(EventoDeAuditoria evento) {
        return new EventoDetalheResponse(evento.id(), evento.dataHora(), evento.tipo().name(), evento.tipo().rotulo(),
                UsuarioDoEventoResponse.de(evento.usuario()), evento.idRequerimento(), evento.numeroProcesso(),
                evento.numeroRequerimento(), evento.sigiloso(), evento.arquivo(), evento.idPedido(), evento.detalhe(),
                evento.origem(), evento.correlationId(), evento.documentos().stream().map(Documento::de).toList(),
                evento.alteracoes().stream().map(Alteracao::de).toList());
    }
}
