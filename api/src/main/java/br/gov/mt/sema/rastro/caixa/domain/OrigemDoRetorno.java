package br.gov.mt.sema.rastro.caixa.domain;

import java.util.List;

import br.gov.mt.sema.rastro.tramitacao.domain.MotivosDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.Ramo;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;

/**
 * De onde o requerimento chegou à caixa (S11, bloco 5), pelo evento anterior do histórico da tramitação (S8). Quando o
 * histórico não permite dizer, {@link #MUDOU_DE_ETAPA}: nunca inventa.
 */
public enum OrigemDoRetorno {
    NOVO("Novo na sua caixa"),
    VOLTOU_DA_CORRECAO("Voltou da correção"),
    VOLTOU_DA_VALIDACAO("Voltou da validação do parecer"),
    VOLTOU_DA_EMISSAO("Voltou da emissão do ato"),
    VOLTOU_DA_REVISAO("Voltou da revisão"),
    MUDOU_DE_ETAPA("Mudou de etapa");

    private final String rotulo;

    OrigemDoRetorno(String rotulo) {
        this.rotulo = rotulo;
    }

    /** Texto ao usuário. */
    public String rotulo() {
        return rotulo;
    }

    /**
     * Deduz a origem: a passagem atual em análise ou revisão e a que a precedeu. {@code null}, sem passagem atual de posse
     * ou passagem anterior que não diz nada: {@link #MUDOU_DE_ETAPA}.
     */
    public static OrigemDoRetorno de(TramitacaoDoRequerimento tramitacao) {
        if (tramitacao == null || tramitacao.estado() == TramitacaoDoRequerimento.Estado.INDISPONIVEL) {
            return MUDOU_DE_ETAPA;
        }
        List<PassagemPelaEtapa> passagens = tramitacao.caminho().passagens();
        PassagemPelaEtapa atual = null;
        for (int i = passagens.size() - 1; i >= 0 && atual == null; i--) {
            PassagemPelaEtapa p = passagens.get(i);
            if (p.atual() && p.idDaEtapa().filter(DefinicaoDaPosse.ETAPAS_DE_POSSE::contains).isPresent()) {
                atual = p;
            }
        }
        if (atual == null) {
            return MUDOU_DE_ETAPA;
        }
        PassagemPelaEtapa anterior = null;
        for (PassagemPelaEtapa p : passagens) {
            if (p != atual && p.entrada().isBefore(atual.entrada())
                    && (anterior == null || !p.entrada().isBefore(anterior.entrada()))) {
                anterior = p;
            }
        }
        if (anterior == null) {
            return atual.ciclo() == 1 ? NOVO : MUDOU_DE_ETAPA;
        }
        String id = anterior.idDaEtapa().orElse("");
        if (anterior.ramo() == Ramo.CORRECAO || id.equals(DefinicaoDaPosse.ETAPA_CORRECAO)) {
            return VOLTOU_DA_CORRECAO;
        }
        return switch (id) {
            case MotivosDaVolta.VALIDACAO -> VOLTOU_DA_VALIDACAO;
            case MotivosDaVolta.EMISSAO -> VOLTOU_DA_EMISSAO;
            case MotivosDaVolta.REVISAO -> atual.idDaEtapa().filter(MotivosDaVolta.ANALISE::equals).isPresent()
                    ? VOLTOU_DA_REVISAO : MUDOU_DE_ETAPA;
            case MotivosDaVolta.ACEITE, MotivosDaVolta.ATRIBUICAO -> atual.ciclo() == 1 ? NOVO : MUDOU_DE_ETAPA;
            default -> MUDOU_DE_ETAPA;
        };
    }
}
