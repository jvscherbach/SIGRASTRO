package br.gov.mt.sema.rastro.acesso.application;

import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Troca da própria senha (L1). Confere a senha atual sob o mesmo limite de tentativas do login, aplica a política, grava o
 * hash novo e encerra as outras sessões do usuário (a atual continua). Nenhuma mensagem repete senha alguma.
 */
@Service
public class TrocarSenha {

    private final CadastroDeUsuarios cadastro;
    private final HashDeSenha hashes;
    private final LimiteDeTentativas limite;
    private final RepositorioSessoes sessoes;
    private final ApplicationEventPublisher eventos;

    public TrocarSenha(CadastroDeUsuarios cadastro, HashDeSenha hashes, LimiteDeTentativas limite,
            RepositorioSessoes sessoes, ApplicationEventPublisher eventos) {
        this.cadastro = cadastro;
        this.hashes = hashes;
        this.limite = limite;
        this.sessoes = sessoes;
        this.eventos = eventos;
    }

    /**
     * @param login quem está autenticado
     * @param tokenAtual sessão que continua aberta
     * @param origem IP de origem (para o limite de tentativas)
     */
    public void executar(String login, String tokenAtual, String senhaAtual, String novaSenha, String confirmacao,
            String origem) {
        limite.exigirLiberado(login, origem);
        Usuario atual = cadastro.buscarPorLogin(login).filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutenticadoException("Sessão ausente, inválida ou expirada."));
        String guardado = atual.senhaHash() != null ? atual.senhaHash() : hashes.hashFalso();
        if (!hashes.confere(senhaAtual, guardado) || atual.senhaHash() == null) {
            limite.registrarFalha(login, origem);
            throw new ParametroInvalidoException("senhaAtual", "A senha atual não confere.");
        }
        limite.registrarAcerto(login, origem);
        if (novaSenha == null || !novaSenha.equals(confirmacao)) {
            throw new ParametroInvalidoException("confirmacao", "A confirmação não confere com a nova senha.");
        }
        PoliticaDeSenha.validar(atual.login(), novaSenha);
        if (novaSenha.equals(senhaAtual)) {
            throw new ParametroInvalidoException("novaSenha", "A nova senha deve ser diferente da atual.");
        }
        cadastro.salvar(atual.comSenhaHash(hashes.gerar(novaSenha)));
        sessoes.encerrarDoUsuario(atual.login(), tokenAtual);
        try {
            eventos.publishEvent(new SenhaTrocada(atual));
        } catch (RuntimeException e) {
            // a troca já aconteceu: falha de auditoria não a desfaz
        }
    }
}
