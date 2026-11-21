package br.gov.mt.sema.rastro.tramitacao.application;

import java.time.Duration;
import java.util.Optional;

import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.port.CacheDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.application.port.ParametrosDoRastro;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDoConfronto;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MontadorDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoConfrontada;
import br.gov.mt.sema.rastro.tramitacao.domain.Reconciliar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Posição atual de um requerimento (S7, blocos 3 a 5). O acesso vem do contrato de {@code consulta} (existe, 404; RN07,
 * 403), como nos documentos, e é conferido a cada chamada, antes do cache. Depois disso a leitura do Camunda nunca
 * derruba a chamada: falha ou lentidão vira {@code INDISPONIVEL}, com a situação do Licenciamento se ela já foi lida.
 * Tipo de processo fora do catálogo (Trifásico, LAS) é {@code NAO_SUPORTADO}, sem ir ao Camunda.
 *
 * <p>A posição montada é confrontada com a situação do Licenciamento ({@link Reconciliar}): o confronto só acrescenta
 * avisos, a etapa do fluxo prevalece.
 *
 * <p>Cache curto ({@code RASTRO_POSICAO_CACHE_SEGUNDOS}; chave = id + versão do catálogo) da posição já confrontada, com
 * o responsável resolvido e sem CPF; {@code INDISPONIVEL} não entra. Os parâmetros de exibição (nome, limite de nomes)
 * são aplicados depois do cache, para valerem na próxima leitura.
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
    private final Reconciliar reconciliar;
    private final LimiteDeLeiturasDoCamunda limite;

    public ConsultarPosicaoAtual(VerificarAcessoAoRequerimento acesso, PosicaoNoFluxo camunda, CatalogoDeEtapas catalogo,
            ResolverResponsavel responsaveis, ParametrosDoRastro parametros, CacheDaPosicao cache,
            CatalogoDoConfronto confronto, LimiteDeLeiturasDoCamunda limite) {
        this.limite = limite;
        this.acesso = acesso;
        this.camunda = camunda;
        this.catalogo = catalogo;
        this.responsaveis = responsaveis;
        this.parametros = parametros;
        this.cache = cache;
        this.reconciliar = new Reconciliar(confronto);
    }

    public PosicaoConfrontada executar(long idRequerimento) {
        acesso.executar(idRequerimento);
        return ler(idRequerimento);
    }

    /** Sem a checagem de acesso: só para quem já a fez (ex.: lista de requerimentos de um processo já autorizado). */
    PosicaoConfrontada ler(long idRequerimento) {
        boolean mostrarNome = parametros.mostrarNomeDoResponsavel();
        int maximoDeNomes = parametros.maximoDeNomesPorEquipe();
        return doCacheOuDoCamunda(idRequerimento)
                .comPosicao(p -> p.comPontos(ponto -> ponto.conforme(mostrarNome, maximoDeNomes)));
    }

    private PosicaoConfrontada doCacheOuDoCamunda(long idRequerimento) {
        int validade = parametros.cacheDaPosicaoSegundos();
        String versao = catalogo.versao();
        if (validade > 0) {
            Optional<PosicaoConfrontada> guardada = cache.obter(idRequerimento, versao);
            if (guardada.isPresent()) {
                return guardada.get();
            }
        }
        PosicaoConfrontada lida = comVaga(idRequerimento);
        if (validade > 0 && lida.posicao().estado() != EstadoDaPosicao.INDISPONIVEL) {
            cache.guardar(idRequerimento, versao, lida, Duration.ofSeconds(validade));
        }
        return lida;
    }

    /**
     * S8, bloco 7: a leitura ao Camunda passa pelo mesmo limite de leituras simultâneas da tramitação; sem vaga dentro do
     * tempo limite de uma consulta, {@code INDISPONIVEL} (como qualquer falha do Camunda).
     */
    private PosicaoConfrontada comVaga(long idRequerimento) {
        try {
            return limite.executar(Duration.ofSeconds(Math.max(1, parametros.camundaTimeoutSegundos())),
                    () -> doCamunda(idRequerimento));
        } catch (LimiteDeLeiturasDoCamunda.SemVagaNoCamunda e) {
            log.warn("Posição do requerimento {} indisponível (sem vaga para ler o Camunda)", idRequerimento);
            return reconciliar.reconciliar(PosicaoAtual.indisponivel(null), Optional.empty());
        }
    }

    private PosicaoConfrontada doCamunda(long idRequerimento) {
        ModeloDeFluxo modelo = null;
        Optional<String> situacao = Optional.empty();
        try {
            Optional<PosicaoNoFluxo.RequerimentoNoFluxo> requerimento = camunda.requerimento(idRequerimento);
            situacao = requerimento.map(PosicaoNoFluxo.RequerimentoNoFluxo::situacao);
            Optional<ModeloDeFluxo> doTipo = requerimento.flatMap(r -> catalogo.modeloDaInstancia(r.instancia()));
            if (doTipo.isEmpty()) {
                return reconciliar.reconciliar(PosicaoAtual.naoSuportado(), situacao);
            }
            modelo = doTipo.get();
            PosicaoAtual montada = MontadorDaPosicao.montar(modelo, camunda.ler(idRequerimento, modelo.chave()));
            return reconciliar.reconciliar(responsaveis.resolver(idRequerimento, montada), situacao);
        } catch (RuntimeException e) {
            // Sem a mensagem da exceção: o texto do driver pode trazer valores de parâmetros.
            log.warn("Posição do requerimento {} indisponível ({})", idRequerimento, e.getClass().getSimpleName());
            return reconciliar.reconciliar(PosicaoAtual.indisponivel(modelo), situacao);
        }
    }
}
