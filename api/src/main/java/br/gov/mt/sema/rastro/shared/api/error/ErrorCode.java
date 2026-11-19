package br.gov.mt.sema.rastro.shared.api.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    PARAMETRO_INVALIDO(HttpStatus.BAD_REQUEST, "Parâmetros inválidos"),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Não autenticado"),
    PERFIL_SEM_PERMISSAO(HttpStatus.FORBIDDEN, "Acesso negado"),
    DOCUMENTO_SIGILOSO(HttpStatus.FORBIDDEN, "Documento sigiloso"),
    NAO_INTERESSADO(HttpStatus.FORBIDDEN, "Acesso negado"),
    NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Não encontrado"),
    // Administração de usuários (S5).
    LOGIN_JA_EXISTE(HttpStatus.CONFLICT, "Login já existe"),
    ULTIMO_ADMINISTRADOR(HttpStatus.CONFLICT, "Último Administrador"),
    PROPRIO_USUARIO(HttpStatus.CONFLICT, "Próprio usuário"),
    TESTE_DE_CONEXAO_EM_ANDAMENTO(HttpStatus.CONFLICT, "Teste de conexão em andamento"),
    DOSSIE_SEM_ARQUIVO(HttpStatus.CONFLICT, "Dossiê sem arquivo"),
    DOSSIE_SEM_DECISAO_PENDENTE(HttpStatus.CONFLICT, "Dossiê sem decisão pendente"),
    DOSSIE_EXPIRADO(HttpStatus.GONE, "Dossiê expirado"),
    // Conteúdo do documento para o modal (S3).
    FORMATO_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato sem conversão para PDF"),
    DOCUMENTO_PROTEGIDO_POR_SENHA(HttpStatus.UNPROCESSABLE_ENTITY, "Documento protegido por senha"),
    DOCUMENTO_CORROMPIDO(HttpStatus.UNPROCESSABLE_ENTITY, "Documento corrompido"),
    CONVERSAO_RECUSADA(HttpStatus.UNPROCESSABLE_ENTITY, "Conversão recusada"),
    MUITAS_CONVERSOES(HttpStatus.TOO_MANY_REQUESTS, "Muitas conversões em andamento"),
    TICKET_INVALIDO(HttpStatus.FORBIDDEN, "Ticket de download inválido"),
    // Geometrias em ZIP (S4). GEOMETRIAS_INCOMPLETAS sai como 504 quando todas as falhas são de tempo limite.
    GEOMETRIAS_INCOMPLETAS(HttpStatus.BAD_GATEWAY, "Geometrias incompletas"),
    GEOMETRIAS_GRANDES_DEMAIS(HttpStatus.PAYLOAD_TOO_LARGE, "Geometrias grandes demais"),
    CREDENCIAL_ARQUIVOS_RECUSADA(HttpStatus.BAD_GATEWAY, "Credencial de arquivos recusada"),
    REPOSITORIO_DE_ARQUIVOS_INDISPONIVEL(HttpStatus.BAD_GATEWAY, "Repositório de arquivos indisponível"),
    TEMPO_LIMITE_EXCEDIDO(HttpStatus.GATEWAY_TIMEOUT, "Tempo limite excedido"),
    CONVERSOR_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Conversor indisponível"),
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
