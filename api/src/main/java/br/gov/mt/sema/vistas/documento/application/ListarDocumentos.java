package br.gov.mt.sema.vistas.documento.application;

import java.util.List;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.domain.CatalogoDeDocumentos;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.TipoArquivo;
import org.springframework.stereotype.Service;

/**
 * Lista os documentos do requerimento (RF06 a RF08, RF20). O requerimento é a chave (D31). O acesso (404 e RN07) é
 * conferido antes de ler os documentos; sigilosos aparecem para todos, mas só Gestor e Administrador os acessam.
 */
@Service
public class ListarDocumentos {

    private final VerificarAcessoAoRequerimento acesso;
    private final DocumentosDoRequerimento documentos;
    private final PoliticaDeAcesso politica;
    private final ParametrosDaConversao conversao;

    public ListarDocumentos(VerificarAcessoAoRequerimento acesso, DocumentosDoRequerimento documentos,
            PoliticaDeAcesso politica, ParametrosDaConversao conversao) {
        this.acesso = acesso;
        this.documentos = documentos;
        this.politica = politica;
        this.conversao = conversao;
    }

    public DocumentosListados executar(long idRequerimento) {
        acesso.executar(idRequerimento);
        boolean podeVerSigilosos = politica.podeVerDocumentoSigiloso();
        boolean converte = conversao.habilitada();
        return DocumentosListados.de(listaDoRequerimento(idRequerimento).stream()
                .map(d -> new DocumentoListado(d, politica.podeAcessarDocumento(d.sigiloso()),
                        d.bloqueio(podeVerSigilosos, converte), d.selecionavelPorPadrao(podeVerSigilosos, converte)))
                .toList());
    }

    /** Lista consolidada (RN05, RN11, RN03). Geometrias ficam fora até a seção própria da S4 (RN10). */
    List<Documento> listaDoRequerimento(long idRequerimento) {
        return CatalogoDeDocumentos.consolidar(documentos.listar(idRequerimento)).stream()
                .filter(d -> d.tipoArquivo() != TipoArquivo.GEOMETRIA)
                .toList();
    }
}
