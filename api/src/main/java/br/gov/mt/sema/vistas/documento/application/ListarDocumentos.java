package br.gov.mt.sema.vistas.documento.application;

import java.util.ArrayList;
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
 * Geometrias saem em lista própria (RN10, S4): fora da seleção e do dossiê; para o Operador, a sigilosa não aparece e
 * só entra na contagem de omitidas. Os documentos pessoais do SCORP (S4) entram na mesma lista, depois das seis
 * origens do SIGA; falha do SCORP não impede a listagem e vira aviso.
 */
@Service
public class ListarDocumentos {

    private final VerificarAcessoAoRequerimento acesso;
    private final DocumentosDoRequerimento documentos;
    private final PoliticaDeAcesso politica;
    private final ParametrosDaConversao conversao;
    private final DocumentosPessoaisDoRequerimento pessoais;

    public ListarDocumentos(VerificarAcessoAoRequerimento acesso, DocumentosDoRequerimento documentos,
            PoliticaDeAcesso politica, ParametrosDaConversao conversao, DocumentosPessoaisDoRequerimento pessoais) {
        this.acesso = acesso;
        this.documentos = documentos;
        this.politica = politica;
        this.conversao = conversao;
        this.pessoais = pessoais;
    }

    public DocumentosListados executar(long idRequerimento) {
        acesso.executar(idRequerimento);
        boolean podeVerSigilosos = politica.podeVerDocumentoSigiloso();
        boolean converte = conversao.habilitada();
        ListaDoRequerimento lista = consolidada(idRequerimento);
        List<Documento> consolidada = lista.documentos();
        List<DocumentoListado> itens = semGeometrias(consolidada).stream()
                .map(d -> new DocumentoListado(d, politica.podeAcessarDocumento(d.sigiloso()),
                        d.bloqueio(podeVerSigilosos, converte), d.selecionavelPorPadrao(podeVerSigilosos, converte)))
                .toList();
        List<DocumentoListado> geometrias = new ArrayList<>();
        int omitidas = 0;
        for (Documento geometria : soGeometrias(consolidada)) {
            if (politica.podeAcessarDocumento(geometria.sigiloso())) {
                geometrias.add(new DocumentoListado(geometria, true, null, false));
            } else {
                omitidas++;
            }
        }
        return DocumentosListados.de(itens, geometrias, omitidas, lista.avisos());
    }

    /** Lista consolidada (RN05, RN11, RN03), sem as geometrias (RN10): é a base da seleção e do dossiê. */
    List<Documento> listaDoRequerimento(long idRequerimento) {
        return listaComAvisos(idRequerimento).documentos();
    }

    /** Como {@link #listaDoRequerimento}, com os avisos do SCORP (para explicar um documento pessoal que sumiu). */
    ListaDoRequerimento listaComAvisos(long idRequerimento) {
        ListaDoRequerimento lista = consolidada(idRequerimento);
        return new ListaDoRequerimento(semGeometrias(lista.documentos()), lista.avisos());
    }

    /** Só as geometrias da lista consolidada, em ordem cronológica, sem filtro de acesso. */
    List<Documento> geometriasDoRequerimento(long idRequerimento) {
        return soGeometrias(consolidada(idRequerimento).documentos());
    }

    /** SIGA primeiro (falha dele derruba a lista); depois o SCORP, que nunca derruba. Deduplicação entre os dois (RN05). */
    private ListaDoRequerimento consolidada(long idRequerimento) {
        List<Documento> brutos = new ArrayList<>(documentos.listar(idRequerimento));
        DocumentosPessoaisDoRequerimento.Resultado doScorp = pessoais.listar(idRequerimento);
        brutos.addAll(doScorp.documentos());
        return new ListaDoRequerimento(CatalogoDeDocumentos.consolidar(brutos), doScorp.avisos());
    }

    record ListaDoRequerimento(List<Documento> documentos, List<AvisoDaLista> avisos) {
    }

    private static List<Documento> semGeometrias(List<Documento> lista) {
        return lista.stream().filter(d -> d.tipoArquivo() != TipoArquivo.GEOMETRIA).toList();
    }

    private static List<Documento> soGeometrias(List<Documento> lista) {
        return lista.stream().filter(d -> d.tipoArquivo() == TipoArquivo.GEOMETRIA).toList();
    }
}
