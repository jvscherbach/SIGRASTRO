package br.gov.mt.sema.vistas.documento.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.ArquivosDoDocumento;
import br.gov.mt.sema.vistas.documento.application.port.ConversorDeDocumentos;
import br.gov.mt.sema.vistas.documento.application.port.ConversorDeImagens;
import br.gov.mt.sema.vistas.documento.application.port.FalhaAoObterArquivoException;
import br.gov.mt.sema.vistas.documento.application.port.FalhaDeConversaoException;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaVisualizacao;
import br.gov.mt.sema.vistas.documento.domain.DetectorDeTipoReal;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.vistas.documento.domain.TipoDetectado;
import br.gov.mt.sema.vistas.documento.domain.TipoReal;
import br.gov.mt.sema.vistas.shared.api.error.ErrorCode;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Conteúdo de um documento para o modal (RF09, S3): o arquivo como veio ou a versão em PDF, convertida com o mesmo
 * conversor e os mesmos parâmetros do dossiê.
 *
 * <p>O acesso vem antes de tudo e roda a cada requisição, mesmo com o arquivo em cache: RN07 no requerimento, o
 * documento relido da lista do servidor (origem e id precisam pertencer ao requerimento; os ids se repetem entre
 * origens), sigilo (RF20) e situação. O cliente só informa origem e id; a chave do arquivo fica no domínio e no adapter.
 */
@Service
public class ObterConteudoDoDocumento {

    public enum Formato {
        ORIGINAL,
        PDF
    }

    static final String NAO_ENCONTRADO = "Documento não encontrado neste requerimento.";

    private final VerificarAcessoAoRequerimento acesso;
    private final ListarDocumentos listagem;
    private final PoliticaDeAcesso politica;
    private final ArquivosDoDocumento arquivos;
    private final ConversorDeDocumentos conversorDeDocumentos;
    private final ConversorDeImagens conversorDeImagens;
    private final ParametrosDaConversao conversao;
    private final CacheDeVisualizacao cache;
    private final int limiteDeConversoes;
    private final Semaphore vagas;
    private final AtomicInteger admitidas = new AtomicInteger();

    public ObterConteudoDoDocumento(VerificarAcessoAoRequerimento acesso, ListarDocumentos listagem,
            PoliticaDeAcesso politica, ArquivosDoDocumento arquivos,
            ConversorDeDocumentos conversorDeDocumentos, ConversorDeImagens conversorDeImagens,
            ParametrosDaConversao conversao, ParametrosDaVisualizacao visualizacao, CacheDeVisualizacao cache) {
        this.acesso = acesso;
        this.listagem = listagem;
        this.politica = politica;
        this.arquivos = arquivos;
        this.conversorDeDocumentos = conversorDeDocumentos;
        this.conversorDeImagens = conversorDeImagens;
        this.conversao = conversao;
        this.cache = cache;
        int simultaneas = Math.max(1, visualizacao.conversoesSimultaneas());
        this.vagas = new Semaphore(simultaneas, true);
        this.limiteDeConversoes = simultaneas + Math.max(0, visualizacao.filaDeConversoes());
    }

    public ConteudoDoDocumento executar(long idRequerimento, String origem, String id, Formato formato) {
        Documento documento = documentoAcessivel(idRequerimento, origem, id);
        Path pasta = cache.pastaDe(documento);
        return cache.comTrava(pasta, () -> {
            Path original = original(documento, pasta);
            TipoDetectado tipo = DetectorDeTipoReal.detectar(original, documento.extensao());
            if (formato == Formato.ORIGINAL) {
                boolean exibivel = tipo.tipo() == TipoReal.PDF || tipo.tipo() == TipoReal.IMAGEM;
                return abrir(original, tipoDeConteudo(tipo), nomeOriginal(documento, tipo), !exibivel, null);
            }
            if (tipo.tipo() == TipoReal.PDF) {
                return abrir(original, "application/pdf", nomePdf(documento), false, null);
            }
            Path pdf = cache.vigente(pasta, CacheDeVisualizacao.PDF);
            if (pdf == null) {
                pdf = converter(original, tipo, pasta, documento.titulo());
            }
            return abrir(pdf, "application/pdf", nomePdf(documento), false, rotulo(tipo));
        });
    }

