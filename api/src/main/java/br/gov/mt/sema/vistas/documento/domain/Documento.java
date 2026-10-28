package br.gov.mt.sema.vistas.documento.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * Documento do requerimento. O {@code id} é texto para acomodar origens futuras (SCORP). A chave do arquivo no
 * repositório é interna: só o adapter de arquivos a usa, e ela nunca vai para DTO, log nem resposta (RNF09);
 * por isso fica de fora do {@code toString}.
 */
public final class Documento {

    /** Ids numéricos em texto: o mais curto vem antes (9 antes de 10); em igual tamanho, ordem lexical. */
    static final Comparator<String> ORDEM_DE_ID = Comparator.comparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());

    public static final Comparator<Documento> ORDEM_CRONOLOGICA = Comparator
            .comparing(Documento::data, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(d -> d.origem().ordinal())
            .thenComparing(Documento::id, ORDEM_DE_ID);

    private final OrigemDocumento origem;
    private final String id;
    private final long idRequerimento;
    private final String chave;
    private final String titulo;
    private final String nomeArquivo;
    private final String extensao;
    private final TipoArquivo tipoArquivo;
    private final LocalDateTime data;
    private final SituacaoDocumento situacao;
    private final boolean sigiloso;
    private final String complementoDaOrigem;

    private Documento(OrigemDocumento origem, String id, long idRequerimento, String chave, String titulo,
            String nomeArquivo, LocalDateTime data, SituacaoDocumento situacao, boolean sigiloso) {
        this(origem, id, idRequerimento, chave, titulo, nomeArquivo, data, situacao, sigiloso, null);
    }

    private Documento(OrigemDocumento origem, String id, long idRequerimento, String chave, String titulo,
            String nomeArquivo, LocalDateTime data, SituacaoDocumento situacao, boolean sigiloso,
            String complementoDaOrigem) {
        this.origem = origem;
        this.id = id;
        this.idRequerimento = idRequerimento;
        this.chave = chave == null ? null : chave.trim();
        this.nomeArquivo = vazioParaNulo(nomeArquivo);
        this.titulo = vazioParaNulo(titulo) != null ? titulo.trim()
                : this.nomeArquivo != null ? this.nomeArquivo : origem.descricao() + " " + id;
        this.extensao = TipoArquivo.extensaoDe(this.nomeArquivo);
        this.tipoArquivo = TipoArquivo.daExtensao(extensao);
        this.data = data;
        this.situacao = situacao;
        this.sigiloso = sigiloso;
        this.complementoDaOrigem = complementoDaOrigem;
    }

    /**
     * Monta o documento a partir de uma linha da origem.
     *
     * @param fimDaVigencia coluna DATA_FIM (nula se a vigência está em aberto)
     * @param sigiloBruto   valor cru da coluna de sigilo (NUMBER 0 ou 1; normalização tolerante, RN06)
     */
    public static Documento deLinha(OrigemDocumento origem, String id, long idRequerimento, String chave,
            String titulo, String nomeArquivo, LocalDateTime data, LocalDateTime fimDaVigencia, Object sigiloBruto) {
        SituacaoDocumento situacao = SituacaoDocumento.calcular(chave, fimDaVigencia);
        boolean sigiloso = Sigilo.ehSigiloso(sigiloBruto);
        return new Documento(origem, id, idRequerimento, chave, titulo, nomeArquivo, data, situacao, sigiloso);
    }

    /**
     * Documento pessoal do SCORP (S4). O {@code id} é estável e não expõe a chave: os 16 primeiros caracteres
     * hexadecimais do SHA-256 de {@code idCorporativo|chave} (sem chave válida, entram também descrição, nome do arquivo
     * e data, para não colidir). Título = descrição (ou nome do arquivo); data = {@code dataInicio}; vigência pela
     * {@code dataFim} e pela chave (RN01). O SCORP não informa sigilo: vale o padrão configurado (P30).
     */
    public static Documento pessoal(String idCorporativo, PapelDaPessoa papel, long idRequerimento, String chave,
            String descricao, String nomeArquivo, LocalDateTime dataInicio, LocalDateTime dataFim, boolean sigiloso) {
        String base = idCorporativo + "|" + (chave == null ? "" : chave.trim());
        if (!SituacaoDocumento.chaveValida(chave)) {
            base += "|" + descricao + "|" + nomeArquivo + "|" + dataInicio;
        }
        return new Documento(OrigemDocumento.SCORP, resumo(base), idRequerimento, chave, descricao, nomeArquivo,
                dataInicio, SituacaoDocumento.calcular(chave, dataFim), sigiloso, papel.descricao());
    }

    private static String resumo(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrigemDocumento origem() {
        return origem;
    }

    /** Descrição da origem para a lista: "Pessoal · Interessado" no SCORP; nas demais, a da origem. */
    public String descricaoDaOrigem() {
        return complementoDaOrigem == null ? origem.descricao() : origem.descricao() + " · " + complementoDaOrigem;
    }

    public String id() {
        return id;
    }

    public long idRequerimento() {
        return idRequerimento;
    }

    /** Título; se vazio, o nome do arquivo; se ambos faltam, "<descrição da origem> <id>". */
    public String titulo() {
        return titulo;
    }

    public String nomeArquivo() {
        return nomeArquivo;
    }

    public String extensao() {
        return extensao;
    }

    public TipoArquivo tipoArquivo() {
        return tipoArquivo;
    }

    public LocalDateTime data() {
        return data;
    }

    public SituacaoDocumento situacao() {
        return situacao;
    }

    public boolean sigiloso() {
        return sigiloso;
    }

    public boolean ehPdf() {
        return tipoArquivo == TipoArquivo.PDF;
    }

    /** Sem extensão ou com extensão não reconhecida: o tipo real é conferido ao baixar (assinatura "%PDF-"). */
    public boolean tipoNaoInformado() {
        return tipoArquivo == TipoArquivo.DESCONHECIDO;
    }

    /** Chave do arquivo no repositório. Interna: só a obtenção do binário a usa. */
    public String chaveDoArquivo() {
        return chave;
    }

    /** Mesma chave válida (diferente de "0") em outra linha: é o mesmo arquivo (RN05). */
    boolean temChaveValida() {
        return SituacaoDocumento.chaveValida(chave);
    }

    /** Planilha, texto ou imagem de formato convertível para PDF (RF13), pela extensão. */
    public boolean convertivel() {
        return TipoArquivo.convertivel(extensao);
    }

    /**
     * Motivo pelo qual o documento não pode ser selecionado para o dossiê, ou nulo. Precedência: indisponível,
     * sigiloso sem permissão, não PDF. Tipo desconhecido não bloqueia. Com a conversão disponível (S3), o tipo
     * convertível deixa de ser {@code NAO_PDF}.
     */
    public Bloqueio bloqueio(boolean perfilPodeVerSigilosos, boolean conversaoDisponivel) {
        if (situacao == SituacaoDocumento.INDISPONIVEL) {
            return Bloqueio.INDISPONIVEL;
        }
        if (sigiloso && !perfilPodeVerSigilosos) {
            return Bloqueio.SIGILOSO;
        }
        if (!ehPdf() && !tipoNaoInformado() && !(conversaoDisponivel && convertivel())) {
            return Bloqueio.NAO_PDF;
        }
        return null;
    }

    /**
     * Seleção inicial (S2.2; a Vista do SIGA não pré-seleciona): todo documento selecionável, vigente ou sem efeito,
     * de qualquer tipo (inclusive {@code DESCONHECIDO}). Ficam fora só os que têm bloqueio.
     */
    public boolean selecionavelPorPadrao(boolean perfilPodeVerSigilosos, boolean conversaoDisponivel) {
        return bloqueio(perfilPodeVerSigilosos, conversaoDisponivel) == null;
    }

    public enum Bloqueio {
        NAO_PDF,
        INDISPONIVEL,
        SIGILOSO
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    @Override
    public String toString() {
        return "Documento[" + origem + "/" + id + "]";
    }
}
