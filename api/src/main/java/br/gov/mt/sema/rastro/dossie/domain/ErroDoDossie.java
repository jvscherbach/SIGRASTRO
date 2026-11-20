package br.gov.mt.sema.rastro.dossie.domain;

/**
 * Motivo geral que deixou o pedido em PENDENTE_DECISAO sem ser de um documento só (S2.2): credencial recusada (com
 * {@code codigo}, vale para todos os documentos; origem, id e título são do documento em que a recusa ocorreu) ou falha
 * na montagem. As falhas por documento ficam nos itens. {@code codigo} é preenchido na recusa da credencial da API
 * de arquivos (vale para todos os documentos) e nos motivos de FALHA (S6): sem espaço em disco e arquivo final perdido.
 */
public record ErroDoDossie(String origem, String id, String titulo, String motivo, String codigo) {

    public static final String CREDENCIAL_ARQUIVOS_RECUSADA = "CREDENCIAL_ARQUIVOS_RECUSADA";
    /** Sem espaço em disco (S6, bloco 3): o pedido vai para FALHA. */
    public static final String ARMAZENAMENTO_INSUFICIENTE = "ARMAZENAMENTO_INSUFICIENTE";
    /** O PDF final sumiu do disco depois de concluído (S6, bloco 3): o pedido vai para FALHA. */
    public static final String ARQUIVO_AUSENTE = "DOSSIE_ARQUIVO_AUSENTE";
    /** O PDF final ficou vazio ou com tamanho diferente do gravado (S6, bloco 3): o pedido vai para FALHA. */
    public static final String ARQUIVO_INVALIDO = "DOSSIE_ARQUIVO_INVALIDO";

    /** Motivo geral com código, sem documento. */
    public static ErroDoDossie geral(String motivo, String codigo) {
        return new ErroDoDossie(null, null, null, motivo, codigo);
    }
}
