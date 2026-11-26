package br.gov.mt.sema.rastro.caixa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.caixa.domain.PosseLida;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.IdentificadorDoResponsavel;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;

/**
 * O cenário da caixa da S11 (tabela do bloco 1 em {@code docs/progresso/s11.md}) montado <b>em memória</b>, sem Docker: o que a
 * porta de leitura devolveria ({@link PosseLida}), o histórico da tramitação de cada retorno e o que se espera (estado,
 * aviso, origem, dias). Só dados fictícios: os analistas são chaves opacas, nenhum CPF aparece aqui.
 */
public final class CenarioDaCaixaEmMemoria {

    public static final CatalogoDeEtapas CATALOGO = CatalogoDeEtapasDeTeste.padrao();
    public static final String MODELO = "procOutorgaAguaSupSub";
    public static final DefinicaoDaPosse DEFINICAO = DefinicaoDaPosse.doCatalogo(CATALOGO);
    public static final byte[] SAL = "sal-de-teste-0123456789".getBytes(StandardCharsets.UTF_8);

    /** 2028-06-15, 12h em Cuiabá (UTC-4). */
    public static final Instant AGORA = Instant.parse("2028-06-15T16:00:00Z");
    public static final LocalDateTime AGORA_EM_CUIABA = LocalDateTime.of(2028, 6, 15, 12, 0);

    public static final String ANALISE = "taskAnalisarRequerimento";
    public static final String REVISAO = "taskRevisarParecerTecnico";
    public static final String CORRECAO = "taskCorrecaoRequerimento";

    public static final ChaveDoResponsavel A1 = chave("00000000001");
    public static final ChaveDoResponsavel A2 = chave("00000000002");
    public static final ChaveDoResponsavel A3 = chave("00000000003");

    /**
     * Uma linha do cenário.
     *
     * @param vistoAntes {@code null} = não constava no último visto (INEXISTENTE)
     * @param lida       o que a porta devolve; vazio = o requerimento nem é candidato (fora da posse de todos)
     */
    public record Caso(String codigo, long id, ChaveDoResponsavel analista, Optional<PosseLida> lida,
            TramitacaoDoRequerimento historia, EstadoDePosse esperado, EstadoDePosse vistoAntes, boolean aviso,
            OrigemDoRetorno origem, Optional<Long> dias) {
    }

    private CenarioDaCaixaEmMemoria() {
    }

    public static ChaveDoResponsavel chave(String identificadorFicticio) {
        return ChaveDoResponsavel.de(IdentificadorDoResponsavel.de(identificadorFicticio).orElseThrow(), SAL);
    }

    public static LocalDateTime haDias(long dias) {
        return AGORA_EM_CUIABA.minusDays(dias);
    }

    public static PosseLida.TarefaAtivaDaPosse ativa(String atividade, long dias, ChaveDoResponsavel dono) {
        return new PosseLida.TarefaAtivaDaPosse(atividade, haDias(dias), Optional.ofNullable(dono));
    }

    public static PosseLida posse(long id, String interessado, PosseLida.UltimaTarefaDeAnalise ultima,
            PosseLida.TarefaAtivaDaPosse... tarefas) {
        return new PosseLida(id, Optional.of(id + "/2028"), MODELO, List.of(tarefas), Optional.ofNullable(ultima),
                Optional.of(interessado), Optional.of("Outorga Exemplo"));
    }

    /** Histórico com a passagem atual na última etapa; ciclos contados pelas repetições; ramo da etapa do catálogo. */
    public static TramitacaoDoRequerimento historia(String... etapas) {
        List<PassagemPelaEtapa> passagens = new ArrayList<>();
        Map<String, Integer> ciclos = new HashMap<>();
        LocalDateTime inicio = AGORA_EM_CUIABA.minusDays(100);
        for (int i = 0; i < etapas.length; i++) {
            Etapa etapa = CATALOGO.modelo(MODELO).orElseThrow().etapa(etapas[i]).orElseThrow();
            int ciclo = ciclos.merge(etapas[i], 1, Integer::sum);
            LocalDateTime entrada = inicio.plusDays(i * 10L);
            Optional<LocalDateTime> saida = i == etapas.length - 1 ? Optional.empty() : Optional.of(entrada.plusDays(9));
            passagens.add(new PassagemPelaEtapa(Optional.of(etapa), etapa.idsTecnicos().get(0), ciclo, etapa.ramo(), entrada,
                    saida, List.of(), false, List.of(), Optional.empty(), false, 1, false));
        }
        CaminhoReconstruido caminho = new CaminhoReconstruido(passagens, List.of(),
                CaminhoReconstruido.ClassificacaoDaTaxa.INDETERMINADA, Optional.empty(), Set.of(), 0, Set.of(), false, 1);
        return new TramitacaoDoRequerimento(TramitacaoDoRequerimento.Estado.ATIVO, Optional.of(MODELO), Optional.empty(),
                caminho, Optional.empty(), Optional.empty(), List.of());
    }

    private static Caso caso(String codigo, long id, ChaveDoResponsavel analista, PosseLida lida,
            TramitacaoDoRequerimento historia, EstadoDePosse esperado, EstadoDePosse vistoAntes, OrigemDoRetorno origem,
            Long dias) {
        return new Caso(codigo, id, analista, Optional.ofNullable(lida), historia, esperado, vistoAntes, origem != null,
                origem, Optional.ofNullable(dias));
    }

