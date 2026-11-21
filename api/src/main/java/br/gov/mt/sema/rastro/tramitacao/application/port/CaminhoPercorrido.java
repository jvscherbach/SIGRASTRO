package br.gov.mt.sema.rastro.tramitacao.application.port;

import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;

/**
 * Leitura do caminho percorrido no histórico do Camunda (S8, bloco 2). Implementada só em {@code integration.siga}
 * ({@code CamundaPosicaoJdbc}, o mesmo componente de acesso da posição, R04). Só {@code SELECT} por {@code PROC_INST_ID_},
 * com limite de linhas por instância e tempo limite; devolve fatos crus ({@link CaminhoBruto}), sem interpretação. Falha de
 * banco sai como exceção: quem chama transforma em {@code INDISPONIVEL}, e nunca usa o plano B no lugar.
 */
public interface CaminhoPercorrido {

    /**
     * Instância raiz mais recente do requerimento no modelo (regras técnicas 1 e 2), as filhas (pela instância-mãe), as
     * complementações (encerradas ou não) e as variáveis da lista branca. Sem raiz, {@link CaminhoBruto#semInstancia()}.
     */
    CaminhoBruto lerCaminho(long idRequerimento, String chaveDoModelo);
}
