package br.gov.mt.sema.vistas.documento.application;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Identificacao;
import br.gov.mt.sema.vistas.documento.application.port.ArquivosDoDocumento;
import br.gov.mt.sema.vistas.documento.application.port.EspacoLivreEmDisco;
import br.gov.mt.sema.vistas.documento.application.port.FalhaAoObterArquivoException;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDasGeometrias;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.NomesNoZip;
import br.gov.mt.sema.vistas.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.vistas.shared.application.exception.VistasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * "Baixar geometrias" (RF15, RN10, S4): um ZIP com todas as geometrias vigentes que o usuário acessa.
 *
 * <p>Acesso primeiro (404 e RN07, pela política); geometria sigilosa não chega ao Operador. Todos os arquivos são
 * obtidos com o mesmo cliente do dossiê (tempos, novas tentativas, token) para uma pasta temporária própria, com a
 * checagem de espaço antes de cada um. <b>Tudo ou nada</b> (como a RN09): se algum falhar, nenhum ZIP é montado e o
 * erro lista os arquivos com problema. Só então o ZIP é montado, com nomes seguros e únicos; os originais são apagados
 * à medida que entram nele. A pasta some quando a resposta termina (ou na falha); a limpeza agendada pega o que sobrar.
 */
@Service
public class BaixarGeometrias {

    private static final Logger log = LoggerFactory.getLogger(BaixarGeometrias.class);

    static final String SEM_GEOMETRIAS = "Este requerimento não tem geometrias.";
    static final String ESPACO_INSUFICIENTE = "espaço em disco insuficiente";
    private static final String PREFIXO_DA_PASTA = "pedido-";
    private static final String NOME_DO_ZIP = "geometrias.zip";
    private static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private final VerificarAcessoAoRequerimento acesso;
    private final ListarDocumentos listagem;
    private final PoliticaDeAcesso politica;
    private final ArquivosDoDocumento arquivos;
    private final ParametrosDasGeometrias parametros;
    private final EspacoLivreEmDisco espaco;

    public BaixarGeometrias(VerificarAcessoAoRequerimento acesso, ListarDocumentos listagem, PoliticaDeAcesso politica,
            ArquivosDoDocumento arquivos, ParametrosDasGeometrias parametros, EspacoLivreEmDisco espaco) {
        this.acesso = acesso;
        this.listagem = listagem;
        this.politica = politica;
        this.arquivos = arquivos;
        this.parametros = parametros;
        this.espaco = espaco;
    }

    /** O ZIP pronto em disco; quem entrega chama {@link ZipDeGeometrias#descartar()} ao terminar. */
    public ZipDeGeometrias executar(long idRequerimento) {
        Identificacao identificacao = acesso.executar(idRequerimento).identificacao();
        // Para o Operador, "só sigilosas" e "nenhuma" dão a mesma resposta: nada revela a diferença.
        List<Documento> geometrias = listagem.geometriasDoRequerimento(idRequerimento).stream()
                .filter(d -> politica.podeAcessarDocumento(d.sigiloso()))
                .filter(d -> d.situacao() == SituacaoDocumento.VIGENTE)
                .toList();
        if (geometrias.isEmpty()) {
            throw new RecursoNaoEncontradoException(SEM_GEOMETRIAS);
        }
        if (geometrias.size() > parametros.maximoDeArquivos()) {
            throw new VistasException(ErrorCode.GEOMETRIAS_GRANDES_DEMAIS, "Este requerimento tem "
                    + geometrias.size() + " geometrias; o download em ZIP aceita até "
                    + parametros.maximoDeArquivos() + " arquivos.");
        }
        // S5: auditoria — evento GEOMETRIAS (docs/11): requerimento, quantidade de arquivos, quantas sigilosas e nome do ZIP.
        Path pasta = criarPasta();
        try {
            List<Path> obtidos = new ArrayList<>();
            List<String> nomes = new ArrayList<>();
            List<GeometriasIncompletasException.Falha> falhas = new ArrayList<>();
            NomesNoZip nomesNoZip = new NomesNoZip();
            long limite = parametros.maximoMb() * 1024L * 1024L;
            long total = 0;
            for (int i = 0; i < geometrias.size(); i++) {
                Documento geometria = geometrias.get(i);
                String nome = nomesNoZip.reservar(geometria.nomeArquivo(), geometria.extensao());
                Path destino = pasta.resolve(String.format(Locale.ROOT, "%05d", i));
                if (!haEspacoLivre(pasta)) {
                    falhas.add(new GeometriasIncompletasException.Falha(nome, ESPACO_INSUFICIENTE, false));
                    continue;
                }
                try {
                    arquivos.baixar(geometria, destino);
                } catch (FalhaAoObterArquivoException e) {
                    falhas.add(new GeometriasIncompletasException.Falha(nome, e.getMessage(),
                            e.motivo() == FalhaAoObterArquivoException.Motivo.TEMPO_LIMITE));
                    continue;
                }
                total += tamanho(destino);
                if (total > limite) {
                    throw new VistasException(ErrorCode.GEOMETRIAS_GRANDES_DEMAIS, "As geometrias deste requerimento "
                            + "passam do limite de " + parametros.maximoMb() + " MB para o download em ZIP.");
                }
                obtidos.add(destino);
                nomes.add(nome);
            }
            if (!falhas.isEmpty()) {
                log.warn("Geometrias do requerimento {}: {} de {} arquivo(s) com problema; nenhum ZIP entregue",
                        idRequerimento, falhas.size(), geometrias.size());
                throw new GeometriasIncompletasException(falhas, geometrias.size());
            }
            Path zip = pasta.resolve(NOME_DO_ZIP);
            montar(zip, obtidos, nomes, geometrias);
            return new ZipDeGeometrias(zip, nomeDoZip(identificacao), tamanho(zip), () -> apagarPasta(pasta));
        } catch (RuntimeException e) {
            apagarPasta(pasta);
            throw e;
        }
    }

