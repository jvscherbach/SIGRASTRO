package br.gov.mt.sema.rastro.painel.api.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.painel.application.AbrirPainel.PainelAberto;
import br.gov.mt.sema.rastro.painel.domain.AvisoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.CargaPorResponsavel;
import br.gov.mt.sema.rastro.painel.domain.FilaPorEtapa;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.SerieDoAno;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contrato do painel (S10). Só números, rótulos e avisos: nenhum campo de CPF, id técnico do Camunda ou chave de arquivo.
 * Com os nomes ocultos (RN12) a carga traz só as categorias sem nome.
 */
@Schema(description = "Instantâneo do painel do Gestor: três gráficos, estado da leitura e avisos.")
public record PainelResponse(
        String estado, int ano, OffsetDateTime geradoEm, OffsetDateTime falhouEm, OffsetDateTime proximaAtualizacaoEm,
        boolean truncado,
        List<MesResponse> serie, long totalProtocolados, long totalConcluidos, long semDataDeProtocolo,
        List<EtapaResponse> filaPorEtapa, long totalNaFila, long foraDoCatalogo, long semInstancia, long etapaNaoMapeada,
        List<CargaResponse> cargaPorResponsavel, boolean nomesOcultos,
        List<AvisoResponse> avisos) {

    private static final ZoneId CUIABA = ZoneId.of("America/Cuiaba");

    public record MesResponse(int mes, long protocolados, long concluidos) {
    }

    public record EtapaResponse(String rotulo, long total) {
    }

    public record CargaResponse(String rotulo, long total, String tipo) {
    }

    public record AvisoResponse(String codigo, String texto) {
    }

    public static PainelResponse de(PainelAberto aberto) {
        InstantaneoDoPainel i = aberto.instantaneo();
        Optional<SerieDoAno> serie = i.serie();
        Optional<FilaPorEtapa> fila = i.fila();
        Optional<CargaPorResponsavel> carga = i.carga(aberto.mostrarNome());

        List<AvisoResponse> avisos = new ArrayList<>();
        i.avisos().forEach(a -> acrescentar(avisos, a));
        carga.ifPresent(c -> c.avisos().forEach(a -> acrescentar(avisos, a)));

        return new PainelResponse(
                i.estado().name(), aberto.ano(), data(i.geradoEm()), data(i.falhouEm()),
                data(aberto.proximaAtualizacaoEm()), i.truncado(),
                serie.map(s -> s.meses().stream().map(m -> new MesResponse(m.mes(), m.protocolados(), m.concluidos()))
                        .toList()).orElse(List.of()),
                serie.map(SerieDoAno::totalProtocolados).orElse(0L),
                serie.map(SerieDoAno::totalConcluidos).orElse(0L),
                serie.map(SerieDoAno::semDataDeProtocolo).orElse(0L),
                fila.map(f -> f.barras().stream()
                        .map(b -> new EtapaResponse(b.modelo() + " · " + b.rotulo(), b.total())).toList())
                        .orElse(List.of()),
                fila.map(FilaPorEtapa::totalNaFila).orElse(0L),
                fila.map(FilaPorEtapa::foraDoCatalogo).orElse(0L),
                fila.map(FilaPorEtapa::semInstancia).orElse(0L),
                fila.map(FilaPorEtapa::etapaNaoMapeada).orElse(0L),
                carga.map(c -> c.linhas().stream()
                        .map(l -> new CargaResponse(l.rotulo(), l.total(), l.tipo().name())).toList())
                        .orElse(List.of()),
                carga.map(CargaPorResponsavel::nomesOcultos).orElse(!aberto.mostrarNome()),
                List.copyOf(avisos));
    }

    private static void acrescentar(List<AvisoResponse> avisos, AvisoDoPainel aviso) {
        AvisoResponse novo = new AvisoResponse(aviso.codigo(), aviso.texto());
        if (!avisos.contains(novo)) {
            avisos.add(novo);
        }
    }

    private static OffsetDateTime data(Optional<Instant> instante) {
        return instante.map(x -> OffsetDateTime.ofInstant(x, CUIABA)).orElse(null);
    }
}
