package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.application.PosicaoDoRequerimento.SituacaoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.application.port.FinanceiroDoRequerimento.MarcaFinanceira;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;

/**
 * A tramitação completa de um requerimento como sai para a API (S8, bloco 6): os números, a tramitação já com as regras de
 * exibição do usuário aplicadas e a situação do Licenciamento com a descrição.
 *
 * @param idsTecnicos        o usuário pode ver os ids técnicos do fluxo (só Administrador); o DTO decide o que entra por aqui
 * @param marcasFinanceiras  S9, bloco 5: uma lista por passagem do caminho, mesma ordem (vazia sem caminho exato ou sem o
 *                           financeiro); só acrescenta, nunca muda a passagem
 */
public record TramitacaoAberta(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        TramitacaoDoRequerimento tramitacao,
        Optional<String> rotuloDoModelo,
        Optional<SituacaoDoLicenciamento> situacao,
        boolean idsTecnicos,
        List<List<MarcaFinanceira>> marcasFinanceiras) {

    public TramitacaoAberta {
        Objects.requireNonNull(tramitacao);
        rotuloDoModelo = Objects.requireNonNullElse(rotuloDoModelo, Optional.empty());
        situacao = Objects.requireNonNullElse(situacao, Optional.empty());
        marcasFinanceiras = List.copyOf(Objects.requireNonNullElse(marcasFinanceiras, List.of()));
    }

    /** As marcas da passagem de índice {@code i}; vazia quando não há. */
    public List<MarcaFinanceira> marcasDa(int i) {
        return i < marcasFinanceiras.size() ? marcasFinanceiras.get(i) : List.of();
    }
}
