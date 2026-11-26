package br.gov.mt.sema.rastro.acesso.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.domain.CpfCnpjDoUsuario;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import org.springframework.stereotype.Service;

/**
 * Contrato do módulo acesso para a caixa do analista (S11, bloco 5): quais usuários Analista ativos têm vínculo com um
 * servidor, isto é, têm CPF cadastrado (o {@code ASSIGNEE_} do Camunda). O CPF só atravessa esta fronteira para virar a
 * chave opaca do responsável na caixa e nunca é guardado, logado ou devolvido por outra camada; {@link Vinculo#toString()}
 * não o mostra. Gestor, Administrador e Operador nunca entram.
 */
@Service
public class ConsultarAnalistas {

    /** O login do Analista e o identificador dele no Camunda (somente dígitos). */
    public record Vinculo(String login, String identificadorProtegido) {

        @Override
        public String toString() {
            return "Vinculo[" + login + ", protegido]";
        }
    }

    private final CadastroDeUsuarios cadastro;

    public ConsultarAnalistas(CadastroDeUsuarios cadastro) {
        this.cadastro = cadastro;
    }

    /** Analistas ativos com CPF cadastrado, como estão agora. */
    public List<Vinculo> vinculados() {
        return cadastro.listar().stream().map(ConsultarAnalistas::vinculo).flatMap(Optional::stream).toList();
    }

    /** O vínculo de um Analista ativo; vazio se o login não é de Analista ativo com CPF. */
    public Optional<Vinculo> doLogin(String login) {
        if (login == null) {
            return Optional.empty();
        }
        return cadastro.buscarPorLogin(login.strip().toLowerCase(Locale.ROOT)).flatMap(ConsultarAnalistas::vinculo);
    }

    private static Optional<Vinculo> vinculo(Usuario usuario) {
        if (usuario.perfil() != Perfil.ANALISTA || !usuario.ativo()) {
            return Optional.empty();
        }
        String digitos = CpfCnpjDoUsuario.somenteDigitos(usuario.cpfCnpj());
        return digitos.isEmpty() ? Optional.empty() : Optional.of(new Vinculo(usuario.login(), digitos));
    }
}
