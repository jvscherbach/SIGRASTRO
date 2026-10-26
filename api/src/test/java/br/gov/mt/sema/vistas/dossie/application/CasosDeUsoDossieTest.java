package br.gov.mt.sema.vistas.dossie.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.gov.mt.sema.vistas.acesso.application.UsuarioAtual;
import br.gov.mt.sema.vistas.acesso.domain.Perfil;
import br.gov.mt.sema.vistas.acesso.domain.Usuario;
import br.gov.mt.sema.vistas.consulta.application.DetalheDoRequerimento;
import br.gov.mt.sema.vistas.consulta.application.ObterRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.Interessado;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.AtividadeLicenciamento;
import br.gov.mt.sema.vistas.consulta.domain.ObjetoDoRequerimento.FinalidadeAtividade;
import br.gov.mt.sema.vistas.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.vistas.consulta.domain.Requerimento;
import br.gov.mt.sema.vistas.consulta.domain.Situacao;
import br.gov.mt.sema.vistas.consulta.domain.TarefaAtual;
import br.gov.mt.sema.vistas.documento.application.ReferenciaDeDocumento;
import br.gov.mt.sema.vistas.documento.application.SelecionarDocumentosParaDossie;
import br.gov.mt.sema.vistas.documento.domain.Documento;
import br.gov.mt.sema.vistas.documento.domain.OrigemDocumento;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.vistas.dossie.application.Fakes.FilaSincrona;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Montador;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.vistas.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Repositorio;
import br.gov.mt.sema.vistas.dossie.application.Fakes.Validador;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.vistas.dossie.domain.StatusDossie;
import br.gov.mt.sema.vistas.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.vistas.shared.application.exception.DossieExpiradoException;
import br.gov.mt.sema.vistas.shared.application.exception.DossieSemArquivoException;
import br.gov.mt.sema.vistas.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CasosDeUsoDossieTest {

    private static final LocalDateTime D1 = LocalDateTime.of(2025, 4, 1, 10, 0);

    @TempDir
    Path pasta;

    private Usuario usuario;
    private final ObterRequerimento requerimentos = mock(ObterRequerimento.class);
    private final SelecionarDocumentosParaDossie selecao = mock(SelecionarDocumentosParaDossie.class);
    private Repositorio repositorio;
    private FilaSincrona fila;
    private Arquivos arquivos;
    private Parametros parametros;
    private RelogioControlado relogio;
    private LocalizarPedido localizar;
    private CriarDossie criar;
    private ObterArquivoDoDossie obterArquivo;
    private CancelarDossie cancelar;
    private ConsultarDossie consultar;
    private LimparDossies limpar;

    @BeforeEach
    void preparar() {
        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                return usuario;
            }

            @Override
            public String token() {
                return "token";
            }
        };
        repositorio = new Repositorio();
        fila = new FilaSincrona();
        arquivos = new Arquivos();
        parametros = new Parametros(pasta);
        relogio = new RelogioControlado();
        GeradorDeDossie gerador = new GeradorDeDossie(arquivos, new Validador(), new Montador(), parametros, d -> Long.MAX_VALUE, relogio);
        localizar = new LocalizarPedido(repositorio, atual, relogio);
        criar = new CriarDossie(requerimentos, selecao, atual, repositorio, fila, gerador, parametros, relogio);
        obterArquivo = new ObterArquivoDoDossie(localizar);
        cancelar = new CancelarDossie(localizar);
        consultar = new ConsultarDossie(localizar);
        limpar = new LimparDossies(repositorio, parametros, relogio);

        Requerimento requerimento = new Requerimento(101, "3331/2025", "2825/2025", "Requerimento de Outorga",
                Situacao.de("EM_ANALISE"), LocalDateTime.of(2025, 3, 31, 10, 12), null,
                new Interessado("Associação Residencial Vale Verde", "12345678000190"), "Uso de Recursos Hídricos");
        ObjetoDoRequerimento objeto = new ObjetoDoRequerimento(List.of("Consumo humano", "Irrigação"),
                List.of(new FinalidadeAtividade("Abastecimento público", "Captação"), new FinalidadeAtividade("Agricultura", null)),
                List.of(), new AtividadeLicenciamento("Mineração", "Extração de areia"));
        when(requerimentos.executar(101)).thenReturn(new DetalheDoRequerimento(requerimento, objeto,
                PrioridadeAtendimento.de(List.of()), TarefaAtual.semTarefa()));
        when(selecao.executar(anyLong(), anyList())).thenReturn(List.of(
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 101, "K1", "Requerimento", "r.pdf", D1, null, 0),
                Documento.deLinha(OrigemDocumento.COMPLEMENTACAO, "2", 101, "K2", null, "oficio.pdf", D1.plusDays(1), null, 0)));
    }

    private static List<ReferenciaDeDocumento> refs() {
        return List.of(new ReferenciaDeDocumento("DOCUMENTO_PROCESSO", "1"), new ReferenciaDeDocumento("COMPLEMENTACAO", "2"));
    }

    private PedidoDeDossie criarPedido() {
        return repositorio.buscar(criar.executar(101, refs())).orElseThrow();
    }

    // --- criação

    @Test
    void criaOPedidoComIdAleatorioInstantaneoECapaEEnfileira() {
        fila.adiar = true;

        String id = criar.executar(101, refs());

        assertThat(UUID.fromString(id)).isNotNull();
        PedidoDeDossie pedido = repositorio.buscar(id).orElseThrow();
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE);
        assertThat(pedido.login()).isEqualTo("gestor.demo");
        assertThat(pedido.itens()).extracting(i -> i.origem() + "/" + i.id() + ":" + i.titulo())
                .containsExactly("DOCUMENTO_PROCESSO/1:Requerimento", "COMPLEMENTACAO/2:oficio.pdf");
        assertThat(pedido.itens().get(0).chaveDoArquivo()).isEqualTo("K1");
        assertThat(pedido.expiraEm()).isEqualTo(LocalDateTime.now(relogio).plusMinutes(30));
        assertThat(fila.pendentes).hasSize(1);
        assertThat(arquivos.baixadas).as("nada é baixado antes de a fila executar").isEmpty();
    }

    @Test
    void capaUsaARn04ECpfFormatadoEObjetoComoTexto() {
        fila.adiar = true;

        PedidoDeDossie pedido = repositorio.buscar(criar.executar(101, refs())).orElseThrow();

        assertThat(pedido.capa().titulo()).isEqualTo("Dossiê do Processo 2825/2025");
        assertThat(pedido.capa().nomeDoArquivo()).isEqualTo("dossie-processo-2825-2025.pdf");
        assertThat(pedido.capa().cpfCnpj()).isEqualTo("12.345.678/0001-90");
        assertThat(pedido.capa().interessado()).isEqualTo("Associação Residencial Vale Verde");
        assertThat(pedido.capa().situacao()).isEqualTo("Em análise");
        assertThat(pedido.capa().objetivos()).containsExactly("Consumo humano", "Irrigação");
        assertThat(pedido.capa().atividades()).containsExactly("Abastecimento público - Captação", "Agricultura",
                "Extração de areia");
        assertThat(pedido.capa().usuario()).isEqualTo("Gestor Demo");
    }

    @Test
    void naoProtocoladoUsaORotuloRequerimentoNoNomeDoArquivo() {
        Requerimento semProcesso = new Requerimento(102, "18933/2026", null, null, Situacao.de("EM_CADASTRO"), D1, null,
                new Interessado("Fulano", "12345678901"), "Tipo");
        when(requerimentos.executar(102)).thenReturn(new DetalheDoRequerimento(semProcesso, ObjetoDoRequerimento.vazio(),
                PrioridadeAtendimento.de(List.of()), TarefaAtual.semTarefa()));
        fila.adiar = true;

        PedidoDeDossie pedido = repositorio.buscar(criar.executar(102, refs())).orElseThrow();

        assertThat(pedido.capa().nomeDoArquivo()).isEqualTo("dossie-requerimento-18933-2026.pdf");
        assertThat(pedido.capa().cpfCnpj()).isEqualTo("123.456.789-01");
    }

    @Test
    void oSigaNaoEReconsultadoDuranteAGeracao() {
        criarPedido();

        // o requerimento e a seleção foram lidos uma vez, na criação; a geração (executada pela fila) não os relê
        verify(requerimentos, times(1)).executar(101);
        verify(selecao, times(1)).executar(anyLong(), anyList());
        assertThat(arquivos.baixadas).containsExactly("K1", "K2");
    }

    @Test
    void requerimentoSemAcessoOuInexistenteNaoCriaPedidoNemLeDocumentos() {
        when(requerimentos.executar(202)).thenThrow(new NaoInteressadoException());
        when(requerimentos.executar(404)).thenThrow(new RecursoNaoEncontradoException("Requerimento não encontrado."));

        assertThatThrownBy(() -> criar.executar(202, refs())).isInstanceOf(NaoInteressadoException.class);
        assertThatThrownBy(() -> criar.executar(404, refs())).isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(repositorio.todos()).isEmpty();
        verify(selecao, never()).executar(anyLong(), any());
    }

    @Test
    void selecaoInvalidaOuSigilosaNaoCriaPedido() {
        doThrow(new ParametroInvalidoException("documentos", "inválido")).when(selecao).executar(anyLong(), anyList());
        assertThatThrownBy(() -> criar.executar(101, refs())).isInstanceOf(ParametroInvalidoException.class);

        doThrow(new DocumentoSigilosoException()).when(selecao).executar(anyLong(), anyList());
        assertThatThrownBy(() -> criar.executar(101, refs())).isInstanceOf(DocumentoSigilosoException.class);

        assertThat(repositorio.todos()).isEmpty();
        assertThat(fila.pendentes).isEmpty();
    }

    // --- acesso ao pedido

    @Test
    void soQuemCriouEGestorEAdminAcessamOPedido() {
        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true);
        String idDoOperador = criar.executar(101, refs());
        usuario = new Usuario("outro.operador", "Outro", Perfil.OPERADOR, "98.765.432/0001-10", true);

        assertThatThrownBy(() -> consultar.executar(idDoOperador)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> obterArquivo.executar(idDoOperador)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> cancelar.executar(idDoOperador)).isInstanceOf(RecursoNaoEncontradoException.class);

        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true);
        assertThat(consultar.executar(idDoOperador).status()).isEqualTo(StatusDossie.CONCLUIDO);
        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        assertThat(consultar.executar(idDoOperador).status()).isEqualTo(StatusDossie.CONCLUIDO);
        usuario = new Usuario("admin.demo", "Admin", Perfil.ADMIN, null, true);
        assertThat(consultar.executar(idDoOperador).status()).isEqualTo(StatusDossie.CONCLUIDO);
    }

    @Test
    void idInexistenteOuNuloEhNaoEncontrado() {
        assertThatThrownBy(() -> consultar.executar("nao-existe")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> consultar.executar(null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void andamentoTrazItensEProgresso() {
        PedidoDeDossie pedido = criarPedido();

        AndamentoDoDossie andamento = consultar.executar(pedido.id());

        assertThat(andamento.total()).isEqualTo(2);
        assertThat(andamento.processados()).isEqualTo(2);
        assertThat(andamento.itens()).extracting(AndamentoDoDossie.Item::situacao)
                .containsOnly(br.gov.mt.sema.vistas.dossie.domain.SituacaoItem.OK);
    }

    // --- arquivo

    @Test
    void arquivoDoConcluidoEntregaCaminhoNomeETamanho() {
        PedidoDeDossie pedido = criarPedido();

        ObterArquivoDoDossie.ArquivoDoDossie arquivo = obterArquivo.executar(pedido.id());

        assertThat(arquivo.nome()).isEqualTo("dossie-processo-2825-2025.pdf");
        assertThat(arquivo.caminho()).exists();
        assertThat(arquivo.tamanhoBytes()).isPositive();
    }

    @Test
    void arquivoEm409QuandoEmAndamentoOuFalhouOuCancelado() {
        fila.adiar = true;
        String emAndamento = criar.executar(101, refs());
        assertThatThrownBy(() -> obterArquivo.executar(emAndamento)).isInstanceOf(DossieSemArquivoException.class);

        fila.adiar = false;
        arquivos.comportamentos.put("K1", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        String falho = criar.executar(101, refs());
        assertThat(consultar.executar(falho).status()).isEqualTo(StatusDossie.FALHA);
        assertThatThrownBy(() -> obterArquivo.executar(falho)).isInstanceOf(DossieSemArquivoException.class);

        cancelar.executar(emAndamento);
        assertThatThrownBy(() -> obterArquivo.executar(emAndamento)).isInstanceOf(DossieSemArquivoException.class);
    }

    @Test
    void concluirDownloadApagaOArquivoEDepoisEh410() {
        PedidoDeDossie pedido = criarPedido();
        Path caminho = obterArquivo.executar(pedido.id()).caminho();

        obterArquivo.concluirDownload(pedido.id());

        assertThat(caminho).doesNotExist();
        assertThat(pedido.status()).isEqualTo(StatusDossie.EXPIRADO);
        assertThatThrownBy(() -> obterArquivo.executar(pedido.id())).isInstanceOf(DossieExpiradoException.class);
    }

    @Test
    void vencidoSemEsperarALimpezaAgendadaTambemEh410EOArquivoSome() {
        PedidoDeDossie pedido = criarPedido();
        Path caminho = pedido.arquivo();
        relogio.avancarMinutos(31);

        assertThatThrownBy(() -> obterArquivo.executar(pedido.id())).isInstanceOf(DossieExpiradoException.class);

        assertThat(caminho).doesNotExist();
        assertThat(consultar.executar(pedido.id()).status()).isEqualTo(StatusDossie.EXPIRADO);
    }

    // --- cancelamento

    @Test
    void cancelarDescartaOArquivoJaGeradoEEhIdempotente() {
        PedidoDeDossie pedido = criarPedido();
        Path caminho = pedido.arquivo();

        cancelar.executar(pedido.id());
        cancelar.executar(pedido.id());

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(caminho).doesNotExist();
    }

    // --- limpeza agendada (relógio controlado)

    @Test
    void limpezaExpiraVencidosApagaOArquivoEDepoisEsqueceORegistro() throws IOException {
        PedidoDeDossie pedido = criarPedido();
        Path caminho = pedido.arquivo();

        assertThat(limpar.expirarVencidos()).isZero();
        assertThat(caminho).exists();

        relogio.avancarMinutos(31);
        assertThat(limpar.expirarVencidos()).isEqualTo(1);
        assertThat(pedido.status()).isEqualTo(StatusDossie.EXPIRADO);
        assertThat(caminho).doesNotExist();
        assertThat(repositorio.buscar(pedido.id())).as("o registro fica um tempo para o front mostrar 'expirado'").isPresent();

        relogio.avancarMinutos(LimparDossies.RETENCAO_DO_REGISTRO_MINUTOS);
        limpar.expirarVencidos();
        assertThat(repositorio.buscar(pedido.id())).isEmpty();
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
    }

    @Test
    void limpezaEIdempotente() {
        criarPedido();
        relogio.avancarMinutos(31);

        limpar.expirarVencidos();

        assertThat(limpar.expirarVencidos()).isZero();
    }

    @Test
    void limpezaNaoMexeEmPedidoEmAndamento() {
        fila.adiar = true;
        String id = criar.executar(101, refs());
        relogio.avancarMinutos(500);

        limpar.expirarVencidos();

        assertThat(repositorio.buscar(id)).isPresent();
    }

    @Test
    void falhaTambemEEsquecidaDepoisDaRetencao() {
        arquivos.comportamentos.put("K1", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        String id = criar.executar(101, refs());
        assertThat(repositorio.buscar(id)).isPresent();

        relogio.avancarMinutos(30 + LimparDossies.RETENCAO_DO_REGISTRO_MINUTOS + 1);
        limpar.expirarVencidos();

        assertThat(repositorio.buscar(id)).isEmpty();
    }

    @Test
    void orfaosNaPastaTemporariaSaoApagadosEOPedidoAtivoFica() throws IOException {
        PedidoDeDossie ativo = criarPedido();
        Path orfa = Files.createDirectories(pasta.resolve("0f8fad5b-d9cb-469f-a165-70867728950e"));
        Files.writeString(orfa.resolve("0001.pdf"), "lixo");
        Files.writeString(pasta.resolve("nao-e-do-dossie.txt"), "documento do usuário");

        int apagados = limpar.apagarOrfaos();

        assertThat(apagados).isEqualTo(1);
        assertThat(orfa).doesNotExist();
        assertThat(pasta.resolve("nao-e-do-dossie.txt")).as("arquivo alheio ao dossiê nunca é apagado").exists();
        assertThat(ativo.arquivo()).exists();
    }

    @Test
    void naSubidaPastasDePedidoSobrandoSaoApagadas() throws IOException {
        Files.createDirectories(pasta.resolve("0f8fad5b-d9cb-469f-a165-70867728950e/b"));
        Files.writeString(pasta.resolve("0f8fad5b-d9cb-469f-a165-70867728950e/b/x.pdf"), "x");

        limpar.apagarOrfaos();

        try (var filhos = Files.list(pasta)) {
            assertThat(filhos.toList()).isEmpty();
        }
    }
}
