package br.gov.mt.sema.rastro.configuracao.application;

import java.util.ArrayList;
import java.util.List;

import br.gov.mt.sema.rastro.acesso.application.AdministrarUsuarios;
import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAdministrado;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import org.springframework.stereotype.Service;

/**
 * Aba "Usuários e perfis" (RF25): só Administrador, com um evento {@code USUARIO} por inclusão ou alteração real. As
 * regras do cadastro ficam em {@link AdministrarUsuarios}. O CPF/CNPJ nunca entra no evento.
 */
@Service
public class GerirUsuarios {

    private final AdministrarUsuarios usuarios;
    private final PoliticaDeAcesso politica;
    private final UsuarioAtual usuarioAtual;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    public GerirUsuarios(AdministrarUsuarios usuarios, PoliticaDeAcesso politica, UsuarioAtual usuarioAtual,
            RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.usuarios = usuarios;
        this.politica = politica;
        this.usuarioAtual = usuarioAtual;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    public List<UsuarioAdministrado> listar() {
        politica.exigirConfiguracoes();
        return usuarios.listar();
    }

    public UsuarioAdministrado incluir(String nome, String login, String cpfCnpj, String perfil) {
        politica.exigirConfiguracoes();
        UsuarioAdministrado novo = usuarios.incluir(nome, login, cpfCnpj, perfil);
        registrarEvento(novo, List.of(new AlteracaoDoEvento("Inclusão", null, "incluído"),
                new AlteracaoDoEvento("Nome", null, novo.nome()),
                new AlteracaoDoEvento("Login", null, novo.login()),
                new AlteracaoDoEvento("Perfil", null, novo.perfilRotulo()),
                new AlteracaoDoEvento("Situação", null, situacao(novo))));
        return novo;
    }

    public UsuarioAdministrado alterar(String login, String perfil, Boolean ativo) {
        politica.exigirConfiguracoes();
        AdministrarUsuarios.Alteracao alteracao = usuarios.alterar(login, perfil, ativo, usuarioAtual.obter().login());
        UsuarioAdministrado antes = alteracao.antes();
        UsuarioAdministrado depois = alteracao.depois();
        List<AlteracaoDoEvento> mudancas = new ArrayList<>();
        if (!antes.perfil().equals(depois.perfil())) {
            mudancas.add(new AlteracaoDoEvento("Perfil", antes.perfilRotulo(), depois.perfilRotulo()));
        }
        if (antes.ativo() != depois.ativo()) {
            mudancas.add(new AlteracaoDoEvento("Situação", situacao(antes), situacao(depois)));
        }
        if (!mudancas.isEmpty()) {
            registrarEvento(depois, mudancas);
        }
        return depois;
    }

    private void registrarEvento(UsuarioAdministrado alvo, List<AlteracaoDoEvento> mudancas) {
        registrar.registrar(contexto.novo(TipoDeEvento.USUARIO).detalhe(alvo.login()).alteracoes(mudancas).criar());
    }

    private static String situacao(UsuarioAdministrado usuario) {
        return usuario.ativo() ? "Ativo" : "Inativo";
    }
}
