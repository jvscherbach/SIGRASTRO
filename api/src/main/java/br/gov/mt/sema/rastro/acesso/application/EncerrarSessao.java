package br.gov.mt.sema.rastro.acesso.application;

import org.springframework.stereotype.Service;

@Service
public class EncerrarSessao {

    private final RepositorioSessoes sessoes;

    public EncerrarSessao(RepositorioSessoes sessoes) {
        this.sessoes = sessoes;
    }

    public void executar(String token) {
        sessoes.encerrar(token);
    }
}