    /** RN07 (403/404 do requerimento), pertencimento ao requerimento (404), sigilo (403) e situação (404). */
    private Documento documentoAcessivel(long idRequerimento, String origem, String id) {
        acesso.executar(idRequerimento);
        String idLimpo = id == null ? "" : id.trim();
        List<Documento> lista = listagem.listaDoRequerimento(idRequerimento);
        Documento documento = lista.stream()
                .filter(d -> d.origem().name().equals(origem) && d.id().equals(idLimpo))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException(NAO_ENCONTRADO));
        politica.exigirAcessoAoDocumento(documento.sigiloso());
        if (documento.situacao() == SituacaoDocumento.INDISPONIVEL) {
            throw new RecursoNaoEncontradoException("O arquivo deste documento não está disponível no repositório.");
        }
        return documento;
    }

    /** Do cache ou do repositório. Chamado com a trava da pasta. */
    private Path original(Documento documento, Path pasta) {
        Path emCache = cache.vigente(pasta, CacheDeVisualizacao.ORIGINAL);
        if (emCache != null) {
            return emCache;
        }
        Path parcial = cache.parcial(pasta, CacheDeVisualizacao.ORIGINAL);
        try {
            arquivos.baixar(documento, parcial);
        } catch (FalhaAoObterArquivoException e) {
            CacheDeVisualizacao.apagar(parcial);
            throw traduzir(e);
        } catch (RuntimeException e) {
            CacheDeVisualizacao.apagar(parcial);
            throw e;
        }
        return cache.publicar(parcial, CacheDeVisualizacao.ORIGINAL);
    }

    private Path converter(Path original, TipoDetectado tipo, Path pasta, String titulo) {
        if (!tipo.convertivel()) {
            throw new FalhaNoConteudoException(ErrorCode.FORMATO_NAO_SUPORTADO,
                    "Este documento não pode ser exibido como PDF: " + descrever(tipo) + " não tem conversão.");
        }
        if (!conversao.habilitada()) {
            throw new FalhaNoConteudoException(ErrorCode.FORMATO_NAO_SUPORTADO,
                    "Este documento não é um PDF (" + descrever(tipo) + ") e a conversão está desligada.");
        }
        boolean imagem = tipo.tipo() == TipoReal.IMAGEM;
        if (!imagem && !conversorDeDocumentos.disponivel()) {
            throw new FalhaNoConteudoException(ErrorCode.CONVERSOR_INDISPONIVEL,
                    "Não foi possível converter o documento para PDF: "
                            + FalhaDeConversaoException.Motivo.CONVERSOR_INDISPONIVEL.mensagem() + ".");
        }
        admitir();
        Path parcial = cache.parcial(pasta, CacheDeVisualizacao.PDF);
        try {
            vagas.acquire();
            try {
                if (imagem) {
                    conversorDeImagens.converter(original, tipo, parcial);
                } else {
                    conversorDeDocumentos.converter(original, tipo, parcial, titulo);
                }
            } finally {
                vagas.release();
            }
            return cache.publicar(parcial, CacheDeVisualizacao.PDF);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw traduzir(new FalhaDeConversaoException(FalhaDeConversaoException.Motivo.INTERROMPIDA));
        } catch (FalhaDeConversaoException e) {
            throw traduzir(e);
        } finally {
            CacheDeVisualizacao.apagar(parcial);
            admitidas.decrementAndGet();
        }
    }

    /** Conversões em andamento mais as que esperam a vez; acima do limite, 429. */
    private void admitir() {
        if (admitidas.incrementAndGet() > limiteDeConversoes) {
            admitidas.decrementAndGet();
            throw new FalhaNoConteudoException(ErrorCode.MUITAS_CONVERSOES,
                    "Muitas conversões em andamento. Tente de novo em alguns instantes.");
        }
    }

    private static ConteudoDoDocumento abrir(Path arquivo, String tipoDeConteudo, String nome, boolean anexo,
            String convertidoDe) {
        try {
            long tamanho = Files.size(arquivo);
            InputStream corpo = Files.newInputStream(arquivo);
            return new ConteudoDoDocumento(corpo, tamanho, tipoDeConteudo, nome, anexo, convertidoDe);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível abrir o arquivo do cache de visualização.", e);
        }
    }

    static FalhaNoConteudoException traduzir(FalhaAoObterArquivoException e) {
        return switch (e.motivo()) {
            case NAO_ENCONTRADO -> new FalhaNoConteudoException(ErrorCode.NAO_ENCONTRADO,
                    "O arquivo do documento não foi encontrado no repositório.");
            case TEMPO_LIMITE -> new FalhaNoConteudoException(ErrorCode.TEMPO_LIMITE_EXCEDIDO,
                    "Tempo limite excedido ao obter o arquivo do repositório.");
            case INDISPONIVEL -> new FalhaNoConteudoException(ErrorCode.REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL,
                    "Não foi possível obter o arquivo: " + e.getMessage() + ".");
        };
    }

    static FalhaNoConteudoException traduzir(FalhaDeConversaoException e) {
        ErrorCode codigo = switch (e.motivo()) {
            case PROTEGIDO_POR_SENHA -> ErrorCode.DOCUMENTO_PROTEGIDO_POR_SENHA;
            case CORROMPIDO -> ErrorCode.DOCUMENTO_CORROMPIDO;
            case TEMPO_LIMITE -> ErrorCode.TEMPO_LIMITE_EXCEDIDO;
            case CONVERSOR_INDISPONIVEL, INTERROMPIDA -> ErrorCode.CONVERSOR_INDISPONIVEL;
            case FORMATO_NAO_SUPORTADO -> ErrorCode.FORMATO_NAO_SUPORTADO;
            default -> ErrorCode.CONVERSAO_RECUSADA;
        };
        return new FalhaNoConteudoException(codigo,
                "Não foi possível converter o documento para PDF: " + e.motivo().mensagem() + ".");
    }

    /** "XLSX", "DOCX", "PNG"... para o cabeçalho de quem exibe. */
    private static String rotulo(TipoDetectado tipo) {
        return tipo.extensaoSugerida() == null ? null : tipo.extensaoSugerida().toUpperCase(Locale.ROOT);
    }

    private static String descrever(TipoDetectado tipo) {
        String familia = switch (tipo.tipo()) {
            case PLANILHA -> "planilha";
            case TEXTO -> "texto";
            case IMAGEM -> "imagem";
            case COMPACTADO -> "arquivo compactado";
            case PDF -> "PDF";
            case DESCONHECIDO -> "tipo não reconhecido";
        };
        String rotulo = rotulo(tipo);
        return rotulo == null ? familia : familia + " " + rotulo;
    }

    static String tipoDeConteudo(TipoDetectado tipo) {
        String extensao = tipo.extensaoSugerida() == null ? "" : tipo.extensaoSugerida();
        return switch (extensao) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "odt" -> "application/vnd.oasis.opendocument.text";
            case "rtf" -> "application/rtf";
            case "html" -> "text/html";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    /** Nome do arquivo como veio; sem extensão, recebe a do tipo real. */
    static String nomeOriginal(Documento documento, TipoDetectado tipo) {
        String nome = nomeSeguro(documento);
        if (documento.extensao() == null && tipo.extensaoSugerida() != null) {
            nome = nome + "." + tipo.extensaoSugerida();
        }
        return nome;
    }

    /** Nome com a extensão trocada por ".pdf". */
    static String nomePdf(Documento documento) {
        String nome = nomeSeguro(documento);
        String extensao = documento.extensao();
        if (extensao != null && nome.toLowerCase(Locale.ROOT).endsWith("." + extensao.toLowerCase(Locale.ROOT))
                && nome.length() > extensao.length() + 1) {
            nome = nome.substring(0, nome.length() - extensao.length() - 1);
        }
        return nome.toLowerCase(Locale.ROOT).endsWith(".pdf") ? nome : nome + ".pdf";
    }

    /**
     * Nome para o {@code Content-Disposition}: sem caminho (só o que vem depois da última barra), sem caractere de
     * controle, aspas nem barra invertida, sem ponto no início e com até 150 caracteres.
     */
    static String nomeSeguro(Documento documento) {
        String bruto = documento.nomeArquivo() != null ? documento.nomeArquivo() : documento.titulo();
        String nome = bruto == null ? "" : bruto;
        int barra = Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\'));
        nome = nome.substring(barra + 1);
        StringBuilder limpo = new StringBuilder();
        nome.codePoints()
                .filter(c -> !Character.isISOControl(c) && c != '"' && c != '\\' && c != ';'
                        && Character.getType(c) != Character.FORMAT)
                .forEach(limpo::appendCodePoint);
        nome = limpo.toString().strip();
        while (nome.startsWith(".")) {
            nome = nome.substring(1).strip();
        }
        if (nome.length() > 150) {
            nome = nome.substring(nome.length() - 150);
        }
        return nome.isEmpty() ? "documento" : nome;
    }
}