    /** As linhas CX01 a CX17 (cada requerimento visto pelo analista dono). */
    public static List<Caso> casos() {
        EstadoDePosse analise = EstadoDePosse.comAnalista(MotivosDaVolta.ANALISE);
        EstadoDePosse revisao = EstadoDePosse.comAnalista(MotivosDaVolta.REVISAO);
        EstadoDePosse correcao = EstadoDePosse.COM_INTERESSADO;
        EstadoDePosse fora = EstadoDePosse.FORA;
        var ultimaDeA1 = new PosseLida.UltimaTarefaDeAnalise(ANALISE, Optional.of(A1), haDias(13));
        var ultimaDeA2 = new PosseLida.UltimaTarefaDeAnalise(ANALISE, Optional.of(A2), haDias(10));
        String cadastro = MotivosDaVolta.CADASTRO;
        return List.of(
                caso("CX01", 15001, A1, posse(15001, "Interessado Exemplo 01", null, ativa(ANALISE, 20, A1)), null, analise,
                        analise, null, 20L),
                caso("CX02", 15002, A2, posse(15002, "Interessado Exemplo LTDA", null, ativa(REVISAO, 8, A2)), null, revisao,
                        revisao, null, 8L),
                caso("CX03", 15003, A1, posse(15003, "Interessado Exemplo 03", null, ativa(REVISAO, 6, A1)), null, revisao,
                        analise, null, 6L),
                caso("CX04", 15004, A1, posse(15004, "Cooperativa Exemplo 04", ultimaDeA1, ativa(CORRECAO, 12, null)), null,
                        correcao, analise, null, 12L),
                caso("CX05", 15005, A1, posse(15005, "Interessado Exemplo 03", null, ativa(ANALISE, 10, A1)),
                        historia(MotivosDaVolta.ANALISE, DefinicaoDaPosse.ETAPA_CORRECAO, MotivosDaVolta.ANALISE),
                        analise, correcao, OrigemDoRetorno.VOLTOU_DA_CORRECAO, 10L),
                caso("CX06", 15006, A1, posse(15006, "Interessado Exemplo 01", null, ativa(REVISAO, 4, A1)),
                        historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO, MotivosDaVolta.VALIDACAO,
                                MotivosDaVolta.EMISSAO, MotivosDaVolta.REVISAO),
                        revisao, fora, OrigemDoRetorno.VOLTOU_DA_EMISSAO, 4L),
                caso("CX07", 15007, A1, posse(15007, "Interessado Exemplo LTDA", null, ativa(ANALISE, 9, A1)),
                        historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO, MotivosDaVolta.VALIDACAO,
                                MotivosDaVolta.ANALISE),
                        analise, fora, OrigemDoRetorno.VOLTOU_DA_VALIDACAO, 9L),
                caso("CX08", 15008, A1, posse(15008, "Cooperativa Exemplo 04", null, ativa(ANALISE, 7, A1)),
                        historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO, MotivosDaVolta.ANALISE),
                        analise, fora, OrigemDoRetorno.VOLTOU_DA_REVISAO, 7L),
                caso("CX09", 15009, A1, posse(15009, "Interessado Exemplo 03", null, ativa(ANALISE, 2, A1)),
                        historia(cadastro, MotivosDaVolta.CONFERENCIA, MotivosDaVolta.ATRIBUICAO, MotivosDaVolta.ACEITE,
                                MotivosDaVolta.ANALISE),
                        analise, null, OrigemDoRetorno.NOVO, 2L),
                // CX10 a CX12 (validação, emissão do ato, concluído) e CX15 (correção sem análise) nem são candidatos.
                caso("CX10", 15010, A1, null, null, fora, revisao, null, null),
                caso("CX11", 15011, A1, null, null, fora, fora, null, null),
                caso("CX12", 15012, A1, null, null, fora, fora, null, null),
                caso("CX13", 15013, A2, posse(15013, "Interessado Exemplo LTDA", ultimaDeA2, ativa(CORRECAO, 9, null)), null,
                        correcao, analise, null, 9L),
                caso("CX14", 15014, A1, posse(15014, "Interessado Exemplo LTDA", null, ativa(ANALISE, 400, A1)), null, analise,
                        analise, null, 400L),
                caso("CX15", 15015, A1, posse(15015, "Interessado Exemplo 03", null, ativa(CORRECAO, 28, null)), null, fora,
                        null, null, null),
                caso("CX16", 15016, A3, posse(15016, "Interessado Exemplo 01", null, ativa(ANALISE, 15, A3)), null, analise,
                        analise, null, 15L),
                caso("CX17", 15017, A1, posse(15017, "Interessado Exemplo 03", null, ativa(ANALISE, 3, A1)),
                        historia(MotivosDaVolta.ANALISE, MotivosDaVolta.REVISAO, MotivosDaVolta.VALIDACAO,
                                MotivosDaVolta.EMISSAO, MotivosDaVolta.ANALISE),
                        analise, fora, OrigemDoRetorno.VOLTOU_DA_EMISSAO, 3L));
    }

    /** O que a porta devolve: os requerimentos lidos de todos os casos, na ordem do id. */
    public static List<PosseLida> lidas(List<Caso> casos) {
        return casos.stream().flatMap(c -> c.lida().stream()).toList();
    }

    /** O item esperado na caixa do analista, vazio se o estado esperado é FORA. */
    public static Optional<ItemDaCaixa> itemEsperado(Caso caso) {
        if (caso.esperado().tipo() == EstadoDePosse.Tipo.FORA || caso.lida().isEmpty()) {
            return Optional.empty();
        }
        PosseLida lida = caso.lida().get();
        LocalDateTime inicio = lida.tarefasAtivas().stream().map(PosseLida.TarefaAtivaDaPosse::inicio).findFirst().orElse(null);
        return Optional.of(new ItemDaCaixa(lida.idRequerimento(), lida.numero(), lida.interessado(), lida.tipo(),
                caso.esperado(), Optional.ofNullable(inicio)));
    }
}
