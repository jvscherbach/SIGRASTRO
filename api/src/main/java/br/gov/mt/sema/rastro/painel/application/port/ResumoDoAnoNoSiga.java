package br.gov.mt.sema.rastro.painel.application.port;

import java.time.Duration;

import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.BaseDoAno;
import br.gov.mt.sema.rastro.painel.domain.LeituraDaSerie;

/**
 * Leitura agregada do Licenciamento para o painel (S10, bloco 2). Só é chamada pela atualização do instantâneo, nunca por
 * uma requisição. Implementada em {@code integration.siga}; sem SIGA configurado, lança {@code SigaIndisponivelException}.
 * Falhas de banco sobem como exceção: quem atualiza mantém o último instantâneo bom.
 */
public interface ResumoDoAnoNoSiga {

    /**
     * Gráfico 1: <b>uma</b> consulta agregada por mês (protocolados, concluídos e "sem data de protocolo"). Não depende do
     * teto de requerimentos.
     *
     * @param tempoLimite tempo limite da consulta (arredondado para cima, no mínimo 1 s)
     */
    LeituraDaSerie lerSerie(AnoDoPainel ano, Duration tempoLimite);

    /**
     * Base dos gráficos 2 e 3: ids, tipo de processo e situação dos requerimentos do ano, por {@code ID}, até {@code limite};
     * havendo mais, {@link BaseDoAno#truncado()}.
     */
    BaseDoAno lerBase(AnoDoPainel ano, int limite, Duration tempoLimite);
}
