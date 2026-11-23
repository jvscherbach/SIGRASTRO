package br.gov.mt.sema.rastro.financeiro.application.port;

import br.gov.mt.sema.rastro.financeiro.domain.CaminhoDoFluxo;

/**
 * O caminho <b>exato</b> do requerimento na tramitação, visto pelo financeiro (S9, blocos 4 e 5): dá a entrada no ramo de
 * correção (etiqueta "da correção") e as entradas "Isenção confirmada" (data de apoio da isenção). Plano B (aproximado),
 * tramitação indisponível, tipo não suportado ou qualquer falha devolvem {@link CaminhoDoFluxo#NENHUM}, nunca exceção (o
 * financeiro não depende do Camunda). O acesso ao requerimento já foi conferido por quem chama.
 */
public interface CaminhoDaTramitacao {

    CaminhoDoFluxo ler(long idRequerimento);
}
