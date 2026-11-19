package br.gov.mt.sema.rastro.integration.scorp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.PessoasDoRequerimento.Pessoa;
import br.gov.mt.sema.rastro.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.rastro.documento.domain.TipoArquivo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Teste de fumaça do SCORP REAL (S4), para o responsável executar na homologação. Nunca roda no verify normal: só
 * com SCORP_SMOKE=true. Lê as pessoas do requerimento SCORP_SMOKE_REQUERIMENTO no SIGA real (somente leitura) e
 * consulta o SCORP com a mesma configuração da aplicação (SCORP_API_URL e o token do SCORP ou da API de arquivos).
 *
 * <p>Imprime só contagens: pessoas por papel, quantas responderam, documentos por situação e por tipo de arquivo.
 * Nunca imprime identificador da pessoa, nome, chave, nome do arquivo nem o token.
 *
 * <pre>
 * SCORP_SMOKE=true SCORP_SMOKE_REQUERIMENTO=<ID_DE_TESTE> SIGA_DB_URL=... SIGA_DB_USER=... SIGA_DB_PASSWORD=...
 * SCORP_API_URL=... LICENCIAMENTO_API_TOKEN_FILE=... ./mvnw -Dtest=ScorpFumacaTest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "SCORP_SMOKE", matches = "true")
@SpringBootTest(properties = "rastro.agendamento.enabled=false")
class ScorpFumacaTest {

    @Autowired
    PessoasDoRequerimento pessoas;

    @Autowired
    DocumentosPessoais scorp;

    @Test
    void contagensDasPessoasEDosDocumentosPessoaisDeUmRequerimentoReal() {
        String id = System.getenv("SCORP_SMOKE_REQUERIMENTO");
        assertThat(id).as("informe SCORP_SMOKE_REQUERIMENTO (id numérico)").matches("[0-9]+");
        assertThat(scorp.habilitado()).as("SCORP_API_URL vazia: nada a testar").isTrue();

        List<Pessoa> lidas = pessoas.listarPessoas(Long.parseLong(id));
        Map<String, Integer> porPapel = new TreeMap<>();
        lidas.forEach(p -> porPapel.merge(p.papel().name(), 1, Integer::sum));
        Set<String> distintas = new LinkedHashSet<>();
        lidas.forEach(p -> distintas.add(p.idCorporativo()));
        System.out.println("[scorp-fumaca] pessoas por papel: " + porPapel + "; distintas: " + distintas.size());

        DocumentosPessoais.Consulta consulta = scorp.consultar(distintas);
        System.out.println("[scorp-fumaca] responderam: " + consulta.documentos().size() + "; falharam: "
                + consulta.falharam().size() + " (todas falhando com HTTP 401/403 no log: autenticação, P06)");

        Map<String, Integer> porSituacao = new TreeMap<>();
        Map<String, Integer> porTipo = new TreeMap<>();
        int total = 0;
        for (List<DocumentosPessoais.DocumentoPessoal> itens : consulta.documentos().values()) {
            for (DocumentosPessoais.DocumentoPessoal item : itens) {
                total++;
                porSituacao.merge(SituacaoDocumento.calcular(item.chave(), item.dataFim()).name(), 1, Integer::sum);
                String extensao = TipoArquivo.extensaoDe(item.nomeArquivo());
                porTipo.merge(extensao.isEmpty() ? "(sem extensão)" : extensao, 1, Integer::sum);
            }
        }
        System.out.println("[scorp-fumaca] documentos: " + total + "; por situação: " + porSituacao
                + "; por extensão: " + porTipo);
    }
}
