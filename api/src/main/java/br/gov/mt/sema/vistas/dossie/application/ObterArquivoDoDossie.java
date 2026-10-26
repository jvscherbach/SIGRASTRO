package br.gov.mt.sema.vistas.dossie.application;

import java.nio.file.Path;

import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import br.gov.mt.sema.vistas.shared.application.exception.DossieExpiradoException;
import br.gov.mt.sema.vistas.shared.application.exception.DossieSemArquivoException;
import org.springframework.stereotype.Service;

/**
 * Entrega o PDF final. 409 se não há arquivo (em andamento, à espera de decisão ou cancelado), 410 se expirou ou já
 * foi baixado.
 * O arquivo é apagado quando o download (attachment) termina por inteiro (RNF05).
 */
@Service
public class ObterArquivoDoDossie {

    private final LocalizarPedido localizar;
    private final ParametrosDoDossie parametros;

    public ObterArquivoDoDossie(LocalizarPedido localizar, ParametrosDoDossie parametros) {
        this.localizar = localizar;
        this.parametros = parametros;
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
        if (pedido.expirar()) {
            ArquivosTemporarios.apagarPasta(ArquivosTemporarios.pastaDo(parametros, pedido));
        }
    }

    private static String mensagem(StatusDossie status) {
        return switch (status) {
            case AGUARDANDO, OBTENDO, MONTANDO -> "O dossiê ainda está sendo gerado.";
            case PENDENTE_DECISAO -> "Há documentos com problema: decida se tenta de novo ou remove antes de montar.";
            default -> "Não há arquivo para este dossiê.";
        };
    }

    public record ArquivoDoDossie(String id, Path caminho, String nome, long tamanhoBytes) {
    }
}
