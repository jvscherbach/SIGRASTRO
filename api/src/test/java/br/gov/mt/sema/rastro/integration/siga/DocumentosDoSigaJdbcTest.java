package br.gov.mt.sema.rastro.integration.siga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.documento.domain.CatalogoDeDocumentos;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.documento.domain.SituacaoDocumento;
import br.gov.mt.sema.rastro.documento.domain.TipoArquivo;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Cada origem lida do recorte de teste, que só tem as colunas que as consultas usam. */
@Testcontainers(disabledWithoutDocker = true)
class DocumentosDoSigaJdbcTest {

    private static HikariDataSource dataSource;
    private static DocumentosDoSigaJdbc adapter;

    @BeforeAll
    static void abrir() {
        dataSource = SigaOracleTeste.dataSourceLeitura();
        adapter = new DocumentosDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource), EnumSet.allOf(OrigemDocumento.class));
    }

    @AfterAll
    static void fechar() {
        dataSource.close();
    }

    private static Optional<Documento> procurar(List<Documento> lista, OrigemDocumento origem, String id) {
        return lista.stream().filter(d -> d.origem() == origem && d.id().equals(id)).findFirst();
    }

    private static Documento obter(OrigemDocumento origem, String id) {
        return procurar(adapter.listar(101), origem, id).orElseThrow(() -> new AssertionError(origem + "/" + id));
    }

    @Test
    void trazDocumentosDasSeisOrigensDaVista() {
        List<Documento> lista = adapter.listar(101);

        assertThat(lista).extracting(Documento::origem).containsAll(EnumSet.complementOf(EnumSet.of(OrigemDocumento.SCORP)));
        assertThat(lista).extracting(Documento::origem).doesNotContain(OrigemDocumento.SCORP);
        assertThat(lista).allMatch(d -> d.idRequerimento() == 101);
        assertThat(lista).hasSize(26);
    }

    @Test
    void cadaOrigemTraSeusDocumentosPelaJuncaoCorreta() {
        List<Documento> lista = adapter.listar(101);

        assertThat(ids(lista, OrigemDocumento.DOCUMENTO_PROCESSO)).containsExactlyInAnyOrder("1", "2", "3", "4", "5", "6", "7", "8",
                "9", "10", "11", "12");
        assertThat(ids(lista, OrigemDocumento.DOCUMENTO_REQUERIMENTO)).containsExactlyInAnyOrder("1", "11", "12", "13", "14");
        assertThat(ids(lista, OrigemDocumento.COMPLEMENTACAO)).containsExactlyInAnyOrder("21", "22");
        assertThat(ids(lista, OrigemDocumento.IMOVEL)).containsExactlyInAnyOrder("31", "32", "33");
        assertThat(ids(lista, OrigemDocumento.REPRESENTANTE_LEGAL)).containsExactlyInAnyOrder("41", "42");
        assertThat(ids(lista, OrigemDocumento.RESPONSAVEL_TECNICO)).containsExactlyInAnyOrder("51", "52");
    }

    private static List<String> ids(List<Documento> lista, OrigemDocumento origem) {
        return lista.stream().filter(d -> d.origem() == origem).map(Documento::id).toList();
    }

    @Test
    void complementacaoTrazItemERespostaComTituloNosDoisCampos() {
        Documento item = obter(OrigemDocumento.COMPLEMENTACAO, "21");
        Documento resposta = obter(OrigemDocumento.COMPLEMENTACAO, "22");

        assertThat(item.titulo()).isEqualTo("complemento.pdf");
        assertThat(item.nomeArquivo()).isEqualTo("complemento.pdf");
        assertThat(item.chaveDoArquivo()).isEqualTo("K-CO-21");
        assertThat(resposta.titulo()).isEqualTo("resposta.pdf");
        assertThat(resposta.ehPdf()).isTrue();
    }

    @Test
    void todosOsImoveisDoRequerimentoEntram() {
        assertThat(ids(adapter.listar(101), OrigemDocumento.IMOVEL)).hasSize(3);
    }

    @Test
    void representanteERespLegalTecnicoAparecemUmaVezCadaDocumento() {
        List<Documento> lista = adapter.listar(101);

        assertThat(lista).filteredOn(d -> d.origem() == OrigemDocumento.REPRESENTANTE_LEGAL).hasSize(2)
                .extracting(Documento::id).doesNotHaveDuplicates();
        assertThat(lista).filteredOn(d -> d.origem() == OrigemDocumento.RESPONSAVEL_TECNICO).hasSize(2)
                .extracting(Documento::id).doesNotHaveDuplicates();
    }

    @Test
    void vinculoCorretoNaoTrazDocumentoDeOutroRequerimento() {
        assertThat(adapter.listar(101)).extracting(Documento::chaveDoArquivo).doesNotContain("K-OUTRO");
        assertThat(adapter.listar(102)).extracting(Documento::chaveDoArquivo).containsExactly("K-OUTRO");
    }

    @Test
    void requerimentoSemNenhumDocumentoDevolveListaVazia() {
        assertThat(adapter.listar(105)).isEmpty();
    }

    @Test
    void mesmoIdEmDuasOrigensSaoDocumentosDistintos() {
        List<Documento> lista = adapter.listar(101);

        assertThat(lista).filteredOn(d -> d.id().equals("1")).extracting(Documento::origem)
                .containsExactlyInAnyOrder(OrigemDocumento.DOCUMENTO_PROCESSO, OrigemDocumento.DOCUMENTO_REQUERIMENTO);
        assertThat(CatalogoDeDocumentos.consolidar(lista)).filteredOn(d -> d.id().equals("1")).hasSize(2);
    }

    @Test
    void naoFiltraVigenciaCalculaASituacao() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "1").situacao()).isEqualTo(SituacaoDocumento.VIGENTE);
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "3").situacao()).isEqualTo(SituacaoDocumento.SUBSTITUIDO);
    }

    @Test
    void chaveZeroEhIndisponivel() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "2").situacao()).isEqualTo(SituacaoDocumento.INDISPONIVEL);
    }

    @Test
    void dataEDaPropriaOrigemENulaNaoDerrubaALista() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "1").data()).isEqualTo(LocalDateTime.of(2025, 3, 31, 10, 0));
        assertThat(obter(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "11").data()).isEqualTo(LocalDateTime.of(2025, 3, 31, 12, 0));
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "9").data()).isNull();
        assertThat(obter(OrigemDocumento.IMOVEL, "33").data()).isNull();
        assertThat(CatalogoDeDocumentos.consolidar(adapter.listar(101)).getLast().data()).isNull();
    }

    @Test
    void sigiloEmNumeroInclusiveNasColunasSigiloDeRepresentanteEResponsavel() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "5").sigiloso()).isTrue();
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "1").sigiloso()).isFalse();
        assertThat(obter(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "12").sigiloso()).isTrue();
        assertThat(obter(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "13").sigiloso()).isFalse();
        assertThat(obter(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "14").sigiloso()).isTrue();   // 2: na dúvida
        assertThat(obter(OrigemDocumento.REPRESENTANTE_LEGAL, "41").sigiloso()).isTrue();      // SIGILO real
        assertThat(obter(OrigemDocumento.REPRESENTANTE_LEGAL, "42").sigiloso()).isFalse();
        assertThat(obter(OrigemDocumento.RESPONSAVEL_TECNICO, "52").sigiloso()).isTrue();      // SIGILO real
        assertThat(obter(OrigemDocumento.RESPONSAVEL_TECNICO, "51").sigiloso()).isFalse();
    }

    @Test
    void nomeNuloOuEmBrancoNaoDerrubaATitulacaoEmTresNiveis() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "10").titulo()).isEqualTo("Oficio sem nome de arquivo");
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "10").nomeArquivo()).isNull();
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "3").titulo()).isEqualTo("antigo.pdf");
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "12").titulo()).isEqualTo("Documento do processo 12");
        assertThat(obter(OrigemDocumento.IMOVEL, "33").titulo()).isEqualTo("Imóvel 33");
        assertThat(obter(OrigemDocumento.IMOVEL, "32").titulo()).isEqualTo("Planta do segundo imóvel");
    }

    @Test
    void extensaoDesconhecidaOuNomeAusenteViraTipoNaoInformado() {
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "11").tipoArquivo()).isEqualTo(TipoArquivo.DESCONHECIDO);
        assertThat(obter(OrigemDocumento.DOCUMENTO_PROCESSO, "10").tipoArquivo()).isEqualTo(TipoArquivo.DESCONHECIDO);
        assertThat(obter(OrigemDocumento.IMOVEL, "32").tipoNaoInformado()).isTrue();
        assertThat(obter(OrigemDocumento.IMOVEL, "31").tipoArquivo()).isEqualTo(TipoArquivo.PDF);
    }

    @Test
    void documentoDuplicadoEntreOrigensVeioDasDuasMasAListaConsolidadaTemUm() {
        List<Documento> bruta = adapter.listar(101);

        assertThat(bruta).filteredOn(d -> "K-DUP".equals(d.chaveDoArquivo())).hasSize(2);
        List<Documento> consolidada = CatalogoDeDocumentos.consolidar(bruta);
        assertThat(consolidada).filteredOn(d -> "K-DUP".equals(d.chaveDoArquivo()))
                .singleElement().extracting(Documento::origem).isEqualTo(OrigemDocumento.DOCUMENTO_PROCESSO);
    }

    @Test
    void origemDesligadaNaoEConsultada() {
        DocumentosDoSigaJdbc somenteProcesso = new DocumentosDoSigaJdbc(new NamedParameterJdbcTemplate(dataSource),
                EnumSet.of(OrigemDocumento.DOCUMENTO_PROCESSO, OrigemDocumento.IMOVEL));

        assertThat(somenteProcesso.listar(101)).extracting(Documento::origem)
                .containsOnly(OrigemDocumento.DOCUMENTO_PROCESSO, OrigemDocumento.IMOVEL);
    }

    @Test
    void conexaoRecusadaViraSigaIndisponivel() {
        try (HikariDataSource fora = SigaOracleTeste.dataSourceInacessivel()) {
            DocumentosDoSigaJdbc indisponivel = new DocumentosDoSigaJdbc(new NamedParameterJdbcTemplate(fora),
                    EnumSet.allOf(OrigemDocumento.class));

            assertThatThrownBy(() -> indisponivel.listar(101)).isInstanceOf(SigaIndisponivelException.class);
        }
    }

    @Test
    void faltaDeGrantViraErroInterno() {
        try (HikariDataSource semGrant = SigaOracleTeste.dataSource(SigaOracleTeste.SEM_PERMISSAO,
                SigaOracleTeste.SENHA_SEM_PERMISSAO)) {
            DocumentosDoSigaJdbc sem = new DocumentosDoSigaJdbc(new NamedParameterJdbcTemplate(semGrant),
                    EnumSet.allOf(OrigemDocumento.class));

            assertThatThrownBy(() -> sem.listar(101)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void aConexaoVoltaAoPoolAoFimDaListagem() {
        adapter.listar(101);

        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }
}
