package br.gov.mt.sema.rastro.caixa.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import br.gov.mt.sema.rastro.caixa.application.port.VistoDoAnalista;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.EstadoDePosse;
import br.gov.mt.sema.rastro.caixa.domain.InstantaneoDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.OrigemDoRetorno;
import br.gov.mt.sema.rastro.caixa.domain.RegrasDoAviso;
import org.springframework.stereotype.Service;

/**
 * Aviso de recebimento (S11, bloco 5): o que chegou à caixa do analista desde o último visto e o gesto de "ver". Só lê o
 * instantâneo em memória. Quem chama confere o perfil e entrega a chave da <b>própria</b> sessão.
 *
 * <ul>
 *   <li><b>Primeiro acesso</b> (nenhum visto): tudo o que já está com o analista entra como visto, sem aviso; o reinício do
 *       sistema (visto em memória) vale como primeiro acesso. Sem retrato bom ainda, nada é gravado.</li>
 *   <li>Abrir a lista de mudanças ({@link #marcarVisto}) grava o visto do conjunto devolvido; o que chegar depois avisa de
 *       novo. Duas abas: a gravação é atômica e só vale para o que aquela aba viu.</li>
 * </ul>
 */
@Service
public class AvisosDaCaixa {

    /** Um requerimento que chegou, com de onde veio. */
    public record Aviso(ItemDaCaixa item, OrigemDoRetorno origem) {
    }

    private final GuardarInstantaneoDaCaixa guardar;
    private final VistoDoAnalista visto;

    public AvisosDaCaixa(GuardarInstantaneoDaCaixa guardar, VistoDoAnalista visto) {
        this.guardar = guardar;
        this.visto = visto;
    }

    /** O que chegou desde o último visto, na ordem do id. */
    public List<Aviso> avisos(ChaveDoResponsavel analista) {
        InstantaneoDaCaixa instantaneo = guardar.atual();
        if (!instantaneo.temDadosBons()) {
            return List.of();
        }
        List<ItemDaCaixa> itens = instantaneo.itensDe(analista);
        if (visto.gravarSeAusente(analista, RegrasDoAviso.estados(itens))) {
            return List.of();
        }
        Map<Long, EstadoDePosse> ultimo = visto.ultimo(analista).orElse(Map.of());
        return RegrasDoAviso.avisados(itens, ultimo).stream()
                .map(item -> new Aviso(item, instantaneo.origemDe(analista, item.id()).orElse(OrigemDoRetorno.MUDOU_DE_ETAPA)))
                .toList();
    }

    /** Os ids avisados, para a marca "Novo" da lista; a quantidade é {@code size()}. */
    public Set<Long> idsAvisados(ChaveDoResponsavel analista) {
        return avisos(analista).stream().map(a -> a.item().id()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Grava o visto do conjunto devolvido ({@code idsVistos}). O que chegou depois (ou que esta aba não viu) continua
     * avisando. Sem retrato bom, nada muda.
     */
    public void marcarVisto(ChaveDoResponsavel analista, Collection<Long> idsVistos) {
        InstantaneoDaCaixa instantaneo = guardar.atual();
        if (!instantaneo.temDadosBons()) {
            return;
        }
        List<ItemDaCaixa> itens = instantaneo.itensDe(analista);
        if (visto.gravarSeAusente(analista, RegrasDoAviso.estados(itens))) {
            return;
        }
        Set<Long> vistos = Set.copyOf(idsVistos);
        visto.atualizar(analista, ultimo -> RegrasDoAviso.aposAbrirALista(itens, ultimo, vistos));
    }
}
