package br.gov.mt.sema.rastro.dossie.infrastructure.pdf;

import java.nio.file.Path;
import java.util.List;

import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.DadosDaCapa;
import org.springframework.stereotype.Component;

/** Cada geração usa uma {@link MontagemPdfBox} própria: as fontes do PDFBox não são compartilhadas entre threads. */
@Component
class MontadorPdfBox implements MontadorDoDossie {

    @Override
    public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
            OpcoesDeMontagem opcoes, Path destino) {
        return new MontagemPdfBox().montar(capa, documentos, inventario, opcoes, destino);
    }
}
