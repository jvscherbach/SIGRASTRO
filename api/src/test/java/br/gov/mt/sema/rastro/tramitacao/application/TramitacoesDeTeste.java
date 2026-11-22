package br.gov.mt.sema.rastro.tramitacao.application;

import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.ID_NAO_MAPEADO;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_01;
import static br.gov.mt.sema.rastro.tramitacao.application.PosicoesDeTeste.SERVIDOR_02;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.tramitacao.domain.AtoDaEmissao;
import br.gov.mt.sema.rastro.tramitacao.domain.AvaliarDesfecho;
import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido.ClassificacaoDaTaxa;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido.Diagnostico;
import br.gov.mt.sema.rastro.tramitacao.domain.CodigoDoAviso;
import br.gov.mt.sema.rastro.tramitacao.domain.DesfechoDoCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.FinalDoFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.EtapaAproximada;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.Marco;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivoDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaPassagem;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaPassagem.Estado;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;

/**
 * Tramitações prontas para os testes de contrato HTTP (S8, bloco 6), montadas à mão com o catálogo real: a reconstrução do
 * caminho é provada em {@code ReconstruirCaminhoTest}/{@code ReconstruirCaminhoComFixturesTest}; aqui interessa o que sai
 * para cada perfil. Tudo fictício (nomes "Servidor Exemplo NN", ids técnicos evidentes).
 */
public final class TramitacoesDeTeste {

