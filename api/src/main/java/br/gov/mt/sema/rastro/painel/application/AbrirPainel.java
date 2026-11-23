package br.gov.mt.sema.rastro.painel.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.painel.domain.AnoDoPainel;
import br.gov.mt.sema.rastro.painel.domain.InstantaneoDoPainel;
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
