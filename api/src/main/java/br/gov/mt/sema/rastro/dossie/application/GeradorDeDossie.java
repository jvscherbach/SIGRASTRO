package br.gov.mt.sema.rastro.dossie.application;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.gov.mt.sema.rastro.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.rastro.dossie.application.port.EspacoEmDisco;
import br.gov.mt.sema.rastro.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.DocumentoParaMontagem;
import br.gov.mt.sema.rastro.dossie.application.port.MontadorDoDossie.OpcoesDeMontagem;
import br.gov.mt.sema.rastro.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.rastro.dossie.application.PreparadorDePdf.PdfPreparado;
import br.gov.mt.sema.rastro.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.SituacaoItem;
import br.gov.mt.sema.rastro.shared.application.exception.ArmazenamentoInsuficienteException;
import br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa a geração de um pedido (docs/06). Percorre os documentos na ordem cronológica e, para cada um ainda não
 * resolvido, obtém o arquivo e o prepara como PDF ({@link PreparadorDePdf}: validação ou conversão pelo tipo real,
 * S3), coletando as falhas sem parar na primeira (S2.2). Sem falhas, monta capa, sumário, documentos, marcadores e
 * numeração; a página inicial de cada documento já é conhecida (conversão antes da montagem). Com falhas, o pedido fica em PENDENTE_DECISAO e nada é entregue (RN09):
 * os arquivos já obtidos ficam no diretório do pedido, e a continuação ({@link ContinuarDossie}) só obtém o que falta.
 * Credencial recusada (401/403) interrompe a obtenção na hora, também em PENDENTE_DECISAO. Esta classe não usa o
 * SIGA (o instantâneo já está no pedido).
 *
 * <p>Sem espaço em disco (S6, bloco 3, D77) não há decisão que resolva: na obtenção, na conversão ou na montagem, o
 * pedido vai para FALHA com o código {@code ARMAZENAMENTO_INSUFICIENTE}, o diretório do pedido é apagado (inclusive os
 * parciais) e um único evento de falha é registrado, sem caminho de arquivo. CONCLUIDO só depois de o PDF final ser
 * forçado ao disco ({@code fsync}, que revela a falta de espaço que a gravação em cache esconde) e ter tamanho maior que
 * zero.
 */
@Service
public class GeradorDeDossie {

    private static final Logger log = LoggerFactory.getLogger(GeradorDeDossie.class);
    private static final String ARQUIVO_FINAL = "dossie.pdf";
    static final String ESPACO_INSUFICIENTE = "espaço em disco insuficiente";
    static final String FALHA_INESPERADA = "falha inesperada ao obter o documento";
    static final String FINAL_NAO_GRAVADO = "o PDF final não foi gravado";
    /**
     * Abaixo disto, uma falha opaca (o LibreOffice não diz por que não gravou) é tratada como disco cheio. Pequeno de
     * propósito: um documento corrompido não pode virar "sem espaço" só porque o volume está perto do mínimo.
     */
    static final long LIMIAR_DE_DISCO_CHEIO = 16L * 1024 * 1024;

    private final ArquivosDoRepositorio arquivos;
    private final PreparadorDePdf preparador;
    private final MontadorDoDossie montador;
    private final ParametrosDoDossie parametros;
    private final EspacoEmDisco espaco;
    private final Clock relogio;
    private final EventosDoDossie eventos;
    /** Thread que está convertendo um documento de cada pedido: o cancelamento a interrompe. */
    private final Map<String, Thread> convertendo = new ConcurrentHashMap<>();

    public GeradorDeDossie(ArquivosDoRepositorio arquivos, PreparadorDePdf preparador, MontadorDoDossie montador,
            ParametrosDoDossie parametros, EspacoEmDisco espaco, Clock relogio, EventosDoDossie eventos) {
        this.eventos = eventos;
        this.arquivos = arquivos;
        this.preparador = preparador;
        this.montador = montador;
        this.parametros = parametros;
        this.espaco = espaco;
        this.relogio = relogio;
    }

