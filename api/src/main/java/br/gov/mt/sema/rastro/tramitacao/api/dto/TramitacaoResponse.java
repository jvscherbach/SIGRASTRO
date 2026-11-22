package br.gov.mt.sema.rastro.tramitacao.api.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.rastro.tramitacao.application.TramitacaoAberta;
import br.gov.mt.sema.rastro.tramitacao.domain.AtoDaEmissao;
import br.gov.mt.sema.rastro.tramitacao.domain.Aviso;
import br.gov.mt.sema.rastro.tramitacao.domain.CaminhoReconstruido;
import br.gov.mt.sema.rastro.tramitacao.domain.DesfechoDoCaminho;
import br.gov.mt.sema.rastro.tramitacao.domain.HistoricoAproximado;
import br.gov.mt.sema.rastro.tramitacao.domain.MotivoDaVolta;
import br.gov.mt.sema.rastro.tramitacao.domain.PassagemPelaEtapa;
import br.gov.mt.sema.rastro.tramitacao.domain.ResponsavelDaPassagem;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tramitação completa de um requerimento (S8, bloco 6; docs/07, "Tramitação"): o caminho percorrido em etapas
 * simplificadas, com quem esteve em cada uma, as voltas, o final, ou, sem instância no Camunda, o histórico aproximado do
 * plano B. O fluxograma é desenhado pelo front a partir de {@code passagens}: o back-end não devolve imagem.
 *
 * <p>Sem CPF, sem chave de arquivo e, para quem não é Administrador, sem nenhum id técnico do Camunda: {@code idTecnico},
 * {@code idsNaoMapeados}, {@code diagnosticos}, {@code repeticoes} e {@code final.idTecnico} só existem na resposta do
 * Administrador. O plano B não tem campo de responsável.
 */
