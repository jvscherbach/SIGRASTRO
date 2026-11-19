package br.gov.mt.sema.rastro.documento.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDosDocumentosPessoais;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.PapelDaPessoa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Documentos pessoais do SCORP do requerimento (S4, P21): do interessado, dos representantes legais e dos responsáveis
 * técnicos. Cada pessoa é consultada uma vez, mesmo em mais de um papel (vale o primeiro de {@link PapelDaPessoa}).
 * Sigilo = {@code SCORP_SIGILO_PADRAO} (P30). Falha do SCORP nunca derruba a lista: vira aviso
 * ({@code SCORP_INDISPONIVEL} se nenhuma pessoa respondeu, {@code SCORP_PARCIAL} se parte). Sem SCORP configurado,
 * nem as pessoas são lidas e não há aviso. Quem chama já conferiu o acesso ao requerimento (RN07).
 */
@Service
public class DocumentosPessoaisDoRequerimento {

    private static final Logger log = LoggerFactory.getLogger(DocumentosPessoaisDoRequerimento.class);

    private final PessoasDoRequerimento pessoas;
    private final DocumentosPessoais scorp;
    private final ParametrosDosDocumentosPessoais parametros;

    public DocumentosPessoaisDoRequerimento(PessoasDoRequerimento pessoas, DocumentosPessoais scorp,
            ParametrosDosDocumentosPessoais parametros) {
        this.pessoas = pessoas;
        this.scorp = scorp;
        this.parametros = parametros;
    }

    public record Resultado(List<Documento> documentos, List<AvisoDaLista> avisos) {

        static final Resultado VAZIO = new Resultado(List.of(), List.of());
    }

    Resultado listar(long idRequerimento) {
        if (!scorp.habilitado()) {
            return Resultado.VAZIO;
        }
        Map<String, PapelDaPessoa> papelPorPessoa = new LinkedHashMap<>();
        for (PessoasDoRequerimento.Pessoa pessoa : pessoas.listarPessoas(idRequerimento)) {
            if (pessoa.idCorporativo() == null || pessoa.idCorporativo().isBlank() || pessoa.papel() == null) {
                continue;
            }
            papelPorPessoa.merge(pessoa.idCorporativo().trim(), pessoa.papel(),
                    (atual, outro) -> atual.ordinal() <= outro.ordinal() ? atual : outro);
        }
        if (papelPorPessoa.isEmpty()) {
            return Resultado.VAZIO;
        }
        DocumentosPessoais.Consulta consulta = scorp.consultar(papelPorPessoa.keySet());
        boolean sigiloso = parametros.sigiloPadrao();
        List<Documento> documentos = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        papelPorPessoa.forEach((idCorporativo, papel) -> {
            for (DocumentosPessoais.DocumentoPessoal item : consulta.documentos().getOrDefault(idCorporativo, List.of())) {
                Documento documento = Documento.pessoal(idCorporativo, papel, idRequerimento, item.chave(),
                        item.descricao(), item.nomeArquivo(), item.dataInicio(), item.dataFim(), sigiloso);
                // Linha repetida pelo SCORP (mesmo id): um item só; chaves iguais entre pessoas, a RN05 resolve.
                if (ids.add(documento.id())) {
                    documentos.add(documento);
                }
            }
        });
        int falharam = (int) papelPorPessoa.keySet().stream().filter(consulta.falharam()::contains).count();
        if (falharam == 0) {
            return new Resultado(documentos, List.of());
        }
        log.warn("SCORP: {} de {} pessoa(s) do requerimento {} sem resposta; a lista segue sem elas", falharam,
                papelPorPessoa.size(), idRequerimento);
        return new Resultado(documentos, List.of(falharam == papelPorPessoa.size()
                ? AvisoDaLista.scorpIndisponivel() : AvisoDaLista.scorpParcial()));
    }
}
