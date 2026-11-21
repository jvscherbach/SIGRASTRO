package br.gov.mt.sema.rastro.tramitacao.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.tramitacao.application.PosicaoDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.ElementoDaPosicao;
import br.gov.mt.sema.rastro.tramitacao.domain.Etapa;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoAtual;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaEtapa;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Posição atual de um requerimento (S7, bloco 6; docs/07, "Tramitação"). Sem CPF, sem chave de arquivo e, para quem não
 * é Administrador, sem nenhum id técnico do Camunda: os campos {@code idTecnico}, {@code processoFilho} e
 * {@code idsNaoMapeados} só existem na resposta do Administrador. As tarefas cruas, os grupos técnicos e o diagnóstico
 * da instância nunca saem.
 */
@Schema(description = "Onde o requerimento está agora, desde quando e com quem")
public record PosicaoResponse(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        @Schema(description = "Situação do Licenciamento; a etapa do fluxo prevalece sobre ela") SituacaoResponse situacaoLicenciamento,
        @Schema(description = "Rótulo do fluxo; vazio quando não suportado") String modelo,
        @Schema(description = "ATIVO, ENCERRADO, SEM_INSTANCIA, NAO_SUPORTADO ou INDISPONIVEL") String estado,
        EtapaResponse etapaAtual,
        List<EtapaResponse> emParalelo,
        ComplementacaoResponse complementacao,
        MultiInstanciaResponse multiInstancia,
        List<AvisoResponse> avisos,
        @JsonProperty("final") FinalResponse fim,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Só para o Administrador") List<String> idsNaoMapeados) {

    public static PosicaoResponse de(PosicaoDoRequerimento p) {
        PosicaoAtual posicao = p.posicao().posicao();
        boolean admin = p.idsTecnicos();
        return new PosicaoResponse(
                p.idRequerimento(),
                p.numeroRequerimento(),
                p.numeroProcesso(),
                p.situacao().map(s -> new SituacaoResponse(s.codigo(), s.descricao())).orElse(null),
                posicao.rotuloDoModelo().orElse(null),
                posicao.estado().name(),
                posicao.principal().map(e -> EtapaResponse.de(e, admin)).orElse(null),
                posicao.emParalelo().stream().map(e -> EtapaResponse.de(e, admin)).toList(),
                posicao.complementacao().map(c -> new ComplementacaoResponse(c.quantidade(), c.desde())).orElse(null),
                posicao.principal().flatMap(MultiInstanciaResponse::de).orElse(null),
                p.posicao().avisos().stream().map(AvisoResponse::de).toList(),
                posicao.fim().map(f -> new FinalResponse(f.rotulo(), posicao.encerradaEm().orElse(null),
                        admin ? f.idTecnico() : null)).orElse(null),
                admin ? posicao.idsNaoMapeados() : null);
    }

    public record SituacaoResponse(String codigo, String descricao) {}

    @Schema(description = "Etapa simplificada (vocabulário do usuário)")
    public record EtapaResponse(
            @Schema(description = "Id estável da etapa (ex.: analise_do_processo); vazio quando não mapeada") String id,
            String rotulo,
            @Schema(description = "TAREFA_HUMANA, ESPERA, AUTOMATICA ou EMISSAO; vazio quando não mapeada") String tipo,
            @Schema(description = "Início da etapa; vazio quando não se sabe") LocalDateTime desde,
            @Schema(description = "O \"desde\" é o da última entrada nesta etapa (a tarefa é recriada a cada volta)")
            boolean desdeUltimaEntrada,
            ResponsavelResponse responsavel,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Só para o Administrador") String idTecnico,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Só para o Administrador") String processoFilho) {

        static EtapaResponse de(ElementoDaPosicao e, boolean admin) {
            return new EtapaResponse(
                    e.etapa().map(Etapa::id).orElse(null),
                    e.rotulo(),
                    e.etapa().map(et -> et.tipo().name()).orElse(null),
                    e.desde(),
                    e.desdeUltimaEntrada(),
                    e.responsavel().map(ResponsavelResponse::de).orElse(null),
                    admin ? e.idTecnico() : null,
                    admin ? e.processoFilho().orElse(null) : null);
        }
    }

    @Schema(description = "Com quem está, sem CPF: nome (se permitido), senão área, senão papel")
    public record ResponsavelResponse(String tipo, String papel, String area, String nome, String texto) {

        static ResponsavelResponse de(ResponsavelDaEtapa r) {
            return new ResponsavelResponse(r.tipo().name(), r.papel(), r.area().orElse(null), r.nome().orElse(null),
                    r.texto());
        }
    }

    public record ComplementacaoResponse(int quantidade, LocalDateTime desde) {}

    @Schema(description = "Etapa com uma tarefa por analista ou signatário: \"x de y concluídos\"")
    public record MultiInstanciaResponse(
            @Schema(description = "Concluídos do ciclo; vazio quando o ciclo não é conhecido") Integer concluidos,
            int ativas,
            @Schema(description = "Total do ciclo; vazio quando não se sabe (o Rastro não inventa o \"y\")") Integer total,
            List<ParticipanteResponse> participantes,
            @Schema(description = "Participantes além do limite de nomes (\"e mais N\")") int maisParticipantes) {

        static java.util.Optional<MultiInstanciaResponse> de(ElementoDaPosicao e) {
            return e.multiInstancia().map(m -> {
                List<ResponsavelDaEtapa.Participante> participantes = e.responsavel()
                        .map(ResponsavelDaEtapa::participantes).orElse(List.of());
                int mais = e.responsavel().map(ResponsavelDaEtapa::maisParticipantes).orElse(0);
                return new MultiInstanciaResponse(m.concluidos().orElse(null), m.ativas(), m.total().orElse(null),
                        participantes.stream()
                                .map(pa -> new ParticipanteResponse(pa.nome().orElse(null), pa.papel(), pa.concluiu()))
                                .toList(),
                        mais);
            });
        }
    }

    public record ParticipanteResponse(String nome, String papel, boolean concluiu) {}

    @Schema(description = "Selo de aviso: texto ao usuário (não só cor), com código estável e gravidade INFO ou ATENCAO")
    public record AvisoResponse(String codigo, String texto, String gravidade) {

        static AvisoResponse de(Aviso a) {
            return new AvisoResponse(a.codigo().name(), a.texto(), a.gravidade().name());
        }
    }

    public record FinalResponse(
            String rotulo,
            LocalDateTime encerradoEm,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Só para o Administrador") String idTecnico) {}
}
