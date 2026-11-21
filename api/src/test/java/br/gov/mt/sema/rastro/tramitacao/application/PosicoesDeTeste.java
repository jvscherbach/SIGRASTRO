package br.gov.mt.sema.rastro.tramitacao.application;

import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.ElementoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.EstadoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoConfrontada;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;

/**
 * Posições prontas (já resolvidas e sem CPF, como o {@link ConsultarPosicaoAtual} as entrega) para os testes de contrato
 * HTTP, e o acesso ao método {@code ler} (package-private) do dublê. Dados fictícios, com as etapas do catálogo real.
 */
public final class PosicoesDeTeste {

    public static final LocalDateTime DESDE = LocalDateTime.of(2026, 3, 8, 9, 30);
    public static final String SERVIDOR_01 = "Servidor Exemplo 01";
    public static final String SERVIDOR_02 = "Servidor Exemplo 02";
    public static final String AREA = "Conferência de documentos (subterrânea)";
    public static final String ID_NAO_MAPEADO = "taskFicticiaNaoMapeada";

    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo("procOutorgaAguaSupSub")
            .orElseThrow();

    private PosicoesDeTeste() {
    }

    /** Faz o dublê de {@link ConsultarPosicaoAtual} devolver a posição dada na leitura do requerimento. */
    public static void quandoLer(ConsultarPosicaoAtual duble, long idRequerimento, PosicaoConfrontada posicao) {
        when(duble.ler(idRequerimento)).thenReturn(posicao);
    }

    private static Etapa etapa(String id) {
        return HIDRICO.etapa(id).orElseThrow();
    }

    /**
     * Análise com 3 analistas (1 concluiu), validação em paralelo (para a lista "+N"), um ponto não mapeado,
     * complementação em andamento e um aviso.
     */
    public static PosicaoConfrontada ativaComMultiInstancia() {
        ResponsavelDaEtapa equipe = new ResponsavelDaEtapa(ResponsavelDaEtapa.Tipo.VARIOS, "Analista", Optional.empty(),
                Optional.empty(), List.of(
                        new ResponsavelDaEtapa.Participante(Optional.of(SERVIDOR_01), "Analista", true),
                        new ResponsavelDaEtapa.Participante(Optional.of(SERVIDOR_02), "Analista", false),
                        new ResponsavelDaEtapa.Participante(Optional.empty(), ResponsavelDaEtapa.NAO_IDENTIFICADO, false)),
                0);
        ElementoDaPosicao analise = new ElementoDaPosicao(Optional.of(etapa("analise_do_processo")),
                "taskAnalisarRequerimento", DESDE, true, List.of(),
                Optional.of(new ElementoDaPosicao.MultiInstancia(Optional.of(1), 2)), Optional.empty(), List.of(),
                Optional.of(equipe));
        ResponsavelDaEtapa area = new ResponsavelDaEtapa(ResponsavelDaEtapa.Tipo.AREA, "Equipe", Optional.of(AREA),
                Optional.empty(), List.of(), 0);
        ElementoDaPosicao emissao = new ElementoDaPosicao(Optional.of(etapa("emissao_do_ato")), "taskEmitirPortarias",
                DESDE.plusDays(1), false, List.of(), Optional.empty(), Optional.of("procSurhEmissaoPortaria"), List.of(),
                Optional.of(area));
        ElementoDaPosicao naoMapeado = new ElementoDaPosicao(Optional.empty(), ID_NAO_MAPEADO, null, false, List.of(),
                Optional.empty(), Optional.empty(), List.of(), Optional.empty());
        PosicaoAtual posicao = new PosicaoAtual(EstadoDaPosicao.ATIVO, Optional.of(HIDRICO.chave()),
                Optional.of(HIDRICO.rotulo()), Optional.of(analise), List.of(emissao, naoMapeado),
                Optional.of(new PosicaoAtual.ComplementacaoEmAndamento(1, DESDE.minusDays(2))), Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), List.of(ID_NAO_MAPEADO), Optional.of("ACTIVE"));
        return new PosicaoConfrontada(posicao, Optional.of("EM_ANALISE"), List.of(
                new Aviso(CodigoDoAviso.ETAPA_NAO_MAPEADA, "Há uma etapa do fluxo que o Rastro ainda não conhece.",
                        Aviso.Gravidade.INFO)));
    }

    /** Uma tarefa com o nome de um servidor (para provar RN12 e o parâmetro de nome). */
    public static PosicaoConfrontada ativaComServidor() {
        ResponsavelDaEtapa chefia = new ResponsavelDaEtapa(ResponsavelDaEtapa.Tipo.SERVIDOR, "Chefia", Optional.empty(),
                Optional.of(SERVIDOR_01), List.of(), 0);
        ElementoDaPosicao revisao = new ElementoDaPosicao(Optional.of(etapa("revisao_do_parecer_tecnico")),
                "taskRevisarParecerTecnico", DESDE, false, List.of(), Optional.empty(), Optional.empty(), List.of(),
                Optional.of(chefia));
        PosicaoAtual posicao = new PosicaoAtual(EstadoDaPosicao.ATIVO, Optional.of(HIDRICO.chave()),
                Optional.of(HIDRICO.rotulo()), Optional.of(revisao), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), List.of(), Optional.empty());
        return new PosicaoConfrontada(posicao, Optional.of("EM_ANALISE"), List.of());
    }

    /** Encerrada por taxa vencida, com o aviso de situação não atualizada. */
    public static PosicaoConfrontada encerradaPorTaxa() {
        FinalDoFluxo fim = HIDRICO.finais().stream().filter(f -> f.idTecnico().equals("Event_0h1oige")).findFirst()
                .orElseThrow();
        PosicaoAtual posicao = new PosicaoAtual(EstadoDaPosicao.ENCERRADO, Optional.of(HIDRICO.chave()),
                Optional.of(HIDRICO.rotulo()), Optional.empty(), List.of(), Optional.empty(), Optional.of(fim),
                Optional.of(DESDE), Optional.empty(), Set.of(), List.of(), Optional.of("COMPLETED"));
        return new PosicaoConfrontada(posicao, Optional.of("AGUARDANDO_PAGAMENTO_DE_TAXAS"), List.of(
                new Aviso(CodigoDoAviso.FLUXO_ENCERRADO_SEM_STATUS, "O fluxo foi encerrado por taxa não paga no prazo.",
                        Aviso.Gravidade.ATENCAO)));
    }

    public static PosicaoConfrontada indisponivel() {
        return new PosicaoConfrontada(PosicaoAtual.indisponivel(HIDRICO), Optional.of("EM_ANALISE"), List.of());
    }

    public static PosicaoConfrontada naoSuportado() {
        return new PosicaoConfrontada(PosicaoAtual.naoSuportado(), Optional.of("EM_ANALISE"), List.of());
    }

    public static PosicaoConfrontada semInstancia() {
        return new PosicaoConfrontada(PosicaoAtual.semInstancia(HIDRICO), Optional.of("CONCLUIDO"), List.of(
                new Aviso(CodigoDoAviso.SEM_INSTANCIA, "Histórico detalhado indisponível para este requerimento.",
                        Aviso.Gravidade.INFO)));
    }
}
