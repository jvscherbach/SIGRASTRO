package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.Objects;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoConfrontada;

/**
 * Posição de um requerimento como sai para a API (S7, bloco 6): os números, a posição confrontada já com as regras de
 * exibição do usuário aplicadas e a situação do Licenciamento com a descrição.
 *
 * @param situacao      código e descrição de {@code SITUACAO_PROCESSO}, quando lida
 * @param idsTecnicos   o usuário pode ver os ids técnicos do fluxo (só Administrador); o DTO decide o que entra por aqui
 */
public record PosicaoDoRequerimento(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        PosicaoConfrontada posicao,
        Optional<SituacaoDoLicenciamento> situacao,
        boolean idsTecnicos) {

    public PosicaoDoRequerimento {
        Objects.requireNonNull(posicao);
        situacao = Objects.requireNonNullElse(situacao, Optional.empty());
    }

    /** {@code REQUERIMENTO.SITUACAO_PROCESSO} e a descrição (Apêndice A de docs/04). */
    public record SituacaoDoLicenciamento(String codigo, String descricao) {}
}
