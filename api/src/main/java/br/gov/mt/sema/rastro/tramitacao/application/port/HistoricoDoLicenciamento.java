package br.gov.mt.sema.rastro.tramitacao.application.port;

import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;

/**
 * Leitura do Licenciamento para o plano B (S8, bloco 5): eventos de {@code HISTORICO_REQUERIMENTO} (data e evento; nunca
 * {@code CPF_USUARIO}) e datas e situação de {@code REQUERIMENTO}. Implementada só em {@code integration.siga}
 * ({@code CamundaPosicaoJdbc}, o componente de acesso da tramitação). Só é chamada quando a raiz não existe no Camunda;
 * nunca como substituta de uma falha.
 */
public interface HistoricoDoLicenciamento {

    LeituraDoPlanoB lerPlanoB(long idRequerimento);
}
