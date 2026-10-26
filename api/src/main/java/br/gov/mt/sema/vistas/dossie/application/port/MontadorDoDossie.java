package br.gov.mt.sema.vistas.dossie.application.port;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.DocumentoNaoIncluido;

/**
 * Monta o PDF final: capa, sumário, "Documentos não incluídos" (S2.2, omitida se vazia), documentos na ordem
 * recebida, marcadores e numeração. Devolve o total de páginas.
 */
public interface MontadorDoDossie {

    int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
            OpcoesDeMontagem opcoes, Path destino);

    /** Sem nada fora do dossiê: tudo o que foi listado foi incluído. */
    default int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, OpcoesDeMontagem opcoes, Path destino) {
        return montar(capa, documentos, new Inventario(documentos.size(), List.of()), opcoes, destino);
    }

    /** @param paginas páginas do arquivo, já contadas na validação (o sumário depende delas) */
    record DocumentoParaMontagem(String titulo, String origem, LocalDateTime data, Path arquivo, int paginas) {
    }

    record OpcoesDeMontagem(boolean sumario, boolean marcadores, boolean numeracao) {
    }

    /**
     * O que a lista tinha e o que ficou de fora (capa: "N documentos incluídos de M listados").
     *
     * @param listados total da lista que o usuário viu ao gerar
     */
    record Inventario(int listados, List<DocumentoNaoIncluido> naoIncluidos) {

        public Inventario {
            naoIncluidos = List.copyOf(naoIncluidos);
        }
    }
}
