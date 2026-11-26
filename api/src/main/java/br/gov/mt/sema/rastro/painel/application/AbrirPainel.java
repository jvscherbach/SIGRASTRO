package br.gov.mt.sema.rastro.painel.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel.LinhaDeAnalista;
import br.gov.mt.sema.rastro.painel.domain.ListaPorResponsavel.RequerimentoDaLista;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * O painel do Gestor para o usuário (S10, bloco 5): confere o perfil (só Gestor e Administrador, 403
 * {@code PERFIL_SEM_PERMISSAO}) <b>antes</b> de qualquer coisa, devolve o instantâneo em memória e registra o evento
 * {@code PAINEL}. A resposta é imediata: nunca consulta o SIGA nem o Camunda (a leitura roda em segundo plano).
 *
 * <p>Auditoria: um evento por consulta e um por atualização manual aceita, com só o ano e o estado no detalhe (nunca nome
 * de servidor nem CPF). Acesso negado e atualização recusada por intervalo não registram.
 */
@Service
public class AbrirPainel {

    /** O painel como a API o entrega. {@code ano} é sempre o considerado (o do instantâneo ou, antes dele, o do parâmetro). */
    public record PainelAberto(InstantaneoDoPainel instantaneo, int ano, boolean mostrarNome,
            Optional<Instant> proximaAtualizacaoEm) {
    }

    private final PoliticaDeAcesso politica;
    private final ConsultarPainel consultar;
    private final SolicitarAtualizacao solicitar;
    private final ParametrosDoPainel parametros;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;
    private final GuardarInstantaneo guardar;
    private final Clock relogio;

    public AbrirPainel(PoliticaDeAcesso politica, ConsultarPainel consultar, SolicitarAtualizacao solicitar,
            ParametrosDoPainel parametros, RegistrarEvento registrar, ContextoDoEvento contexto,
            GuardarInstantaneo guardar, Clock relogio) {
        this.politica = politica;
        this.consultar = consultar;
        this.solicitar = solicitar;
        this.parametros = parametros;
        this.registrar = registrar;
        this.contexto = contexto;
        this.guardar = guardar;
        this.relogio = relogio;
    }

    public PainelAberto abrir() {
        politica.exigirPainel();
        ConsultarPainel.Visao visao = consultar.consultar();
        int ano = anoConsiderado(visao.instantaneo());
        registrar("consulta", ano, visao.instantaneo());
        return new PainelAberto(visao.instantaneo(), ano, visao.mostrarNome(), visao.proximaAtualizacaoEm());
    }

    /** O 403 sem ler nada: o controlador chama antes de validar filtros. */
    public void exigirPerfil() {
        politica.exigirPainel();
    }

    /** A lista de analistas (S11, bloco 9): o instantâneo e a lista no formato do parâmetro de nomes vigente (RN12). */
    public record AnalistasAbertos(InstantaneoDoPainel instantaneo, int ano, boolean mostrarNome,
            Optional<ListaPorResponsavel> lista) {
    }

    /** Os requerimentos de um analista: {@code total} é o da barra; {@code requerimentos} já traz os filtros. */
    public record RequerimentosAbertos(InstantaneoDoPainel instantaneo, int ano, LinhaDeAnalista linha,
            List<RequerimentoDaLista> requerimentos, List<String> etapas) {
    }

    /** Lista de analistas com o total de cada um (o da barra). Mesmo 403 do painel, antes de qualquer leitura. */
    public AnalistasAbertos analistas() {
        politica.exigirPainel();
        ConsultarPainel.Visao visao = consultar.consultar();
        int ano = anoConsiderado(visao.instantaneo());
        registrar("lista por analista", ano, visao.instantaneo());
        return new AnalistasAbertos(visao.instantaneo(), ano, visao.mostrarNome(),
                visao.instantaneo().lista(visao.mostrarNome()));
    }

    /**
     * Requerimentos de uma chave de analista, com os filtros (etapa pelo rótulo exato; busca por interessado ou número).
     * Chave desconhecida, ou de servidor com os nomes ocultos, é 404.
     */
    public RequerimentosAbertos requerimentosDoAnalista(String chave, Optional<String> etapa, String busca) {
        politica.exigirPainel();
        ConsultarPainel.Visao visao = consultar.consultar();
        int ano = anoConsiderado(visao.instantaneo());
        ListaPorResponsavel lista = visao.instantaneo().lista(visao.mostrarNome())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Analista não encontrado."));
        LinhaDeAnalista linha = lista.linha(chave)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Analista não encontrado."));
        registrar("lista de requerimentos por analista", ano, visao.instantaneo());
        return new RequerimentosAbertos(visao.instantaneo(), ano, linha, lista.requerimentos(chave, etapa, busca),
                lista.etapas());
    }

    public SolicitarAtualizacao.Resultado atualizar() {
        politica.exigirPainel();
        SolicitarAtualizacao.Resultado resultado = solicitar.solicitar();
        if (resultado.aceita()) {
            InstantaneoDoPainel atual = guardar.atual();
            registrar("atualizacao manual", anoConsiderado(atual), atual);
        }
        return resultado;
    }

    private int anoConsiderado(InstantaneoDoPainel instantaneo) {
        if (instantaneo.ano() > 0) {
            return instantaneo.ano();
        }
        try {
            return AnoDoPainel.doParametro(parametros.ano(), relogio).ano();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private void registrar(String acao, int ano, InstantaneoDoPainel instantaneo) {
        registrar.registrar(contexto.novo(TipoDeEvento.PAINEL)
                .detalhe("painel " + acao + " ano " + ano + " " + instantaneo.estado())
                .criar());
    }
}
