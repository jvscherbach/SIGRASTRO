package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Final do caminho e divergências entre o evento de fim e a situação do Licenciamento (S8, bloco 4). Função pura.
 *
 * <ol>
 *   <li><b>Instância encerrada:</b> {@code END_ACT_ID_} → final do catálogo (bola preta com o rótulo do evento). Evento
 *       fora do catálogo: encerrado <b>sem</b> final e aviso {@code ETAPA_NAO_MAPEADA}. A situação só gera aviso pelo
 *       {@link Reconciliar#confrontarFinal}: divergência conhecida ({@code FLUXO_ENCERRADO_SEM_STATUS},
 *       {@code EXPIRADO_NA_CORRECAO_LAC}) ou {@code FINAL_E_SITUACAO_DIVERGEM}. A situação nunca troca o rótulo.</li>
 *   <li><b>Ativa na verificação da revisão</b> (regra técnica 9): {@code CONCLUIDO}, sem bola preta, desde a entrada na
 *       verificação, com o aviso neutro {@code CONCLUIDO_ANTES_DA_VERIFICACAO} sempre; situação fora das aceitas para a
 *       etapa: também {@code SITUACAO_NAO_CONFERE}.</li>
 *   <li><b>Ativa em outra etapa:</b> em andamento; o confronto da etapa atual é o da posição (S7).</li>
 *   <li>{@code ARQUIVADO_TEMPORARIAMENTE} como situação: aviso {@code INFO} neutro, em qualquer estado.</li>
 * </ol>
 */
public final class AvaliarDesfecho {

    public static final String ROTULO_CONCLUIDO = "Concluído";

    private final Reconciliar reconciliar;

    public AvaliarDesfecho(Reconciliar reconciliar) {
        this.reconciliar = Objects.requireNonNull(reconciliar);
    }

    public DesfechoDoCaminho avaliar(CaminhoBruto bruto, CaminhoReconstruido caminho, ModeloDeFluxo modelo,
            Optional<String> situacao) {
        Optional<String> lida = Objects.requireNonNullElse(situacao, Optional.<String>empty()).map(String::strip)
                .filter(s -> !s.isEmpty());
        Optional<InstanciaDoHistorico> raiz = bruto == null ? Optional.empty() : bruto.raiz();
        if (raiz.isEmpty()) {
            return new DesfechoDoCaminho(DesfechoDoCaminho.Estado.SEM_INSTANCIA, Optional.empty(), Optional.empty(),
                    List.of(reconciliar.aviso(CodigoDoAviso.SEM_INSTANCIA, lida.orElse(""))));
        }
        List<Aviso> avisos = new ArrayList<>();
        InstanciaDoHistorico r = raiz.get();
        if (r.encerrada()) {
            Optional<FinalDoFluxo> fim = modelo.resolver(r.atividadeFinal(), Set.of())
                    instanceof ResolucaoDeEtapa.Encerrado(FinalDoFluxo f) ? Optional.of(f) : Optional.empty();
            if (fim.isEmpty()) {
                avisos.add(reconciliar.aviso(CodigoDoAviso.ETAPA_NAO_MAPEADA, lida.orElse("")));
                lida.filter(Reconciliar.ARQUIVADO_TEMPORARIAMENTE::equals).ifPresent(s -> avisos.add(
                        reconciliar.aviso(CodigoDoAviso.SITUACAO_ARQUIVADO_TEMPORARIAMENTE, s)));
            } else {
                lida.flatMap(s -> reconciliar.confrontarFinal(modelo.chave(), fim.get(), s))
                        .ifPresent(c -> avisos.add(reconciliar.aviso(c, lida.get())));
            }
            return new DesfechoDoCaminho(DesfechoDoCaminho.Estado.ENCERRADO, fim, Optional.ofNullable(r.fim()), avisos);
        }
        Optional<PassagemPelaEtapa> verificacao = caminho.passagens().stream()
                .filter(p -> p.atual() && p.processoConcluido()).findFirst();
        if (verificacao.isPresent()) {
            avisos.add(reconciliar.aviso(CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO, lida.orElse("")));
            lida.flatMap(s -> reconciliar.confrontarEtapa(modelo.chave(), MotivosDaVolta.VERIFICACAO, s))
                    .filter(c -> c != CodigoDoAviso.CONCLUIDO_ANTES_DA_VERIFICACAO)
                    .ifPresent(c -> avisos.add(reconciliar.aviso(c, lida.get())));
            return new DesfechoDoCaminho(DesfechoDoCaminho.Estado.CONCLUIDO, Optional.empty(),
                    Optional.of(verificacao.get().entrada()), avisos);
        }
        lida.filter(Reconciliar.ARQUIVADO_TEMPORARIAMENTE::equals).ifPresent(
                s -> avisos.add(reconciliar.aviso(CodigoDoAviso.SITUACAO_ARQUIVADO_TEMPORARIAMENTE, s)));
        return new DesfechoDoCaminho(DesfechoDoCaminho.Estado.EM_ANDAMENTO, Optional.empty(), Optional.empty(), avisos);
    }
}
