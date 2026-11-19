package br.gov.mt.sema.rastro.documento.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaVisualizacao;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cache em disco do conteúdo do modal (S3): uma pasta por documento ({@code origem}, {@code id}) com o original e a
 * versão em PDF. O cache só guarda bytes; quem pode ver é decidido a cada requisição, antes de qualquer leitura daqui.
 *
 * <p>Vale por {@code VISUALIZACAO_TTL_MINUTOS} a partir da gravação; acima de {@code VISUALIZACAO_CACHE_MAX_MB} os
 * arquivos mais antigos saem. Uma trava por pasta (em faixas) impede duas requisições de baixar ou converter o mesmo
 * documento ao mesmo tempo; a limpeza só apaga a pasta cuja trava consegue pegar, então nunca apaga o que está sendo
 * gravado nem o que acabou de ser aberto para a resposta.
 */
@Service
public class CacheDeVisualizacao {

    private static final Logger log = LoggerFactory.getLogger(CacheDeVisualizacao.class);

    static final String ORIGINAL = "original";
    static final String PDF = "documento.pdf";
    private static final String PARCIAL = ".parcial";
    private static final int FAIXAS = 64;

    private final ParametrosDaVisualizacao parametros;
    private final Clock relogio;
    private final ReentrantLock[] travas = new ReentrantLock[FAIXAS];

    public CacheDeVisualizacao(ParametrosDaVisualizacao parametros, Clock relogio) {
        this.parametros = parametros;
        this.relogio = relogio;
        for (int i = 0; i < FAIXAS; i++) {
            travas[i] = new ReentrantLock();
        }
    }

    /** Pasta do documento. O id vai em hexadecimal: nenhum caractere dele chega ao caminho. */
    Path pastaDe(Documento documento) {
        return parametros.diretorio().resolve(documento.origem().name() + "-"
                + HexFormat.of().formatHex(documento.id().getBytes(StandardCharsets.UTF_8)));
    }

    /** Executa com a trava da pasta (bloqueante). */
    <T> T comTrava(Path pasta, Supplier<T> tarefa) {
        ReentrantLock trava = travaDe(pasta);
        trava.lock();
        try {
            return tarefa.get();
        } finally {
            trava.unlock();
        }
    }

    /** O arquivo da pasta, se existe e está dentro do TTL; vencido é apagado. Chame com a trava. */
    Path vigente(Path pasta, String nome) {
        Path arquivo = pasta.resolve(nome);
        try {
            if (!venceu(Files.getLastModifiedTime(arquivo).toInstant())) {
                return arquivo;
            }
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            log.warn("Não foi possível ler um arquivo do cache de visualização: {}", e.getMessage());
        }
        apagar(arquivo);
        return null;
    }

