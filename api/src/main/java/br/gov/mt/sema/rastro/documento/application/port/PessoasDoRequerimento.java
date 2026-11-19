package br.gov.mt.sema.rastro.documento.application.port;

import java.util.List;

import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;

/**
 * Pessoas do requerimento que têm documentos pessoais no SCORP (S4): interessado, representantes legais e responsáveis
 * técnicos, com o {@code PESSOA.ID_CORPORATIVO} (implementada por integration.siga, só leitura). Pessoa sem
 * {@code ID_CORPORATIVO} não vem; a mesma pessoa pode vir em mais de um papel.
 */
public interface PessoasDoRequerimento {

    List<Pessoa> listarPessoas(long idRequerimento);

    record Pessoa(String idCorporativo, PapelDaPessoa papel) {
    }
}
