package br.gov.mt.sema.vistas.integration.libreoffice;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.documento.application.port.ConversorDeDocumentos;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException.Motivo;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import com.sun.star.document.MacroExecMode;
import com.sun.star.document.UpdateDocMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.filter.NoopFilter;
import org.jodconverter.local.office.ExistingProcessAction;
import org.jodconverter.local.office.LocalOfficeManager;
import org.jodconverter.local.office.LocalOfficeUtils;
import org.jodconverter.local.office.PasswordProtectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Conversor de textos, HTML e planilhas para PDF pelo LibreOffice headless, via JODConverter (único lugar que usa a
 * biblioteca; ArchitectureTest). Os documentos são de terceiros e tratados como hostis.
 *
 * <p><b>Processos.</b> Pool de {@code CONVERSAO_PROCESSOS} instâncias do {@code soffice}, em portas livres do
 * loopback, cada uma com perfil próprio e descartável (cópia do {@link PerfilSeguro}) dentro do diretório de trabalho
 * desta aplicação ({@code <tmp>/vistas-conversor-<id>}). Cada instância é reiniciada a cada
 * {@code CONVERSAO_TAREFAS_POR_PROCESSO} tarefas; a tarefa que passa de {@code CONVERSAO_TIMEOUT_SEGUNDOS} falha e o
 * processo é encerrado à força e reiniciado. O pool sobe na primeira conversão (as instâncias da aplicação que nunca
 * convertem, como os contextos de teste, não abrem {@code soffice}) e para com a aplicação, quando os processos que
 * restarem são encerrados à força. Na subida do pool, processos de uma execução anterior que caiu são encerrados.
 *
 * <p><b>Segurança.</b> Macro nunca executada, vínculo externo nunca atualizado (propriedades de carga e perfil),
 * rede bloqueada pelo perfil e HTML limpo antes ({@link HtmlSemRecursosExternos}). A entrada é copiada para um
 * diretório novo ao lado do destino, com nome gerado; nunca se converte do caminho de origem. A saída é validada
 * (tamanho máximo, abre, tem páginas).
 *
 * <p><b>Disponibilidade.</b> Sem LibreOffice (ou sem permissão de execução), {@link #disponivel()} é falso, a
 * conversão falha com "conversor indisponível" e a aplicação sobe normalmente. Falha ao subir o pool deixa o conversor
 * indisponível por {@link #NOVA_PARTIDA_APOS}; depois disso, a próxima conversão tenta de novo.
 */
@Component
class ConversorLibreOffice implements ConversorDeDocumentos, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConversorLibreOffice.class);

    /** Extensões que o LibreOffice converte aqui (a do arquivo de trabalho vem do tipo detectado, não do nome). */
    static final Set<String> EXTENSOES = Set.of("doc", "docx", "odt", "rtf", "html", "xls", "xlsx", "ods");

    static final Duration NOVA_PARTIDA_APOS = Duration.ofMinutes(1);
    private static final Duration ESPERA_DE_ENCERRAMENTO = Duration.ofSeconds(10);
    private static final long TEMPO_DE_PARTIDA_MS = 60_000L;
    private static final long MB = 1024L * 1024;

    /** Propriedades de carga: oculto, só leitura, macro nunca executada e vínculo nunca atualizado. */
    private static final Map<String, Object> CARGA = Map.of(
            "Hidden", true,
            "ReadOnly", true,
            "UpdateDocMode", UpdateDocMode.NO_UPDATE,
            "MacroExecutionMode", MacroExecMode.NEVER_EXECUTE);

    private final ParametrosDaConversao parametros;
    private final File officeHome;
    private final Path base;
    private final Clock relogio;
    private final String marca = ProcessosDoConversor.PREFIXO + UUID.randomUUID().toString().substring(0, 8);

    private LocalOfficeManager gerente;
    private Path trabalho;
    private volatile Instant partidaFalhouEm;
    private boolean encerrado;

    @Autowired
    ConversorLibreOffice(ParametrosDaConversao parametros, @Value("${vistas.conversao.office-home:}") String officeHome,
            Clock relogio) {
        this(parametros, localizar(officeHome), Path.of(System.getProperty("java.io.tmpdir")), relogio);
    }

    ConversorLibreOffice(ParametrosDaConversao parametros, File officeHome, Path base, Clock relogio) {
        this.parametros = parametros;
        this.officeHome = officeHome;
        this.base = base;
        this.relogio = relogio;
        if (officeHome == null) {
            log.info("Conversor de documentos indisponível: LibreOffice (soffice) não encontrado.");
        }
    }

    /** Pasta informada (CONVERSAO_OFFICE_HOME) ou a padrão do sistema; nula quando não há {@code soffice} executável. */
    static File localizar(String informada) {
        File home;
        try {
            home = informada == null || informada.isBlank() ? LocalOfficeUtils.getDefaultOfficeHome()
                    : new File(informada.trim());
        } catch (RuntimeException e) {
            return null;
        }
        if (home == null) {
            return null;
        }
        try {
            File executavel = LocalOfficeUtils.getOfficeExecutable(home);
            return executavel.isFile() && executavel.canExecute() ? home : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean disponivel() {
        return parametros.habilitada() && officeHome != null && !partidaFalhouRecentemente();
    }

    @Override
    public void converter(Path origem, TipoDetectado tipo, Path destinoPdf) {
        String extensao = extensao(tipo);
        if (!disponivel()) {
            throw new FalhaDeConversaoException(Motivo.CONVERSOR_INDISPONIVEL);
        }
        LocalOfficeManager pool = pool();
        Path pasta;
        try {
            pasta = Files.createTempDirectory(destinoPdf.toAbsolutePath().getParent(), "conversao-");
        } catch (IOException e) {
            throw new UncheckedIOException("sem diretório de trabalho para a conversão", e);
        }
        try {
            Path entrada = pasta.resolve("entrada." + extensao);
            copiarEntrada(origem, entrada, extensao);
            if (DocumentoCriptografado.ooxmlCriptografado(entrada)) {
                throw new FalhaDeConversaoException(Motivo.PROTEGIDO_POR_SENHA);
            }
            Path saida = pasta.resolve("saida.pdf");
            executar(pool, entrada, saida);
            validarSaida(saida);
            Files.move(saida, destinoPdf, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FalhaDeConversaoException(Motivo.CORROMPIDO, e);
        } finally {
            apagarPasta(pasta);
        }
    }

    private static String extensao(TipoDetectado tipo) {
        boolean doLibreOffice = tipo != null && (tipo.tipo() == TipoReal.TEXTO || tipo.tipo() == TipoReal.PLANILHA);
        if (!doLibreOffice || tipo.extensaoSugerida() == null || !EXTENSOES.contains(tipo.extensaoSugerida())) {
            throw new FalhaDeConversaoException(Motivo.FORMATO_NAO_SUPORTADO);
        }
        return tipo.extensaoSugerida();
    }

    private static void copiarEntrada(Path origem, Path entrada, String extensao) throws IOException {
        if (!"html".equals(extensao)) {
            Files.copy(origem, entrada);
            return;
        }
        if (Files.size(origem) > HtmlSemRecursosExternos.TAMANHO_MAXIMO) {
            throw new FalhaDeConversaoException(Motivo.ENTRADA_GRANDE_DEMAIS);
        }
        HtmlSemRecursosExternos.limpar(origem, entrada);
    }

    private void executar(LocalOfficeManager pool, Path entrada, Path saida) {
        try {
            LocalConverter.builder()
                    .officeManager(pool)
                    .applyDefaultLoadProperties(false)
                    .loadProperties(CARGA)
                    .filterChain(NoopFilter.CHAIN)
                    .build()
                    .convert(entrada.toFile())
                    .to(saida.toFile())
                    .execute();
        } catch (OfficeException e) {
            Motivo motivo = motivo(e);
            log.warn("Conversão para PDF falhou: {} ({})", motivo, e.getClass().getSimpleName());
            log.debug("Detalhe da falha da conversão", e);
            throw new FalhaDeConversaoException(motivo, e);
        }
    }

    static Motivo motivo(OfficeException e) {
        if (Thread.currentThread().isInterrupted() || temCausa(e, InterruptedException.class)) {
            return Motivo.INTERROMPIDA;
        }
        if (temCausa(e, PasswordProtectedException.class)) {
            return Motivo.PROTEGIDO_POR_SENHA;
        }
        String mensagem = e.getMessage() == null ? "" : e.getMessage();
        if (temCausa(e, TimeoutException.class) || mensagem.startsWith("No office manager available")) {
            return Motivo.TEMPO_LIMITE;
        }
        return Motivo.CORROMPIDO;
    }

    private static boolean temCausa(Throwable e, Class<? extends Throwable> tipo) {
        for (Throwable atual = e; atual != null; atual = atual.getCause()) {
            if (tipo.isInstance(atual)) {
                return true;
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return false;
    }

    private void validarSaida(Path saida) throws IOException {
        if (!Files.isRegularFile(saida) || Files.size(saida) == 0) {
            throw new FalhaDeConversaoException(Motivo.SAIDA_VAZIA);
        }
        if (Files.size(saida) > parametros.saidaMaxMb() * MB) {
            throw new FalhaDeConversaoException(Motivo.SAIDA_GRANDE_DEMAIS);
        }
        try (PDDocument pdf = Loader.loadPDF(saida.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
            if (pdf.getNumberOfPages() == 0) {
                throw new FalhaDeConversaoException(Motivo.SAIDA_VAZIA);
            }
        }
    }

    private synchronized LocalOfficeManager pool() {
        if (encerrado) {
            throw new FalhaDeConversaoException(Motivo.CONVERSOR_INDISPONIVEL);
        }
        if (gerente != null && gerente.isRunning()) {
            return gerente;
        }
        if (partidaFalhouRecentemente()) {
            throw new FalhaDeConversaoException(Motivo.CONVERSOR_INDISPONIVEL);
        }
        try {
            Files.createDirectories(base);
            int orfaos = ProcessosDoConversor.encerrarOrfaosAnteriores(base, ESPERA_DE_ENCERRAMENTO);
            if (orfaos > 0) {
                log.warn("Encerrados {} processo(s) do LibreOffice deixados por uma execução anterior.", orfaos);
            }
            trabalho = Files.createDirectories(base.resolve(marca));
            ProcessosDoConversor.registrarDono(trabalho);
            Path modelo = PerfilSeguro.criar(trabalho.resolve("perfil-modelo"));
            LocalOfficeManager novo = LocalOfficeManager.builder()
                    .officeHome(officeHome)
                    .hostName("127.0.0.1")
                    .portNumbers(portasLivres(Math.max(1, parametros.processos())))
                    .workingDir(trabalho.toFile())
                    .templateProfileDir(modelo.toFile())
                    .maxTasksPerProcess(Math.max(1, parametros.tarefasPorProcesso()))
                    .taskExecutionTimeout(parametros.timeoutSegundos() * 1000L)
                    .taskQueueTimeout(parametros.timeoutSegundos() * 1000L)
                    .processTimeout(TEMPO_DE_PARTIDA_MS)
                    .existingProcessAction(ExistingProcessAction.KILL)
                    .startFailFast(true)
                    .build();
            novo.start();
            gerente = novo;
            partidaFalhouEm = null;
            log.info("Conversor de documentos pronto: {} processo(s) do LibreOffice.", parametros.processos());
            return gerente;
        } catch (OfficeException | IOException | RuntimeException e) {
            partidaFalhouEm = relogio.instant();
            log.warn("Conversor de documentos indisponível: o LibreOffice não subiu ({}).", e.getClass().getSimpleName());
            log.debug("Detalhe da falha ao subir o LibreOffice", e);
            pararPool();
            throw new FalhaDeConversaoException(Motivo.CONVERSOR_INDISPONIVEL, e);
        }
    }

    private boolean partidaFalhouRecentemente() {
        return partidaFalhouEm != null && relogio.instant().isBefore(partidaFalhouEm.plus(NOVA_PARTIDA_APOS));
    }

    private static int[] portasLivres(int quantidade) throws IOException {
        List<ServerSocket> abertas = new ArrayList<>();
        try {
            for (int i = 0; i < quantidade; i++) {
                abertas.add(new ServerSocket(0, 1, InetAddress.getLoopbackAddress()));
            }
            return abertas.stream().mapToInt(ServerSocket::getLocalPort).toArray();
        } finally {
            for (ServerSocket socket : abertas) {
                socket.close();
            }
        }
    }

    /** Para o pool e garante que nenhum processo desta instância sobreviva. */
    @Override
    public synchronized void destroy() {
        encerrado = true;
        pararPool();
    }

    private void pararPool() {
        if (gerente != null) {
            try {
                gerente.stop();
            } catch (OfficeException | RuntimeException e) {
                log.warn("Falha ao parar o LibreOffice ({}); processos encerrados à força.", e.getClass().getSimpleName());
            }
            gerente = null;
        }
        ProcessosDoConversor.encerrar(marca, ESPERA_DE_ENCERRAMENTO);
        if (trabalho != null) {
            apagarPasta(trabalho);
            trabalho = null;
        }
    }

    /** Marca na linha de comando dos processos desta instância (testes de órfãos). */
    String marca() {
        return marca;
    }

    static void apagarPasta(Path pasta) {
        if (pasta == null || !Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            caminhos.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // Segue: a limpeza agendada do dossiê remove o que sobrar.
                }
            });
        } catch (IOException e) {
            // Idem.
        }
    }
}
