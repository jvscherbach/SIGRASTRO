package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.consulta.application.BuscarRequerimentos;
import br.gov.mt.sema.rastro.consulta.application.PedidoDeBusca;
import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Identificacao;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.tramitacao.application.PosicaoDoRequerimento.SituacaoDoLicenciamento;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDeEtapas;
import br.gov.mt.sema.rastro.tramitacao.domain.ModeloDeFluxo;
import br.gov.mt.sema.rastro.tramitacao.domain.TramitacaoDoRequerimento;
import org.springframework.stereotype.Service;

/**
 * Abertura da tramitação completa pelo usuário (S8, bloco 6): confere o acesso (404; RN07, 403) <b>antes</b> de qualquer
 * leitura, lê a tramitação pelo {@link LerTramitacao} (cache e limites), aplica as regras de exibição do perfil e
 * registra o evento
 * {@code TRAMITACAO} (detalhe "histórico").
 *
 * <ul>
 *   <li><b>Responsável:</b> o nome do servidor segue o RN12 como na posição: o Operador vê só área e papel (em passagens
 *       e participantes). Para Gestor e Administrador vale {@code RASTRO_MOSTRAR_NOME_RESPONSAVEL}.</li>
 *   <li><b>Ids técnicos:</b> só o Administrador ({@link PoliticaDeAcesso#podeVerIdsTecnicosDoFluxo()}); quem monta o DTO
 *       decide o que entra por {@link TramitacaoAberta#idsTecnicos()}.</li>
 *   <li><b>Auditoria:</b> um evento por requerimento devolvido, só com usuário e números (nunca o responsável nem CPF).
 *       Acesso negado não registra. Camunda indisponível registra: a consulta aconteceu.</li>
 *   <li><b>Processo (1:N):</b> o acesso é conferido uma vez, pela busca por número de processo da consulta (filtro do
 *       interessado na própria consulta). Até {@value #MAXIMO_POR_PROCESSO} requerimentos por processo.</li>
 * </ul>
 *
 * <p>Cache, tempo total e limite de leituras simultâneas: {@link LerTramitacao} (bloco 7). O RN12 vale depois do cache.
 */
@Service
public class AbrirTramitacao {

    static final int MAXIMO_POR_PROCESSO = 100;
    private static final String DETALHE = "histórico";

    private final VerificarAcessoAoRequerimento acesso;
    private final BuscarRequerimentos busca;
    private final LerTramitacao ler;
    private final PoliticaDeAcesso politica;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;
    private final CatalogoDeEtapas catalogo;

    public AbrirTramitacao(VerificarAcessoAoRequerimento acesso, BuscarRequerimentos busca, LerTramitacao ler,
            PoliticaDeAcesso politica, RegistrarEvento registrar, ContextoDoEvento contexto, CatalogoDeEtapas catalogo) {
        this.catalogo = catalogo;
        this.acesso = acesso;
        this.busca = busca;
        this.ler = ler;
        this.politica = politica;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    public TramitacaoAberta doRequerimento(long idRequerimento) {
        Requerimento requerimento = acesso.executar(idRequerimento);
        return abrir(requerimento.id(), requerimento.numeroRequerimento(), requerimento.numeroProcesso(),
                requerimento.cpfCnpjDoInteressado(), requerimento.identificacao());
    }

    /** @param numeroDoProcesso no formato {@code N/AAAA} */
    public List<TramitacaoAberta> doProcesso(String numeroDoProcesso) {
        Pagina<ResumoRequerimento> requerimentos = busca.executar(
                new PedidoDeBusca("PROCESSO", numeroDoProcesso, null, null, 0, MAXIMO_POR_PROCESSO));
        return requerimentos.itens().stream()
                .map(r -> abrir(r.id(), r.numeroRequerimento(), r.numeroProcesso(),
                        Optional.ofNullable(r.interessado()).map(Interessado::cpfCnpj).orElse(null), r.identificacao()))
                .toList();
    }

    private TramitacaoAberta abrir(long id, String numeroRequerimento, String numeroProcesso, String cpfCnpj,
            Identificacao identificacao) {
        TramitacaoDoRequerimento tramitacao = ler.ler(id);
        if (!politica.podeVerResponsavelTarefa()) {
            // RN12: sem nome do servidor em nenhuma passagem nem participante.
            tramitacao = tramitacao.semNomes();
        }
        registrar.registrar(contexto.novo(TipoDeEvento.TRAMITACAO)
                .requerimento(id, numeroProcesso, numeroRequerimento)
                .interessado(cpfCnpj)
                .detalhe(DETALHE + " " + identificacao.rotulo() + " " + identificacao.numero())
                .criar());
        Optional<SituacaoDoLicenciamento> situacao = tramitacao.situacao()
                .map(Situacao::de)
                .map(s -> new SituacaoDoLicenciamento(s.codigo(), s.descricao()));
        Optional<String> rotuloDoModelo = tramitacao.modelo().flatMap(catalogo::modelo).map(ModeloDeFluxo::rotulo);
        return new TramitacaoAberta(id, numeroRequerimento, numeroProcesso, tramitacao, rotuloDoModelo, situacao,
                politica.podeVerIdsTecnicosDoFluxo());
    }
}
