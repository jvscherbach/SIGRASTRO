package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import br.gov.mt.sema.rastro.integration.siga.CamundaFicticioBuilder.Fluxo;
import br.gov.mt.sema.rastro.tramitacao.infrastructure.catalogo.CatalogoDeEtapasDeTeste;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Varredura de vazamento da tramitação ponta a ponta (S7, bloco 7): o serviço inteiro, com o datasource do SIGA apontado
 * para o Oracle de teste (usuário de leitura) e os cenários fictícios da S7 (7001 a 7103) mais os criados pelos testes
 * (7200 a 7205). Para cada requerimento e cada perfil, a resposta HTTP e o log são varridos atrás de CPF (com e sem
 * máscara, 11 dígitos soltos), ids técnicos do Camunda (para quem não é Administrador), grupos técnicos, chave de
 * arquivo e token. Sem sistema externo real: só o Oracle 23 Free do Testcontainers.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"rastro.demo.senha=senha-de-teste", "rastro.tramitacao.posicao-cache-segundos=0"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
abstract class BaseVarreduraDaTramitacao {

    /** Requerimento fictício cujo interessado é o do Operador de demonstração (pessoa 1), para a visão do Operador. */
    static final long DO_OPERADOR = 7205;
    static final String PROCESSO_DO_OPERADOR = "7905/2026";
    static final String OPERADOR = "operador.demo";
    static final String GESTOR = "gestor.demo";
    static final String ADMIN = "admin.demo";
    static final List<String> PERFIS = List.of(OPERADOR, GESTOR, ADMIN);

    private static final Pattern CPF_SOLTO = Pattern.compile("(?<![0-9])\\d{11}(?![0-9])");
    private static final Pattern CPF_COM_MASCARA = Pattern.compile("[\\d*xX]{3}\\.[\\d*xX]{3}\\.[\\d*xX]{3}-[\\d*xX]{2}");
    private static final Pattern ID_TECNICO = Pattern.compile(
            "\\b(task|gtw|gwt|evt|proc)[A-Z]\\w*|\\b(Event|Activity|Gateway)_\\w+|#multiInstanceBody");
    private static final Pattern GRUPO_TECNICO = Pattern.compile("\\b[Gg]rp[A-Z]\\w*");
    private static final Pattern SEGREDO = Pattern.compile("(?i)chaveDoArquivo|\"chave\"|\"token\"|bearer |ASSIGNEE_|STATE_");

    @DynamicPropertySource
    static void sigaDeTeste(DynamicPropertyRegistry registro) {
        registro.add("rastro.datasource.siga.url", SigaOracleTeste::urlJdbc);
        registro.add("rastro.datasource.siga.username", () -> SigaOracleTeste.LEITURA);
        registro.add("rastro.datasource.siga.password", () -> SigaOracleTeste.SENHA_LEITURA);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private final Map<String, String> tokens = new LinkedHashMap<>();

    @BeforeAll
    static void criarCenarioDoOperador() {
        assertThat(SigaOracleTeste.CENARIOS_S7).isNotEmpty();
        try (HikariDataSource admin = SigaOracleTeste.dataSourceAdministrador()) {
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(admin);
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID = :id",
                    new MapSqlParameterSource("id", DO_OPERADOR), Integer.class);
            if (existe != null && existe > 0) {
                return;
            }
            new CamundaFicticioBuilder(jdbc, CatalogoDeEtapasDeTeste.padrao())
                    .processo(Fluxo.HIDRICO, DO_OPERADOR).situacao("EM_ANALISE").numeroProcesso(PROCESSO_DO_OPERADOR)
                    .naEtapa("taskAnalisarRequerimento").comAnalistas(3).concluidos(1)
                    .desde(LocalDateTime.of(2026, 5, 8, 9, 0)).gravar();
            jdbc.update("UPDATE SIGA2_LICENCIAMENTO.REQUERIMENTO SET ID_PESSOA = 1 WHERE ID = :id",
                    new MapSqlParameterSource("id", DO_OPERADOR));
        }
    }

    /** Todos os requerimentos fictícios da tramitação que existem no Oracle de teste (7000 a 7299). */
    static List<Long> requerimentosFicticios() {
        try (HikariDataSource leitura = SigaOracleTeste.dataSourceLeitura()) {
            return new NamedParameterJdbcTemplate(leitura).queryForList(
                    "SELECT ID FROM SIGA2_LICENCIAMENTO.REQUERIMENTO WHERE ID BETWEEN 7000 AND 7299 ORDER BY ID",
                    new MapSqlParameterSource(), Long.class);
        }
    }

    String token(String usuario) throws Exception {
        String guardado = tokens.get(usuario);
        if (guardado != null) {
            return guardado;
        }
        String corpo = json.writeValueAsString(Map.of("usuario", usuario, "senha", "senha-de-teste"));
        String resposta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(resposta).get("token").asText();
        tokens.put(usuario, token);
        return token;
    }

    MockHttpServletResponse obter(String usuario, String caminho) throws Exception {
        return mvc.perform(get(caminho).header("Authorization", "Bearer " + token(usuario))).andReturn().getResponse();
    }

    static String corpo(MockHttpServletResponse resposta) throws Exception {
        return resposta.getContentAsString(StandardCharsets.UTF_8);
    }

    /** Nada de CPF, grupo técnico, chave, token nem coluna do Camunda; ids técnicos só para o Administrador. */
    static void varrer(SoftAssertions soft, String onde, String texto, boolean administrador) {
        soft.assertThat(texto).as(onde + ": CPF solto").doesNotContainPattern(CPF_SOLTO);
        soft.assertThat(texto).as(onde + ": CPF com ou sem máscara").doesNotContainPattern(CPF_COM_MASCARA);
        soft.assertThat(texto).as(onde + ": grupo técnico").doesNotContainPattern(GRUPO_TECNICO);
        soft.assertThat(texto).as(onde + ": chave, token ou coluna do Camunda").doesNotContainPattern(SEGREDO);
        if (!administrador) {
            soft.assertThat(texto).as(onde + ": id técnico").doesNotContainPattern(ID_TECNICO);
        }
    }

    /**
     * O log da JVM inteira tem números alheios (ids de contêiner, carimbos), então aqui se procuram os CPFs e CNPJs que
     * de fato circulam nos cenários: os fictícios {@code 000000000NN} (servidores, interessado, sem servidor), o CNPJ de
     * demonstração do Operador, com e sem máscara, e os nomes dos servidores fictícios.
     */
    static void varrerLog(SoftAssertions soft, String log) {
        soft.assertThat(log).as("log: CPF fictício").doesNotContainPattern("(?<![0-9])000000000\\d{2}(?![0-9])");
        soft.assertThat(log).as("log: CPF fictício com máscara").doesNotContain("000.000.000-");
        soft.assertThat(log).as("log: CNPJ do Operador").doesNotContain("12345678000190", "12.345.678/0001-90");
        soft.assertThat(log).as("log: nome de servidor").doesNotContain("Servidor Exemplo");
    }
}
