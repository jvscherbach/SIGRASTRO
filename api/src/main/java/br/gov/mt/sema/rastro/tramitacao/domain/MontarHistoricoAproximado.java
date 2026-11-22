package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.EtapaAproximada;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.Marco;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado.TipoDeMarco;

/**
 * Monta a linha do tempo aproximada do plano B (S8, bloco 5; docs/15, "Plano B"). Função pura.
 *
 * <ol>
 *   <li>Cada evento vira, pelo mapa, uma etapa do modelo, um marco ou nada (desconhecido: ignorado e contado).</li>
 *   <li>Eventos seguidos da mesma etapa viram uma etapa (entrada = o primeiro); a mesma etapa mais adiante vira outra
 *       linha, sem número de ciclo nem motivo (sem voltas finas).</li>
 *   <li>Saída = entrada da etapa seguinte; a última fecha no primeiro marco de encerramento depois dela (conclusão,
 *       cancelamento, expiração), senão fica sem saída.</li>
 *   <li>Marcos: as datas de {@code REQUERIMENTO} (abertura, protocolo, cancelamento, expirações) e os eventos de marco;
 *       um por tipo (a coluna de {@code REQUERIMENTO} vence o evento), por data.</li>
 * </ol>
 */
public final class MontarHistoricoAproximado {

    private MontarHistoricoAproximado() {
    }

    public static HistoricoAproximado montar(LeituraDoPlanoB leitura, ModeloDeFluxo modelo, MapaDeEventosDoHistorico mapa) {
        Map<TipoDeMarco, LocalDateTime> marcos = new EnumMap<>(TipoDeMarco.class);
        LeituraDoPlanoB.Datas d = leitura.datas();
        colocar(marcos, TipoDeMarco.ABERTURA, d.abertura());
        colocar(marcos, TipoDeMarco.PROTOCOLO, d.protocolo());
        colocar(marcos, TipoDeMarco.CANCELAMENTO, d.cancelamento());
        colocar(marcos, TipoDeMarco.EXPIRACAO_DO_REQUERIMENTO, d.expiracaoDoRequerimento());
        colocar(marcos, TipoDeMarco.EXPIRACAO_DO_PROCESSO, d.expiracaoDoProcesso());

        record Bloco(Etapa etapa, LocalDateTime entrada, int eventos) {}
        List<Bloco> blocos = new ArrayList<>();
        int ignorados = 0;
        List<LeituraDoPlanoB.Evento> ordenados = leitura.eventos().stream()
                .sorted(Comparator.comparing(LeituraDoPlanoB.Evento::quando)).toList();
        for (LeituraDoPlanoB.Evento e : ordenados) {
            switch (mapa.resolver(modelo.chave(), e.evento().strip())) {
                case MapaDeEventosDoHistorico.NaEtapa(String idDaEtapa) -> {
                    Optional<Etapa> etapa = modelo.etapa(idDaEtapa);
                    if (etapa.isEmpty()) {
                        ignorados++;
                    } else if (!blocos.isEmpty() && blocos.getLast().etapa().equals(etapa.get())) {
                        Bloco ultimo = blocos.removeLast();
                        blocos.add(new Bloco(ultimo.etapa(), ultimo.entrada(), ultimo.eventos() + 1));
                    } else {
                        blocos.add(new Bloco(etapa.get(), e.quando(), 1));
                    }
                }
                case MapaDeEventosDoHistorico.Marco(TipoDeMarco tipo) -> marcos.putIfAbsent(tipo, e.quando());
                case MapaDeEventosDoHistorico.Desconhecido desconhecido -> ignorados++;
            }
        }

        List<Marco> listaDeMarcos = marcos.entrySet().stream().map(m -> new Marco(m.getKey(), m.getValue()))
                .sorted(Comparator.comparing(Marco::data)).toList();
        List<EtapaAproximada> etapas = new ArrayList<>();
        for (int i = 0; i < blocos.size(); i++) {
            Bloco b = blocos.get(i);
            Optional<LocalDateTime> saida = i + 1 < blocos.size()
                    ? Optional.of(blocos.get(i + 1).entrada())
                    : listaDeMarcos.stream().filter(m -> m.tipo().encerra() && !m.data().isBefore(b.entrada()))
                            .map(Marco::data).findFirst();
            etapas.add(new EtapaAproximada(b.etapa(), b.entrada(), saida, b.eventos()));
        }
        return new HistoricoAproximado(etapas, listaDeMarcos, leitura.situacao(), ignorados, leitura.eventosTruncados());
    }

    private static void colocar(Map<TipoDeMarco, LocalDateTime> marcos, TipoDeMarco tipo, LocalDateTime data) {
        if (data != null) {
            marcos.put(tipo, data);
        }
    }
}