    /** Arquivo parcial para gravar {@code nome}; vira definitivo em {@link #publicar}. Chame com a trava. */
    Path parcial(Path pasta, String nome) {
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta do cache de visualização.", e);
        }
        Path parcial = pasta.resolve(nome + PARCIAL);
        apagar(parcial);
        return parcial;
    }

    /** Torna o parcial definitivo (com a hora de agora, que conta o TTL) e respeita o teto. Chame com a trava. */
    Path publicar(Path parcial, String nome) {
        Path pasta = parcial.getParent();
        Path destino = pasta.resolve(nome);
        try {
            Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.setLastModifiedTime(destino, FileTime.from(relogio.instant()));
        } catch (IOException e) {
            apagar(parcial);
            throw new IllegalStateException("Não foi possível gravar no cache de visualização.", e);
        }
        respeitarTeto(pasta);
        return destino;
    }

    /** Apaga o que venceu e o que excede o teto. @return arquivos apagados */
    public int limparVencidos() {
        int apagados = 0;
        for (Arquivo arquivo : arquivos()) {
            if (venceu(arquivo.modificado()) && apagarSeLivre(arquivo.caminho())) {
                apagados++;
            }
        }
        apagados += respeitarTeto(null);
        apagarPastasVazias();
        return apagados;
    }

    /** Na subida: tudo o que há no cache é de uma execução anterior. @return arquivos apagados */
    public int esvaziar() {
        int apagados = 0;
        for (Arquivo arquivo : arquivos()) {
            if (apagarSeLivre(arquivo.caminho())) {
                apagados++;
            }
        }
        apagarPastasVazias();
        return apagados;
    }

    /** Descarta os mais antigos até caber no teto, sem tocar em {@code preservada} (a pasta que acabou de ser gravada). */
    private int respeitarTeto(Path preservada) {
        long teto = Math.max(0, parametros.cacheMaxMb()) * 1024 * 1024;
        List<Arquivo> todos = arquivos();
        long total = todos.stream().mapToLong(Arquivo::tamanho).sum();
        int apagados = 0;
        for (Arquivo arquivo : todos) {
            if (total <= teto) {
                break;
            }
            if (arquivo.caminho().getParent().equals(preservada)) {
                continue;
            }
            if (apagarSeLivre(arquivo.caminho())) {
                total -= arquivo.tamanho();
                apagados++;
            }
        }
        return apagados;
    }

    private boolean apagarSeLivre(Path arquivo) {
        ReentrantLock trava = travaDe(arquivo.getParent());
        // Trava da própria thread (gravando outra pasta da mesma faixa) não conta como livre.
        if (trava.isHeldByCurrentThread() || !trava.tryLock()) {
            return false;
        }
        try {
            return apagar(arquivo);
        } finally {
            trava.unlock();
        }
    }

    /** Arquivos do cache, do mais antigo para o mais novo. */
    private List<Arquivo> arquivos() {
        Path base = parametros.diretorio();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<Arquivo> arquivos = new ArrayList<>();
        try (Stream<Path> pastas = Files.list(base)) {
            for (Path pasta : pastas.filter(Files::isDirectory).toList()) {
                try (Stream<Path> filhos = Files.list(pasta)) {
                    for (Path filho : filhos.filter(Files::isRegularFile).toList()) {
                        try {
                            arquivos.add(new Arquivo(filho, Files.size(filho),
                                    Files.getLastModifiedTime(filho).toInstant()));
                        } catch (IOException e) {
                            // Apagado no meio da listagem.
                        }
                    }
                } catch (IOException e) {
                    // Pasta apagada no meio da listagem.
                }
            }
        } catch (IOException e) {
            log.warn("Não foi possível listar o cache de visualização: {}", e.getMessage());
        }
        arquivos.sort(Comparator.comparing(Arquivo::modificado));
        return arquivos;
    }

    private void apagarPastasVazias() {
        Path base = parametros.diretorio();
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> pastas = Files.list(base)) {
            for (Path pasta : pastas.filter(Files::isDirectory).toList()) {
                ReentrantLock trava = travaDe(pasta);
                if (trava.isHeldByCurrentThread() || !trava.tryLock()) {
                    continue;
                }
                try (Stream<Path> filhos = Files.list(pasta)) {
                    if (filhos.findAny().isEmpty()) {
                        Files.deleteIfExists(pasta);
                    }
                } catch (IOException e) {
                    // Ainda em uso: fica para a próxima limpeza.
                } finally {
                    trava.unlock();
                }
            }
        } catch (IOException e) {
            log.warn("Não foi possível listar o cache de visualização: {}", e.getMessage());
        }
    }

    private boolean venceu(Instant gravadoEm) {
        return !gravadoEm.plusSeconds(Math.max(0, parametros.ttlMinutos()) * 60L).isAfter(relogio.instant());
    }

    private ReentrantLock travaDe(Path pasta) {
        return travas[Math.floorMod(pasta.getFileName().toString().hashCode(), FAIXAS)];
    }

    static boolean apagar(Path arquivo) {
        try {
            return Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            // No Windows, um arquivo aberto (resposta em andamento) não pode ser apagado: fica para a próxima limpeza.
            log.warn("Não foi possível apagar um arquivo do cache de visualização: {}", e.getMessage());
            return false;
        }
    }

    private record Arquivo(Path caminho, long tamanho, Instant modificado) {
    }
}
