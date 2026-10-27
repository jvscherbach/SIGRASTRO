package br.gov.mt.sema.vistas.integration.licenciamento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * De onde vem o token da API de arquivos (docs/05). Com {@code LICENCIAMENTO_API_TOKEN_FILE} definida, o token é lido
 * desse arquivo a cada pedido e a cada nova tentativa (cache pela data de modificação e pelo tamanho), e tem
 * precedência sobre {@code LICENCIAMENTO_API_TOKEN}: trocar o conteúdo do arquivo vale na próxima chamada, sem
 * reiniciar o container. Arquivo ausente ou vazio: "API de arquivos não configurada". O valor e o caminho nunca vão
 * para log, erro ou resposta.
 */
final class FonteDoToken {

    private static final Logger log = LoggerFactory.getLogger(FonteDoToken.class);

    private final String fixo;
    private final Path arquivo;

    private FileTime modificadoEm;
    private long tamanho = -1;
    private String emCache;

    private FonteDoToken(String fixo, Path arquivo) {
        this.fixo = fixo;
        this.arquivo = arquivo;
    }

    /** @param caminhoDoArquivo LICENCIAMENTO_API_TOKEN_FILE; vazio = usa o token fixo */
    static FonteDoToken de(String token, String caminhoDoArquivo) {
        if (caminhoDoArquivo != null && !caminhoDoArquivo.isBlank()) {
            return new FonteDoToken(null, Path.of(caminhoDoArquivo.trim()));
        }
        return new FonteDoToken(token == null ? "" : token.trim(), null);
    }

    /** O token já aparado (sem espaços nem quebra de linha final); vazio só no modo fixo sem token. */
    synchronized String obter() {
        if (arquivo == null) {
            return fixo;
        }
        try {
            FileTime modificado = Files.getLastModifiedTime(arquivo);
            long tamanhoAtual = Files.size(arquivo);
            if (emCache == null || !modificado.equals(modificadoEm) || tamanhoAtual != tamanho) {
                emCache = Files.readString(arquivo, StandardCharsets.UTF_8).trim();
                modificadoEm = modificado;
                tamanho = tamanhoAtual;
            }
        } catch (NoSuchFileException e) {
            emCache = null;
            log.warn("Arquivo do token da API de arquivos não encontrado");
            throw naoConfigurada();
        } catch (IOException e) {
            emCache = null;
            log.warn("Não foi possível ler o arquivo do token da API de arquivos: {}", e.getClass().getSimpleName());
            throw naoConfigurada();
        }
        if (emCache.isEmpty()) {
            log.warn("Arquivo do token da API de arquivos está vazio");
            throw naoConfigurada();
        }
        return emCache;
    }

    private static FalhaNoDocumentoException naoConfigurada() {
        return new FalhaNoDocumentoException(ArquivosNaoConfigurados.MOTIVO, 0);
    }
}
