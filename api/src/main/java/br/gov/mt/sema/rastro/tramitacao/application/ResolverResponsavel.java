package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.List;
import java.util.Map;

import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeGrupos;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.RegraDoResponsavel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Preenche com quem está cada ponto da posição (S7, bloco 4). Uma consulta ao SIGA por requerimento, só se houver
 * {@code ASSIGNEE_} com cara de CPF. Depois disto os identificadores (CPF) saem da posição: nada a jusante (cache,
 * resposta, auditoria, log) os recebe.
 *
 * <p>Falha na consulta não derruba a posição: os servidores ficam "Responsável não identificado" e o log registra só o id
 * do requerimento e a classe da exceção (a mensagem do driver pode trazer os valores dos parâmetros, que são CPFs).
 */
@Service
public class ResolverResponsavel {

    private static final Logger log = LoggerFactory.getLogger(ResolverResponsavel.class);

    private final IdentificacaoDosResponsaveis siga;
    private final CatalogoDeGrupos grupos;

    public ResolverResponsavel(IdentificacaoDosResponsaveis siga, CatalogoDeGrupos grupos) {
        this.siga = siga;
        this.grupos = grupos;
    }

    public PosicaoAtual resolver(long idRequerimento, PosicaoAtual posicao) {
        List<IdentificadorDoResponsavel> consultar = RegraDoResponsavel.paraConsultar(posicao.pontos());
        Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificados = Map.of();
        if (!consultar.isEmpty()) {
            try {
                identificados = siga.identificar(idRequerimento, consultar);
            } catch (RuntimeException e) {
                log.warn("Responsáveis do requerimento {} não identificados ({})", idRequerimento,
                        e.getClass().getSimpleName());
            }
        }
        Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> encontrados = identificados;
        return posicao.comPontos(ponto -> ponto.comResponsavel(RegraDoResponsavel.resolver(ponto, encontrados, grupos)));
    }
}
