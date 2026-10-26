package br.gov.mt.sema.vistas.documento.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import br.gov.mt.sema.vistas.documento.domain.Documento.Bloqueio;
import org.junit.jupiter.api.Test;

class CatalogoEDocumentoTest {

    private static final LocalDateTime D1 = LocalDateTime.of(2025, 1, 1, 10, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2025, 2, 1, 10, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2025, 3, 1, 10, 0);

    private static Documento doc(OrigemDocumento origem, String id, String chave, String arquivo, LocalDateTime data) {
        return Documento.deLinha(origem, id, 1, chave, null, arquivo, data, null, null);
    }

    // --- deduplicação (RN05)

    @Test
    void mesmaChaveEmDuasOrigensApareceUmaVezEVenceAPrimeiraOrigemDaOrdem() {
        Documento deRequerimento = doc(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "11", "K", "a.pdf", D1);
        Documento deProcesso = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "4", "K", "a.pdf", D2);

        List<Documento> lista = CatalogoDeDocumentos.consolidar(List.of(deRequerimento, deProcesso));

        assertThat(lista).containsExactly(deProcesso);
    }

    @Test
    void chaveZeroNuncaDeduplica() {
        Documento a = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "0", "a.pdf", D1);
        Documento b = doc(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "2", "0", "b.pdf", D2);
        Documento c = doc(OrigemDocumento.IMOVEL, "3", null, "c.pdf", D3);
        Documento d = doc(OrigemDocumento.RESPONSAVEL_TECNICO, "4", null, "d.pdf", D3);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(a, b, c, d))).containsExactlyInAnyOrder(a, b, c, d);
    }

    @Test
    void mesmaChaveNaMesmaOrigemTambemEUnica() {
        Documento a = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K", "a.pdf", D1);
        Documento b = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K", "a.pdf", D1);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(b, a))).containsExactly(a);
    }

    // --- exclusões

    @Test
    void compactadosNuncaAparecemEGeometriasFicamClassificadas() {
        Documento zip = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "tudo.zip", D1);
        Documento rar = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K2", "tudo.RAR", D1);
        Documento kml = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "3", "K3", "area.kml", D1);
        Documento pdf = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "4", "K4", "a.pdf", D1);

        List<Documento> lista = CatalogoDeDocumentos.consolidar(List.of(zip, rar, kml, pdf));

        assertThat(lista).containsExactlyInAnyOrder(kml, pdf);
        assertThat(kml.tipoArquivo()).isEqualTo(TipoArquivo.GEOMETRIA);
    }

    // --- ordem (RN03)

    @Test
    void ordemCronologicaCrescenteSemDataAoFinal() {
        Documento semData = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "a.pdf", null);
        Documento tarde = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K2", "b.pdf", D3);
        Documento cedo = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "3", "K3", "c.pdf", D1);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(semData, tarde, cedo))).containsExactly(cedo, tarde, semData);
    }

    @Test
    void empateDeDataDesempataPelaOrigemEDepoisPeloIdNumerico() {
        Documento imovel = doc(OrigemDocumento.IMOVEL, "1", "K1", "a.pdf", D1);
        Documento processo9 = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "9", "K2", "b.pdf", D1);
        Documento processo10 = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "10", "K3", "c.pdf", D1);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(imovel, processo10, processo9)))
                .containsExactly(processo9, processo10, imovel);
    }

    @Test
    void doisSemDataTambemSeguemOrigemEId() {
        Documento b = doc(OrigemDocumento.RESPONSAVEL_TECNICO, "1", "K1", "a.pdf", null);
        Documento a = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K2", "b.pdf", null);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(b, a))).containsExactly(a, b);
    }

    // --- bloqueio e pré-seleção

    @Test
    void pdfVigenteNaoTemBloqueioEVemPreSelecionado() {
        Documento d = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K", "a.pdf", D1);

        assertThat(d.bloqueio(false)).isNull();
        assertThat(d.selecionavelPorPadrao(false)).isTrue();
    }

    @Test
    void naoPdfFicaBloqueadoEForaDaPreSelecao() {
        Documento d = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K", "planilha.xlsx", D1);

        assertThat(d.bloqueio(true)).isEqualTo(Bloqueio.NAO_PDF);
        assertThat(d.selecionavelPorPadrao(true)).isFalse();
    }

    @Test
    void indisponivelFicaBloqueadoMesmoSendoNaoPdfOuSigiloso() {
        Documento d = Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 1, "0", null, "x.xlsx", D1, null, 1);

        assertThat(d.bloqueio(false)).isEqualTo(Bloqueio.INDISPONIVEL);
        assertThat(d.bloqueio(true)).isEqualTo(Bloqueio.INDISPONIVEL);
    }

    @Test
    void substituidoEPdfFicaSelecionavelManualmenteMasNaoPorPadrao() {
        Documento d = Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 1, "K", null, "a.pdf", D1, D2, null);

        assertThat(d.situacao()).isEqualTo(SituacaoDocumento.SUBSTITUIDO);
        assertThat(d.bloqueio(false)).isNull();
        assertThat(d.selecionavelPorPadrao(false)).isFalse();
    }

    @Test
    void sigilosoBloqueiaSoParaQuemNaoPodeVer() {
        Documento d = Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 1, "K", null, "a.pdf", D1, null, 1);

        assertThat(d.bloqueio(false)).isEqualTo(Bloqueio.SIGILOSO);
        assertThat(d.bloqueio(true)).isNull();
        assertThat(d.selecionavelPorPadrao(true)).isTrue();
        assertThat(d.selecionavelPorPadrao(false)).isFalse();
    }

    // --- título e chave

    @Test
    void tituloVazioUsaONomeDoArquivo() {
        Documento d = Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 1, "K", "  ", "Relatório.pdf", D1, null, null);

        assertThat(d.titulo()).isEqualTo("Relatório.pdf");
        assertThat(d.extensao()).isEqualTo("pdf");
    }

    @Test
    void tituloEmTresNiveisTituloDepoisNomeDepoisDescricaoDaOrigemEId() {
        Documento comTitulo = Documento.deLinha(OrigemDocumento.IMOVEL, "5", 1, "K", " Matrícula ", "m.pdf", D1, null, 0);
        Documento soNome = Documento.deLinha(OrigemDocumento.IMOVEL, "6", 1, "K", null, "m.pdf", D1, null, 0);
        Documento nenhum = Documento.deLinha(OrigemDocumento.IMOVEL, "7", 1, "K", "  ", "   ", D1, null, 0);
        Documento nulos = Documento.deLinha(OrigemDocumento.IMOVEL, "8", 1, "K", null, null, D1, null, 0);

        assertThat(comTitulo.titulo()).isEqualTo("Matrícula");
        assertThat(soNome.titulo()).isEqualTo("m.pdf");
        assertThat(nenhum.titulo()).isEqualTo("Imóvel 7");
        assertThat(nulos.titulo()).isEqualTo("Imóvel 8");
        assertThat(nulos.nomeArquivo()).isNull();
    }

    @Test
    void semExtensaoOuExtensaoNaoReconhecidaEDesconhecidoSelecionavelEForaDaPreSelecao() {
        Documento semExtensao = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "semextensao", D1);
        Documento naoReconhecida = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K2", "projeto.dwg", D1);
        Documento semNome = doc(OrigemDocumento.IMOVEL, "3", "K3", null, D1);

        for (Documento d : List.of(semExtensao, naoReconhecida, semNome)) {
            assertThat(d.tipoArquivo()).isEqualTo(TipoArquivo.DESCONHECIDO);
            assertThat(d.tipoNaoInformado()).isTrue();
            assertThat(d.bloqueio(false)).isNull();
            assertThat(d.selecionavelPorPadrao(false)).isFalse();
        }
    }

    @Test
    void desconhecidoIndisponivelOuSigilosoContinuaBloqueado() {
        Documento indisponivel = Documento.deLinha(OrigemDocumento.IMOVEL, "1", 1, "0", null, null, D1, null, 0);
        Documento sigiloso = Documento.deLinha(OrigemDocumento.IMOVEL, "2", 1, "K", null, null, D1, null, 1);

        assertThat(indisponivel.bloqueio(true)).isEqualTo(Bloqueio.INDISPONIVEL);
        assertThat(sigiloso.bloqueio(false)).isEqualTo(Bloqueio.SIGILOSO);
    }

    @Test
    void dataNulaVaiParaOFinalSemDerrubarAListagem() {
        Documento semData = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "a.pdf", null);
        Documento comData = doc(OrigemDocumento.RESPONSAVEL_TECNICO, "2", "K2", "b.pdf", D3);

        assertThat(CatalogoDeDocumentos.consolidar(List.of(semData, comData))).containsExactly(comData, semData);
    }

    @Test
    void chaveNaoApareceNoToString() {
        Documento d = doc(OrigemDocumento.DOCUMENTO_PROCESSO, "7", "CHAVE-SECRETA", "a.pdf", D1);

        assertThat(d.toString()).doesNotContain("CHAVE-SECRETA").contains("DOCUMENTO_PROCESSO", "7");
        assertThat(d.chaveDoArquivo()).isEqualTo("CHAVE-SECRETA");
    }
}
