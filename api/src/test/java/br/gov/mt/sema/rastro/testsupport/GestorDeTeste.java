package br.gov.mt.sema.rastro.testsupport;

import br.gov.mt.sema.rastro.acesso.application.CadastroDeUsuarios;
import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Cria o Gestor de teste na subida de cada contexto Spring dos testes (L1, bloco 5). O Gestor não é usuário padrão da
 * produção (é criado pelo Administrador em Configurações); aqui existe só para os testes que precisam desse perfil.
 */
@Component
public class GestorDeTeste {

    private final CadastroDeUsuarios cadastro;
    private final HashDeSenha hashes;

    public GestorDeTeste(CadastroDeUsuarios cadastro, HashDeSenha hashes) {
        this.cadastro = cadastro;
        this.hashes = hashes;
    }

    @PostConstruct
    void criar() {
        cadastro.salvar(new Usuario(UsuariosDeTeste.GESTOR, "Gestor", Perfil.OPERADOR_SIGILO, null, true,
                hashes.gerar(UsuariosDeTeste.SENHA_GESTOR)));
    }
}
