package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.EspacoEmDisco;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.ValidadorDePdf;
import br.gov.mt.sema.vistas.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.SituacaoItem;
import br.gov.mt.sema.vistas.shared.application.exception.CredencialArquivosRecusadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa a geração de um pedido (docs/06). Percorre os documentos na ordem cronológica e, para cada um ainda não
 * resolvido, obtém o PDF e o valida, coletando as falhas sem parar na primeira (S2.2). Sem falhas, monta capa,
 * sumário, documentos, marcadores e numeração. Com falhas, o pedido fica em PENDENTE_DECISAO e nada é entregue (RN09):
 * os arquivos já obtidos ficam no diretório do pedido, e a continuação ({@link ContinuarDossie}) só obtém o que falta.
 * Credencial recusada (401/403) interrompe a obtenção na hora, também em PENDENTE_DECISAO. Esta classe não usa o
 * SIGA (o instantâneo já está no pedido).
 */
@Service
public class GeradorDeDossie {

    private static final Logger log = LoggerFactory.getLogger(GeradorDeDossie.class);
    private static final String ARQUIVO_FINAL = "dossie.pdf";
    static final String ESPACO_INSUFICIENTE = "espaço em disco insuficiente";
    static final String FALHA_INESPERADA = "falha inesperada ao obter o documento";

    private final ArquivosDoRepositorio arquivos;
    private final ValidadorDePdf validador;
    private final MontadorDoDossie montador;
    private final ParametrosDoDossie parametros;
    private final EspacoEmDisco espaco;
    private final Clock relogio;

    public GeradorDeDossie(ArquivosDoRepositorio arquivos, ValidadorDePdf validador, MontadorDoDossie montador,
            ParametrosDoDossie parametros, EspacoEmDisco espaco, Clock relogio) {
        this.arquivos = arquivos;
        this.validador = validador;
        this.montador = montador;
        this.parametros = parametros;
        this.espaco = espaco;
        this.relogio = relogio;
    }

