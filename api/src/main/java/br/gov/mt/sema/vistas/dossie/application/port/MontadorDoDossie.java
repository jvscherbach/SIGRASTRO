package br.gov.mt.sema.vistas.dossie.application.port;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;

/** Monta o PDF final: capa, sumário, documentos na ordem recebida, marcadores e numeração. Devolve o total de páginas. */
public interface MontadorDoDossie {

    int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, OpcoesDeMontagem opcoes, Path destino);

    /** @param paginas páginas do arquivo, já contadas na validação (o sumário depende delas) */
    record DocumentoParaMontagem(String titulo, String origem, LocalDateTime data, Path arquivo, int paginas) {
    }

    record OpcoesDeMontagem(boolean sumario, boolean marcadores, boolean numeracao) {
    }
}
