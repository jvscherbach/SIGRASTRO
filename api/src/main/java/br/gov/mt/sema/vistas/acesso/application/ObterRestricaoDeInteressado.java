package br.gov.mt.sema.vistas.acesso.application;

import br.gov.mt.sema.vistas.acesso.domain.RestricaoDeInteressado;
import org.springframework.stereotype.Service;

/** Contrato do módulo acesso para aplicar o vínculo com o interessado (RN07) ao usuário da requisição. */
@Service
public class ObterRestricaoDeInteressado {

    private final UsuarioAtual usuarioAtual;

    public ObterRestricaoDeInteressado(UsuarioAtual usuarioAtual) {
        this.usuarioAtual = usuarioAtual;
    }

    public RestricaoDeInteressado paraUsuarioAtual() {
        return RestricaoDeInteressado.de(usuarioAtual.obter());
    }
}
