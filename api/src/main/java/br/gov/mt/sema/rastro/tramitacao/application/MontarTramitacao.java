package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.application.port.CaminhoPercorrido;
import br.gov.mt.sema.rastro.tramitacao.application.port.HistoricoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.application.port.IdentificacaoDosResponsaveis;
import br.gov.mt.sema.rastro.tramitacao.application.port.PosicaoNoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.AvaliarDesfecho;
import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoBruto;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDoConfronto;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.DesfechoDoCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificacaoDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.LeituraDoPlanoB;
import br.gov.mt.sema.rastro.tramitacao.domain.MapaDeEventosDoHistorico;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MontarHistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.Reconciliar;
import br.gov.mt.sema.rastro.tramitacao.domain.ReconstruirCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento.Estado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Monta a tramitação completa de um requerimento (S8, blocos 2 a 5), <b>sem</b> a checagem de acesso, a auditoria e o
 * contrato HTTP (bloco 6). O cache, o tempo total e o limite de leituras simultâneas ficam em {@link LerTramitacao} (bloco 7).
 *
 * <ol>
 *   <li>Tipo do requerimento → modelo do catálogo; fora do catálogo: {@code NAO_SUPORTADO}, sem ir ao Camunda.</li>
 *   <li>Caminho cru do Camunda. <b>Qualquer falha</b> (conexão, tempo limite) = {@code INDISPONIVEL}, e o plano B
 *       <b>não</b> é tentado: mostrar dado aproximado como se fosse exato seria pior que não mostrar.</li>
 *   <li>Raiz inexistente em {@code ACT_HI_PROCINST} = {@code SEM_INSTANCIA}: só então o plano B (histórico aproximado,
 *       aviso {@code HISTORICO_APROXIMADO}); falha no plano B também é {@code INDISPONIVEL}.</li>
 *   <li>Com raiz: identificação dos responsáveis (falha tolerante: "não identificado"), reconstrução e desfecho, com os
 *       nomes dos servidores (o parâmetro de nome e o RN12 valem depois do cache). Os CPFs não saem daqui.</li>
 * </ol>
 */
@Service
public class MontarTramitacao {

    private static final Logger log = LoggerFactory.getLogger(MontarTramitacao.class);

    private final PosicaoNoFluxo requerimentos;
    private final CaminhoPercorrido camunda;
    private final HistoricoDoLicenciamento licenciamento;
    private final IdentificacaoDosResponsaveis identificacao;
    private final CatalogoDeEtapas catalogo;
    private final MapaDeEventosDoHistorico eventos;
    private final Reconciliar reconciliar;
    private final AvaliarDesfecho desfecho;

    public MontarTramitacao(PosicaoNoFluxo requerimentos, CaminhoPercorrido camunda, HistoricoDoLicenciamento licenciamento,
            IdentificacaoDosResponsaveis identificacao, CatalogoDeEtapas catalogo, MapaDeEventosDoHistorico eventos,
            CatalogoDoConfronto confronto) {
        this.requerimentos = requerimentos;
        this.camunda = camunda;
        this.licenciamento = licenciamento;
        this.identificacao = identificacao;
        this.catalogo = catalogo;
        this.eventos = eventos;
        this.reconciliar = new Reconciliar(confronto);
        this.desfecho = new AvaliarDesfecho(reconciliar);
    }

    public TramitacaoDoRequerimento montar(long idRequerimento) {
        Optional<String> situacao = Optional.empty();
        Optional<String> chave = Optional.empty();
        try {
            Optional<PosicaoNoFluxo.RequerimentoNoFluxo> requerimento = requerimentos.requerimento(idRequerimento);
            situacao = requerimento.map(PosicaoNoFluxo.RequerimentoNoFluxo::situacao);
            Optional<ModeloDeFluxo> doTipo = requerimento.flatMap(r -> catalogo.modeloDaInstancia(r.instancia()));
            if (doTipo.isEmpty()) {
                return TramitacaoDoRequerimento.naoSuportado(situacao);
            }
            ModeloDeFluxo modelo = doTipo.get();
            chave = Optional.of(modelo.chave());
            CaminhoBruto bruto = camunda.lerCaminho(idRequerimento, modelo.chave());
            if (bruto.vazio()) {
                return planoB(idRequerimento, modelo);
            }
            return doCamunda(idRequerimento, modelo, bruto, situacao);
        } catch (RuntimeException e) {
            // Sem a mensagem da exceção: o texto do driver pode trazer valores de parâmetros.
            log.warn("Tramitação do requerimento {} indisponível ({})", idRequerimento, e.getClass().getSimpleName());
            return TramitacaoDoRequerimento.indisponivel(chave, situacao);
        }
    }

    private TramitacaoDoRequerimento planoB(long idRequerimento, ModeloDeFluxo modelo) {
        LeituraDoPlanoB leitura = licenciamento.lerPlanoB(idRequerimento);
        HistoricoAproximado aproximado = MontarHistoricoAproximado.montar(leitura, modelo, eventos);
        return new TramitacaoDoRequerimento(Estado.SEM_INSTANCIA, Optional.of(modelo.chave()), leitura.situacao(), null,
                Optional.empty(), Optional.of(aproximado),
                List.of(reconciliar.aviso(CodigoDoAviso.HISTORICO_APROXIMADO, leitura.situacao().orElse(""))));
    }

    private TramitacaoDoRequerimento doCamunda(long idRequerimento, ModeloDeFluxo modelo, CaminhoBruto bruto,
            Optional<String> situacao) {
        CaminhoReconstruido caminho = ReconstruirCaminho.reconstruir(bruto, modelo, identificar(idRequerimento, bruto));
        DesfechoDoCaminho fim = desfecho.avaliar(bruto, caminho, modelo, situacao);
        List<Aviso> avisos = new ArrayList<>(fim.avisos());
        if (caminho.instanciasRaiz() > 1) {
            avisos.add(reconciliar.aviso(CodigoDoAviso.VARIAS_INSTANCIAS, situacao.orElse("")));
        }
        if (caminho.truncado()) {
            avisos.add(reconciliar.aviso(CodigoDoAviso.HISTORICO_TRUNCADO, situacao.orElse("")));
        }
        Estado estado = fim.estado() == DesfechoDoCaminho.Estado.ENCERRADO ? Estado.ENCERRADO : Estado.ATIVO;
        return new TramitacaoDoRequerimento(estado, Optional.of(modelo.chave()), situacao, caminho, Optional.of(fim),
                Optional.empty(), avisos);
    }

    private Map<IdentificadorDoResponsavel, IdentificacaoDoResponsavel> identificar(long idRequerimento,
            CaminhoBruto bruto) {
        List<IdentificadorDoResponsavel> consultar = ReconstruirCaminho.paraConsultar(bruto);
        if (consultar.isEmpty()) {
            return Map.of();
        }
        try {
            return identificacao.identificar(idRequerimento, consultar);
        } catch (RuntimeException e) {
            log.warn("Responsáveis da tramitação do requerimento {} não identificados ({})", idRequerimento,
                    e.getClass().getSimpleName());
            return Map.of();
        }
    }
}
