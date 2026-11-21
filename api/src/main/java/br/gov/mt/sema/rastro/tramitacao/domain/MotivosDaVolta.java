package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Regras explícitas do motivo da volta (S8, bloco 3; tabela em docs/15, "Motivos da volta"). Cada regra exige a <b>rota</b>
 * (a etapa que volta e a passagem imediatamente anterior) e, quando o prompt a liga a uma decisão, o <b>valor da época</b> da
 * variável da lista branca entre a entrada da passagem anterior e a entrada da volta. Faltou um dos dois, ou duas regras
 * disputam a mesma volta: sem motivo. Nunca inventado.
 *
 * <p><b>NÃO CONFIRMADO (P40):</b> os valores esperados das variáveis ({@code RA}, {@code RP}, {@code reprovado},
 * {@code REVISAR}, booleano verdadeiro como {@code true} ou {@code 1}) e as duas regras só de rota (recusa no aceite e
 * redistribuição) vêm do prompt da S8 e das fixtures, não do BPMN, que não está no repositório. Conferir na homologação;
 * corrigir aqui (um lugar só).
 */
public final class MotivosDaVolta {

    // Ids estáveis das etapas (docs/15, "Conteúdo inicial"): são contrato, não mudam com o BPMN.
    public static final String CADASTRO = "cadastro_do_requerimento";
    public static final String CONFERENCIA = "conferencia_dos_documentos";
    public static final String ATRIBUICAO = "atribuicao_ao_analista";
    public static final String ACEITE = "aceite_pelo_analista";
    public static final String ANALISE = "analise_do_processo";
    public static final String REVISAO = "revisao_do_parecer_tecnico";
    public static final String VALIDACAO = "validacao_do_parecer_tecnico";
    public static final String EMISSAO = "emissao_do_ato";
    public static final String VERIFICACAO = "verificacao_da_revisao_do_processo";
    public static final String ISENCAO = "isencao_confirmada";

    static final String STATUS_CONFERENCIA = "statusConferencia";
    static final String DECISAO_VALIDACAO = "decisaoValidacaoParecerTecnico";
    static final String TROCA_DE_ANALISTA = "isRevisaoAnalise";
    static final String DEVOLVIDO_DA_EMISSAO = "isRevisaoParecer";
    static final String DECISAO_VERIFICACAO = "decisaoVerificarRevisao";

    static final String REPROVADO = "reprovado";
    static final String VOLTA_A_ANALISE = "RA";
    static final String VOLTA_A_REVISAO = "RP";
    static final String REVISAR = "REVISAR";

    private MotivosDaVolta() {
    }

    /**
     * @param volta     passagem de ciclo 2 ou mais
     * @param anterior  a passagem imediatamente anterior na linha (vazia se não há)
     * @param daEpoca   alterações das variáveis da lista branca, em ordem
     */
    public static Optional<MotivoDaVolta> motivo(PassagemPelaEtapa volta, PassagemPelaEtapa anterior,
            List<VariavelBruta> daEpoca) {
        if (volta.ciclo() < 2 || anterior == null) {
            return Optional.empty();
        }
        if (anterior.ramo() == Ramo.CORRECAO && volta.ramo() == Ramo.PRINCIPAL) {
            return Optional.of(MotivoDaVolta.CORRECAO_DO_REQUERIMENTO);
        }
        Janela janela = new Janela(daEpoca, anterior.entrada(), volta.entrada());
        String etapa = volta.idDaEtapa().orElse("");
        String antes = anterior.idDaEtapa().orElse("");
        boolean ra = janela.ultimoE(DECISAO_VALIDACAO, VOLTA_A_ANALISE);
        boolean rp = janela.ultimoE(DECISAO_VALIDACAO, VOLTA_A_REVISAO);
        boolean troca = janela.verdadeiro(TROCA_DE_ANALISTA);
        MotivoDaVolta motivo = switch (etapa) {
            case CADASTRO -> antes.equals(CONFERENCIA) && janela.ultimoE(STATUS_CONFERENCIA, REPROVADO)
                    ? MotivoDaVolta.REPROVADO_NA_CONFERENCIA : null;
            case ANALISE -> {
                if (antes.equals(VALIDACAO) && ra != troca) {
                    yield ra ? MotivoDaVolta.DEVOLVIDO_NA_VALIDACAO_PARA_ANALISE : MotivoDaVolta.TROCA_DE_ANALISTA;
                }
                yield antes.equals(REVISAO) && troca && !ra ? MotivoDaVolta.TROCA_DE_ANALISTA : null;
            }
            case REVISAO -> {
                if (antes.equals(VALIDACAO)) {
                    yield rp && !troca ? MotivoDaVolta.DEVOLVIDO_NA_VALIDACAO_PARA_REVISAO : null;
                }
                yield antes.equals(EMISSAO) && janela.verdadeiro(DEVOLVIDO_DA_EMISSAO)
                        ? MotivoDaVolta.DEVOLVIDO_NA_EMISSAO : null;
            }
            case EMISSAO -> antes.equals(VERIFICACAO) && janela.ultimoE(DECISAO_VERIFICACAO, REVISAR)
                    ? MotivoDaVolta.REVISAR_NA_VERIFICACAO : null;
            case ATRIBUICAO -> {
                if (antes.equals(ACEITE)) {
                    yield anterior.responsaveis().stream()
                            .anyMatch(r -> r.estado() == ResponsavelDaPassagem.Estado.ENCERRADA_SEM_ACAO)
                            ? MotivoDaVolta.RECUSA_NO_ACEITE : null;
                }
                yield antes.equals(ANALISE) ? MotivoDaVolta.REDISTRIBUICAO : null;
            }
            default -> null;
        };
        return Optional.ofNullable(motivo);
    }

    /** Variáveis com {@code quando} entre a entrada da passagem anterior e a entrada da volta (inclusive). */
    private record Janela(List<VariavelBruta> variaveis, LocalDateTime de, LocalDateTime ate) {

        private Optional<String> ultimo(String nome) {
            Optional<String> valor = Optional.empty();
            for (VariavelBruta v : variaveis) {
                if (v.nome().equals(nome) && v.quando() != null && !v.quando().isBefore(de) && !v.quando().isAfter(ate)) {
                    valor = v.valor();
                }
            }
            return valor;
        }

        boolean ultimoE(String nome, String esperado) {
            return ultimo(nome).map(v -> v.strip().equalsIgnoreCase(esperado)).orElse(false);
        }

        boolean verdadeiro(String nome) {
            return ultimo(nome).map(v -> v.strip().equalsIgnoreCase("true") || v.strip().equals("1")).orElse(false);
        }
    }
}
