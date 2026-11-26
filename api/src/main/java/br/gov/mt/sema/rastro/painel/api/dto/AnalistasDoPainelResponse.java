package br.gov.mt.sema.rastro.painel.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.painel.application.AbrirPainel.AnalistasAbertos;
import br.gov.mt.sema.rastro.painel.application.AbrirPainel.RequerimentosAbertos;
import br.gov.mt.sema.rastro.painel.domain.AvisoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.CargaPorResponsavel;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato da lista de requerimentos por analista do painel (S11, bloco 9). O analista é identificado só pela chave opaca
 * (nunca por CPF); o interessado é só o nome. Com os nomes ocultos, só as categorias sem nome, sem nome algum.
 */
@Schema(description = "Analistas do painel, com o total de cada um (o da barra da carga por responsável), o estado da leitura "
        + "e os avisos. O campo analista é um identificador opaco (não é CPF) e muda a cada reinício do sistema.")
public record AnalistasDoPainelResponse(
        String estado, int ano, OffsetDateTime geradoEm, OffsetDateTime falhouEm, boolean truncado, boolean nomesOcultos,
        List<AnalistaResponse> analistas, List<String> etapas, List<PainelResponse.AvisoResponse> avisos) {

    private static final ZoneId CUIABA = ZoneId.of("America/Cuiaba");

    public record AnalistaResponse(String analista, String rotulo, long total, String tipo) {
    }

    public static AnalistasDoPainelResponse de(AnalistasAbertos aberto) {
        var i = aberto.instantaneo();
        Optional<ListaPorResponsavel> lista = aberto.lista();
        List<PainelResponse.AvisoResponse> avisos = avisos(aberto.instantaneo().avisos(),
                i.carga(aberto.mostrarNome()).map(CargaPorResponsavel::avisos).orElse(List.of()));
        return new AnalistasDoPainelResponse(i.estado().name(), aberto.ano(), data(i.geradoEm()), data(i.falhouEm()),
                i.truncado(), i.carga(aberto.mostrarNome()).map(CargaPorResponsavel::nomesOcultos)
                        .orElse(!aberto.mostrarNome()),
                lista.map(l -> l.analistas().stream()
                        .map(a -> new AnalistaResponse(a.chave(), a.rotulo(), a.total(), a.tipo().name())).toList())
                        .orElse(List.of()),
                lista.map(ListaPorResponsavel::etapas).orElse(List.of()), avisos);
    }

    /** Contrato da lista de requerimentos de um analista. */
    @Schema(description = "Requerimentos de um analista na etapa atual, com os filtros aplicados. total é o da barra; "
            + "quantidade é o que sobrou dos filtros (a lista traz no máximo 1000).")
    public record RequerimentosResponse(
            String estado, int ano, OffsetDateTime geradoEm, boolean truncado, String analista, String rotulo, String tipo,
            long total, int quantidade, boolean limitada, List<RequerimentoResponse> requerimentos,
            List<PainelResponse.AvisoResponse> avisos) {

        /** Teto de linhas na resposta; o total e a quantidade seguem exatos. */
        public static final int MAXIMO_NA_RESPOSTA = 1000;

        public static RequerimentosResponse de(RequerimentosAbertos aberto) {
            var i = aberto.instantaneo();
            var todos = aberto.requerimentos();
            List<RequerimentoResponse> linhas = todos.stream().limit(MAXIMO_NA_RESPOSTA)
                    .map(r -> new RequerimentoResponse(r.id(), r.numero().orElse(null), r.interessado().orElse(null),
                            r.protocolo().orElse(null), r.etapas(), r.responsavel())).toList();
            return new RequerimentosResponse(i.estado().name(), aberto.ano(), data(i.geradoEm()), i.truncado(),
                    aberto.linha().chave(), aberto.linha().rotulo(), aberto.linha().tipo().name(), aberto.linha().total(),
                    todos.size(), todos.size() > MAXIMO_NA_RESPOSTA, linhas,
                    AnalistasDoPainelResponse.avisos(i.avisos(), List.<AvisoDoPainel>of()));
        }
    }

    public record RequerimentoResponse(long id, String numero, String interessado, LocalDate protocolo,
            List<String> etapas, String responsavel) {
    }

    private static List<PainelResponse.AvisoResponse> avisos(List<AvisoDoPainel> do1, List<AvisoDoPainel> do2) {
        List<PainelResponse.AvisoResponse> avisos = new ArrayList<>();
        for (AvisoDoPainel a : do1) {
            acrescentar(avisos, a);
        }
        for (AvisoDoPainel a : do2) {
            acrescentar(avisos, a);
        }
        return List.copyOf(avisos);
    }

    private static void acrescentar(List<PainelResponse.AvisoResponse> avisos, AvisoDoPainel aviso) {
        var novo = new PainelResponse.AvisoResponse(aviso.codigo(), aviso.texto());
        if (!avisos.contains(novo)) {
            avisos.add(novo);
        }
    }

    private static OffsetDateTime data(Optional<Instant> instante) {
        return instante.map(x -> OffsetDateTime.ofInstant(x, CUIABA)).orElse(null);
    }
}
