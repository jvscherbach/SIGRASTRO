package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * O SQL do financeiro (S9, bloco 2), sem banco: só {@code SELECT}, só as colunas da lista fechada (docs/09), nenhuma das
 * proibidas (nem em comentário), parâmetros nomeados, limite por requerimento e {@code FETCH FIRST}. A prova com o SQL
 * realmente enviado ao Oracle está em {@link LancamentosDoSigaJdbcTest}.
 */
class LancamentosSqlTest {

    private static final String SQL = SigaSql.carregar("financeiro/lancamentos");
    private static final List<String> PERMITIDAS = List.of("ID", "ID_REQUERIMENTO", "STATUS", "TAXA_ISENTA",
            "STATUS_PROCESSADO", "NUM_DAR", "DATA_EMISSAO", "DATA_VENCIMENTO", "DATA_PAGAMENTO", "DATA_FIM", "VALOR_REAL",
            "VALOR_DESCONTO", "QTDE_TOTAL_UPF", "PASSOU_POR_REAPROV_TAXA_CORREC");

    @Test
    void nunca_menciona_as_colunas_proibidas() {
        String maiusculo = SQL.toUpperCase(Locale.ROOT);
        assertThat(maiusculo).doesNotContain("CHAVE_GED").doesNotContain("MENSAGEM").doesNotContain("ID_TITULACAO");
        assertThat(maiusculo).doesNotContainPattern("SELECT\\s+\\*").doesNotContainPattern("\\.\\s*\\*");
    }

    @Test
    void le_da_tabela_so_colunas_permitidas() {
        Matcher coluna = Pattern.compile("\\bl\\.([A-Z_]+)").matcher(semComentarios());
        int lidas = 0;
        while (coluna.find()) {
            assertThat(PERMITIDAS).as("coluna lida de LANCAMENTO_TAXA").contains(coluna.group(1));
            lidas++;
        }
        assertThat(lidas).isGreaterThanOrEqualTo(PERMITIDAS.size());
    }

    @Test
    void somente_leitura_parametrizada_e_com_limite() {
        String codigo = semComentarios().toUpperCase(Locale.ROOT);
        assertThat(codigo.strip()).startsWith("SELECT");
        assertThat(codigo).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|GRANT|TRUNCATE)\\b");
        assertThat(codigo).contains("SIGA2_LICENCIAMENTO.LANCAMENTO_TAXA");
        assertThat(semComentarios()).contains("IN (:idsRequerimento)").contains(":limiteMaisUm")
                .contains("FETCH  FIRST :maximoDeLinhas ROWS ONLY");
        assertThat(codigo).doesNotContain("'").doesNotContain("||");
    }

    private static String semComentarios() {
        return SQL.lines().filter(l -> !l.strip().startsWith("--")).reduce("", (a, b) -> a + "\n" + b);
    }
}
