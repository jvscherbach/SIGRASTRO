package br.gov.mt.sema.vistas.configuracao.application;

import br.gov.mt.sema.vistas.configuracao.domain.DefinicaoDeParametro;

/**
 * Situação de um parâmetro.
 *
 * @param valor o valor atual; num "exige reinício" sobreposto, o que foi pedido, ainda sem efeito
 * @param padrao o valor do ambiente
 * @param pendenteDeReinicio sobreposto e de classe "exige reinício"
 */
public record ParametroVigente(DefinicaoDeParametro definicao, String valor, String padrao, boolean sobreposto,
        boolean pendenteDeReinicio) {
}
