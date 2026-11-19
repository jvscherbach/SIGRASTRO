package br.gov.mt.sema.rastro.configuracao.api.dto;

import java.util.List;

import br.gov.mt.sema.rastro.configuracao.application.ParametroVigente;
import br.gov.mt.sema.rastro.configuracao.domain.DefinicaoDeParametro;

/** Todos os parâmetros editáveis, com faixa, padrão do ambiente e classe de aplicação. */
public record ParametrosResponse(List<Parametro> parametros) {

    /**
     * @param minimo só em INTEIRO
     * @param maximo só em INTEIRO
     * @param opcoes só em OPCAO
     */
    public record Parametro(String nome, String rotulo, String grupo, String tipo, String valor, String padrao,
            Integer minimo, Integer maximo, List<String> opcoes, String classeDeAplicacao, boolean sobreposto,
            boolean pendenteDeReinicio) {

        static Parametro de(ParametroVigente vigente) {
            DefinicaoDeParametro d = vigente.definicao();
            boolean inteiro = d.tipo() == DefinicaoDeParametro.Tipo.INTEIRO;
            return new Parametro(d.nome(), d.rotulo(), d.grupo(), d.tipo().name(), vigente.valor(), vigente.padrao(),
                    inteiro ? d.minimo() : null, inteiro ? d.maximo() : null, d.opcoes(), d.classe().name(),
                    vigente.sobreposto(), vigente.pendenteDeReinicio());
        }
    }

    public static ParametrosResponse de(List<ParametroVigente> vigentes) {
        return new ParametrosResponse(vigentes.stream().map(Parametro::de).toList());
    }
}
