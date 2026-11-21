package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MontadorDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Posição atual de um requerimento (S7, bloco 3). O acesso vem do contrato de {@code consulta} (existe, 404; RN07, 403),
 * como nos documentos. Depois disso a leitura do Camunda nunca derruba a chamada: falha ou lentidão vira
 * {@code INDISPONIVEL}. Tipo de processo fora do catálogo (Trifásico, LAS) é {@code NAO_SUPORTADO}, sem ir ao Camunda.
 */
@Service
public class ConsultarPosicaoAtual {

    private static final Logger log = LoggerFactory.getLogger(ConsultarPosicaoAtual.class);

    private final VerificarAcessoAoRequerimento acesso;
    private final PosicaoNoFluxo camunda;
    private final CatalogoDeEtapas catalogo;

    public ConsultarPosicaoAtual(VerificarAcessoAoRequerimento acesso, PosicaoNoFluxo camunda, CatalogoDeEtapas catalogo) {
        this.acesso = acesso;
        this.camunda = camunda;
        this.catalogo = catalogo;
    }

    public PosicaoAtual executar(long idRequerimento) {
        acesso.executar(idRequerimento);
        return ler(idRequerimento);
    }

    /** Sem a checagem de acesso: só para quem já a fez (ex.: lista de requerimentos de um processo já autorizado). */
    PosicaoAtual ler(long idRequerimento) {
        ModeloDeFluxo modelo = null;
        try {
            Optional<PosicaoNoFluxo.RequerimentoNoFluxo> requerimento = camunda.requerimento(idRequerimento);
            Optional<ModeloDeFluxo> doTipo = requerimento.flatMap(r -> catalogo.modeloDaInstancia(r.instancia()));
            if (doTipo.isEmpty()) {
                return PosicaoAtual.naoSuportado();
            }
            modelo = doTipo.get();
            return MontadorDaPosicao.montar(modelo, camunda.ler(idRequerimento, modelo.chave()));
        } catch (RuntimeException e) {
            // Sem a mensagem da exceção: o texto do driver pode trazer valores de parâmetros.
            log.warn("Posição do requerimento {} indisponível ({})", idRequerimento, e.getClass().getSimpleName());
            return PosicaoAtual.indisponivel(modelo);
        }
    }
}
