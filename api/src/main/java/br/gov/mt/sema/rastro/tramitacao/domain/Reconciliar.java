package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Confronto entre a situação do Licenciamento e a posição do fluxo (S7, bloco 5; docs/15, "Avisos"). A saída leva a
 * posição <b>inalterada</b> (o mesmo objeto) e só acrescenta avisos: a etapa do fluxo sempre prevalece sobre a situação.
 *
 * <ol>
 *   <li>Ativa: confronta a situação com a etapa <b>principal</b> (mapeada); encerrada: com o <b>final</b>.</li>
 *   <li>Situação listada como divergência conhecida daquela etapa ou final: o aviso próprio (ex.: taxa vencida sem
 *       atualizar a situação, concluído antes da verificação, expirado na correção do LAC).</li>
 *   <li>Senão, situação fora das aceitas: {@link CodigoDoAviso#SITUACAO_NAO_CONFERE}, texto neutro.</li>
 *   <li>Etapa ou final sem tabela, situação ausente, ou posição sem instância, não suportada ou indisponível: sem
 *       confronto (não se acusa divergência sem base).</li>
 *   <li>As marcas da leitura (várias instâncias, suporte, não mapeada, sem instância) viram os avisos de mesmo nome.</li>
 * </ol>
 */
public final class Reconciliar {

    private final CatalogoDoConfronto catalogo;

    public Reconciliar(CatalogoDoConfronto catalogo) {
        this.catalogo = Objects.requireNonNull(catalogo);
    }

    public PosicaoConfrontada reconciliar(PosicaoAtual posicao, Optional<String> situacao) {
        Optional<String> situacaoLida = Objects.requireNonNullElse(situacao, Optional.<String>empty())
                .map(String::strip).filter(s -> !s.isEmpty());
        Set<CodigoDoAviso> codigos = EnumSet.noneOf(CodigoDoAviso.class);
        List<Aviso> avisos = new ArrayList<>();

        situacaoLida.flatMap(s -> confrontar(posicao, s)).ifPresent(codigo -> {
            codigos.add(codigo);
            avisos.add(catalogo.aviso(codigo, situacaoLida.get()));
        });
        for (MarcaDaPosicao marca : MarcaDaPosicao.values()) {
            CodigoDoAviso codigo = CodigoDoAviso.da(marca);
            if (posicao.marcas().contains(marca) && codigos.add(codigo)) {
                avisos.add(catalogo.aviso(codigo, situacaoLida.orElse("")));
            }
        }
        return new PosicaoConfrontada(posicao, situacaoLida, avisos);
    }

    private Optional<CodigoDoAviso> confrontar(PosicaoAtual posicao, String situacao) {
        Optional<String> chave = posicao.chaveDoModelo();
        if (chave.isEmpty()) {
            return Optional.empty();
        }
        Optional<CatalogoDoConfronto.RegraDaSituacao> regra = switch (posicao.estado()) {
            case ATIVO -> posicao.principal().flatMap(ElementoDaPosicao::etapa)
                    .flatMap(e -> catalogo.daEtapa(chave.get(), e.id()));
            case ENCERRADO -> posicao.fim().flatMap(f -> catalogo.doFinal(chave.get(), f.id()));
            default -> Optional.empty();
        };
        return regra.flatMap(r -> aplicar(r, situacao));
    }

    private static Optional<CodigoDoAviso> aplicar(CatalogoDoConfronto.RegraDaSituacao regra, String situacao) {
        CodigoDoAviso conhecida = regra.conhecidas().get(situacao);
        if (conhecida != null) {
            return Optional.of(conhecida);
        }
        if (!regra.aceitas().isEmpty() && !regra.aceitas().contains(situacao)) {
            return Optional.of(CodigoDoAviso.SITUACAO_NAO_CONFERE);
        }
        return Optional.empty();
    }
}
