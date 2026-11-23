package br.gov.mt.sema.rastro.financeiro.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Marcas financeiras nas passagens da tramitação (S9, bloco 5; docs/15). Devolve uma lista de marcas <b>por passagem</b>,
 * na mesma ordem e tamanho do caminho; passagem sem lançamento casado fica sem marca (nada é inventado).
 *
 * <ul>
 *   <li><b>Emissão da taxa</b> (principal ou da correção): cada guia é casada com a <b>última</b> passagem de emissão cuja
 *       data de entrada é igual ou anterior à data de emissão (comparação por data: {@code DATA_EMISSAO} pode vir sem
 *       hora). Marcas: "Emitida em", "DAR" e, se for o caso, "da correção". Guia emitida antes de qualquer passagem de
 *       emissão não é casada.</li>
 *   <li><b>Aguardando pagamento</b> (principal ou da correção): a guia mais recente da passagem de emissão que vem logo
 *       antes da espera. Marcas: "Vence em", "Paga em" (se houve), "Taxa vencida" (bloco 3) e "da correção".</li>
 *   <li><b>Isenção confirmada:</b> o "Isento de Taxa" do bloco 4, pareado por ordem só quando o número de isenções e de
 *       passagens bate (a mesma regra da data de apoio).</li>
 * </ul>
 */
public final class MarcarPassagens {

    private MarcarPassagens() {
    }

    public static List<List<MarcaFinanceira>> marcar(HistoricoFinanceiro historico, CaminhoDoFluxo caminho) {
        List<PassagemDoFluxo> passagens = caminho.passagens();
        List<List<MarcaFinanceira>> marcas = new ArrayList<>();
        passagens.forEach(p -> marcas.add(new ArrayList<>()));

        Map<Integer, List<LancamentoAvaliado>> guiasPorEmissao = casarGuias(historico, passagens);
        for (int i = 0; i < passagens.size(); i++) {
            PassagemDoFluxo p = passagens.get(i);
            if (CaminhoDoFluxo.EMISSOES.contains(p.idDaEtapa().orElse(""))) {
                for (LancamentoAvaliado guia : guiasPorEmissao.getOrDefault(i, List.of())) {
                    marcas.get(i).addAll(daEmissao(guia));
                }
            } else if (CaminhoDoFluxo.ESPERAS.contains(p.idDaEtapa().orElse(""))) {
                List<MarcaFinanceira> daPassagem = marcas.get(i);
                emissaoAntes(passagens, i)
                        .map(e -> guiasPorEmissao.getOrDefault(e, List.of()))
                        .filter(guias -> !guias.isEmpty())
                        .map(List::getLast)
                        .ifPresent(guia -> daPassagem.addAll(daEspera(guia)));
            }
        }
        marcarIsencoes(historico, passagens, marcas);
        return marcas.stream().map(List::copyOf).toList();
    }

    /** Guia → índice da última passagem de emissão com entrada (data) até a emissão (data). */
    private static Map<Integer, List<LancamentoAvaliado>> casarGuias(HistoricoFinanceiro historico,
            List<PassagemDoFluxo> passagens) {
        Map<Integer, List<LancamentoAvaliado>> porEmissao = new HashMap<>();
        for (LancamentoAvaliado a : historico.lancamentos()) {
            Optional<LocalDate> emissao = a.emitidaEm();
            if (a.numeroDaTaxa() == null || emissao.isEmpty()) {
                continue;
            }
            Integer escolhida = null;
            for (int i = 0; i < passagens.size(); i++) {
                PassagemDoFluxo p = passagens.get(i);
                if (CaminhoDoFluxo.EMISSOES.contains(p.idDaEtapa().orElse(""))
                        && !p.entrada().toLocalDate().isAfter(emissao.get())
                        && (escolhida == null || !p.entrada().isBefore(passagens.get(escolhida).entrada()))) {
                    escolhida = i;
                }
            }
            if (escolhida != null) {
                porEmissao.computeIfAbsent(escolhida, k -> new ArrayList<>()).add(a);
            }
        }
        return porEmissao;
    }

    /** A passagem de emissão mais recente que entrou até a entrada da espera. */
    private static Optional<Integer> emissaoAntes(List<PassagemDoFluxo> passagens, int espera) {
        Integer escolhida = null;
        for (int i = 0; i < passagens.size(); i++) {
            PassagemDoFluxo p = passagens.get(i);
            if (CaminhoDoFluxo.EMISSOES.contains(p.idDaEtapa().orElse(""))
                    && !p.entrada().isAfter(passagens.get(espera).entrada())
                    && (escolhida == null || !p.entrada().isBefore(passagens.get(escolhida).entrada()))) {
                escolhida = i;
            }
        }
        return Optional.ofNullable(escolhida);
    }

    private static List<MarcaFinanceira> daEmissao(LancamentoAvaliado guia) {
        List<MarcaFinanceira> m = new ArrayList<>();
        guia.emitidaEm().ifPresent(d -> m.add(MarcaFinanceira.data(MarcaFinanceira.Tipo.EMITIDA_EM, d)));
        guia.numeroDar().ifPresent(dar -> m.add(
                new MarcaFinanceira(MarcaFinanceira.Tipo.NUMERO_DAR, Optional.empty(), Optional.of(dar), false)));
        if (guia.daCorrecao()) {
            m.add(MarcaFinanceira.etiqueta(MarcaFinanceira.Tipo.DA_CORRECAO));
        }
        return m;
    }

    private static List<MarcaFinanceira> daEspera(LancamentoAvaliado guia) {
        List<MarcaFinanceira> m = new ArrayList<>();
        guia.venceEm().ifPresent(d -> m.add(MarcaFinanceira.data(MarcaFinanceira.Tipo.VENCE_EM, d)));
        guia.pagaEm().ifPresent(d -> m.add(MarcaFinanceira.data(MarcaFinanceira.Tipo.PAGA_EM, d)));
        if (guia.vencida()) {
            m.add(new MarcaFinanceira(MarcaFinanceira.Tipo.TAXA_VENCIDA, guia.venceEm(), Optional.empty(), false));
        }
        if (guia.daCorrecao()) {
            m.add(MarcaFinanceira.etiqueta(MarcaFinanceira.Tipo.DA_CORRECAO));
        }
        return m;
    }

    private static void marcarIsencoes(HistoricoFinanceiro historico, List<PassagemDoFluxo> passagens,
            List<List<MarcaFinanceira>> marcas) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < passagens.size(); i++) {
            if (passagens.get(i).eh(CaminhoDoFluxo.ISENCAO)) {
                indices.add(i);
            }
        }
        indices.sort((a, b) -> passagens.get(a).entrada().compareTo(passagens.get(b).entrada()));
        List<RegistroDeIsencao> isencoes = historico.isencoes();
        if (indices.size() != isencoes.size()) {
            return;
        }
        for (int k = 0; k < indices.size(); k++) {
            RegistroDeIsencao r = isencoes.get(k);
            marcas.get(indices.get(k)).add(new MarcaFinanceira(MarcaFinanceira.Tipo.ISENTO_DE_TAXA, r.data(),
                    Optional.empty(), r.dataAConfirmar()));
        }
    }
}
