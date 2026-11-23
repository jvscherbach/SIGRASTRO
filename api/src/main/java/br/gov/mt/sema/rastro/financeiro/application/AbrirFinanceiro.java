package br.gov.mt.sema.rastro.financeiro.application;

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
import br.gov.mt.sema.rastro.financeiro.application.FinanceiroAberto.Estado;
import br.gov.mt.sema.rastro.financeiro.application.port.ParametrosDoFinanceiro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Abertura do histórico financeiro pelo usuário (S9, bloco 6): confere o acesso (404; RN07, 403) <b>antes</b> de qualquer
 * leitura, lê o histórico pelo {@link LerLancamentos}, aplica as regras de exibição do perfil e registra o evento
 * {@code FINANCEIRO}.
 *
 * <ul>
 *   <li><b>Falha:</b> banco do SIGA fora do ar, lento ou não configurado depois do acesso conferido vira
 *       {@link Estado#INDISPONIVEL} (200, sem 5xx). A falha do financeiro não afeta a tramitação nem os documentos.</li>
 *   <li><b>Diagnóstico e ids técnicos:</b> só o Administrador ({@link PoliticaDeAcesso#podeVerIdsTecnicosDoFluxo()}).</li>
 *   <li><b>Valores:</b> {@code RASTRO_MOSTRAR_VALORES} é lido a cada consulta e vale para todos os perfis.</li>
 *   <li><b>Auditoria:</b> um evento por requerimento devolvido, só com usuário e números (nunca valor, DAR nem CPF).
 *       Acesso negado não registra. {@code INDISPONIVEL} registra: a consulta aconteceu.</li>
 *   <li><b>Processo (1:N):</b> o acesso é conferido uma vez, pela busca por número de processo da consulta (filtro do
 *       interessado na própria consulta). Até {@value #MAXIMO_POR_PROCESSO} requerimentos por processo.</li>
 * </ul>
 */
@Service
public class AbrirFinanceiro {

    static final int MAXIMO_POR_PROCESSO = 100;
    private static final String DETALHE = "financeiro";
    private static final Logger log = LoggerFactory.getLogger(AbrirFinanceiro.class);

    private final VerificarAcessoAoRequerimento acesso;
    private final BuscarRequerimentos busca;
    private final LerLancamentos ler;
    private final PoliticaDeAcesso politica;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;
    private final ParametrosDoFinanceiro parametros;

    public AbrirFinanceiro(VerificarAcessoAoRequerimento acesso, BuscarRequerimentos busca, LerLancamentos ler,
            PoliticaDeAcesso politica, RegistrarEvento registrar, ContextoDoEvento contexto,
            ParametrosDoFinanceiro parametros) {
        this.acesso = acesso;
        this.busca = busca;
        this.ler = ler;
        this.politica = politica;
        this.registrar = registrar;
        this.contexto = contexto;
        this.parametros = parametros;
    }

    public FinanceiroAberto doRequerimento(long idRequerimento) {
        Requerimento requerimento = acesso.executar(idRequerimento);
        return abrir(requerimento.id(), requerimento.numeroRequerimento(), requerimento.numeroProcesso(),
                requerimento.cpfCnpjDoInteressado(), requerimento.identificacao());
    }

    /** @param numeroDoProcesso no formato {@code N/AAAA} */
    public List<FinanceiroAberto> doProcesso(String numeroDoProcesso) {
        Pagina<ResumoRequerimento> requerimentos = busca.executar(
                new PedidoDeBusca("PROCESSO", numeroDoProcesso, null, null, 0, MAXIMO_POR_PROCESSO));
        return requerimentos.itens().stream()
                .map(r -> abrir(r.id(), r.numeroRequerimento(), r.numeroProcesso(),
                        Optional.ofNullable(r.interessado()).map(Interessado::cpfCnpj).orElse(null), r.identificacao()))
                .toList();
    }

    private FinanceiroAberto abrir(long id, String numeroRequerimento, String numeroProcesso, String cpfCnpj,
            Identificacao identificacao) {
        boolean diagnostico = politica.podeVerIdsTecnicosDoFluxo();
        boolean mostrarValores = parametros.mostrarValores();
        FinanceiroAberto aberto;
        try {
            aberto = new FinanceiroAberto(id, numeroRequerimento, numeroProcesso, Estado.ATIVO, ler.historico(id),
                    mostrarValores, diagnostico);
        } catch (RuntimeException e) {
            // Só a classe, nunca a mensagem (pode trazer SQL ou dado): o financeiro degrada e o resto da tela segue.
            log.warn("Financeiro indisponível para o requerimento {} ({})", id, e.getClass().getSimpleName());
            aberto = new FinanceiroAberto(id, numeroRequerimento, numeroProcesso, Estado.INDISPONIVEL, null,
                    mostrarValores, diagnostico);
        }
        registrar.registrar(contexto.novo(TipoDeEvento.FINANCEIRO)
                .requerimento(id, numeroProcesso, numeroRequerimento)
                .interessado(cpfCnpj)
                .detalhe(DETALHE + " " + identificacao.rotulo() + " " + identificacao.numero())
                .criar());
        return aberto;
    }
}
