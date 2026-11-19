package br.gov.mt.sema.rastro.consulta.application;

import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;

/** Parâmetros crus da busca, como chegam da interface; a validação é do domínio. */
public record PedidoDeBusca(String tipo, String numero, String cpfCnpj, String nome, Integer pagina,
        Integer tamanho) {

    CriterioBusca paraCriterio() {
        return CriterioBusca.de(tipo, numero, cpfCnpj, nome, pagina, tamanho);
    }
}
