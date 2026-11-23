package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.acesso.domain.CpfCnpjDoUsuario;
import org.junit.jupiter.api.Test;

/**
 * Nenhum dado real nas fixtures da S7: todo CPF/CNPJ é inválido de propósito e os nomes são evidentes. Sem Docker (lê os
 * arquivos-fonte das fixtures). O valor encontrado nunca é impresso na mensagem de falha, só a posição.
 */
class DadosFicticiosDaS7Test {

    private static final List<Path> FIXTURES = List.of(
            Path.of("src/test/resources/sql/siga/06-camunda-s7.sql"),
            Path.of("src/test/java/br/gov/mt/sema/rastro/integration/siga/CamundaFicticioBuilder.java"),
            Path.of("src/test/java/br/gov/mt/sema/rastro/integration/siga/CenariosFicticiosDaS7.java"),
            // S8 (bloco 1): caminho percorrido e plano B
            Path.of("src/test/resources/sql/siga/07-historico-s8.sql"),
            Path.of("src/test/java/br/gov/mt/sema/rastro/integration/siga/CenariosFicticiosDaS8.java"),
            // S9 (bloco 1): lançamentos de taxa
            Path.of("src/test/resources/sql/siga/08-financeiro-s9.sql"),
            Path.of("src/test/java/br/gov/mt/sema/rastro/integration/siga/CenariosFicticiosDaS9.java"),
            // S10 (bloco 1): população do painel
            Path.of("src/test/java/br/gov/mt/sema/rastro/integration/siga/CenariosFicticiosDaS10.java"));

    /** 11 ou 14 dígitos seguidos, com ou sem máscara, sem fazer parte de um número maior. */
    private static final Pattern DOCUMENTO = Pattern.compile(
            "(?<![0-9])(\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}|\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2})(?![0-9])");

    @Test
    void cpfsGeradosPeloConstrutorSaoInvalidos() {
        for (int n = 1; n <= CamundaFicticioBuilder.SERVIDORES; n++) {
            assertThat(CpfCnpjDoUsuario.valido(CamundaFicticioBuilder.cpfDoServidor(n))).as("servidor %d", n).isFalse();
        }
        assertThat(CpfCnpjDoUsuario.valido(CamundaFicticioBuilder.CPF_DO_INTERESSADO)).isFalse();
        assertThat(CpfCnpjDoUsuario.valido(CamundaFicticioBuilder.CPF_SEM_SERVIDOR)).isFalse();
    }

    @Test
    void nenhumCpfOuCnpjValidoNasFixtures() throws IOException {
        List<String> encontrados = new ArrayList<>();
        for (Path arquivo : FIXTURES) {
            String texto = Files.readString(arquivo, StandardCharsets.UTF_8);
            Matcher m = DOCUMENTO.matcher(texto);
            while (m.find()) {
                if (CpfCnpjDoUsuario.valido(CpfCnpjDoUsuario.somenteDigitos(m.group(1)))) {
                    encontrados.add(arquivo.getFileName() + " posição " + m.start());
                }
            }
        }
        assertThat(encontrados).as("CPF/CNPJ válido nas fixtures (valor omitido)").isEmpty();
    }

    @Test
    void nomesDasFixturesSaoEvidentementeFicticios() throws IOException {
        String sql = Files.readString(FIXTURES.getFirst(), StandardCharsets.UTF_8);
        assertThat(sql).contains("'Servidor Exemplo '").contains("'Interessado Fictício S7'");
        // Toda descrição de tipo de processo e todo nome gravado leva a marca FICTÍCIO.
        Matcher tipos = Pattern.compile("TIPO_PROCESSO \\(ID, DESCRICAO, INSTANCIA_REQUERIMENTO\\) VALUES \\(\\d+, '([^']*)'")
                .matcher(sql);
        int quantos = 0;
        while (tipos.find()) {
            assertThat(tipos.group(1)).contains("FICTÍCIO");
            quantos++;
        }
        assertThat(quantos).isEqualTo(3);
    }
}
