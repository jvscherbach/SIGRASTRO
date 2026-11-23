package br.gov.mt.sema.rastro.painel.domain;

import java.util.List;

/**
 * O que a consulta agregada do SIGA devolve para o gráfico 1 (S10, bloco 2), cru: os meses que têm dado, sem completar os
 * doze nem conferir nada (isso é do bloco 3).
 *
 * @param protocolados       requerimentos do ano por mês de {@code DATA_PROTOCOLO_PROCESSO}
 * @param concluidos         dos mesmos requerimentos, os {@code CONCLUIDO}, por mês de {@code DATA_ALTERACAO} (mês
 *                           aproximado, NÃO CONFIRMADO; pode cair fora do ano ou vir sem mês)
 * @param semDataDeProtocolo requerimentos abertos no ano ({@code DATA_ABERTURA}) sem {@code DATA_PROTOCOLO_PROCESSO}: não
 *                           entram nos gráficos, só no aviso
 */
public record LeituraDaSerie(List<ContagemDoMes> protocolados, List<ContagemDoMes> concluidos, long semDataDeProtocolo) {

    public LeituraDaSerie {
        protocolados = List.copyOf(protocolados);
        concluidos = List.copyOf(concluidos);
    }
}
