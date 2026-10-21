package br.gov.mt.sema.vistas.acesso.api.dto;

public record PermissoesResponse(
        boolean verSigilosos,
        boolean verAuditoriaAcessos,
        boolean acessarConfiguracoes,
        boolean restritoAoInteressado,
        boolean verResponsavelTarefa) {
}
