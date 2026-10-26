package br.gov.mt.sema.vistas.documento.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import br.gov.mt.sema.vistas.acesso.application.ObterPermissoesDoUsuario;
import br.gov.mt.sema.vistas.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.Documento.Bloqueio;
import br.gov.mt.sema.vistas.shared.application.exception.DocumentoSigilosoException;
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
    private final ObterPermissoesDoUsuario permissoes;

    public SelecionarDocumentosParaDossie(VerificarAcessoAoRequerimento acesso, ListarDocumentos listagem,
            ObterPermissoesDoUsuario permissoes) {
        this.acesso = acesso;
        this.listagem = listagem;
        this.permissoes = permissoes;
    }

    public List<Documento> executar(long idRequerimento, List<ReferenciaDeDocumento> referencias) {
        acesso.executar(idRequerimento);
        if (referencias == null || referencias.isEmpty()) {
            throw new ParametroInvalidoException("documentos", "Selecione ao menos um documento.");
        }
        boolean podeVerSigilosos = permissoes.podeVerSigilosos();
        List<Documento> lista = listagem.listaDoRequerimento(idRequerimento);

        Set<Documento> escolhidos = new LinkedHashSet<>();
        for (ReferenciaDeDocumento referencia : referencias) {
            Documento documento = localizar(lista, referencia).orElseThrow(() -> new ParametroInvalidoException(
                    "documentos", "O documento " + descrever(referencia) + " não pertence ao requerimento."));
            if (documento.sigiloso() && !podeVerSigilosos) {
                throw new DocumentoSigilosoException();
            }
            Bloqueio bloqueio = documento.bloqueio(podeVerSigilosos);
            if (bloqueio == Bloqueio.INDISPONIVEL) {
                throw new ParametroInvalidoException("documentos",
                        "O documento " + descrever(referencia) + " está indisponível e não pode entrar no dossiê.");
            }
            if (bloqueio == Bloqueio.NAO_PDF) {
                throw new ParametroInvalidoException("documentos",
                        "O documento " + descrever(referencia) + " não é PDF; a conversão estará disponível em breve.");
            }
            escolhidos.add(documento);
        }
        List<Documento> ordenados = new ArrayList<>(escolhidos);
        ordenados.sort(Documento.ORDEM_CRONOLOGICA);
        return ordenados;
    }

    private static Optional<Documento> localizar(List<Documento> lista, ReferenciaDeDocumento referencia) {
        if (referencia == null || referencia.origem() == null || referencia.id() == null) {
            return Optional.empty();
        }
        return lista.stream()
                .filter(d -> d.origem().name().equals(referencia.origem()) && d.id().equals(referencia.id().trim()))
                .findFirst();
    }

    private static String descrever(ReferenciaDeDocumento referencia) {
        return referencia == null ? "informado" : referencia.origem() + "/" + referencia.id();
    }
}