    public static final String ID_TECNICO_DA_ANALISE = "taskAnalisarRequerimento";
    public static final String ID_TECNICO_DA_CORRECAO = "taskCorrecaoRequerimento";
    public static final String ID_DO_FINAL = "Event_0h1oige";
    public static final String CHAVE_DO_MODELO = "procOutorgaAguaSupSub";
    /** Ids técnicos que só o Administrador pode ver (inclui a chave do modelo). */
    public static final List<String> IDS_TECNICOS = List.of(ID_TECNICO_DA_ANALISE, ID_TECNICO_DA_CORRECAO, ID_DO_FINAL,
            ID_NAO_MAPEADO, CHAVE_DO_MODELO, "taskCadastrarRequerimento", "taskEmitirPortarias", "procSurhEmissaoPortaria");

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 2, 8, 0);
    private static final ModeloDeFluxo HIDRICO = CatalogoDeEtapasDeTeste.padrao().modelo(CHAVE_DO_MODELO).orElseThrow();

    private TramitacoesDeTeste() {
    }

    private static Etapa etapa(String id) {
        return HIDRICO.etapa(id).orElseThrow();
    }

    private static LocalDateTime dia(int n) {
        return T0.plusDays(n);
    }

    private static ResponsavelDaPassagem servidor(String papel, String nome, int ini, int fim, Estado estado) {
        return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.SERVIDOR, papel, Optional.ofNullable(nome), dia(ini),
                estado == Estado.PENDENTE ? null : dia(fim), estado);
    }

    private static PassagemPelaEtapa passagem(String idEtapa, String idTecnico, int ciclo, Ramo ramo, int entrada,
            Integer saida, List<ResponsavelDaPassagem> responsaveis, boolean multi, List<AtoDaEmissao> atos,
            MotivoDaVolta motivo, boolean concluido) {
        return new PassagemPelaEtapa(Optional.of(etapa(idEtapa)), idTecnico, ciclo, ramo, dia(entrada),
                Optional.ofNullable(saida).map(T0::plusDays), responsaveis, multi, atos, Optional.ofNullable(motivo), false, 1,
                concluido);
    }

    private static ResponsavelDaPassagem interessado(int ini, int fim) {
        return new ResponsavelDaPassagem(ResponsavelDaEtapa.Tipo.INTERESSADO, ResponsavelDaEtapa.INTERESSADO,
                Optional.empty(), dia(ini), dia(fim), Estado.CONCLUIU);
    }

    private static TramitacaoDoRequerimento comCaminho(TramitacaoDoRequerimento.Estado estado, List<PassagemPelaEtapa> passagens,
            Optional<DesfechoDoCaminho> desfecho, List<Aviso> avisos, boolean truncado) {
        CaminhoReconstruido caminho = new CaminhoReconstruido(passagens,
                List.of(new CaminhoReconstruido.Complementacao(dia(10), Optional.of(dia(13)))), ClassificacaoDaTaxa.TAXAVEL,
                Optional.of(ClassificacaoDaTaxa.SEM_NOVA_TAXA), Set.of(ID_NAO_MAPEADO), 2,
                Set.of(Diagnostico.ROTA_DIVERGE_DO_STATUS), truncado, 1);
        return new TramitacaoDoRequerimento(estado, Optional.of(CHAVE_DO_MODELO), Optional.of("EM_ANALISE"), caminho, desfecho,
                Optional.empty(), avisos);
    }

    /**
     * Em andamento: cadastro, conferência que reprova, cadastro de novo (2ª passagem), conferência, análise multi-instância
     * (concluída), emissão com dois atos e um não concluído, correção (ramo da correção) e a análise atual (aberta).
     */
    public static TramitacaoDoRequerimento ativaComVoltas() {
        List<PassagemPelaEtapa> passagens = List.of(
                passagem("cadastro_do_requerimento", "taskCadastrarRequerimento", 1, Ramo.PRINCIPAL, 0, 1,
                        List.of(interessado(0, 1)), false, List.of(), null, false),
                passagem("conferencia_dos_documentos", "taskConferirDocumentosApresentados", 1, Ramo.PRINCIPAL, 1, 2,
                        List.of(servidor("Conferidor", SERVIDOR_01, 1, 2, Estado.CONCLUIU)), false, List.of(), null, false),
                passagem("cadastro_do_requerimento", "taskCadastrarRequerimento", 2, Ramo.PRINCIPAL, 2, 3,
                        List.of(interessado(2, 3)), false, List.of(), MotivoDaVolta.REPROVADO_NA_CONFERENCIA, false),
                passagem("analise_do_processo", ID_TECNICO_DA_ANALISE, 1, Ramo.PRINCIPAL, 3, 8,
                        List.of(servidor("Analista", SERVIDOR_01, 3, 5, Estado.CONCLUIU),
                                servidor("Analista", SERVIDOR_02, 3, 8, Estado.CONCLUIU),
                                servidor("Analista", null, 3, 8, Estado.ENCERRADA_SEM_ACAO)),
                        true, List.of(), null, false),
                passagem("emissao_do_ato", "taskEmitirPortarias", 1, Ramo.PRINCIPAL, 8, 9, List.of(), false,
                        List.of(new AtoDaEmissao(AtoDaEmissao.Tipo.PORTARIA, dia(8), dia(9), AtoDaEmissao.Estado.EMITIDO),
                                new AtoDaEmissao(AtoDaEmissao.Tipo.AUTORIZACAO, dia(8), dia(9),
                                        AtoDaEmissao.Estado.NAO_CONCLUIDO)),
                        null, false),
                passagem("correcao_do_requerimento", ID_TECNICO_DA_CORRECAO, 1, Ramo.CORRECAO, 9, 11,
                        List.of(interessado(9, 11)), false, List.of(), null, false),
                passagem("analise_do_processo", ID_TECNICO_DA_ANALISE, 2, Ramo.PRINCIPAL, 11, null,
                        List.of(servidor("Analista", SERVIDOR_01, 11, 11, Estado.PENDENTE)), true, List.of(), null, false));
        return comCaminho(TramitacaoDoRequerimento.Estado.ATIVO, passagens, Optional.of(new DesfechoDoCaminho(
                DesfechoDoCaminho.Estado.EM_ANDAMENTO, Optional.empty(), Optional.empty(), List.of())), List.of(), false);
    }

    /** Encerrada pelo evento de fim "taxa não paga": bola preta, com o aviso de origem. */
    public static TramitacaoDoRequerimento encerradaPorTaxa() {
        List<PassagemPelaEtapa> passagens = List.of(
                passagem("cadastro_do_requerimento", "taskCadastrarRequerimento", 1, Ramo.PRINCIPAL, 0, 1,
                        List.of(interessado(0, 1)), false, List.of(), null, false));
        FinalDoFluxo fim = HIDRICO.finais().stream().filter(f -> f.idTecnico().equals(ID_DO_FINAL)).findFirst().orElseThrow();
        Aviso aviso = new Aviso(CodigoDoAviso.FLUXO_ENCERRADO_SEM_STATUS,
                "O fluxo foi encerrado por taxa não paga no prazo, mas o SIGA ainda mostra \"aguardando pagamento\".",
                Aviso.Gravidade.ATENCAO);
        return comCaminho(TramitacaoDoRequerimento.Estado.ENCERRADO, passagens, Optional.of(new DesfechoDoCaminho(
                DesfechoDoCaminho.Estado.ENCERRADO, Optional.of(fim), Optional.of(dia(30)), List.of(aviso))), List.of(aviso),
                false);
    }

    /** Ativa na verificação da revisão: concluída, sem bola preta, e com o histórico truncado. */
    public static TramitacaoDoRequerimento concluidaNaVerificacaoTruncada() {
        List<PassagemPelaEtapa> passagens = List.of(
                passagem("cadastro_do_requerimento", "taskCadastrarRequerimento", 1, Ramo.PRINCIPAL, 0, 1,
                        List.of(interessado(0, 1)), false, List.of(), null, false),
                passagem("verificacao_da_revisao_do_processo", "taskVerificarRevisaoProcesso", 1, Ramo.PRINCIPAL, 20, null,
                        List.of(), false, List.of(), null, true));
        Aviso truncado = new Aviso(CodigoDoAviso.HISTORICO_TRUNCADO, "Histórico incompleto.", Aviso.Gravidade.ATENCAO);
        return comCaminho(TramitacaoDoRequerimento.Estado.ATIVO, passagens, Optional.of(new DesfechoDoCaminho(
                DesfechoDoCaminho.Estado.CONCLUIDO, Optional.empty(), Optional.of(dia(20)), List.of())), List.of(truncado),
                true);
    }

    /** Plano B: sem instância, histórico aproximado, sem responsável e com o selo fixo. */
    public static TramitacaoDoRequerimento planoB() {
        Etapa conferencia = etapa("conferencia_dos_documentos");
        HistoricoAproximado aproximado = new HistoricoAproximado(
                List.of(new EtapaAproximada(conferencia, dia(1), Optional.of(dia(3)), 2)),
                List.of(new Marco(TipoDeMarco.ABERTURA, dia(0)), new Marco(TipoDeMarco.CONCLUSAO, dia(30))),
                Optional.of("CONCLUIDO"), 1, false);
        Aviso selo = new Aviso(CodigoDoAviso.HISTORICO_APROXIMADO, HistoricoAproximado.SELO, Aviso.Gravidade.INFO);
        return new TramitacaoDoRequerimento(TramitacaoDoRequerimento.Estado.SEM_INSTANCIA, Optional.of(CHAVE_DO_MODELO),
                Optional.of("CONCLUIDO"), null, Optional.empty(), Optional.of(aproximado), List.of(selo));
    }

    public static TramitacaoDoRequerimento indisponivel() {
        return TramitacaoDoRequerimento.indisponivel(Optional.of(CHAVE_DO_MODELO), Optional.of("EM_ANALISE"));
    }

    public static TramitacaoDoRequerimento naoSuportado() {
        return TramitacaoDoRequerimento.naoSuportado(Optional.of("EM_ANALISE"));
    }

    /** Rótulo do arquivamento, para os testes conferirem o texto sem repeti-lo. */
    public static String rotuloConcluido() {
        return AvaliarDesfecho.ROTULO_CONCLUIDO;
    }
}