@Schema(description = "Caminho percorrido pelo requerimento, com quem esteve em cada etapa e como terminou")
public record TramitacaoResponse(
        long idRequerimento,
        String numeroRequerimento,
        String numeroProcesso,
        @Schema(description = "Situação do Licenciamento; o caminho do fluxo prevalece sobre ela")
        PosicaoResponse.SituacaoResponse situacaoLicenciamento,
        @Schema(description = "Rótulo do fluxo; vazio quando não suportado") String modelo,
        @Schema(description = "ATIVO, ENCERRADO, SEM_INSTANCIA, NAO_SUPORTADO ou INDISPONIVEL") String estado,
        @Schema(description = "true no plano B (sem instância no Camunda): dado aproximado, nunca exato") boolean aproximado,
        @Schema(description = "Alguma instância passou do limite de linhas: o caminho é o que coube") boolean truncado,
        @Schema(description = "Em ordem de entrada; só o que já aconteceu (nunca etapa futura)") List<PassagemResponse> passagens,
        @Schema(description = "Complementações paralelas, ao lado do caminho") List<ComplementacaoResponse> complementacoes,
        @Schema(description = "Taxável ou isento pela rota seguida; vazio sem caminho") TaxaResponse taxa,
        @JsonProperty("final") @Schema(description = "Como terminou; vazio em andamento") FinalResponse fim,
        List<AvisoResponse> avisos,
        @Schema(description = "Só no plano B (SEM_INSTANCIA)") HistoricoAproximadoResponse planoB,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Só para o Administrador") List<String> idsNaoMapeados,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Só para o Administrador") List<String> diagnosticos) {

    public static TramitacaoResponse de(TramitacaoAberta a) {
        TramitacaoDoRequerimento t = a.tramitacao();
        boolean admin = a.idsTecnicos();
        CaminhoReconstruido caminho = t.caminho();
        return new TramitacaoResponse(
                a.idRequerimento(),
                a.numeroRequerimento(),
                a.numeroProcesso(),
                a.situacao().map(s -> new PosicaoResponse.SituacaoResponse(s.codigo(), s.descricao())).orElse(null),
                a.rotuloDoModelo().orElse(null),
                t.estado().name(),
                t.aproximado(),
                caminho.truncado(),
                caminho.passagens().stream().map(p -> PassagemResponse.de(p, admin)).toList(),
                caminho.complementacoes().stream()
                        .map(c -> new ComplementacaoResponse(c.entrada(), c.saida().orElse(null))).toList(),
                taxa(t, caminho),
                t.desfecho().flatMap(d -> FinalResponse.de(d, admin)).orElse(null),
                t.avisos().stream().map(AvisoResponse::de).toList(),
                t.planoB().map(HistoricoAproximadoResponse::de).orElse(null),
                admin ? caminho.idsNaoMapeados().stream().sorted().toList() : null,
                admin ? caminho.diagnosticos().stream().map(Enum::name).sorted().toList() : null);
    }

    private static TaxaResponse taxa(TramitacaoDoRequerimento t, CaminhoReconstruido caminho) {
        if (caminho.passagens().isEmpty()) {
            return null;
        }
        return new TaxaResponse(caminho.taxaPrincipal().name(), caminho.taxaDaCorrecao().map(Enum::name).orElse(null));
    }

    @Schema(description = "Classificação pela rota: TAXAVEL, ISENTA, SEM_NOVA_TAXA ou INDETERMINADA")
    public record TaxaResponse(String principal, @Schema(description = "Vazio se não houve correção") String correcao) {}

    @Schema(description = "Uma passagem do requerimento por uma etapa simplificada")
    public record PassagemResponse(
            EtapaResponse etapa,
            @Schema(description = "1 na primeira passagem pela etapa; 2, 3... nas voltas") int ciclo,
            @Schema(description = "PRINCIPAL ou CORRECAO") String ramo,
            LocalDateTime entrada,
            @Schema(description = "Vazio na passagem atual") LocalDateTime saida,
            @Schema(description = "Vazio na passagem atual") Long duracaoSegundos,
            @Schema(description = "É a etapa em que o requerimento está agora") boolean atual,
            boolean emParalelo,
            @Schema(description = "\"Verificação da revisão do processo\": selo \"Processo concluído\"") boolean processoConcluido,
            @Schema(description = "Só se a regra de volta casa; vazio = sem motivo determinável") MotivoResponse motivoDaVolta,
            @Schema(description = "Com quem esteve, fora da multi-instância (pessoa, papel, interessado, sistema)")
            List<ResponsavelResponse> responsaveis,
            @Schema(description = "Multi-instância: cada analista ou signatário, com concluiu/pendente/encerrada sem ação")
            List<ResponsavelResponse> participantes,
            @Schema(description = "Só na \"Emissão do ato\": cada ato (autorização, outros atos, certificado, portaria)")
            List<AtoResponse> atos,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador") String idTecnico,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador: atividades juntadas nesta passagem") Integer repeticoes) {

        static PassagemResponse de(PassagemPelaEtapa p, boolean admin) {
            List<ResponsavelResponse> pessoas = p.responsaveis().stream().map(ResponsavelResponse::de).toList();
            return new PassagemResponse(
                    new EtapaResponse(p.idDaEtapa().orElse(null), p.rotulo(),
                            p.etapa().map(e -> e.tipo().name()).orElse(null)),
                    p.ciclo(),
                    p.ramo().name(),
                    p.entrada(),
                    p.saida().orElse(null),
                    p.duracao().map(java.time.Duration::toSeconds).orElse(null),
                    p.atual(),
                    p.emParalelo(),
                    p.processoConcluido(),
                    p.motivoDaVolta().map(MotivoResponse::de).orElse(null),
                    p.multiInstancia() ? List.of() : pessoas,
                    p.multiInstancia() ? pessoas : List.of(),
                    p.atos().stream().map(AtoResponse::de).toList(),
                    admin ? p.idTecnico() : null,
                    admin ? p.repeticoes() : null);
        }
    }

    @Schema(description = "Etapa simplificada (vocabulário do usuário)")
    public record EtapaResponse(
            @Schema(description = "Id estável (ex.: analise_do_processo); vazio quando não mapeada") String id,
            String rotulo,
            @Schema(description = "TAREFA_HUMANA, ESPERA, AUTOMATICA ou EMISSAO; vazio quando não mapeada") String tipo) {}

    public record MotivoResponse(String codigo, @Schema(description = "Ex.: \"devolvido na validação do parecer\"") String texto) {

        static MotivoResponse de(MotivoDaVolta m) {
            return new MotivoResponse(m.name(), m.rotulo());
        }
    }

    @Schema(description = "Com quem a etapa esteve, sem CPF: nome (se permitido) e papel")
    public record ResponsavelResponse(
            String tipo,
            String papel,
            @Schema(description = "Vazio para o Operador, quando o parâmetro de nome está desligado ou não identificado")
            String nome,
            LocalDateTime inicio,
            @Schema(description = "Vazio = pendente") LocalDateTime fim,
            @Schema(description = "CONCLUIU, PENDENTE ou ENCERRADA_SEM_ACAO") String estado) {

        static ResponsavelResponse de(ResponsavelDaPassagem r) {
            return new ResponsavelResponse(r.tipo().name(), r.papel(), r.nome().orElse(null), r.inicio(), r.fim(),
                    r.estado().name());
        }
    }

    public record AtoResponse(
            @Schema(description = "AUTORIZACAO, OUTROS_ATOS, CERTIFICADO, PORTARIA ou NAO_MAPEADO") String tipo,
            String rotulo,
            LocalDateTime inicio,
            LocalDateTime fim,
            @Schema(description = "EMITIDO, NAO_CONCLUIDO, EM_ANDAMENTO ou INDETERMINADO") String estado,
            @Schema(description = "O ato de fato foi emitido (é o que a caixa lista)") boolean ocorreu) {

        static AtoResponse de(AtoDaEmissao a) {
            return new AtoResponse(a.tipo().name(), a.tipo().rotulo(), a.inicio(), a.fim(), a.estado().name(), a.ocorreu());
        }
    }

    public record ComplementacaoResponse(LocalDateTime entrada, @Schema(description = "Vazio = em andamento") LocalDateTime saida) {}

    @Schema(description = "Selo de aviso: texto ao usuário (não só cor), com código estável e gravidade INFO ou ATENCAO")
    public record AvisoResponse(String codigo, String texto, String gravidade) {

        static AvisoResponse de(Aviso a) {
            return new AvisoResponse(a.codigo().name(), a.texto(), a.gravidade().name());
        }
    }

    @Schema(description = "Como terminou: a bola preta (FINAL) só existe com o evento de fim; na verificação da revisão o "
            + "processo está CONCLUIDO sem bola preta")
    public record FinalResponse(
            String rotulo,
            @Schema(description = "FINAL (evento de fim, bola preta) ou CONCLUIDO_NA_VERIFICACAO") String tipo,
            LocalDateTime encerradoEm,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Só para o Administrador") String idTecnico) {

        static java.util.Optional<FinalResponse> de(DesfechoDoCaminho d, boolean admin) {
            return d.rotulo().map(rotulo -> new FinalResponse(
                    rotulo,
                    d.bolaPreta() ? "FINAL" : "CONCLUIDO_NA_VERIFICACAO",
                    d.quando().orElse(null),
                    admin ? d.fim().map(f -> f.idTecnico()).orElse(null) : null));
        }
    }

    @Schema(description = "Linha do tempo aproximada de requerimento sem instância no Camunda: sem ciclos, sem fluxograma e "
            + "sem responsável")
    public record HistoricoAproximadoResponse(
            @Schema(description = "Selo fixo do plano B") String selo,
            @Schema(description = "Sem nenhuma etapa mapeada: a tela mostra só a situação e as datas") boolean semDetalhamento,
            List<EtapaAproximadaResponse> etapas,
            List<MarcoResponse> marcos,
            @Schema(description = "SITUACAO_PROCESSO, quando lida") String situacao,
            @Schema(description = "Eventos que o Rastro não conhece (só a contagem)") int eventosIgnorados,
            @Schema(description = "Havia mais eventos que o limite lido") boolean eventosTruncados) {

        static HistoricoAproximadoResponse de(HistoricoAproximado h) {
            return new HistoricoAproximadoResponse(
                    HistoricoAproximado.SELO,
                    h.semDetalhamento(),
                    h.etapas().stream().map(e -> new EtapaAproximadaResponse(new EtapaResponse(e.etapa().id(),
                            e.etapa().rotulo(), e.etapa().tipo().name()), e.entrada(), e.saida().orElse(null), e.eventos())).toList(),
                    h.marcos().stream().map(m -> new MarcoResponse(m.tipo().name(), m.tipo().rotulo(), m.data())).toList(),
                    h.situacao().orElse(null),
                    h.eventosIgnorados(),
                    h.eventosTruncados());
        }
    }

    public record EtapaAproximadaResponse(EtapaResponse etapa, LocalDateTime entrada,
            @Schema(description = "Entrada da etapa seguinte ou marco de encerramento; vazio se não se sabe") LocalDateTime saida,
            @Schema(description = "Quantos eventos caíram nesta etapa") int eventos) {}

    public record MarcoResponse(String tipo, String rotulo, LocalDateTime data) {}
}
