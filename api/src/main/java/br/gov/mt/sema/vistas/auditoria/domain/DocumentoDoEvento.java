package br.gov.mt.sema.vistas.auditoria.domain;

/**
 * Retrato de um documento no momento do evento. A chave do arquivo não existe aqui (RNF09). A construção só limpa e
 * nunca lança; os obrigatórios são cobrados em {@link EventoDeAuditoria}.
 *
 * @param ordem posição no dossiê; {@code null} quando o evento não é de dossiê
 */
public record DocumentoDoEvento(String origem, String id, String titulo, String nomeDoArquivo, boolean sigiloso,
        Integer ordem) {

    private static final int MAX_ORIGEM = 60;
    private static final int MAX_ID = 100;
    private static final int MAX_TITULO = 500;
    private static final int MAX_NOME_DO_ARQUIVO = 255;

    public DocumentoDoEvento {
        origem = TextoDoEvento.limpar(origem, MAX_ORIGEM);
        id = TextoDoEvento.identificador(id, MAX_ID);
        titulo = TextoDoEvento.limpar(titulo, MAX_TITULO);
        nomeDoArquivo = TextoDoEvento.nomeDeArquivo(nomeDoArquivo, MAX_NOME_DO_ARQUIVO);
    }
}