    public void processar(PedidoDeDossie pedido) {
        if (!pedido.iniciar()) {
            return;
        }
        Path pasta = ArquivosTemporarios.pastaDo(parametros, pedido);
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            log.error("Não foi possível criar o diretório do dossiê {}", pedido.id(), e);
            pedido.aguardarDecisao(new ErroDoDossie(null, null, null, "não foi possível preparar o diretório temporário",
                    null), validade());
            return;
        }
        try {
            ErroDoDossie interrupcao = obterPendentes(pedido, pasta);
            if (pedido.cancelamentoSolicitado()) {
                return;
            }
            if (interrupcao != null || !pedido.falhas().isEmpty()) {
                if (pedido.aguardarDecisao(interrupcao, validade())) {
                    log.info("Dossiê {} aguarda decisão: {} documento(s) com problema", pedido.id(),
                            pedido.itens().size() - pedido.processados());
                }
                return;
            }
            montar(pedido, pasta);
        } finally {
            if (pedido.cancelamentoSolicitado()) {
                pedido.confirmarCancelamento();
                ArquivosTemporarios.apagarPasta(pasta);
            }
        }
    }

    /**
     * Obtém e valida cada item ainda não resolvido. Falha de um documento fica no item e a obtenção continua.
     *
     * @return o motivo geral se a credencial foi recusada (a obtenção para), ou nulo
     */
    private ErroDoDossie obterPendentes(PedidoDeDossie pedido, Path pasta) {
        for (int i = 0; i < pedido.itens().size(); i++) {
            if (pedido.cancelamentoSolicitado()) {
                return null;
            }
            ItemDoPedido item = pedido.itens().get(i);
            if (item.situacao().resolvido()) {
                // OK já está no disco (continuação); REMOVIDO não entra.
                continue;
            }
            Path arquivo = arquivoDoItem(pasta, i);
            item.situacao(SituacaoItem.OBTENDO);
            try {
                exigirEspacoLivre(pasta);
                try {
                    item.tentativas(arquivos.baixar(item.chaveDoArquivo(), arquivo));
                } catch (FalhaNoDocumentoException e) {
                    item.tentativas(e.tentativas());
                    throw e;
                }
                item.paginas(validador.validar(arquivo));
                item.situacao(SituacaoItem.OK);
            } catch (CredencialArquivosRecusadaException e) {
                ArquivosTemporarios.apagarArquivo(arquivo);
                item.falhar(e.getMessage());
                log.warn("Dossiê {}: credencial da API de arquivos recusada; obtenção interrompida", pedido.id());
                return new ErroDoDossie(item.origem(), item.id(), item.titulo(), e.getMessage(),
                        ErroDoDossie.CREDENCIAL_ARQUIVOS_RECUSADA);
            } catch (FalhaNoDocumentoException e) {
                ArquivosTemporarios.apagarArquivo(arquivo);
                item.falhar(e.getMessage());
                log.warn("Dossiê {}: falha no documento {}: {}", pedido.id(), item, e.getMessage());
            } catch (RuntimeException e) {
                ArquivosTemporarios.apagarArquivo(arquivo);
                item.falhar(FALHA_INESPERADA);
                log.error("Dossiê {}: falha inesperada no documento {}", pedido.id(), item, e);
            }
            pedido.registrarAtividade(validade());
        }
        return null;
    }

    private void montar(PedidoDeDossie pedido, Path pasta) {
        if (!pedido.iniciarMontagem()) {
            return;
        }
        List<DocumentoParaMontagem> paraMontar = new ArrayList<>();
        for (int i = 0; i < pedido.itens().size(); i++) {
            ItemDoPedido item = pedido.itens().get(i);
            if (item.situacao() == SituacaoItem.OK) {
                paraMontar.add(new DocumentoParaMontagem(item.titulo(), item.origemDescricao(), item.data(),
                        arquivoDoItem(pasta, i), item.paginas()));
            }
        }
        Path finalizado = pasta.resolve(ARQUIVO_FINAL);
        try {
            int paginas = montador.montar(pedido.capa(), paraMontar,
                    new OpcoesDeMontagem(parametros.sumario(), parametros.marcadores(), parametros.numeracao()),
                    finalizado);
            if (!pedido.concluir(finalizado, paginas, Files.size(finalizado), validade())) {
                ArquivosTemporarios.apagarArquivo(finalizado);
                return;
            }
            for (DocumentoParaMontagem documento : paraMontar) {
                ArquivosTemporarios.apagarArquivo(documento.arquivo());
            }
        } catch (FalhaNoDocumentoException e) {
            falhaNaMontagem(pedido, finalizado, "falha ao montar o dossiê: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            log.error("Falha inesperada ao montar o dossiê {}", pedido.id(), e);
            falhaNaMontagem(pedido, finalizado, "falha inesperada ao montar o dossiê");
        }
    }

    /** Os documentos obtidos continuam no disco: "Tentar novamente" refaz só a montagem. */
    private void falhaNaMontagem(PedidoDeDossie pedido, Path finalizado, String motivo) {
        ArquivosTemporarios.apagarArquivo(finalizado);
        pedido.aguardarDecisao(new ErroDoDossie(null, null, null, motivo, null), validade());
        log.warn("Dossiê {} aguarda decisão: {}", pedido.id(), motivo);
    }

    /** Nome estável pela posição no pedido: a continuação encontra o que já foi obtido. */
    private static Path arquivoDoItem(Path pasta, int indice) {
        return pasta.resolve(String.format("%04d.pdf", indice + 1));
    }

    private LocalDateTime validade() {
        return LocalDateTime.now(relogio).plusMinutes(parametros.ttlMinutos());
    }

    /** Antes de cada download: sem o mínimo de espaço livre no volume do pedido, o documento falha (docs/05). */
    private void exigirEspacoLivre(Path pasta) {
        long minimo = parametros.espacoMinimoMb() * 1024L * 1024L;
        if (minimo > 0 && espaco.livreEmBytes(pasta) < minimo) {
            throw new FalhaNoDocumentoException(ESPACO_INSUFICIENTE, 0);
        }
    }
}
