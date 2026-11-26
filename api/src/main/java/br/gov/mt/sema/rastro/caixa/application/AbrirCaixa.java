package br.gov.mt.sema.rastro.caixa.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.caixa.domain.ChaveDoResponsavel;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.FiltroDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.ResumoDaCaixa;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import org.springframework.stereotype.Service;

/**
 * A caixa do analista para o usuário da requisição (S11, bloco 6). Confere o perfil ({@code verCaixa}; 403
 * {@code PERFIL_SEM_PERMISSAO}) <b>antes</b> de qualquer leitura e descobre a chave da <b>própria</b> sessão: a identidade
 * nunca vem de parâmetro, então não há como pedir a caixa de outro usuário. Resposta imediata, só do instantâneo em memória.
 *
 * <p>Auditoria ({@code CAIXA}): abrir a caixa e abrir a lista de mudanças, cada um com só a quantidade (e o estado na
 * caixa); nunca CPF, chave do analista nem a lista de processos. Acesso negado e atualização manual não registram.
 */
@Service
public class AbrirCaixa {

    /** A caixa como a API a entrega: a visão e o rótulo das etapas que a tela mostra (do catálogo, nunca inventado). */
    public record CaixaAberta(CaixaDoAnalista.Visao visao, Map<String, String> rotulosDeEtapa) {
    }

    /** A lista de mudanças: o que chegou desde o último visto. {@code semVinculo}: usuário sem CPF cadastrado. */
    public record AvisosAbertos(boolean semVinculo, List<AvisosDaCaixa.Aviso> avisos, Map<String, String> rotulosDeEtapa) {
    }

    private final PoliticaDeAcesso politica;
    private final UsuarioAtual usuarioAtual;
    private final ChavesDosAnalistas chaves;
    private final CaixaDoAnalista caixa;
    private final AvisosDaCaixa avisos;
    private final AtualizarCaixa atualizar;
    private final SolicitarAtualizacaoDaCaixa solicitar;
    private final CatalogoDeEtapas catalogo;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    public AbrirCaixa(PoliticaDeAcesso politica, UsuarioAtual usuarioAtual, ChavesDosAnalistas chaves,
            CaixaDoAnalista caixa, AvisosDaCaixa avisos, AtualizarCaixa atualizar,
            SolicitarAtualizacaoDaCaixa solicitar, CatalogoDeEtapas catalogo, RegistrarEvento registrar,
            ContextoDoEvento contexto) {
        this.politica = politica;
        this.usuarioAtual = usuarioAtual;
        this.chaves = chaves;
        this.caixa = caixa;
        this.avisos = avisos;
        this.atualizar = atualizar;
        this.solicitar = solicitar;
        this.catalogo = catalogo;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    /** 403 {@code PERFIL_SEM_PERMISSAO} se o usuário da requisição não tem caixa; antes de validar filtros ou ler qualquer coisa. */
    public void exigirPerfil() {
        politica.exigirCaixa();
    }

    public CaixaAberta abrir(Optional<String> etapa, String busca, FiltroDaCaixa.Ordem ordem) {
        politica.exigirCaixa();
        CaixaDoAnalista.Visao visao = caixa.abrir(chaveDaSessao(), etapa, busca, ordem);
        registrar("consulta total " + visao.resumo().total() + " " + visao.estado());
        return new CaixaAberta(visao, rotulos());
    }

    public AvisosAbertos abrirAvisos() {
        politica.exigirCaixa();
        atualizar.iniciarSeNuncaLeu();
        Optional<ChaveDoResponsavel> chave = chaveDaSessao();
        List<AvisosDaCaixa.Aviso> lista = chave.map(avisos::avisos).orElse(List.of());
        registrar("avisos " + lista.size());
        return new AvisosAbertos(chave.isEmpty(), lista, rotulos());
    }

    /** Grava o visto do conjunto que esta aba recebeu (o que chegou depois continua avisando). */
    public void marcarVisto(Collection<Long> idsVistos) {
        politica.exigirCaixa();
        chaveDaSessao().ifPresent(chave -> avisos.marcarVisto(chave, idsVistos));
    }

    public SolicitarAtualizacaoDaCaixa.Resultado atualizar() {
        politica.exigirCaixa();
        return solicitar.solicitar();
    }

    private Optional<ChaveDoResponsavel> chaveDaSessao() {
        return chaves.chaveDoLogin(usuarioAtual.obter().login());
    }

    private Map<String, String> rotulos() {
        Map<String, String> rotulos = new LinkedHashMap<>();
        for (String id : DefinicaoDaPosse.ETAPAS_DE_POSSE) {
            rotulos.put(id, ResumoDaCaixa.rotulo(id, catalogo));
        }
        rotulos.put(DefinicaoDaPosse.ETAPA_CORRECAO, ResumoDaCaixa.rotulo(DefinicaoDaPosse.ETAPA_CORRECAO, catalogo));
        return rotulos;
    }

    private void registrar(String detalhe) {
        registrar.registrar(contexto.novo(TipoDeEvento.CAIXA).detalhe("caixa " + detalhe).criar());
    }
}

