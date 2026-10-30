package br.gov.mt.sema.vistas.documento.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.vistas.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.Documento.Bloqueio;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;
import org.springframework.stereotype.Service;

/**
 * Contrato do módulo documento para o dossiê: valida a seleção do cliente contra a lista que o servidor relê (o
 * cliente nunca é confiável) e devolve os documentos em ordem cronológica, já com a chave do arquivo resolvida. A
 * leitura do SIGA termina antes de este método retornar, então nenhuma conexão fica aberta durante o download.
 */
@Service
public class SelecionarDocumentosParaDossie {

    private final VerificarAcessoAoRequerimento acesso;
    private final ListarDocumentos listagem;
    private final PoliticaDeAcesso politica;
    private final ParametrosDaConversao conversao;

    public SelecionarDocumentosParaDossie(VerificarAcessoAoRequerimento acesso, ListarDocumentos listagem,
            PoliticaDeAcesso politica, ParametrosDaConversao conversao) {
        this.acesso = acesso;
        this.listagem = listagem;
        this.politica = politica;
        this.conversao = conversao;
    }

    public List<Documento> executar(long idRequerimento, List<ReferenciaDeDocumento> referencias) {
        return executarComInventario(idRequerimento, referencias).escolhidos();
    }

    /** Como {@link #executar}, mais o que ficou fora da seleção e o motivo (S2.2, seção "Documentos não incluídos"). */
    public SelecaoParaDossie executarComInventario(long idRequerimento, List<ReferenciaDeDocumento> referencias) {
        acesso.executar(idRequerimento);
        if (referencias == null || referencias.isEmpty()) {
            throw new ParametroInvalidoException("documentos", "Selecione ao menos um documento.");
        }
        boolean podeVerSigilosos = politica.podeVerDocumentoSigiloso();
        boolean converte = conversao.habilitada();
        ListarDocumentos.ListaDoRequerimento consolidada = listagem.listaComAvisos(idRequerimento);
        List<Documento> lista = consolidada.documentos();

        Set<Documento> escolhidos = new LinkedHashSet<>();
        for (ReferenciaDeDocumento referencia : referencias) {
            Documento documento = localizar(lista, referencia).orElseThrow(() -> new ParametroInvalidoException(
                    "documentos", foraDaLista(referencia, consolidada)));
            politica.exigirAcessoAoDocumento(documento.sigiloso());
            Bloqueio bloqueio = documento.bloqueio(podeVerSigilosos, converte);
            if (bloqueio == Bloqueio.INDISPONIVEL) {
                throw new ParametroInvalidoException("documentos",
                        "O documento " + descrever(referencia) + " está indisponível e não pode entrar no dossiê.");
            }
            if (bloqueio == Bloqueio.NAO_PDF) {
                throw new ParametroInvalidoException("documentos",
                        "O documento " + descrever(referencia) + " não é PDF e a conversão está indisponível.");
            }
            escolhidos.add(documento);
        }
        List<Documento> ordenados = new ArrayList<>(escolhidos);
        ordenados.sort(Documento.ORDEM_CRONOLOGICA);
        List<SelecaoParaDossie.NaoSelecionado> fora = new ArrayList<>();
        for (Documento documento : lista) {
            if (!escolhidos.contains(documento)) {
                boolean acessivel = politica.podeAcessarDocumento(documento.sigiloso());
                fora.add(new SelecaoParaDossie.NaoSelecionado(documento,
                        PoliticaDeAcesso.mascararTitulo(acessivel, documento.titulo()),
                        motivo(documento.bloqueio(podeVerSigilosos, converte))));
            }
        }
        return new SelecaoParaDossie(ordenados, fora, lista.size());
    }

    private static SelecaoParaDossie.Motivo motivo(Bloqueio bloqueio) {
        if (bloqueio == null) {
            return SelecaoParaDossie.Motivo.NAO_SELECIONADO;
        }
        return switch (bloqueio) {
            case INDISPONIVEL -> SelecaoParaDossie.Motivo.INDISPONIVEL;
            case NAO_PDF -> SelecaoParaDossie.Motivo.NAO_PDF;
            case SIGILOSO -> SelecaoParaDossie.Motivo.SIGILOSO;
        };
    }

    private static Optional<Documento> localizar(List<Documento> lista, ReferenciaDeDocumento referencia) {
        if (referencia == null || referencia.origem() == null || referencia.id() == null) {
            return Optional.empty();
        }
        return lista.stream()
                .filter(d -> d.origem().name().equals(referencia.origem()) && d.id().equals(referencia.id().trim()))
                .findFirst();
    }

    /** Documento pessoal com o SCORP fora do ar não é "de outro requerimento": diz o motivo real (S4). */
    private static String foraDaLista(ReferenciaDeDocumento referencia, ListarDocumentos.ListaDoRequerimento lista) {
        if (referencia != null && OrigemDocumento.SCORP.name().equals(referencia.origem()) && !lista.avisos().isEmpty()) {
            return "O documento pessoal " + descrever(referencia) + " não pôde ser conferido: o SCORP não respondeu agora. "
                    + "Tente de novo ou gere o dossiê sem ele.";
        }
        return "O documento " + descrever(referencia) + " não pertence ao requerimento.";
    }

    private static String descrever(ReferenciaDeDocumento referencia) {
        return referencia == null ? "informado" : referencia.origem() + "/" + referencia.id();
    }
}
