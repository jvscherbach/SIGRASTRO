package br.gov.mt.sema.rastro.documento.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaVisualizacao;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TTL, teto de espaço e trava do cache do modal, com relógio controlado. */
class CacheDeVisualizacaoTest {

    private static final int MB = 1024 * 1024;

    @TempDir
    Path base;

    private Instant agora = Instant.parse("2025-05-01T12:00:00Z");
    private long tetoMb = 1;
    private CacheDeVisualizacao cache;

    @BeforeEach
    void preparar() {
        Clock relogio = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zona) {
                return this;
            }

            @Override
            public Instant instant() {
                return agora;
            }
        };
        ParametrosDaVisualizacao parametros = new ParametrosDaVisualizacao() {
            @Override
            public int ttlMinutos() {
                return 10;
            }

            @Override
            public long cacheMaxMb() {
                return tetoMb;
            }

            @Override
            public int conversoesSimultaneas() {
                return 1;
            }

            @Override
            public int filaDeConversoes() {
                return 0;
            }

            @Override
            public Path diretorio() {
                return base.resolve("visualizacao");
            }
        };
        cache = new CacheDeVisualizacao(parametros, relogio);
    }

    @Test
    void arquivoValeAteOTtlEDepoisEhApagado() throws Exception {
        Path pasta = cache.pastaDe(doc("1"));
        gravar(pasta, CacheDeVisualizacao.PDF, 10);

        agora = agora.plus(Duration.ofMinutes(9));
        assertThat(cache.vigente(pasta, CacheDeVisualizacao.PDF)).isNotNull();

        agora = agora.plus(Duration.ofMinutes(1));
        assertThat(cache.vigente(pasta, CacheDeVisualizacao.PDF)).isNull();
        assertThat(pasta.resolve(CacheDeVisualizacao.PDF)).doesNotExist();
    }

    @Test
    void limpezaApagaOsVencidosEAsPastasVazias() throws Exception {
        Path velha = cache.pastaDe(doc("1"));
        gravar(velha, CacheDeVisualizacao.ORIGINAL, 10);
        agora = agora.plus(Duration.ofMinutes(8));
        Path nova = cache.pastaDe(doc("2"));
        gravar(nova, CacheDeVisualizacao.ORIGINAL, 10);
        agora = agora.plus(Duration.ofMinutes(3));

        assertThat(cache.limparVencidos()).isEqualTo(1);
        assertThat(velha).doesNotExist();
        assertThat(nova.resolve(CacheDeVisualizacao.ORIGINAL)).exists();
    }

    @Test
    void acimaDoTetoSaemOsMaisAntigosMenosOQueAcabouDeSerGravado() throws Exception {
        Path a = cache.pastaDe(doc("1"));
        Path b = cache.pastaDe(doc("2"));
        Path c = cache.pastaDe(doc("3"));
        gravar(a, CacheDeVisualizacao.PDF, 400 * 1024);
        agora = agora.plusSeconds(1);
        gravar(b, CacheDeVisualizacao.PDF, 400 * 1024);
        agora = agora.plusSeconds(1);
        // 1,2 MB no total com o terceiro: o mais antigo sai, o recém-gravado fica.
        gravar(c, CacheDeVisualizacao.PDF, 400 * 1024);

        assertThat(a.resolve(CacheDeVisualizacao.PDF)).doesNotExist();
        assertThat(b.resolve(CacheDeVisualizacao.PDF)).exists();
        assertThat(c.resolve(CacheDeVisualizacao.PDF)).exists();
    }

    @Test
    void arquivoMaiorQueOTetoFicaAteAProximaGravacao() throws Exception {
        Path grande = cache.pastaDe(doc("1"));
        gravar(grande, CacheDeVisualizacao.PDF, 2 * MB);
        assertThat(grande.resolve(CacheDeVisualizacao.PDF)).exists();

        agora = agora.plusSeconds(1);
        gravar(cache.pastaDe(doc("2")), CacheDeVisualizacao.PDF, 10);
        assertThat(grande.resolve(CacheDeVisualizacao.PDF)).doesNotExist();
    }

    @Test
    void limpezaNaoApagaAPastaQueEstaComATrava() throws Exception {
        Path pasta = cache.pastaDe(doc("1"));
        gravar(pasta, CacheDeVisualizacao.ORIGINAL, 10);
        agora = agora.plus(Duration.ofHours(1));
        CountDownLatch travada = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        Thread dona = new Thread(() -> cache.comTrava(pasta, () -> {
            travada.countDown();
            try {
                soltar.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        dona.start();
        try {
            assertThat(travada.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(cache.limparVencidos()).isZero();
            assertThat(pasta.resolve(CacheDeVisualizacao.ORIGINAL)).exists();
        } finally {
            soltar.countDown();
            dona.join(10_000);
        }
        assertThat(cache.limparVencidos()).isEqualTo(1);
    }

    @Test
    void esvaziarApagaTudoEOIdNaoChegaAoCaminho() throws Exception {
        Path pasta = cache.pastaDe(doc("../../fora"));
        assertThat(pasta.getParent()).isEqualTo(base.resolve("visualizacao"));
        assertThat(pasta.getFileName().toString()).doesNotContain("/", "\\", "..");
        gravar(pasta, CacheDeVisualizacao.ORIGINAL, 10);

        assertThat(cache.esvaziar()).isEqualTo(1);
        assertThat(pasta).doesNotExist();
    }

    private void gravar(Path pasta, String nome, int tamanho) throws Exception {
        cache.comTrava(pasta, () -> {
            Path parcial = cache.parcial(pasta, nome);
            try {
                Files.write(parcial, new byte[tamanho]);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            return cache.publicar(parcial, nome);
        });
    }

    private static Documento doc(String id) {
        return Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, id, 1, "CHAVE", "t", "a.pdf", null, null, 0);
    }
}
