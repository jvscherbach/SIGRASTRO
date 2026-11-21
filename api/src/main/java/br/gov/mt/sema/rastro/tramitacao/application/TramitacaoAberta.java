package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.Objects;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.application.PosicaoDoRequerimento.SituacaoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;

/**
 * A tramitação completa de um requerimento como sai para a API (S8, bloco 6): os números, a tramitação já com as regras de
 * exibição do usuário aplicadas e a situação do Licenciamento com a descrição.
 *
 * @param idsTecnicos o usuário pode ver os ids técnicos do fluxo (só Administrador); o DTO decide o que entra por aqui
 */
public record TramitacaoAberta(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        TramitacaoDoRequerimento tramitacao,
        Optional<String> rotuloDoModelo,
        Optional<SituacaoDoLicenciamento> situacao,
        boolean idsTecnicos) {

    public TramitacaoAberta {
        Objects.requireNonNull(tramitacao);
        rotuloDoModelo = Objects.requireNonNullElse(rotuloDoModelo, Optional.empty());
        situacao = Objects.requireNonNullElse(situacao, Optional.empty());
    }
}
