package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * O SQL do painel (S10, bloco 2), sem banco: só {@code SELECT}/{@code WITH}, parâmetros nomeados, {@code FETCH FIRST},
 * recorte do ano por intervalo (sem função na coluna) e a coluna do Camunda que guarda o CPF citada só na resolução do
 * nome, nunca na saída. A prova com o SQL realmente enviado ao Oracle está em {@link PainelDoSigaJdbcTest}.
 */
class PainelSqlTest {

    private static final String SERIE = SigaSql.carregar("painel/protocolados-e-concluidos-por-mes");
    private static final String BASE = SigaSql.carregar("painel/requerimentos-da-base");
    private static final String TAREFAS = SigaSql.carregar("painel/tarefas-ativas-em-lote");

    static String semComentarios(String sql) {
        return sql.lines().map(l -> l.replaceAll("--.*$", "")).filter(l -> !l.isBlank())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void somente_leitura_parametrizada_e_com_limite() {
        for (String sql : List.of(SERIE, BASE, TAREFAS)) {
            String codigo = semComentarios(sql).toUpperCase(Locale.ROOT);
            assertThat(codigo.strip()).matches("(?s)^(SELECT|WITH)\\b.*");
            assertThat(codigo).doesNotContainPattern("\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|GRANT|TRUNCATE)\\b");
            assertThat(codigo).doesNotContainPattern("SELECT\\s+\\*").doesNotContainPattern("\\.\\s*\\*");
            assertThat(codigo).doesNotContain("||");
            assertThat(semComentarios(sql)).containsPattern("FETCH\\s+FIRST\\s+:\\w+\\s+ROWS\\s+ONLY");
        }
    }

    @Test
    void recorte_do_ano_por_intervalo_sem_funcao_na_coluna() {
        for (String sql : List.of(SERIE, BASE)) {
            String codigo = semComentarios(sql);
            assertThat(codigo).contains("r.DATA_PROTOCOLO_PROCESSO >= CAST(:inicio AS DATE)")
                    .contains("r.DATA_PROTOCOLO_PROCESSO <  CAST(:fim AS DATE)");
            assertThat(codigo).doesNotContainPattern("\\w+\\(\\s*r\\.DATA_PROTOCOLO_PROCESSO");
        }
        assertThat(semComentarios(SERIE)).contains("r.SITUACAO_PROCESSO = 'CONCLUIDO'");
    }

    @Test
    void tarefas_so_da_raiz_pela_chave_da_definicao_e_em_lote() {
        String codigo = semComentarios(TAREFAS);
        assertThat(codigo).contains("e.BUSINESS_KEY_ IN (:chaves)").contains("r.ID IN (:ids)")
                .contains("e.PARENT_ID_ IS NULL").contains("e.ID_ = e.PROC_INST_ID_").contains("d.KEY_ IN (:modelos)")
                .contains("d.KEY_ <> :chaveDaComplementacao");
        // BUSINESS_KEY_ sem função (o índice vale); nunca TO_NUMBER(BUSINESS_KEY_) (ORA-01722).
        assertThat(codigo).doesNotContainPattern("\\w+\\(\\s*e\\.BUSINESS_KEY_");
    }

    /** A coluna do CPF aparece só no trecho {@code tarefa AS (...)}, que calcula o formato e o CPF interno. */
    @Test
    void cpf_so_na_resolucao_do_nome_e_nunca_na_saida() {
        String codigo = semComentarios(TAREFAS);
        int inicio = codigo.indexOf("tarefa AS (");
        int fim = codigo.indexOf("), servidores AS (");
        assertThat(inicio).isPositive();
        assertThat(fim).isGreaterThan(inicio);
        assertThat(codigo.substring(0, inicio)).doesNotContain("ASSIGNEE_");
        assertThat(codigo.substring(fim)).doesNotContain("ASSIGNEE_");
        // Saída final: só as colunas sem CPF.
        int saida = codigo.lastIndexOf("\nSELECT ");
        String lista = codigo.substring(saida, codigo.indexOf("\nFROM", saida));
        Matcher alias = Pattern.compile("(?:resolvida\\.(\\w+)\\s*,?\\s*$|AS (\\w+)\\s*,?\\s*$)", Pattern.MULTILINE)
                .matcher(lista);
        List<String> colunas = new java.util.ArrayList<>();
        while (alias.find()) {
            colunas.add(alias.group(1) != null ? alias.group(1) : alias.group(2));
        }
        assertThat(colunas).containsExactly("REQUERIMENTO", "MODELO", "TAREFA", "ATIVIDADE", "RESPONSAVEL",
                "NOME_RESPONSAVEL", "GRUPO");
        assertThat(lista).doesNotContain("CPF");
    }

    @Test
    void comentarios_nao_citam_a_coluna_do_cpf() {
        // O SQL vai ao banco com os comentários; nada a esconder neles, mas a coluna só é citada no código.
        assertThat(TAREFAS.lines().filter(l -> l.strip().startsWith("--"))).noneMatch(l -> l.contains("ASSIGNEE_"));
    }
}
