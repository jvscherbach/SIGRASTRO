package br.gov.mt.sema.rastro.dossie.api.dto;

import java.time.LocalDateTime;

import br.gov.mt.sema.rastro.dossie.application.ListarDossiesGerados.DossieGerado;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Item de "Dossiês gerados" (S4). Sem dado de documento (título, nome de arquivo, chave). {@code geradoPor} só existe
 * para Gestor e Administrador: para o Operador o campo nem aparece. {@code baixado} (S6): o download já foi completo e o
 * arquivo está na janela de nova tentativa (D77). {@code falha} (S6) só em FALHA: código e texto para o usuário, sem
 * caminho de arquivo.
 */
public record DossieGeradoResponse(String id, long idRequerimento, Identificacao identificacao, String situacao,
        LocalDateTime criadoEm, LocalDateTime expiraEm, int documentos, int falhas, int listados, Long tamanhoBytes,
        boolean entregaPorTicket, boolean temArquivo, boolean arquivoGerado,
        @JsonInclude(JsonInclude.Include.NON_NULL) String geradoPor, boolean baixado,
        @JsonInclude(JsonInclude.Include.NON_NULL) Falha falha) {

    /** "Processo 2825/2025" ou "Requerimento 3331/2025" (RN04, RF18). */
    public record Identificacao(String rotulo, String numero) {
    }

    /** Motivo da FALHA (ex.: ARMAZENAMENTO_INSUFICIENTE). */
    public record Falha(String codigo, String mensagem) {
    }

    public static DossieGeradoResponse de(DossieGerado d) {
        Falha falha = d.codigoDaFalha() == null && d.motivoDaFalha() == null ? null
                : new Falha(d.codigoDaFalha(), d.motivoDaFalha());
        return new DossieGeradoResponse(d.id(), d.idRequerimento(), new Identificacao(d.rotulo(), d.numero()),
                d.situacao().name(), d.criadoEm(), d.expiraEm(), d.documentos(), d.falhas(), d.listados(),
                d.tamanhoBytes(), d.entregaPorTicket(), d.temArquivo(), d.arquivoGerado(), d.geradoPor(), d.baixado(),
                falha);
    }
}
