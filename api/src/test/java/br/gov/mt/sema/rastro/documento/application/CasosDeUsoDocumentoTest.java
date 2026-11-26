package br.gov.mt.sema.rastro.documento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.consulta.application.RequerimentosDoSiga;
import br.gov.mt.sema.rastro.consulta.application.VerificarAcessoAoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.ParametrosDaConversao;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.Documento.Bloqueio;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.rastro.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CasosDeUsoDocumentoTest {

    private static final String CNPJ_DO_OPERADOR = "11.222.333/0001-81";
    private static final LocalDateTime D1 = LocalDateTime.of(2025, 4, 1, 10, 0);
    private static final LocalDateTime D2 = LocalDateTime.of(2025, 4, 2, 10, 0);
    private static final LocalDateTime D3 = LocalDateTime.of(2025, 4, 3, 10, 0);

    private Usuario usuario;
    private final RequerimentosDoSiga requerimentos = mock(RequerimentosDoSiga.class);
    private final DocumentosDoRequerimento documentos = mock(DocumentosDoRequerimento.class);
    private final ParametrosDaConversao conversao = mock(ParametrosDaConversao.class);

    private ListarDocumentos listar;
    private SelecionarDocumentosParaDossie selecionar;

    @BeforeEach
    void preparar() {
        usuario = new Usuario("operador", "Operador", Perfil.OPERADOR, CNPJ_DO_OPERADOR, true);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                return usuario;
            }

            @Override
            public String token() {
                return "token-de-teste";
            }
        };
        PoliticaDeAcesso politica = new PoliticaDeAcesso(atual);
        VerificarAcessoAoRequerimento acesso = new VerificarAcessoAoRequerimento(requerimentos, politica);
        listar = new ListarDocumentos(acesso, documentos, politica, conversao,
                br.gov.mt.sema.rastro.testsupport.ScorpDeTeste.desligado());
        selecionar = new SelecionarDocumentosParaDossie(acesso, listar, politica, conversao);
        // Os testes da S2.2 descrevem o comportamento só-PDF; os da S3 ligam a conversão.
        when(conversao.habilitada()).thenReturn(false);

        when(requerimentos.buscarPorId(101)).thenReturn(Optional.of(requerimento(101, CNPJ_DO_OPERADOR)));
        when(requerimentos.buscarPorId(202)).thenReturn(Optional.of(requerimento(202, "98.765.432/0001-10")));
        when(requerimentos.buscarPorId(404)).thenReturn(Optional.empty());
        when(documentos.listar(anyLong())).thenReturn(List.of(
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "Requerimento", "req.pdf", D1, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "0", "Perdido", "perdido.pdf", D2, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "3", "K3", "Planilha", "calculo.xlsx", D2, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "4", "K4", "Laudo reservado", "laudo-secreto.pdf", D3, null, 1),
                doc(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "5", "K5", "Antigo", "antigo.pdf", D1, D2, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "6", "K6", "Mapa", "area.kml", D3, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "7", "K7", "Compactado", "tudo.zip", D3, null, 0),
                doc(OrigemDocumento.IMOVEL, "8", "K1", "Cópia do requerimento", "req.pdf", D1, null, 0)));
    }

    private static Requerimento requerimento(long id, String cpfCnpj) {
        return new Requerimento(id, "3331/2025", "2825/2025", null, Situacao.de("EM_ANALISE"), D1, null,
                new Interessado("Associação Residencial Vale Verde", cpfCnpj), "Uso de Recursos Hídricos");
    }

    private static Documento doc(OrigemDocumento origem, String id, String chave, String titulo, String arquivo,
            LocalDateTime data, LocalDateTime fim, Object sigilo) {
        return Documento.deLinha(origem, id, 101, chave, titulo, arquivo, data, fim, sigilo);
    }

    private void comoGestor() {
        usuario = new Usuario("gestor", "Gestor", Perfil.OPERADOR_SIGILO, null, true);
    }

    // --- listagem

    @Test
    void listaConsolidadaSemCompactadosNemGeometriasComTotais() {
        DocumentosListados lista = listar.executar(101);

        // 8 linhas: 1 compactado e 1 geometria saem; 1 cópia (mesma chave K1) é deduplicada.
        assertThat(lista.itens()).extracting(i -> i.documento().id()).containsExactly("1", "5", "2", "3", "4");
        assertThat(lista.totais()).isEqualTo(new DocumentosListados.Totais(5, 3, 1, 1, 0, 1, 1, 0));
    }

    @Test
    void geometriaVaiParaListaPropriaESigilosaSoParaGestor() {
        List<Documento> linhas = new java.util.ArrayList<>(documentos.listar(101));
        linhas.add(doc(OrigemDocumento.DOCUMENTO_PROCESSO, "9", "K9", "Mapa reservado", "reservado.kmz", D2, null, 1));
        when(documentos.listar(101)).thenReturn(linhas);

        DocumentosListados doOperador = listar.executar(101);
        assertThat(doOperador.geometrias()).extracting(g -> g.documento().id()).containsExactly("6");
        assertThat(doOperador.geometriasOmitidas()).isEqualTo(1);
        assertThat(doOperador.itens()).extracting(i -> i.documento().id()).doesNotContain("6", "9");

        comoGestor();
        DocumentosListados doGestor = listar.executar(101);
        assertThat(doGestor.geometrias()).extracting(g -> g.documento().id()).containsExactly("9", "6");
        assertThat(doGestor.geometriasOmitidas()).isZero();
        assertThat(doGestor.totais().total()).isEqualTo(doOperador.totais().total());
    }

    @Test
    void selecaoInicialDoGestorTemTodosOsSelecionaveisInclusiveSemEfeitoESigiloso() {
        comoGestor();

        List<DocumentoListado> itens = listar.executar(101).itens();

        assertThat(itens).filteredOn(DocumentoListado::selecionavelPorPadrao)
                .extracting(i -> i.documento().id()).containsExactly("1", "5", "4");
        assertThat(itens).extracting(i -> i.documento().id() + ":" + i.bloqueio()).contains(
                "2:INDISPONIVEL", "3:NAO_PDF", "1:null", "5:null", "4:null");
    }

    @Test
    void selecaoInicialDoOperadorDeixaDeForaSigilosoIndisponivelENaoPdf() {
        List<DocumentoListado> itens = listar.executar(101).itens();

        assertThat(itens).filteredOn(DocumentoListado::selecionavelPorPadrao)
                .extracting(i -> i.documento().id()).containsExactly("1", "5");
        // selecionados = itens sem bloqueio
        assertThat(itens).filteredOn(i -> i.bloqueio() == null)
                .extracting(i -> i.documento().id()).containsExactly("1", "5");
    }

    @Test
    void tipoNaoInformadoTemLinhaPropriaNosTotaisEEntraNaSelecaoInicial() {
        comoGestor();
        when(documentos.listar(anyLong())).thenReturn(List.of(
                doc(OrigemDocumento.IMOVEL, "1", "K1", "Planta", null, D1, null, 0),
                doc(OrigemDocumento.IMOVEL, "2", "K2", "Matrícula", "m.pdf", D2, null, 0)));

        DocumentosListados lista = listar.executar(101);

        assertThat(lista.totais()).isEqualTo(new DocumentosListados.Totais(2, 2, 0, 0, 1, 0, 0, 0));
        assertThat(lista.itens().get(0).bloqueio()).isNull();
        assertThat(lista.itens().get(0).selecionavelPorPadrao()).isTrue();
        assertThat(selecionar.executar(101, List.of(ref("IMOVEL", "1")))).hasSize(1);
    }

    @Test
    void operadorVeSigilosoMascaradoEBloqueado() {
        DocumentoListado sigiloso = listar.executar(101).itens().stream()
                .filter(i -> i.documento().id().equals("4")).findFirst().orElseThrow();

        assertThat(sigiloso.acessivel()).isFalse();
        assertThat(sigiloso.bloqueio()).isEqualTo(Bloqueio.SIGILOSO);
        assertThat(sigiloso.selecionavelPorPadrao()).isFalse();
        assertThat(sigiloso.titulo()).isEqualTo("Documento sigiloso");
        assertThat(sigiloso.nomeArquivo()).isNull();
        assertThat(sigiloso.extensao()).isNull();
    }

    @Test
    void gestorVeSigilosoComNomeEAcessivel() {
        comoGestor();
        DocumentoListado sigiloso = listar.executar(101).itens().stream()
                .filter(i -> i.documento().id().equals("4")).findFirst().orElseThrow();

        assertThat(sigiloso.acessivel()).isTrue();
        assertThat(sigiloso.titulo()).isEqualTo("Laudo reservado");
        assertThat(sigiloso.nomeArquivo()).isEqualTo("laudo-secreto.pdf");
    }

    @Test
    void operadorDeOutroInteressadoNaoListaENemLeODocumentos() {
        assertThatThrownBy(() -> listar.executar(202)).isInstanceOf(NaoInteressadoException.class);
        verify(documentos, never()).listar(anyLong());
    }

    @Test
    void requerimentoInexistenteEhNaoEncontrado() {
        assertThatThrownBy(() -> listar.executar(404)).isInstanceOf(RecursoNaoEncontradoException.class);
        verify(documentos, never()).listar(anyLong());
    }

    @Test
    void gestorListaDeQualquerInteressado() {
        comoGestor();

        assertThat(listar.executar(202).itens()).isNotEmpty();
    }

    // --- seleção para o dossiê

    private static ReferenciaDeDocumento ref(String origem, String id) {
        return new ReferenciaDeDocumento(origem, id);
    }

    @Test
    void selecaoValidaVoltaEmOrdemCronologicaComAChaveResolvida() {
        List<Documento> escolhidos = selecionar.executar(101, List.of(ref("DOCUMENTO_REQUERIMENTO", "5"), ref("DOCUMENTO_PROCESSO", "1")));

        assertThat(escolhidos).extracting(Documento::id).containsExactly("1", "5");
        assertThat(escolhidos.get(0).chaveDoArquivo()).isEqualTo("K1");
    }

    // --- inventário para a seção "Documentos não incluídos" (S2.2)

    @Test
    void inventarioDoOperadorTrazOsNaoSelecionadosComMotivoESigilosoMascarado() {
        SelecaoParaDossie selecao = selecionar.executarComInventario(101, List.of(ref("DOCUMENTO_PROCESSO", "1")));

        assertThat(selecao.escolhidos()).extracting(Documento::id).containsExactly("1");
        assertThat(selecao.listados()).isEqualTo(5);
        assertThat(selecao.naoSelecionados()).extracting(n -> n.documento().id() + ":" + n.motivo() + ":" + n.titulo())
                .containsExactly("5:NAO_SELECIONADO:antigo.pdf", "2:INDISPONIVEL:perdido.pdf", "3:NAO_PDF:calculo.xlsx",
                        "4:SIGILOSO:Documento sigiloso");
    }

    @Test
    void inventarioDoGestorNaoMascaraOSigilosoNemOTrataComoBloqueado() {
        comoGestor();

        SelecaoParaDossie selecao = selecionar.executarComInventario(101, List.of(ref("DOCUMENTO_PROCESSO", "1")));

        assertThat(selecao.naoSelecionados()).filteredOn(n -> n.documento().id().equals("4")).singleElement()
                .satisfies(n -> {
                    assertThat(n.titulo()).isEqualTo("laudo-secreto.pdf"); // D76: nome do arquivo
                    assertThat(n.motivo()).isEqualTo(SelecaoParaDossie.Motivo.NAO_SELECIONADO);
                });
        assertThat(selecao.escolhidos().size() + selecao.naoSelecionados().size()).isEqualTo(selecao.listados());
    }

    // --- conversão para PDF (RN13, S3)

    @Test
    void comAConversaoLigadaOConvertivelVemSelecionadoEContadoNosTotais() {
        when(conversao.habilitada()).thenReturn(true);
        comoGestor();

        DocumentosListados lista = listar.executar(101);

        assertThat(lista.itens()).filteredOn(DocumentoListado::selecionavelPorPadrao)
                .extracting(i -> i.documento().id()).containsExactly("1", "5", "3", "4");
        assertThat(lista.totais().convertiveis()).isEqualTo(1);
    }

    @Test
    void comAConversaoLigadaAPlanilhaPodeSerSelecionadaParaODossie() {
        when(conversao.habilitada()).thenReturn(true);

        assertThat(selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "3")))).extracting(Documento::id)
                .containsExactly("3");
    }

    @Test
    void comAConversaoDesligadaAPlanilhaVoltaAoBloqueioDaS22() {
        DocumentosListados lista = listar.executar(101);

        assertThat(lista.itens()).filteredOn(i -> i.documento().id().equals("3")).singleElement()
                .satisfies(i -> assertThat(i.bloqueio()).isEqualTo(Bloqueio.NAO_PDF));
        assertThat(lista.totais().convertiveis()).isZero();
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "3"))))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("conversão está indisponível");
    }

    @Test
    void sigilosoConvertivelContinuaBloqueadoParaOOperadorComAConversaoLigada() {
        when(conversao.habilitada()).thenReturn(true);
        when(documentos.listar(anyLong())).thenReturn(List.of(
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "Reservado", "reservado.docx", D1, null, 1)));

        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "1"))))
                .isInstanceOf(DocumentoSigilosoException.class);
        assertThat(listar.executar(101).totais().convertiveis()).isZero();
    }

    // --- ordem cronológica do servidor (RN03, S2.2)

    private List<ReferenciaDeDocumento> refs(String... origemEId) {
        return Arrays.stream(origemEId).map(s -> ref(s.split(":")[0], s.split(":")[1])).toList();
    }

    @Test
    void ordemDoDossieNaoDependeDaOrdemDaSelecao() {
        LocalDateTime mesmoInstante = LocalDateTime.of(2025, 4, 2, 14, 0);
        when(documentos.listar(anyLong())).thenReturn(List.of(
                doc(OrigemDocumento.IMOVEL, "10", "K10", "Matrícula", "m.pdf", mesmoInstante, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "10", "K11", "Ofício 10", "o10.pdf", mesmoInstante, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "9", "K12", "Ofício 9", "o9.pdf", mesmoInstante, null, 0),
                doc(OrigemDocumento.COMPLEMENTACAO, "3", "K13", "Sem data B", "b.pdf", null, null, 0),
                doc(OrigemDocumento.DOCUMENTO_PROCESSO, "4", "K14", "Sem data A", "a.pdf", null, null, 0),
                doc(OrigemDocumento.DOCUMENTO_REQUERIMENTO, "1", "K15", "Mais antigo", "r.pdf",
                        mesmoInstante.minusSeconds(1), null, 0)));
        List<String> esperado = List.of(
                "DOCUMENTO_REQUERIMENTO:1", // data mais antiga (por 1 segundo: timestamp completo)
                "DOCUMENTO_PROCESSO:9", // empate de data: origem, depois id numérico (9 antes de 10)
                "DOCUMENTO_PROCESSO:10",
                "IMOVEL:10",
                "DOCUMENTO_PROCESSO:4", // sem data ao final, também por origem
                "COMPLEMENTACAO:3");

        List<List<String>> selecoes = List.of(
                esperado,
                esperado.reversed(),
                List.of("IMOVEL:10", "COMPLEMENTACAO:3", "DOCUMENTO_PROCESSO:10", "DOCUMENTO_REQUERIMENTO:1",
                        "DOCUMENTO_PROCESSO:4", "DOCUMENTO_PROCESSO:9"));
        for (List<String> selecao : selecoes) {
            assertThat(selecionar.executar(101, refs(selecao.toArray(String[]::new))))
                    .extracting(d -> d.origem().name() + ":" + d.id())
                    .containsExactlyElementsOf(esperado);
        }
    }

    @Test
    void horariosDeBordaComJvmEmUtcMantemODiaEAOrdem() {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            when(documentos.listar(anyLong())).thenReturn(List.of(
                    doc(OrigemDocumento.DOCUMENTO_PROCESSO, "2", "K2", "Madrugada", "m.pdf",
                            LocalDateTime.of(2025, 4, 2, 0, 30), null, 0),
                    doc(OrigemDocumento.DOCUMENTO_PROCESSO, "1", "K1", "Noite", "n.pdf",
                            LocalDateTime.of(2025, 4, 1, 23, 30), null, 0)));

            List<Documento> escolhidos = selecionar.executar(101, refs("DOCUMENTO_PROCESSO:2", "DOCUMENTO_PROCESSO:1"));

            assertThat(escolhidos).extracting(Documento::titulo).containsExactly("Noite", "Madrugada");
            assertThat(escolhidos.get(0).data().toLocalDate()).isEqualTo(LocalDate.of(2025, 4, 1));
            assertThat(escolhidos.get(1).data().toLocalDate()).isEqualTo(LocalDate.of(2025, 4, 2));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void substituidoEmPdfPodeSerMarcadoManualmente() {
        assertThat(selecionar.executar(101, List.of(ref("DOCUMENTO_REQUERIMENTO", "5")))).hasSize(1);
    }

    @Test
    void referenciaRepetidaEntraUmaVez() {
        assertThat(selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "1"), ref("DOCUMENTO_PROCESSO", "1"))))
                .hasSize(1);
    }

    @Test
    void documentoDeOutroRequerimentoOuInexistenteEhRejeitado() {
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "999"))))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("DOCUMENTO_PROCESSO/999");
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("ORIGEM_FALSA", "1"))))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref(null, "1"))))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void geometriaECompactadoNaoPertencemAListaEPortantoSaoRejeitados() {
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "6"))))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "7"))))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void indisponivelENaoPdfSaoRejeitadosIndicandoQual() {
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "2"))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("DOCUMENTO_PROCESSO/2").hasMessageContaining("indisponível");
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "3"))))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessageContaining("DOCUMENTO_PROCESSO/3").hasMessageContaining("não é PDF");
    }

    @Test
    void sigilosoParaOperadorEhProibidoSemRevelarOTitulo() {
        assertThatThrownBy(() -> selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "1"), ref("DOCUMENTO_PROCESSO", "4"))))
                .isInstanceOf(DocumentoSigilosoException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Laudo", "laudo"));
    }

    @Test
    void gestorPodeSelecionarSigiloso() {
        comoGestor();

        assertThat(selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "4")))).hasSize(1);
    }

    @Test
    void selecaoVaziaOuNulaEhRejeitada() {
        assertThatThrownBy(() -> selecionar.executar(101, List.of())).isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> selecionar.executar(101, null)).isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void operadorDeOutroInteressadoNaoSelecionaNada() {
        assertThatThrownBy(() -> selecionar.executar(202, List.of(ref("DOCUMENTO_PROCESSO", "1"))))
                .isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void aSelecaoRelaOSigaUmaUnicaVezPorChamada() {
        selecionar.executar(101, List.of(ref("DOCUMENTO_PROCESSO", "1"), ref("DOCUMENTO_REQUERIMENTO", "5")));

        verify(documentos, times(1)).listar(101);
    }
}
