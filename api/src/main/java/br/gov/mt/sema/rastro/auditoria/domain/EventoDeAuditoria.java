package br.gov.mt.sema.rastro.auditoria.domain;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Evento imutável da auditoria, retrato do momento (docs/11): guarda cópia do usuário, dos números e dos documentos, nunca
 * chave de arquivo, token, cookie nem caminho. Texto longo é cortado, não recusado: perder o evento seria pior. A
 * mensagem das recusas cita só o campo, nunca o valor.
 *
 * @param sigiloso verdadeiro também quando qualquer documento do evento for sigiloso
 * @param vinculo só para o filtro do Operador (RN07); nunca exposto nem exportado
 * @param origem IP da requisição; {@code null} quando o evento não tem requisição ou o valor não é um IP
 */
public record EventoDeAuditoria(long id, ZonedDateTime dataHora, TipoDeEvento tipo, UsuarioDoEvento usuario,
        Long idRequerimento, String numeroProcesso, String numeroRequerimento, VinculoDoInteressado vinculo,
        boolean sigiloso, String arquivo, String idPedido, String origem, String detalhe, String correlationId,
        List<DocumentoDoEvento> documentos, List<AlteracaoDoEvento> alteracoes) {

    public static final ZoneId FUSO = ZoneId.of("America/Cuiaba");

    private static final int MAX_NUMERO = 60;
    private static final int MAX_ARQUIVO = 255;
    private static final int MAX_ID_PEDIDO = 64;
    private static final int MAX_DETALHE = 1000;
    private static final int MAX_CORRELACAO = 64;

    public EventoDeAuditoria {
        if (id <= 0) {
            throw new IllegalArgumentException("id do evento inválido");
        }
        exigir(dataHora != null, "dataHora");
        exigir(tipo != null, "tipo");
        exigir(usuario != null && usuario.login() != null, "usuario.login");
        dataHora = dataHora.withZoneSameInstant(FUSO);
        numeroProcesso = TextoDoEvento.limpar(numeroProcesso, MAX_NUMERO);
        numeroRequerimento = TextoDoEvento.limpar(numeroRequerimento, MAX_NUMERO);
        vinculo = Objects.requireNonNullElseGet(vinculo, () -> VinculoDoInteressado.de(null));
        arquivo = TextoDoEvento.nomeDeArquivo(arquivo, MAX_ARQUIVO);
        idPedido = TextoDoEvento.identificador(idPedido, MAX_ID_PEDIDO);
        origem = OrigemDoEvento.validar(origem);
        detalhe = TextoDoEvento.limpar(detalhe, MAX_DETALHE);
        correlationId = TextoDoEvento.identificador(correlationId, MAX_CORRELACAO);
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
        alteracoes = alteracoes == null ? List.of() : List.copyOf(alteracoes);
        documentos.forEach(d -> exigir(d.origem() != null && d.id() != null && d.titulo() != null, "documento"));
        alteracoes.forEach(a -> exigir(a.item() != null, "alteracao.item"));
        sigiloso = sigiloso || documentos.stream().anyMatch(DocumentoDoEvento::sigiloso);
        exigirCamposDoTipo(tipo, idRequerimento, arquivo, idPedido, detalhe, documentos, alteracoes);
    }

    public static EventoDeAuditoria de(long id, ZonedDateTime dataHora, NovoEvento novo) {
        return new EventoDeAuditoria(id, dataHora, novo.tipo(), novo.usuario(), novo.idRequerimento(),
                novo.numeroProcesso(), novo.numeroRequerimento(), novo.vinculo(), novo.sigiloso(), novo.arquivo(),
                novo.idPedido(), novo.origem(), novo.detalhe(), novo.correlationId(), novo.documentos(),
                novo.alteracoes());
    }

    public GrupoDeEvento grupo() {
        return tipo.grupo();
    }

    /**
     * Cópia para quem não acessa sigilosos: documento sigiloso sem título real nem nome de arquivo. O detalhe e o arquivo
     * de evento sigiloso também saem, porque podem citar o documento (motivo de falha); ficam só os de {@code DOSSIE},
     * {@code DOWNLOAD} e {@code GEOMETRIAS}, montados pelo serviço, sem nome de documento.
     */
    public EventoDeAuditoria comSigilosoMascarado(String tituloMascarado, String detalheOmitido) {
        List<DocumentoDoEvento> mascarados = documentos.stream().map(d -> d.sigiloso()
                ? new DocumentoDoEvento(d.origem(), d.id(), tituloMascarado, null, true, d.ordem())
                : d).toList();
        boolean textoDoServico = tipo == TipoDeEvento.DOSSIE || tipo == TipoDeEvento.DOWNLOAD
                || tipo == TipoDeEvento.GEOMETRIAS;
        boolean omitir = sigiloso && !textoDoServico;
        return new EventoDeAuditoria(id, dataHora, tipo, usuario, idRequerimento, numeroProcesso, numeroRequerimento,
                vinculo, sigiloso, omitir ? null : arquivo, idPedido, origem,
                omitir && detalhe != null ? detalheOmitido : detalhe, correlationId, mascarados, alteracoes);
    }

    /** Campos obrigatórios por tipo, conforme "Detalhe registrado" de docs/11. */
    private static void exigirCamposDoTipo(TipoDeEvento tipo, Long idRequerimento, String arquivo, String idPedido,
            String detalhe, List<DocumentoDoEvento> documentos, List<AlteracaoDoEvento> alteracoes) {
        if (tipo.grupo() == GrupoDeEvento.DOCUMENTOS) {
            exigir(idRequerimento != null, "idRequerimento");
        }
        switch (tipo) {
            case CONSULTA, LOGIN -> { }
            case VISUALIZACAO -> exigir(!documentos.isEmpty(), "documentos");
            case DOSSIE -> {
                exigir(!documentos.isEmpty(), "documentos");
                exigir(arquivo != null, "arquivo");
                exigir(idPedido != null, "idPedido");
            }
            case FALHA -> {
                exigir(idPedido != null, "idPedido");
                exigir(detalhe != null, "detalhe");
            }
            case DOWNLOAD -> {
                exigir(arquivo != null, "arquivo");
                exigir(idPedido != null, "idPedido");
            }
            case GEOMETRIAS -> exigir(arquivo != null, "arquivo");
            case PERFIL -> exigir(detalhe != null, "detalhe");
            case USUARIO, CONFIG -> exigir(!alteracoes.isEmpty(), "alteracoes");
        }
    }

    private static void exigir(boolean presente, String campo) {
        if (!presente) {
            throw new IllegalArgumentException("Evento de auditoria sem " + campo);
        }
    }
}
