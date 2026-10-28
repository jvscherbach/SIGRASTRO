package br.gov.mt.sema.vistas.documento.application.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Documentos pessoais do SCORP por pessoa (S4), implementada por integration.scorp: {@code GET
 * {SCORP_API_URL}/documentos/pessoa-licenciamento/{idCorporativo}} (docs/05). Nunca lança por falha do SCORP: quem
 * falhou (fora do ar, 401/403/404, tempo limite, resposta inválida) vem em {@link Consulta#falharam()}. A chave só é
 * lida pelo domínio e pelo adapter de arquivos (RNF09).
 */
public interface DocumentosPessoais {

    /** {@code false} sem {@code SCORP_API_URL} ou com a origem desligada: nenhuma chamada e nenhum aviso. */
    boolean habilitado();

    /** Uma chamada por pessoa, em paralelo limitado ({@code SCORP_CHAMADAS_SIMULTANEAS}). */
    Consulta consultar(Set<String> idsCorporativos);

    record Consulta(Map<String, List<DocumentoPessoal>> documentos, Set<String> falharam) {

        public Consulta {
            documentos = Map.copyOf(documentos);
            falharam = Set.copyOf(falharam);
        }
    }

    /** Item como o SCORP o devolve. A chave fica fora do {@code toString}. */
    record DocumentoPessoal(String chave, String nomeArquivo, String descricao, LocalDateTime dataInicio,
            LocalDateTime dataFim) {

        @Override
        public String toString() {
            return "DocumentoPessoal[nomeArquivo=" + nomeArquivo + ", dataInicio=" + dataInicio + ", dataFim=" + dataFim + "]";
        }
    }
}
