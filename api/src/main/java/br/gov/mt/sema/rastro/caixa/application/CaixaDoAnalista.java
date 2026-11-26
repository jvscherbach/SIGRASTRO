package br.gov.mt.sema.rastro.caixa.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.FiltroDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.InstantaneoDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ItemDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ResumoDaCaixa;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import org.springframework.stereotype.Service;

/**
 * A caixa do analista como a tela a precisa (S11, bloco 5): resumo, lista filtrada e ordenada, dias na etapa e a marca
 * "Novo". Resposta imediata, só do instantâneo em memória; a primeira requisição de um Analista dispara a primeira leitura em
 * segundo plano ({@link AtualizarCaixa#iniciarSeNuncaLeu()}). O perfil é conferido por quem chama (bloco 6), que entrega a
 * chave da <b>própria</b> sessão ou vazio quando o usuário não está vinculado a um servidor (sem CPF cadastrado).
 */
@Service
public class CaixaDoAnalista {

    /** Uma linha da lista. */
    public record Linha(ItemDaCaixa item, Optional<Long> diasNaEtapa, boolean novo) {
    }

    /**
     * @param semVinculo        usuário sem CPF cadastrado: caixa vazia com a mensagem de vínculo
     * @param novos             quantos requerimentos chegaram desde o último visto (o contador da caixinha)
     * @param resumo            sempre sobre a caixa inteira, sem os filtros da lista
     */
    public record Visao(InstantaneoDaCaixa.Estado estado, Optional<Instant> geradoEm, Optional<Instant> falhouEm,
            boolean incompleta, boolean semVinculo, int novos, ResumoDaCaixa resumo, List<Linha> linhas) {
    }

    private final GuardarInstantaneoDaCaixa guardar;
    private final AtualizarCaixa atualizar;
    private final AvisosDaCaixa avisos;
    private final CatalogoDeEtapas catalogo;
    private final Clock relogio;

    public CaixaDoAnalista(GuardarInstantaneoDaCaixa guardar, AtualizarCaixa atualizar, AvisosDaCaixa avisos,
            CatalogoDeEtapas catalogo, Clock relogio) {
        this.guardar = guardar;
        this.atualizar = atualizar;
        this.avisos = avisos;
        this.catalogo = catalogo;
        this.relogio = relogio;
    }

    public Visao abrir(Optional<ChaveDoResponsavel> analista, Optional<String> etapa, String busca,
            FiltroDaCaixa.Ordem ordem) {
        atualizar.iniciarSeNuncaLeu();
        InstantaneoDaCaixa instantaneo = guardar.atual();
        if (analista.isEmpty()) {
            return new Visao(instantaneo.estado(), instantaneo.geradoEm(), instantaneo.falhouEm(), instantaneo.incompleta(),
                    true, 0, ResumoDaCaixa.de(List.of(), catalogo), List.of());
        }
        ChaveDoResponsavel chave = analista.get();
        List<ItemDaCaixa> itens = instantaneo.itensDe(chave);
        Set<Long> novos = avisos.idsAvisados(chave);
        Instant agora = relogio.instant();
        List<Linha> linhas = FiltroDaCaixa.aplicar(itens, etapa, busca, ordem).stream()
                .map(i -> new Linha(i, i.diasNaEtapa(agora), novos.contains(i.id())))
                .toList();
        return new Visao(instantaneo.estado(), instantaneo.geradoEm(), instantaneo.falhouEm(), instantaneo.incompleta(),
                false, novos.size(), ResumoDaCaixa.de(itens, catalogo), linhas);
    }
}
