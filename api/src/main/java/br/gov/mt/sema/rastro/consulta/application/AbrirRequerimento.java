package br.gov.mt.sema.rastro.consulta.application;

import br.gov.mt.sema.rastro.auditoria.application.ContextoDoEvento;
import br.gov.mt.sema.rastro.auditoria.application.RegistrarEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.consulta.domain.Identificacao;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import org.springframework.stereotype.Service;

/**
 * Abertura do detalhe pelo usuário (evento {@code CONSULTA}). À parte de {@link ObterRequerimento} porque a criação do
 * dossiê também o usa, e aquilo não é uma consulta.
 */
@Service
public class AbrirRequerimento {

    private final ObterRequerimento obter;
    private final RegistrarEvento registrar;
    private final ContextoDoEvento contexto;

    public AbrirRequerimento(ObterRequerimento obter, RegistrarEvento registrar, ContextoDoEvento contexto) {
        this.obter = obter;
        this.registrar = registrar;
        this.contexto = contexto;
    }

    public DetalheDoRequerimento executar(long id) {
        DetalheDoRequerimento detalhe = obter.executar(id);
        Requerimento requerimento = detalhe.requerimento();
        Identificacao identificacao = requerimento.identificacao();
        registrar.registrar(contexto.novo(TipoDeEvento.CONSULTA)
                .requerimento(id, requerimento.numeroProcesso(), requerimento.numeroRequerimento())
                .interessado(requerimento.cpfCnpjDoInteressado())
                .detalhe(identificacao.rotulo() + " " + identificacao.numero())
                .criar());
        return detalhe;
    }
}
