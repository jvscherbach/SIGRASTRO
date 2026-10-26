package br.gov.mt.sema.vistas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.dossie.domain.ItemDoPedido;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fiscaliza docs/padroes/modulos-e-dependencias.md. As regras passam com pacotes ainda inexistentes
 * (allowEmptyShould) e passam a inspecioná-los quando recebem classes.
 */
@AnalyzeClasses(packages = "br.gov.mt.sema.vistas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String RAIZ = "br.gov.mt.sema.vistas.";
    private static final List<String> MODULOS =
            List.of("acesso", "consulta", "documento", "dossie", "auditoria", "configuracao");
    private static final List<String> INTEGRACOES = List.of("siga", "licenciamento", "scorp", "arrecadacao");

    private static final String[] SQL = {"java.sql..", "javax.sql..", "org.springframework.jdbc.."};
    private static final String[] HTTP = {"org.springframework.web.client..", "java.net.http..", "java.net.HttpURLConnection"};

    private static String[] pacotes(String camada) {
        return MODULOS.stream().map(m -> RAIZ + m + "." + camada + "..").toArray(String[]::new);
    }

    // Regra 1 e 3: application não depende de api nem de infrastructure.
    @ArchTest
    static final ArchRule application_nao_depende_de_api_nem_de_infrastructure =
            noClasses().that().resideInAnyPackage(pacotes("application"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            concat(pacotes("api"), pacotes("infrastructure")))
                    .allowEmptyShould(true);

    // Regra 4: domain não depende de nenhuma outra camada.
    @ArchTest
    static final ArchRule domain_nao_depende_de_outras_camadas =
            noClasses().that().resideInAnyPackage(pacotes("domain"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            concat(pacotes("api"), pacotes("application"), pacotes("infrastructure")))
                    .allowEmptyShould(true);

    // Regra 4: domain sem Spring Web, JPA, JDBC nem tipos de integration.*.
    @ArchTest
    static final ArchRule domain_sem_framework_de_entrega_persistencia_ou_integracao =
            noClasses().that().resideInAnyPackage(pacotes("domain"))
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.web..", "jakarta.persistence..", "java.sql..", "javax.sql..",
                            "org.springframework.jdbc..", RAIZ + "integration..")
                    .allowEmptyShould(true);

    // Regra 1 e 5: api não fala direto com infrastructure.
    @ArchTest
    static final ArchRule api_nao_depende_de_infrastructure =
            noClasses().that().resideInAnyPackage(pacotes("api"))
                    .should().dependOnClassesThat().resideInAnyPackage(pacotes("infrastructure"))
                    .allowEmptyShould(true);

    // Regra 6: um módulo não usa api, DTO HTTP, entidade ou repository (infrastructure) de outro módulo;
    // e o DTO HTTP de um módulo não é usado fora dele.
    @ArchTest
    static void modulos_nao_usam_api_nem_infrastructure_de_outros_modulos(JavaClasses classes) {
        for (String modulo : MODULOS) {
            String[] alheios = MODULOS.stream().filter(o -> !o.equals(modulo))
                    .flatMap(o -> Stream.of(RAIZ + o + ".api..", RAIZ + o + ".infrastructure.."))
                    .toArray(String[]::new);
            noClasses().that().resideInAPackage(RAIZ + modulo + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(alheios)
                    .allowEmptyShould(true).check(classes);
        }
    }

    @ArchTest
    static void dto_http_nao_e_usado_fora_do_proprio_modulo(JavaClasses classes) {
        for (String modulo : MODULOS) {
            noClasses().that().resideOutsideOfPackage(RAIZ + modulo + "..")
                    .should().dependOnClassesThat().resideInAPackage(RAIZ + modulo + ".api.dto..")
                    .allowEmptyShould(true).check(classes);
        }
    }

    // Regra 8: sem ciclos entre módulos (e entre config, shared e integration).
    @ArchTest
    static final ArchRule sem_ciclos_entre_modulos =
            slices().matching(RAIZ + "(*)..").should().beFreeOfCycles().allowEmptyShould(true);

    // Regra 9: SQL somente em integration.siga.
    @ArchTest
    static final ArchRule jdbc_somente_em_integration_siga =
            noClasses().that().resideOutsideOfPackage(RAIZ + "integration.siga..")
                    .should().dependOnClassesThat().resideInAnyPackage(SQL)
                    .allowEmptyShould(true);

    // Regra 10: módulos de negócio não enxergam integration.* (tipos externos ficam no adapter).
    @ArchTest
    static final ArchRule modulos_de_negocio_nao_dependem_de_integration =
            noClasses().that().resideInAnyPackage(MODULOS.stream().map(m -> RAIZ + m + "..").toArray(String[]::new))
                    .should().dependOnClassesThat().resideInAnyPackage(RAIZ + "integration..")
                    .allowEmptyShould(true);

    // Regra 10: DTO de integration.<sistema> só é usado dentro do próprio integration.<sistema>.
    @ArchTest
    static void dto_de_integracao_e_interno_ao_sistema(JavaClasses classes) {
        for (String sistema : INTEGRACOES) {
            noClasses().that().resideOutsideOfPackage(RAIZ + "integration." + sistema + "..")
                    .should().dependOnClassesThat().resideInAPackage(RAIZ + "integration." + sistema + ".dto..")
                    .allowEmptyShould(true).check(classes);
        }
    }


    // S2: HTTP de saída (RestClient, java.net.http, HttpURLConnection) só em integration.*; no SIGA, nenhum HTTP.
    @ArchTest
    static final ArchRule http_de_saida_somente_em_integration =
            noClasses().that().resideOutsideOfPackage(RAIZ + "integration..")
                    .should().dependOnClassesThat().resideInAnyPackage(HTTP)
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule integration_siga_nao_fala_http =
            noClasses().that().resideInAPackage(RAIZ + "integration.siga..")
                    .should().dependOnClassesThat().resideInAnyPackage(HTTP)
                    .allowEmptyShould(true);

    // S2 (RNF09): a chave do arquivo nunca chega à camada HTTP (nem por método, nem por campo de DTO).
    @ArchTest
    static final ArchRule api_nao_le_a_chave_do_arquivo =
            noClasses().that().resideInAPackage("..api..")
                    .should().callMethod(Documento.class, "chaveDoArquivo")
                    .orShould().callMethod(ItemDoPedido.class, "chaveDoArquivo")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dto_http_sem_campo_de_chave =
            noFields().that().areDeclaredInClassesThat().resideInAPackage("..api.dto..")
                    .should().haveNameMatching("(?i).*chave.*")
                    .allowEmptyShould(true);

    private static String[] concat(String[]... partes) {
        return java.util.Arrays.stream(partes).flatMap(java.util.Arrays::stream).toArray(String[]::new);
    }
}
