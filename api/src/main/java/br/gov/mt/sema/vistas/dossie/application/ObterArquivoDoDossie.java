package br.gov.mt.sema.vistas.dossie.application;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import br.gov.mt.sema.vistas.shared.application.exception.DossieExpiradoException;
import br.gov.mt.sema.vistas.shared.application.exception.DossieSemArquivoException;
import org.springframework.stereotype.Service;

/**
 * Entrega o PDF final. 409 se não há arquivo (em andamento, falha ou cancelado), 410 se expirou ou já foi baixado.
 * O arquivo é apagado quando o download (attachment) termina por inteiro (RNF05).
 */
@Service
public class ObterArquivoDoDossie {

    private final LocalizarPedido localizar;

    public ObterArquivoDoDossie(LocalizarPedido localizar) {
        this.localizar = localizar;
    }

    public ArquivoDoDossie executar(String id) {
        PedidoDeDossie pedido = localizar.executar(id);
        StatusDossie status = pedido.status();
        if (status == StatusDossie.EXPIRADO) {
            throw new DossieExpiradoException();
        }
        Path arquivo = pedido.arquivo();
        if (status != StatusDossie.CONCLUIDO || arquivo == null) {
            throw new DossieSemArquivoException(mensagem(status));
        }
        return new ArquivoDoDossie(pedido.id(), arquivo, pedido.capa().nomeDoArquivo(), pedido.tamanhoBytes());
    }

    /** Chamado quando o download terminou por inteiro: o arquivo não precisa mais existir. */
    public void concluirDownload(String id) {
        PedidoDeDossie pedido = localizar.executar(id);
        ArquivosTemporarios.apagarArquivo(pedido.expirar());
    }

    private static String mensagem(StatusDossie status) {
        return switch (status) {
            case PENDENTE, PROCESSANDO -> "O dossiê ainda está sendo gerado.";
            case FALHA -> "A geração falhou e nenhum arquivo foi gerado.";
            default -> "Não há arquivo para este dossiê.";
        };
    }

    public record ArquivoDoDossie(String id, Path caminho, String nome, long tamanhoBytes) {
    }
}