    /**
     * Verificação prévia do {@code POST /dossies} (S6, bloco 3): sem o espaço mínimo ({@code DOSSIE_ESPACO_MINIMO_MB})
     * no diretório temporário, o pedido nem é criado (507). Se o espaço não puder ser medido, não bloqueia: a gravação
     * continua protegida pela detecção de falta de espaço.
     */
    public void exigirEspacoParaNovoPedido() {
        if (abaixoDoMinimo(parametros.diretorio())) {
            throw new ArmazenamentoInsuficienteException(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE);
        }
    }

    public void processar(PedidoDeDossie pedido) {
        if (!pedido.iniciar()) {
            return;
        }
        Path pasta = ArquivosTemporarios.pastaDo(parametros, pedido);
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            if (faltouEspaco(e, parametros.diretorio())) {
                semEspaco(pedido, pasta);
                return;
            }
            log.error("Não foi possível criar o diretório do dossiê {}", pedido.id(), e);
            String motivo = "não foi possível preparar o diretório temporário";
            if (pedido.aguardarDecisao(new ErroDoDossie(null, null, null, motivo, null), validade())) {
                eventos.falhaGeral(pedido, motivo);
            }
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
        } catch (SemEspacoNoDisco e) {
            semEspaco(pedido, pasta);
        } finally {
            if (pedido.cancelamentoSolicitado()) {
                pedido.confirmarCancelamento();
                ArquivosTemporarios.apagarPasta(pasta);
            }
        }
    }

    /**
     * Obtém e prepara (valida ou converte) cada item ainda não resolvido. Falha de um documento fica no item e a
     * obtenção continua. O PDF convertido fica no diretório: a continuação nunca o converte de novo.
     *
     * @return o motivo geral se a credencial foi recusada (a obtenção para), ou nulo
     * @throws SemEspacoNoDisco se faltou espaço (a obtenção para e o pedido vai para FALHA)
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
            Path original = originalDoItem(pasta, i);
            item.situacao(SituacaoItem.OBTENDO);
            try {
                exigirEspacoLivre(pasta);
                if (Files.isRegularFile(original)) {
                    // Obtido numa tentativa anterior cuja conversão falhou: não baixa de novo.
                    item.tentativas(0);
                } else {
                    try {
                        item.tentativas(arquivos.baixar(item.chaveDoArquivo(), original));
                    } catch (FalhaNoDocumentoException e) {
                        item.tentativas(e.tentativas());
                        throw e;
                    }
                }
                PdfPreparado pdf = preparar(pedido, original, item, arquivo);
                item.paginas(pdf.paginas());
                item.conversao(pdf.convertidoDe(), pdf.abasEmPaginaUnica());
                ArquivosTemporarios.apagarArquivo(original);
                item.situacao(SituacaoItem.OK);
            } catch (ArmazenamentoInsuficienteException e) {
                pararPorFaltaDeEspaco(item, arquivo, original);
            } catch (CredencialArquivosRecusadaException e) {
                ArquivosTemporarios.apagarArquivo(arquivo);
                falhar(pedido, item, e.getMessage());
                log.warn("Dossiê {}: credencial da API de arquivos recusada; obtenção interrompida", pedido.id());
                return new ErroDoDossie(item.origem(), item.id(), item.titulo(), e.getMessage(),
                        ErroDoDossie.CREDENCIAL_ARQUIVOS_RECUSADA);
            } catch (FalhaNoDocumentoException e) {
                if (faltouEspaco(e, pasta)) {
                    pararPorFaltaDeEspaco(item, arquivo, original);
                }
                ArquivosTemporarios.apagarArquivo(arquivo);
                falhar(pedido, item, e.getMessage());
                log.warn("Dossiê {}: falha no documento {}: {}", pedido.id(), item, e.getMessage());
            } catch (RuntimeException e) {
                if (faltouEspaco(e, pasta)) {
                    pararPorFaltaDeEspaco(item, arquivo, original);
                }
                ArquivosTemporarios.apagarArquivo(arquivo);
                ArquivosTemporarios.apagarArquivo(original);
                falhar(pedido, item, FALHA_INESPERADA);
                log.error("Dossiê {}: falha inesperada no documento {}", pedido.id(), item, e);
            }
            pedido.registrarAtividade(validade());
        }
        return null;
    }

    /**
     * Sem espaço: apaga o que o item deixou (PDF parcial e original), marca o item e para a obtenção inteira. O evento é
     * um só, do pedido ({@link #semEspaco}), e não um por documento.
     */
    private static void pararPorFaltaDeEspaco(ItemDoPedido item, Path arquivo, Path original) {
        ArquivosTemporarios.apagarArquivo(arquivo);
        ArquivosTemporarios.apagarArquivo(original);
        item.falhar(ESPACO_INSUFICIENTE);
        throw new SemEspacoNoDisco();
    }

    /** O pedido vai para FALHA, o diretório inteiro é apagado (libera o espaço) e sai um único evento, sem caminho. */
    private void semEspaco(PedidoDeDossie pedido, Path pasta) {
        ErroDoDossie erro = ErroDoDossie.geral(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE,
                ErroDoDossie.ARMAZENAMENTO_INSUFICIENTE);
        if (pedido.falhar(erro, validade())) {
            ArquivosTemporarios.apagarPasta(pasta);
            eventos.falhaGeral(pedido,
                    ErroDoDossie.ARMAZENAMENTO_INSUFICIENTE + ": sem espaço em disco para gerar o dossiê");
            log.warn("Dossiê {}: sem espaço em disco; geração encerrada em FALHA e temporários apagados", pedido.id());
        }
    }

    /**
     * A falha foi falta de espaço: a cadeia de exceções diz isso (Linux ou Windows), ou o volume está praticamente
     * cheio agora (o conversor não informa por que não gravou).
     */
    private boolean faltouEspaco(Throwable erro, Path diretorio) {
        if (ArmazenamentoInsuficienteException.causadaPorFaltaDeEspaco(erro)) {
            return true;
        }
        Long livre = livre(diretorio);
        return livre != null && livre < LIMIAR_DE_DISCO_CHEIO;
    }

    /** Espaço livre medido, ou nulo se não deu para medir (nunca derruba a geração por isso). */
    private Long livre(Path diretorio) {
        try {
            return espaco.livreEmBytes(diretorio);
        } catch (RuntimeException e) {
            log.warn("Não foi possível medir o espaço livre do diretório dos dossiês ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    private boolean abaixoDoMinimo(Path diretorio) {
        long minimo = parametros.espacoMinimoMb() * 1024L * 1024L;
        if (minimo <= 0) {
            return false;
        }
        Long livre = livre(diretorio);
        return livre != null && livre < minimo;
    }

    /** Sinal interno: a obtenção ou a montagem parou por falta de espaço. */
    private static final class SemEspacoNoDisco extends RuntimeException {
        SemEspacoNoDisco() {
            super(null, null, false, false);
        }
    }

    /** Interrupção causada pelo cancelamento não é falha do documento: não gera evento. */
    private void falhar(PedidoDeDossie pedido, ItemDoPedido item, String motivo) {
        item.falhar(motivo);
        if (!pedido.cancelamentoSolicitado()) {
            eventos.falhaNoDocumento(pedido, item, motivo);
        }
    }

    /**
     * Converte (ou valida) registrando a thread, para que o cancelamento interrompa uma conversão em andamento. A
     * marca de interrupção não sobra para o próximo pedido da mesma thread.
     */
    private PdfPreparado preparar(PedidoDeDossie pedido, Path original, ItemDoPedido item, Path pdf) {
        convertendo.put(pedido.id(), Thread.currentThread());
        try {
            return preparador.preparar(original, item.extensao(), pdf, item.tituloDeCabecalho());
        } finally {
            convertendo.remove(pedido.id());
            Thread.interrupted();
        }
    }

    /** Interrompe a conversão em andamento do pedido, se houver (cancelamento). */
    void interromperConversao(String idDoPedido) {
        // Atômico em relação ao remove: depois que a conversão termina, nenhuma interrupção chega à thread.
        convertendo.computeIfPresent(idDoPedido, (id, thread) -> {
            thread.interrupt();
            return thread;
        });
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
                        arquivoDoItem(pasta, i), item.paginas(), item.convertidoDe(), item.abasEmPaginaUnica()));
            }
        }
        Path finalizado = pasta.resolve(ARQUIVO_FINAL);
        try {
            int paginas = montador.montar(pedido.capa(), paraMontar,
                    new MontadorDoDossie.Inventario(pedido.listados(), pedido.naoIncluidos()),
                    new OpcoesDeMontagem(parametros.sumario(), parametros.marcadores(), parametros.numeracao()),
                    finalizado);
            long tamanho = gravadoNoDisco(finalizado);
            if (tamanho <= 0) {
                falhaNaMontagem(pedido, finalizado, FINAL_NAO_GRAVADO);
                return;
            }
            if (!pedido.concluir(finalizado, paginas, tamanho, validade())) {
                ArquivosTemporarios.apagarArquivo(finalizado);
                return;
            }
            eventos.dossieGerado(pedido);
            for (DocumentoParaMontagem documento : paraMontar) {
                ArquivosTemporarios.apagarArquivo(documento.arquivo());
            }
            // Originais guardados de itens removidos depois de uma falha de conversão.
            for (int i = 0; i < pedido.itens().size(); i++) {
                ArquivosTemporarios.apagarArquivo(originalDoItem(pasta, i));
            }
        } catch (FalhaNoDocumentoException e) {
            if (faltouEspaco(e, pasta)) {
                throw new SemEspacoNoDisco();
            }
            falhaNaMontagem(pedido, finalizado, "falha ao montar o dossiê: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            if (faltouEspaco(e, pasta)) {
                throw new SemEspacoNoDisco();
            }
            log.error("Falha inesperada ao montar o dossiê {}", pedido.id(), e);
            falhaNaMontagem(pedido, finalizado, "falha inesperada ao montar o dossiê");
        }
    }

    /**
     * Força o PDF final ao disco e devolve o tamanho (0 se não existe). Sem o {@code fsync}, a gravação fica no cache do
     * sistema e a falta de espaço (comum no Docker, cujo disco virtual cresce no disco do computador) só aparece
     * depois, com o pedido já CONCLUIDO e um arquivo ilegível.
     */
    private static long gravadoNoDisco(Path finalizado) throws IOException {
        if (!Files.isRegularFile(finalizado)) {
            return 0;
        }
        try (FileChannel canal = FileChannel.open(finalizado, StandardOpenOption.WRITE)) {
            canal.force(true);
        }
        return Files.size(finalizado);
    }

    /** Os documentos obtidos continuam no disco: "Tentar novamente" refaz só a montagem. */
    private void falhaNaMontagem(PedidoDeDossie pedido, Path finalizado, String motivo) {
        ArquivosTemporarios.apagarArquivo(finalizado);
        if (pedido.aguardarDecisao(new ErroDoDossie(null, null, null, motivo, null), validade())) {
            eventos.falhaGeral(pedido, motivo);
        }
        log.warn("Dossiê {} aguarda decisão: {}", pedido.id(), motivo);
    }

    /** Nome estável pela posição no pedido: a continuação encontra o que já foi obtido. */
    private static Path arquivoDoItem(Path pasta, int indice) {
        return pasta.resolve(String.format("%04d.pdf", indice + 1));
    }

    /**
     * O arquivo como veio do repositório (S3). Apagado quando o PDF do item fica pronto; guardado quando a conversão
     * falha, para "Tentar novamente" converter de novo sem baixar de novo.
     */
    private static Path originalDoItem(Path pasta, int indice) {
        return pasta.resolve(String.format("%04d.original", indice + 1));
    }

    private LocalDateTime validade() {
        return LocalDateTime.now(relogio).plusMinutes(parametros.ttlMinutos());
    }

    /**
     * Antes de cada download: sem o mínimo de espaço livre no volume do pedido, a geração para em FALHA (S6, bloco 3;
     * antes, cada documento falhava e o pedido esperava uma decisão que nada resolvia).
     */
    private void exigirEspacoLivre(Path pasta) {
        if (abaixoDoMinimo(pasta)) {
            throw new ArmazenamentoInsuficienteException(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE);
        }
    }
}
