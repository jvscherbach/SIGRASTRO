package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
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
 * Executa a geração de um pedido (docs/06): para cada documento, na ordem cronológica, obtém o PDF e o valida; depois
 * monta capa, sumário, documentos, marcadores e numeração. Regra "completo ou nenhum" (RN09, RNF04): a primeira
 * falha interrompe, descarta todos os temporários e deixa o pedido em FALHA; nenhum PDF é entregue. Esta classe não
 * usa o SIGA (o instantâneo já está no pedido).
 */
@Service
public class GeradorDeDossie {

    private static final Logger log = LoggerFactory.getLogger(GeradorDeDossie.class);
    private static final String ARQUIVO_FINAL = "dossie.pdf";

    private final ArquivosDoRepositorio arquivos;
    private final ValidadorDePdf validador;
    private final MontadorDoDossie montador;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public GeradorDeDossie(ArquivosDoRepositorio arquivos, ValidadorDePdf validador, MontadorDoDossie montador,
            ParametrosDoDossie parametros, Clock relogio) {
        this.arquivos = arquivos;
        this.validador = validador;
        this.montador = montador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public void processar(PedidoDeDossie pedido) {
        if (!pedido.iniciar()) {
            return;
        }
        Path pasta = parametros.diretorio().resolve(pedido.id());
        ItemDoPedido emAndamento = null;
        boolean entregue = false;
        try {
            Files.createDirectories(pasta);
            List<DocumentoParaMontagem> paraMontar = new ArrayList<>();
            int numero = 0;
            for (ItemDoPedido item : pedido.itens()) {
                if (pedido.cancelamentoSolicitado()) {
                    pedido.confirmarCancelamento();
                    return;
                }
                emAndamento = item;
                item.situacao(SituacaoItem.OBTENDO);
                Path arquivo = pasta.resolve(String.format("%04d.pdf", ++numero));
                arquivos.baixar(item.chaveDoArquivo(), arquivo);
                item.paginas(validador.validar(arquivo));
                item.situacao(SituacaoItem.OK);
                pedido.documentoProcessado();
                paraMontar.add(new DocumentoParaMontagem(item.titulo(), item.origemDescricao(), item.data(), arquivo,
                        item.paginas()));
            }
            emAndamento = null;
            if (pedido.cancelamentoSolicitado()) {
                pedido.confirmarCancelamento();
                return;
            }

            Path finalizado = pasta.resolve(ARQUIVO_FINAL);
            int paginas = montador.montar(pedido.capa(), paraMontar,
                    new OpcoesDeMontagem(parametros.sumario(), parametros.marcadores(), parametros.numeracao()),
                    finalizado);
            for (DocumentoParaMontagem documento : paraMontar) {
                ArquivosTemporarios.apagarArquivo(documento.arquivo());
            }
            LocalDateTime expiraEm = LocalDateTime.now(relogio).plusMinutes(parametros.ttlMinutos());
            if (pedido.concluir(finalizado, paginas, Files.size(finalizado), expiraEm)) {
                entregue = true;
            } else {
                pedido.confirmarCancelamento();
            }
        } catch (CredencialArquivosRecusadaException e) {
            registrarFalha(pedido, emAndamento, e.getMessage(), ErroDoDossie.CREDENCIAL_ARQUIVOS_RECUSADA);
        } catch (FalhaNoDocumentoException e) {
            registrarFalha(pedido, emAndamento, e.getMessage(), null);
        } catch (IOException | RuntimeException e) {
            log.error("Falha inesperada ao gerar o dossiê {}", pedido.id(), e);
            registrarFalha(pedido, emAndamento, "falha inesperada ao gerar o dossiê", null);
        } finally {
            if (!entregue) {
                ArquivosTemporarios.apagarPasta(pasta);
            }
        }
    }

    private static void registrarFalha(PedidoDeDossie pedido, ItemDoPedido item, String motivo, String codigo) {
        if (item != null) {
            item.situacao(SituacaoItem.FALHA);
            pedido.falhar(new ErroDoDossie(item.origem(), item.id(), item.titulo(), motivo, codigo));
        } else {
            pedido.falhar(new ErroDoDossie(null, null, null, motivo, codigo));
        }
        log.warn("Dossiê {} interrompido: {}", pedido.id(), motivo);
    }
}
