package br.gov.mt.sema.rastro.tramitacao.application;

import java.util.List;
import java.util.Map;
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
import br.gov.mt.sema.rastro.tramitacao.application.port.FinanceiroDoRequerimento;
import br.gov.mt.sema.rastro.tramitacao.application.port.FinanceiroDoRequerimento.TaxaVencida;
import br.gov.mt.sema.rastro.tramitacao.domain.AvisoDeTaxaVencida;
import br.gov.mt.sema.rastro.tramitacao.domain.CatalogoDoConfronto;
import br.gov.mt.sema.rastro.tramitacao.domain.PosicaoConfrontada;
import org.springframework.stereotype.Service;

/**
 * Abertura da posição pelo usuário (S7, bloco 6): confere o acesso (404; RN07, 403), lê a posição pelo
 * {@link ConsultarPosicaoAtual}, aplica as regras de exibição do perfil e registra o evento {@code TRAMITACAO}.
 *
 * <ul>
 *   <li><b>Responsável:</b> o nome do servidor segue o RN12 como na tarefa atual da consulta: o Operador vê só área e
 *       papel. Para Gestor e Administrador vale o parâmetro {@code RASTRO_MOSTRAR_NOME_RESPONSAVEL}.</li>
 *   <li><b>Ids técnicos:</b> só o Administrador ({@link PoliticaDeAcesso#podeVerIdsTecnicosDoFluxo()}).</li>
 *   <li><b>Auditoria:</b> um evento por requerimento devolvido, só com usuário e números (nunca o responsável nem
 *       CPF). Acesso negado não registra. Camunda indisponível registra: a consulta aconteceu.</li>
 *   <li><b>Processo (1:N):</b> o acesso é conferido uma vez, pela busca por número de processo da consulta (filtro do
 *       interessado na própria consulta; nenhum dele e o número existe: 403; não existe: 404). Até
 *       {@value #MAXIMO_POR_PROCESSO} requerimentos por processo.</li>
 *   <li><b>Taxa vencida (S9, bloco 5):</b> com o resumo financeiro "vencido" (porta {@link FinanceiroDoRequerimento}), a
 *       posição ganha o aviso {@code TAXA_VENCIDA} no fim da lista, depois do cache. Etapa, situação e os demais avisos
 *       não mudam; falha do financeiro = nenhum aviso a mais.</li>
 * </ul>
 */
@Service
public class AbrirPosicao {

    static final int MAXIMO_POR_PROCESSO = 100;

    private final VerificarAcessoAoRequerimento acesso;
    private final BuscarRequerimentos busca;
    private final ConsultarPosicaoAtual consultar;
    private final PoliticaDeAcesso politica;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    private final FinanceiroDoRequerimento financeiro;
    private final CatalogoDoConfronto confronto;

    public AbrirPosicao(VerificarAcessoAoRequerimento acesso, BuscarRequerimentos busca, ConsultarPosicaoAtual consultar,
            PoliticaDeAcesso politica, RegistrarEvento registrar, ContextoDoEvento contexto,
            FinanceiroDoRequerimento financeiro, CatalogoDoConfronto confronto) {
        this.acesso = acesso;
        this.busca = busca;
        this.consultar = consultar;
        this.politica = politica;
        this.registrar = registrar;
        this.contexto = contexto;
        this.financeiro = financeiro;
        this.confronto = confronto;
    }

    public PosicaoDoRequerimento doRequerimento(long idRequerimento) {
        Requerimento requerimento = acesso.executar(idRequerimento);
        Map<Long, TaxaVencida> vencidas = financeiro.taxasVencidas(List.of(requerimento.id()));
        return abrir(requerimento.id(), requerimento.numeroRequerimento(), requerimento.numeroProcesso(),
                requerimento.cpfCnpjDoInteressado(), requerimento.identificacao(),
                Optional.ofNullable(vencidas.get(requerimento.id())));
    }

    /**
     * @param numeroDoProcesso no formato {@code N/AAAA}. O financeiro (S9, bloco 5) é lido numa só consulta em lote para
     *                         todos os requerimentos do processo
     */
    public List<PosicaoDoRequerimento> doProcesso(String numeroDoProcesso) {
        Pagina<ResumoRequerimento> requerimentos = busca.executar(
                new PedidoDeBusca("PROCESSO", numeroDoProcesso, null, null, 0, MAXIMO_POR_PROCESSO));
        Map<Long, TaxaVencida> vencidas = financeiro.taxasVencidas(
                requerimentos.itens().stream().map(ResumoRequerimento::id).toList());
        return requerimentos.itens().stream()
                .map(r -> abrir(r.id(), r.numeroRequerimento(), r.numeroProcesso(),
                        Optional.ofNullable(r.interessado()).map(Interessado::cpfCnpj).orElse(null), r.identificacao(),
                        Optional.ofNullable(vencidas.get(r.id()))))
                .toList();
    }

    private PosicaoDoRequerimento abrir(long id, String numeroRequerimento, String numeroProcesso, String cpfCnpj,
            Identificacao identificacao, Optional<TaxaVencida> taxaVencida) {
        PosicaoConfrontada lida = consultar.ler(id);
        // S9, bloco 5: o aviso de taxa vencida só se acrescenta; posição e situação continuam as do fluxo e do SIGA.
        PosicaoConfrontada posicao = taxaVencida
                .map(t -> lida.comAviso(AvisoDeTaxaVencida.de(confronto, t.vencidaEm(), t.porData())))
                .orElse(lida);
        if (!politica.podeVerResponsavelTarefa()) {
            // RN12: sem nome do servidor; o limite de nomes já foi aplicado na leitura, então não reduz de novo.
            posicao = posicao.comPosicao(p -> p.comPontos(ponto -> ponto.conforme(false, Integer.MAX_VALUE)));
        }
        registrar.registrar(contexto.novo(TipoDeEvento.TRAMITACAO)
                .requerimento(id, numeroProcesso, numeroRequerimento)
                .interessado(cpfCnpj)
                .detalhe(identificacao.rotulo() + " " + identificacao.numero())
                .criar());
        Optional<SituacaoDoLicenciamento> situacao = posicao.situacaoDoLicenciamento()
                .map(Situacao::de)
                .map(s -> new SituacaoDoLicenciamento(s.codigo(), s.descricao()));
        return new PosicaoDoRequerimento(id, numeroRequerimento, numeroProcesso, posicao, situacao,
                politica.podeVerIdsTecnicosDoFluxo());
    }
}
