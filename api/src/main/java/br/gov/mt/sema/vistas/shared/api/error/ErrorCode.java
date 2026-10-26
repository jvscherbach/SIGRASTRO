package br.gov.mt.sema.vistas.shared.api.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST, "Parâmetros inválidos"),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado"),
    PERFIL_SEM_PERMISSAO(HttpStatus.FORBIDDEN, "Acesso negado"),
    DOCUMENTO_SIGILOSO(HttpStatus.FORBIDDEN, "Documento sigiloso"),
    NAO_INTERESSADO(HttpStatus.FORBIDDEN, "Acesso negado"),
    NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Não encontrado"),
    DOSSIE_SEM_ARQUIVO(HttpStatus.CONFLICT, "Dossiê sem arquivo"),
    DOSSIE_SEM_DECISAO_PENDENTE(HttpStatus.CONFLICT, "Dossiê sem decisão pendente"),
    DOSSIE_EXPIRADO(HttpStatus.GONE, "Dossiê expirado"),
    TICKET_INVALIDO(HttpStatus.FORBIDDEN, "Ticket de download inválido"),
    CREDENCIAL_ARQUIVOS_RECUSADA(HttpStatus.BAD_GATEWAY, "Credencial de arquivos recusada"),
    SIGA_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "SIGA indisponível"),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

    private final HttpStatus status;
    private final String tituloPadrao;

    ErrorCode(HttpStatus status, String tituloPadrao) {
        this.status = status;
        this.tituloPadrao = tituloPadrao;
    }

    public HttpStatus status() {
        return status;
    }

    public String tituloPadrao() {
        return tituloPadrao;
    }
}
