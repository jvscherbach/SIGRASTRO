package br.gov.mt.sema.rastro.integration.licenciamento;

import java.nio.file.Path;

import br.gov.mt.sema.rastro.documento.application.port.ArquivosDoDocumento;
import br.gov.mt.sema.rastro.documento.application.port.FalhaAoObterArquivoException;
import br.gov.mt.sema.rastro.documento.application.port.FalhaAoObterArquivoException.Motivo;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import org.springframework.stereotype.Component;

/**
 * Obtenção para o modal (S3) com o mesmo cliente do dossiê: mesmos tempos, novas tentativas e tratamento do token.
 * Só aqui a chave é lida do documento. A falha do documento vira o motivo tipado do módulo documento; credencial
 * recusada segue como está (502 {@code CREDENCIAL_ARQUIVOS_RECUSADA}).
 */
@Component
class ArquivosParaVisualizacao implements ArquivosDoDocumento {

    private final ArquivosDoRepositorio repositorio;

    ArquivosParaVisualizacao(ArquivosDoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void baixar(Documento documento, Path destino) {
        try {
            repositorio.baixar(documento.chaveDoArquivo(), destino);
        } catch (FalhaNoDocumentoException e) {
            throw new FalhaAoObterArquivoException(motivo(e.getMessage()), e.getMessage());
        }
    }

    private static Motivo motivo(String mensagem) {
        if (ArquivosDoLicenciamentoHttp.NAO_ENCONTRADO.equals(mensagem)) {
            return Motivo.NAO_ENCONTRADO;
        }
        if (ArquivosDoLicenciamentoHttp.TEMPO_LIMITE.equals(mensagem)
                || ArquivosDoLicenciamentoHttp.TETO_TOTAL.equals(mensagem)) {
            return Motivo.TEMPO_LIMITE;
        }
        return Motivo.INDISPONIVEL;
    }
}
