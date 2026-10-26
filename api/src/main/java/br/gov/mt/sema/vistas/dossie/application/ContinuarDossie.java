package br.gov.mt.sema.vistas.dossie.application;

import java.time.Clock;
import java.time.LocalDateTime;

import br.gov.mt.sema.vistas.dossie.application.port.FilaDeGeracao;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.domain.AcaoDeContinuacao;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.shared.application.exception.DossieSemDecisaoPendenteException;
import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;
import org.springframework.stereotype.Service;

/**
 * Decisão do usuário sobre um pedido em PENDENTE_DECISAO (S2.2, substitui os "novos POST" da S2): tentar de novo só
 * os documentos com problema ou removê-los e montar. Nada já obtido é baixado de novo. Acesso como no andamento (quem
 * criou, Gestor e Administrador; os demais recebem 404); 409 se o pedido não está à espera de decisão.
 */
@Service
public class ContinuarDossie {

    private final LocalizarPedido localizar;
    private final FilaDeGeracao fila;
    private final GeradorDeDossie gerador;
    private final ParametrosDoDossie parametros;
    private final Clock relogio;

    public ContinuarDossie(LocalizarPedido localizar, FilaDeGeracao fila, GeradorDeDossie gerador,
            ParametrosDoDossie parametros, Clock relogio) {
        this.localizar = localizar;
        this.fila = fila;
        this.gerador = gerador;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public void executar(String id, String acao) {
        AcaoDeContinuacao decisao = interpretar(acao);
        PedidoDeDossie pedido = localizar.executar(id);
        LocalDateTime validade = LocalDateTime.now(relogio).plusMinutes(parametros.ttlMinutos());
        switch (pedido.decidir(decisao, validade)) {
            case NAO_PENDENTE -> throw new DossieSemDecisaoPendenteException(
                    "O dossiê não está aguardando decisão (situação: " + pedido.status() + ").");
            case NADA_A_MONTAR -> throw new ParametroInvalidoException("acao",
                    "Nenhum documento foi obtido: não há o que montar. Tente novamente ou cancele.");
            case ACEITA -> fila.submeter(() -> gerador.processar(pedido));
        }
    }

    private static AcaoDeContinuacao interpretar(String acao) {
        if (acao != null) {
            for (AcaoDeContinuacao conhecida : AcaoDeContinuacao.values()) {
                if (conhecida.name().equals(acao.trim())) {
                    return conhecida;
                }
            }
        }
        throw new ParametroInvalidoException("acao", "Use TENTAR_NOVAMENTE ou REMOVER_E_CONTINUAR.");
    }
}
