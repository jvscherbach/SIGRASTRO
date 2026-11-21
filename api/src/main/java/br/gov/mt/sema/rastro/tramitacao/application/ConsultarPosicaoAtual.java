package br.gov.mt.sema.rastro.tramitacao.application;

import java.time.Duration;
import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MontadorDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Posição atual de um requerimento (S7, blocos 3 e 4). O acesso vem do contrato de {@code consulta} (existe, 404; RN07,
 * 403), como nos documentos, e é conferido a cada chamada, antes do cache. Depois disso a leitura do Camunda nunca
 * derruba a chamada: falha ou lentidão vira {@code INDISPONIVEL}. Tipo de processo fora do catálogo (Trifásico, LAS) é
 * {@code NAO_SUPORTADO}, sem ir ao Camunda.
 *
 * <p>Cache curto ({@code RASTRO_POSICAO_CACHE_SEGUNDOS}; chave = id + versão do catálogo) da posição já com o responsável
 * resolvido e sem CPF; {@code INDISPONIVEL} não entra. Os parâmetros de exibição (nome, limite de nomes) são aplicados
 * depois do cache, para valerem na próxima leitura.
 */
@Service
public class ConsultarPosicaoAtual {

    private static final Logger log = LoggerFactory.getLogger(ConsultarPosicaoAtual.class);

    private final VerificarAcessoAoRequerimento acesso;
    private final PosicaoNoFluxo camunda;
    private final CatalogoDeEtapas catalogo;
    private final ResolverResponsavel responsaveis;
    private final ParametrosDoRastro parametros;
    private final CacheDaPosicao cache;

    public ConsultarPosicaoAtual(VerificarAcessoAoRequerimento acesso, PosicaoNoFluxo camunda, CatalogoDeEtapas catalogo,
            ResolverResponsavel responsaveis, ParametrosDoRastro parametros, CacheDaPosicao cache) {
        this.acesso = acesso;
        this.camunda = camunda;
        this.catalogo = catalogo;
        this.responsaveis = responsaveis;
        this.parametros = parametros;
        this.cache = cache;
    }

    public PosicaoAtual executar(long idRequerimento) {
        acesso.executar(idRequerimento);
        return ler(idRequerimento);
    }

    /** Sem a checagem de acesso: só para quem já a fez (ex.: lista de requerimentos de um processo já autorizado). */
    PosicaoAtual ler(long idRequerimento) {
        boolean mostrarNome = parametros.mostrarNomeDoResponsavel();
        int maximoDeNomes = parametros.maximoDeNomesPorEquipe();
        return doCacheOuDoCamunda(idRequerimento).comPontos(p -> p.conforme(mostrarNome, maximoDeNomes));
    }

    private PosicaoAtual doCacheOuDoCamunda(long idRequerimento) {
        int validade = parametros.cacheDaPosicaoSegundos();
        String versao = catalogo.versao();
        if (validade > 0) {
            Optional<PosicaoAtual> guardada = cache.obter(idRequerimento, versao);
            if (guardada.isPresent()) {
                return guardada.get();
            }
        }
        PosicaoAtual lida = doCamunda(idRequerimento);
        if (validade > 0 && lida.estado() != EstadoDaPosicao.INDISPONIVEL) {
            cache.guardar(idRequerimento, versao, lida, Duration.ofSeconds(validade));
        }
        return lida;
    }

    private PosicaoAtual doCamunda(long idRequerimento) {
        ModeloDeFluxo modelo = null;
        try {
            Optional<PosicaoNoFluxo.RequerimentoNoFluxo> requerimento = camunda.requerimento(idRequerimento);
            Optional<ModeloDeFluxo> doTipo = requerimento.flatMap(r -> catalogo.modeloDaInstancia(r.instancia()));
            if (doTipo.isEmpty()) {
                return PosicaoAtual.naoSuportado();
            }
            modelo = doTipo.get();
            PosicaoAtual montada = MontadorDaPosicao.montar(modelo, camunda.ler(idRequerimento, modelo.chave()));
            return responsaveis.resolver(idRequerimento, montada);
        } catch (RuntimeException e) {
            // Sem a mensagem da exceção: o texto do driver pode trazer valores de parâmetros.
            log.warn("Posição do requerimento {} indisponível ({})", idRequerimento, e.getClass().getSimpleName());
            return PosicaoAtual.indisponivel(modelo);
        }
    }
}