    /** Apaga subpastas de downloads que não terminaram (queda do serviço); com {@code limite} nulo, todas. */
    public int apagarTemporarios(Instant limite) {
        Path raiz = parametros.diretorio();
        if (!Files.isDirectory(raiz)) {
            return 0;
        }
        int apagadas = 0;
        try (Stream<Path> filhos = Files.list(raiz)) {
            for (Path pasta : filhos.filter(p -> p.getFileName().toString().startsWith(PREFIXO_DA_PASTA)).toList()) {
                if (limite == null || modificadaAntesDe(pasta, limite)) {
                    apagarPasta(pasta);
                    apagadas++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return apagadas;
    }

    /** RF18: geometrias-processo-2825-2025.zip ou geometrias-requerimento-3331-2025.zip. */
    static String nomeDoZip(Identificacao identificacao) {
        String numero = identificacao.numero() == null ? "" : identificacao.numero();
        String numeroSeguro = numero.replaceAll("[^0-9A-Za-z]+", "-").replaceAll("^-+|-+$", "");
        return "geometrias-" + identificacao.rotulo().toLowerCase(Locale.ROOT) + "-" + numeroSeguro + ".zip";
    }

    private Path criarPasta() {
        try {
            Files.createDirectories(parametros.diretorio());
            return Files.createDirectory(parametros.diretorio().resolve(PREFIXO_DA_PASTA + UUID.randomUUID()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean haEspacoLivre(Path pasta) {
        long minimo = parametros.espacoMinimoMb() * 1024L * 1024L;
        return minimo <= 0 || espaco.livreEmBytes(pasta) >= minimo;
    }

    /** Cada original sai do disco assim que entra no ZIP: o pico de espaço fica perto de uma vez o total. */
    private static void montar(Path zip, List<Path> obtidos, List<String> nomes, List<Documento> geometrias) {
        try (OutputStream saida = Files.newOutputStream(zip); ZipOutputStream entradas = new ZipOutputStream(saida)) {
            for (int i = 0; i < obtidos.size(); i++) {
                ZipEntry entrada = new ZipEntry(nomes.get(i));
                if (geometrias.get(i).data() != null) {
                    entrada.setLastModifiedTime(FileTime.from(geometrias.get(i).data().atZone(FUSO).toInstant()));
                }
                entradas.putNextEntry(entrada);
                Files.copy(obtidos.get(i), entradas);
                entradas.closeEntry();
                Files.deleteIfExists(obtidos.get(i));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long tamanho(Path arquivo) {
        try {
            return Files.size(arquivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean modificadaAntesDe(Path pasta, Instant limite) {
        try {
            return Files.getLastModifiedTime(pasta).toInstant().isBefore(limite);
        } catch (IOException e) {
            return false;
        }
    }

    static void apagarPasta(Path pasta) {
        if (!Files.exists(pasta)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            for (Path caminho : caminhos.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(caminho);
            }
        } catch (IOException | UncheckedIOException e) {
            log.warn("Não foi possível apagar a pasta temporária das geometrias: {}", e.getClass().getSimpleName());
        }
    }

    /** O ZIP montado. {@link #descartar()} apaga a pasta do download; pode ser chamado mais de uma vez. */
    public record ZipDeGeometrias(Path caminho, String nome, long tamanhoBytes, Runnable aoDescartar) {

        public void descartar() {
            aoDescartar.run();
        }
    }
}
