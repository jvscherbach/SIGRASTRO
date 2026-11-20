package br.gov.mt.sema.rastro.dossie.application;

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

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.consulta.application.DetalheDoRequerimento;
import br.gov.mt.sema.rastro.consulta.application.ObterRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento.AtividadeLicenciamento;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento.FinalidadeAtividade;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.documento.application.ReferenciaDeDocumento;
import br.gov.mt.sema.rastro.documento.application.SelecaoParaDossie;
import br.gov.mt.sema.rastro.documento.application.SelecionarDocumentosParaDossie;
import br.gov.mt.sema.rastro.documento.domain.Documento;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Arquivos;
import br.gov.mt.sema.rastro.dossie.application.Fakes.FilaSincrona;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Montador;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Parametros;
import br.gov.mt.sema.rastro.dossie.application.Fakes.RelogioControlado;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Repositorio;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Tickets;
import br.gov.mt.sema.rastro.dossie.application.Fakes.Validador;
import br.gov.mt.sema.rastro.dossie.domain.DocumentoNaoIncluido;
import br.gov.mt.sema.rastro.dossie.domain.ErroDoDossie;
import br.gov.mt.sema.rastro.dossie.domain.PedidoDeDossie;
import br.gov.mt.sema.rastro.dossie.domain.StatusDossie;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.application.exception.ArmazenamentoInsuficienteException;
import br.gov.mt.sema.rastro.shared.application.exception.ArquivoDoDossieIndisponivelException;
import br.gov.mt.sema.rastro.shared.application.exception.DocumentoSigilosoException;
import br.gov.mt.sema.rastro.shared.application.exception.DossieExpiradoException;
import br.gov.mt.sema.rastro.shared.application.exception.CredencialArquivosRecusadaException;
import br.gov.mt.sema.rastro.shared.application.exception.DossieSemArquivoException;
import br.gov.mt.sema.rastro.shared.application.exception.DossieSemDecisaoPendenteException;
import br.gov.mt.sema.rastro.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.TicketInvalidoException;
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
    private ContinuarDossie continuar;
    private Tickets tickets;
    private EmitirTicketDeDownload emitirTicket;
    private Montador montador;
    private ListarDossiesGerados listarDossies;
    private Fakes.Espaco espaco;

    @BeforeEach
    void preparar() {
        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                if (usuario == null) {
                    throw new br.gov.mt.sema.rastro.shared.application.exception.NaoAutenticadoException("sem sessão");
                }
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
        montador = new Montador();
        espaco = new Fakes.Espaco();
        GeradorDeDossie gerador = new GeradorDeDossie(arquivos, Fakes.preparador(new Validador()), montador, parametros, espaco, relogio, Fakes.eventos(atual));
        Usuario titular = usuario;
        localizar = new LocalizarPedido(repositorio, new PoliticaDeAcesso(atual), parametros, relogio,
                login -> java.util.Optional.of(titular));
        criar = new CriarDossie(requerimentos, selecao, atual, repositorio, fila, gerador, parametros, relogio, Fakes.eventos(atual), Fakes.semRetrato());
        tickets = new Tickets();
        obterArquivo = new ObterArquivoDoDossie(localizar, parametros, tickets, atual, relogio, Fakes.eventos(atual));
        emitirTicket = new EmitirTicketDeDownload(obterArquivo, tickets, atual, relogio);
        cancelar = new CancelarDossie(localizar, parametros, gerador);
        continuar = new ContinuarDossie(localizar, fila, gerador, parametros, relogio, Fakes.semRetrato());
        consultar = new ConsultarDossie(localizar, parametros);
        limpar = new LimparDossies(repositorio, parametros, relogio);
        listarDossies = new ListarDossiesGerados(localizar, new PoliticaDeAcesso(atual), parametros, relogio);

        Requerimento requerimento = new Requerimento(101, "3331/2025", "2825/2025", "Requerimento de Outorga",
                Situacao.de("EM_ANALISE"), LocalDateTime.of(2025, 3, 31, 10, 12), null,
                new Interessado("Associação Residencial Vale Verde", "12345678000190"), "Uso de Recursos Hídricos");
        ObjetoDoRequerimento objeto = new ObjetoDoRequerimento(List.of("Consumo humano", "Irrigação"),
                List.of(new FinalidadeAtividade("Abastecimento público", "Captação"), new FinalidadeAtividade("Agricultura", null)),
                List.of(), new AtividadeLicenciamento("Mineração", "Extração de areia"));
        when(requerimentos.executar(101)).thenReturn(new DetalheDoRequerimento(requerimento, objeto,
                PrioridadeAtendimento.de(List.of()), TarefaAtual.semTarefa()));
        when(selecao.executarComInventario(anyLong(), anyList())).thenReturn(sem(List.of(
                Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "1", 101, "K1", "Requerimento", "r.pdf", D1, null, 0),
                Documento.deLinha(OrigemDocumento.COMPLEMENTACAO, "2", 101, "K2", null, "oficio.pdf", D1.plusDays(1), null, 0))));
    }

    /** Seleção em que tudo o que foi listado foi escolhido. */
    private static SelecaoParaDossie sem(List<Documento> escolhidos) {
        return new SelecaoParaDossie(escolhidos, List.of(), escolhidos.size());
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
        assertThat(pedido.status()).isEqualTo(StatusDossie.AGUARDANDO);
        assertThat(pedido.login()).isEqualTo("gestor.demo");
        assertThat(pedido.itens()).extracting(i -> i.origem() + "/" + i.id() + ":" + i.titulo())
                .containsExactly("DOCUMENTO_PROCESSO/1:r.pdf", "COMPLEMENTACAO/2:oficio.pdf");
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
        verify(selecao, times(1)).executarComInventario(anyLong(), anyList());
        assertThat(arquivos.baixadas).containsExactly("K1", "K2");
    }

    @Test
    void requerimentoSemAcessoOuInexistenteNaoCriaPedidoNemLeDocumentos() {
        when(requerimentos.executar(202)).thenThrow(new NaoInteressadoException());
        when(requerimentos.executar(404)).thenThrow(new RecursoNaoEncontradoException("Requerimento não encontrado."));

        assertThatThrownBy(() -> criar.executar(202, refs())).isInstanceOf(NaoInteressadoException.class);
        assertThatThrownBy(() -> criar.executar(404, refs())).isInstanceOf(RecursoNaoEncontradoException.class);

        assertThat(repositorio.todos()).isEmpty();
        verify(selecao, never()).executarComInventario(anyLong(), any());
    }

    @Test
    void selecaoInvalidaOuSigilosaNaoCriaPedido() {
        doThrow(new ParametroInvalidoException("documentos", "inválido")).when(selecao).executarComInventario(anyLong(), anyList());
        assertThatThrownBy(() -> criar.executar(101, refs())).isInstanceOf(ParametroInvalidoException.class);

        doThrow(new DocumentoSigilosoException()).when(selecao).executarComInventario(anyLong(), anyList());
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
                .containsOnly(br.gov.mt.sema.rastro.dossie.domain.SituacaoItem.OK);
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
    void arquivoEm409QuandoEmAndamentoOuAguardandoDecisaoOuCancelado() {
        fila.adiar = true;
        String emAndamento = criar.executar(101, refs());
        assertThatThrownBy(() -> obterArquivo.executar(emAndamento)).isInstanceOf(DossieSemArquivoException.class);

        fila.adiar = false;
        arquivos.comportamentos.put("K1", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        String falho = criar.executar(101, refs());
        assertThat(consultar.executar(falho).status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
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
        assertThat(pasta.resolve(pedido.id())).as("o diretório do pedido some com o download").doesNotExist();
        assertThat(pedido.status()).isEqualTo(StatusDossie.EXPIRADO);
        assertThatThrownBy(() -> obterArquivo.executar(pedido.id())).isInstanceOf(DossieExpiradoException.class);
    }

    @Test
    void arquivoQueSumiuDoDiscoEh410ComCodigoEOPedidoViraFalha() throws IOException {
        PedidoDeDossie pedido = criarPedido();
        Files.delete(pedido.arquivo());

        assertThatThrownBy(() -> obterArquivo.executar(pedido.id()))
                .isInstanceOf(ArquivoDoDossieIndisponivelException.class)
                .satisfies(e -> assertThat(((ArquivoDoDossieIndisponivelException) e).errorCode())
                        .isEqualTo(ErrorCode.DOSSIE_ARQUIVO_AUSENTE));
        assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
        assertThat(pedido.erro().codigo()).isEqualTo(ErroDoDossie.ARQUIVO_AUSENTE);
        assertThat(pedido.arquivo()).isNull();
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
        assertThatThrownBy(() -> obterArquivo.executar(pedido.id())).isInstanceOf(DossieSemArquivoException.class);
    }

    @Test
    void arquivoVazioOuTruncadoEh500ComCodigoENuncaEntregue() throws IOException {
        PedidoDeDossie truncado = criarPedido();
        Files.write(truncado.arquivo(), new byte[] {'%', 'P'});
        PedidoDeDossie vazio = criarPedido();
        Files.write(vazio.arquivo(), new byte[0]);

        for (PedidoDeDossie pedido : List.of(truncado, vazio)) {
            assertThatThrownBy(() -> obterArquivo.executar(pedido.id()))
                    .isInstanceOf(ArquivoDoDossieIndisponivelException.class)
                    .satisfies(e -> assertThat(((ArquivoDoDossieIndisponivelException) e).errorCode())
                            .isEqualTo(ErrorCode.DOSSIE_ARQUIVO_INVALIDO));
            assertThat(pedido.status()).isEqualTo(StatusDossie.FALHA);
            assertThat(pedido.erro().codigo()).isEqualTo(ErroDoDossie.ARQUIVO_INVALIDO);
            assertThat(pasta.resolve(pedido.id())).doesNotExist();
        }
    }

    @Test
    void semEspacoMinimoOPostEh507SemCriarPedidoNemLerOSiga() {
        parametros.espacoMinimoMb = 500;
        espaco.livre = 500L * 1024 * 1024 - 1;

        assertThatThrownBy(() -> criar.executar(101, refs()))
                .isInstanceOf(ArmazenamentoInsuficienteException.class)
                .hasMessage(ArmazenamentoInsuficienteException.MENSAGEM_DO_DOSSIE)
                .satisfies(e -> assertThat(((ArmazenamentoInsuficienteException) e).errorCode())
                        .isEqualTo(ErrorCode.ARMAZENAMENTO_INSUFICIENTE));
        assertThat(repositorio.pedidos).isEmpty();
        assertThat(fila.pendentes).isEmpty();
    }

    @Test
    void espacoMinimoZeroDesligaAVerificacaoPrevia() {
        parametros.espacoMinimoMb = 0;
        espaco.livre = 0;

        String id = criar.executar(101, refs());

        assertThat(repositorio.pedidos).containsKey(id);
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

    // --- ticket de download (S2.2, D49)

    @Test
    void ticketTem256BitsAleatoriosEOServidorGuardaSoOResumo() {
        PedidoDeDossie pedido = criarPedido();

        EmitirTicketDeDownload.TicketEmitido emitido = emitirTicket.executar(pedido.id());
        EmitirTicketDeDownload.TicketEmitido outro = emitirTicket.executar(pedido.id());

        assertThat(java.util.Base64.getUrlDecoder().decode(emitido.ticket())).hasSize(32);
        assertThat(emitido.ticket()).isNotEqualTo(outro.ticket());
        assertThat(emitido.expiraEm()).isEqualTo(LocalDateTime.now(relogio).plusSeconds(60));
        assertThat(tickets.guardados).hasSize(2).doesNotContainKey(emitido.ticket())
                .containsKey(br.gov.mt.sema.rastro.dossie.domain.TicketDeDownload.resumo(emitido.ticket()));
        assertThat(tickets.guardados.values()).allSatisfy(t -> {
            assertThat(t.idPedido()).isEqualTo(pedido.id());
            assertThat(t.login()).isEqualTo("gestor.demo");
        });
        assertThat(emitido.toString()).doesNotContain(emitido.ticket());
    }

    @Test
    void ticketValeUmaVezSoMesmoSemUsuarioNaRequisicao() {
        PedidoDeDossie pedido = criarPedido();
        String ticket = emitirTicket.executar(pedido.id()).ticket();
        usuario = null;

        assertThat(obterArquivo.executarPorTicket(pedido.id(), ticket).caminho()).exists();
        assertThatThrownBy(() -> obterArquivo.executarPorTicket(pedido.id(), ticket))
                .isInstanceOf(TicketInvalidoException.class);
    }

    @Test
    void ticketVencidoEhInvalido() {
        PedidoDeDossie pedido = criarPedido();
        String ticket = emitirTicket.executar(pedido.id()).ticket();
        relogio.avancarSegundos(59);
        String outro = emitirTicket.executar(pedido.id()).ticket();
        relogio.avancarSegundos(1);

        assertThatThrownBy(() -> obterArquivo.executarPorTicket(pedido.id(), ticket))
                .isInstanceOf(TicketInvalidoException.class);
        assertThat(obterArquivo.executarPorTicket(pedido.id(), outro)).isNotNull();
    }

    @Test
    void ticketDeOutroPedidoEhInvalidoEFicaConsumido() {
        PedidoDeDossie primeiro = criarPedido();
        PedidoDeDossie segundo = criarPedido();
        String ticket = emitirTicket.executar(primeiro.id()).ticket();

        assertThatThrownBy(() -> obterArquivo.executarPorTicket(segundo.id(), ticket))
                .isInstanceOf(TicketInvalidoException.class);
        assertThatThrownBy(() -> obterArquivo.executarPorTicket(primeiro.id(), ticket))
                .isInstanceOf(TicketInvalidoException.class);
    }

    @Test
    void ticketApresentadoPorOutroUsuarioAutenticadoEhInvalido() {
        PedidoDeDossie pedido = criarPedido();
        String ticket = emitirTicket.executar(pedido.id()).ticket();
        usuario = new Usuario("admin.demo", "Admin", Perfil.ADMIN, null, true);

        assertThatThrownBy(() -> obterArquivo.executarPorTicket(pedido.id(), ticket))
                .isInstanceOf(TicketInvalidoException.class);
    }

    @Test
    void ticketInexistenteOuVazioEhInvalido() {
        PedidoDeDossie pedido = criarPedido();

        for (String ticket : new String[] {"nao-existe", "", null}) {
            assertThatThrownBy(() -> obterArquivo.executarPorTicket(pedido.id(), ticket))
                    .isInstanceOf(TicketInvalidoException.class);
        }
    }

    @Test
    void ticketSoSaiComArquivoEParaQuemAcessaOPedido() {
        fila.adiar = true;
        String emAndamento = criar.executar(101, refs());
        assertThatThrownBy(() -> emitirTicket.executar(emAndamento)).isInstanceOf(DossieSemArquivoException.class);

        fila.adiar = false;
        PedidoDeDossie doGestor = criarPedido();
        usuario = new Usuario("outro.operador", "Outro", Perfil.OPERADOR, "98.765.432/0001-10", true);
        assertThatThrownBy(() -> emitirTicket.executar(doGestor.id())).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(tickets.guardados).isEmpty();
    }

    @Test
    void downloadPorTicketConcluidoApagaODiretorioEDepoisEh410() {
        PedidoDeDossie pedido = criarPedido();
        String ticket = emitirTicket.executar(pedido.id()).ticket();
        usuario = null;

        ObterArquivoDoDossie.ArquivoDoDossie arquivo = obterArquivo.executarPorTicket(pedido.id(), ticket);
        obterArquivo.concluirDownload(arquivo);

        assertThat(pasta.resolve(pedido.id())).doesNotExist();
        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        assertThatThrownBy(() -> emitirTicket.executar(pedido.id())).isInstanceOf(DossieExpiradoException.class);
    }

    @Test
    void andamentoIndicaEntregaPorTicketAcimaDoLimite() {
        PedidoDeDossie pedido = criarPedido();
        assertThat(consultar.executar(pedido.id()).entregaPorTicket()).isFalse();
        assertThat(consultar.executar(pedido.id()).tamanhoBytes()).isEqualTo(pedido.tamanhoBytes());

        parametros.blobMaxMb = 0;

        assertThat(consultar.executar(pedido.id()).entregaPorTicket()).isTrue();
    }

    @Test
    void andamentoSemArquivoNaoTemTamanhoNemTicket() {
        fila.adiar = true;
        String id = criar.executar(101, refs());

        assertThat(consultar.executar(id).tamanhoBytes()).isNull();
        assertThat(consultar.executar(id).entregaPorTicket()).isFalse();
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

        // já 1 min depois do vencimento: falta 1 min para sair do histórico
        relogio.avancarMinutos(parametros.historicoHoras * 60L - 2);
        limpar.expirarVencidos();
        assertThat(repositorio.buscar(pedido.id())).as("histórico de Dossiês gerados (S4)").isPresent();
        relogio.avancarMinutos(1);
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
    void pendenteDeDecisaoSemAtividadeExpiraApagaODiretorioEDepoisEEsquecido() {
        arquivos.comportamentos.put("K1", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        String id = criar.executar(101, refs());
        PedidoDeDossie pedido = repositorio.buscar(id).orElseThrow();
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pasta.resolve(id)).as("o documento obtido fica no disco").isDirectoryContaining("glob:**.pdf");

        relogio.avancarMinutos(31);
        assertThat(limpar.expirarVencidos()).isEqualTo(1);
        assertThat(pedido.status()).isEqualTo(StatusDossie.EXPIRADO);
        assertThat(pasta.resolve(id)).doesNotExist();

        relogio.avancarMinutos(parametros.historicoHoras * 60L);
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

    // --- continuação (S2.2): coleta falhas e continua de onde parou

    /** 10 documentos em ordem cronológica (ids 1 a 10, chaves K1 a K10). */
    private void dezDocumentos() {
        List<Documento> dez = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            dez.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, String.valueOf(i), 101, "K" + i, "Doc " + i,
                    "d" + i + ".pdf", D1.plusDays(i), null, 0));
        }
        when(selecao.executarComInventario(anyLong(), anyList())).thenReturn(sem(dez));
    }

    private PedidoDeDossie pedidoComFalhasEm3E7() {
        dezDocumentos();
        arquivos.comportamentos.put("K3", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        arquivos.comportamentos.put("K7", Arquivos.falhaDoDocumento("tempo limite excedido ao obter o arquivo"));
        return criarPedido();
    }

    private long vezesBaixada(String chave) {
        return arquivos.baixadas.stream().filter(chave::equals).count();
    }

    @Test
    void dezDocumentosComDoisFalhandoObtemOsOitoUmaVezSoEAguardaDecisao() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(arquivos.baixadas).as("não para na primeira falha").hasSize(10);
        for (int i = 1; i <= 10; i++) {
            assertThat(vezesBaixada("K" + i)).as("K" + i).isEqualTo(1);
        }
        AndamentoDoDossie andamento = consultar.executar(pedido.id());
        assertThat(andamento.falhas()).extracting(f -> f.id() + ":" + f.motivo()).containsExactly(
                "3:arquivo não encontrado no repositório", "7:tempo limite excedido ao obter o arquivo");
        assertThat(andamento.processados()).isEqualTo(8);
        assertThat(montador.titulosMontados).as("nada é entregue sem decisão").isEmpty();
        assertThatThrownBy(() -> obterArquivo.executar(pedido.id())).isInstanceOf(DossieSemArquivoException.class);
    }

    @Test
    void tentarNovamenteChamaSoOsDoisQueFalharamEConclui() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        arquivos.comportamentos.clear();
        arquivos.baixadas.clear();

        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");

        assertThat(arquivos.baixadas).containsExactly("K3", "K7");
        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(montador.titulosMontados).singleElement().asList().hasSize(10);
        assertThat(montador.titulosMontados.get(0)).containsExactly("d1.pdf", "d2.pdf", "d3.pdf", "d4.pdf",
                "d5.pdf", "d6.pdf", "d7.pdf", "d8.pdf", "d9.pdf", "d10.pdf");
    }

    @Test
    void tentarNovamenteQueFalhaDeNovoVoltaParaADecisaoSemRebaixarOsObtidos() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        arquivos.comportamentos.remove("K3");
        arquivos.baixadas.clear();

        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");

        assertThat(arquivos.baixadas).containsExactly("K3", "K7");
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(consultar.executar(pedido.id()).falhas()).extracting(AndamentoDoDossie.Item::id).containsExactly("7");
        assertThat(pedido.processados()).isEqualTo(9);
    }

    @Test
    void removerEContinuarNaoBaixaNadaDeNovoEMontaOsOitoEmOrdemCronologica() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        arquivos.baixadas.clear();

        continuar.executar(pedido.id(), "REMOVER_E_CONTINUAR");

        assertThat(arquivos.baixadas).as("nenhum download novo").isEmpty();
        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(montador.titulosMontados).singleElement().isEqualTo(List.of("d1.pdf", "d2.pdf", "d4.pdf",
                "d5.pdf", "d6.pdf", "d8.pdf", "d9.pdf", "d10.pdf"));
        assertThat(pedido.itens()).extracting(i -> i.id() + ":" + i.situacao()).contains("3:REMOVIDO", "7:REMOVIDO");
        assertThat(pedido.processados()).as("removidos contam como resolvidos").isEqualTo(10);
    }

    @Test
    void removidosENaoSelecionadosChegamAoMontadorEmOrdemComMotivoEOTotalListado() {
        List<Documento> dez = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            dez.add(Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, String.valueOf(i), 101, "K" + i, "Doc " + i,
                    "d" + i + ".pdf", D1.plusDays(i), null, 0));
        }
        // Doc 0 (mais antigo que todos) é indisponível; Doc 11 (mais novo) foi desmarcado pelo usuário.
        Documento indisponivel = Documento.deLinha(OrigemDocumento.IMOVEL, "0", 101, "0", "Doc 0", "d0.pdf", D1, null, 0);
        Documento desmarcado = Documento.deLinha(OrigemDocumento.DOCUMENTO_PROCESSO, "11", 101, "K11", "Doc 11",
                "d11.pdf", D1.plusDays(11), null, 0);
        when(selecao.executarComInventario(anyLong(), anyList())).thenReturn(new SelecaoParaDossie(dez, List.of(
                new SelecaoParaDossie.NaoSelecionado(desmarcado, "Doc 11", SelecaoParaDossie.Motivo.NAO_SELECIONADO),
                new SelecaoParaDossie.NaoSelecionado(indisponivel, "Doc 0", SelecaoParaDossie.Motivo.INDISPONIVEL)), 12));
        arquivos.comportamentos.put("K3", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        PedidoDeDossie pedido = criarPedido();

        continuar.executar(pedido.id(), "REMOVER_E_CONTINUAR");

        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(montador.inventarios).singleElement().satisfies(inventario -> {
            assertThat(inventario.listados()).isEqualTo(12);
            assertThat(inventario.naoIncluidos()).extracting(d -> d.id() + ":" + d.motivo()).containsExactly(
                    "0:indisponível (chave do arquivo inválida)",
                    "3:removido pelo usuário após falha: arquivo não encontrado no repositório",
                    "11:não selecionado pelo usuário");
            assertThat(inventario.naoIncluidos()).extracting(DocumentoNaoIncluido::origem)
                    .containsExactly("Imóvel", "Documento do processo", "Documento do processo");
        });
        assertThat(montador.titulosMontados.get(0)).hasSize(9);
    }

    @Test
    void semNadaForaOInventarioVemVazioComOsListados() {
        criarPedido();

        assertThat(montador.inventarios).singleElement().satisfies(inventario -> {
            assertThat(inventario.listados()).isEqualTo(2);
            assertThat(inventario.naoIncluidos()).isEmpty();
        });
    }

    @Test
    void oProgressoNaoVoltaAZeroDepoisDaDecisao() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        fila.adiar = true;

        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");

        assertThat(pedido.status()).isEqualTo(StatusDossie.AGUARDANDO);
        assertThat(consultar.executar(pedido.id()).processados()).isEqualTo(8);
        assertThat(pedido.itens()).extracting(i -> i.id() + ":" + i.situacao()).contains("3:AGUARDANDO", "7:AGUARDANDO",
                "1:OK", "10:OK");
    }

    @Test
    void credencialRecusadaNoMeioPreservaOsObtidosEComCredencialNovaSoOsPendentesSaoObtidos() {
        dezDocumentos();
        arquivos.comportamentos.put("K4", destino -> {
            throw new CredencialArquivosRecusadaException();
        });
        PedidoDeDossie pedido = criarPedido();

        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
        assertThat(pedido.erro().codigo()).isEqualTo("CREDENCIAL_ARQUIVOS_RECUSADA");
        assertThat(arquivos.baixadas).containsExactly("K1", "K2", "K3", "K4");
        assertThat(pedido.processados()).isEqualTo(3);

        // credencial nova (o token por arquivo foi trocado): só os pendentes são obtidos
        arquivos.comportamentos.clear();
        arquivos.baixadas.clear();
        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");

        assertThat(arquivos.baixadas).containsExactly("K4", "K5", "K6", "K7", "K8", "K9", "K10");
        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
        assertThat(pedido.erro()).isNull();
    }

    @Test
    void removerSemNenhumDocumentoObtidoEhParametroInvalido() {
        arquivos.comportamentos.put("K1", destino -> {
            throw new CredencialArquivosRecusadaException();
        });
        PedidoDeDossie pedido = criarPedido();

        assertThatThrownBy(() -> continuar.executar(pedido.id(), "REMOVER_E_CONTINUAR"))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
    }

    @Test
    void ttlContaDesdeAUltimaAtividadeInclusiveNaEsperaPorDecisao() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        LocalDateTime decisaoPendenteDesde = LocalDateTime.now(relogio);
        assertThat(pedido.expiraEm()).isEqualTo(decisaoPendenteDesde.plusMinutes(30));

        relogio.avancarMinutos(29);
        assertThat(consultar.executar(pedido.id()).status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);

        // nova ação (que falha de novo): o prazo recomeça
        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");
        assertThat(pedido.expiraEm()).isEqualTo(LocalDateTime.now(relogio).plusMinutes(30));
        relogio.avancarMinutos(29);
        assertThat(limpar.expirarVencidos()).isZero();
        assertThat(consultar.executar(pedido.id()).status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);

        relogio.avancarMinutos(2);
        assertThat(consultar.executar(pedido.id()).status()).isEqualTo(StatusDossie.EXPIRADO);
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
        assertThatThrownBy(() -> continuar.executar(pedido.id(), "TENTAR_NOVAMENTE"))
                .isInstanceOf(DossieSemDecisaoPendenteException.class);
    }

    @Test
    void cancelarPedidoAguardandoDecisaoApagaODiretorio() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        assertThat(pasta.resolve(pedido.id())).exists();

        cancelar.executar(pedido.id());

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
        assertThatThrownBy(() -> continuar.executar(pedido.id(), "REMOVER_E_CONTINUAR"))
                .isInstanceOf(DossieSemDecisaoPendenteException.class);
    }

    @Test
    void cancelarDepoisDaDecisaoAntesDeAFilaRodarApagaODiretorioENaoBaixaNada() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        fila.adiar = true;
        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");
        arquivos.baixadas.clear();

        cancelar.executar(pedido.id());
        fila.pendentes.forEach(Runnable::run);

        assertThat(pedido.status()).isEqualTo(StatusDossie.CANCELADO);
        assertThat(arquivos.baixadas).isEmpty();
        assertThat(pasta.resolve(pedido.id())).doesNotExist();
    }

    @Test
    void aoConcluirSoOPdfFinalFicaNoDiretorioMesmoDepoisDeUmaContinuacao() throws IOException {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();
        arquivos.comportamentos.clear();

        continuar.executar(pedido.id(), "TENTAR_NOVAMENTE");

        try (var arquivosDoPedido = Files.list(pasta.resolve(pedido.id()))) {
            assertThat(arquivosDoPedido.map(p -> p.getFileName().toString())).containsExactly("dossie.pdf");
        }
    }

    @Test
    void concluidoTambemTemODiretorioApagadoAoCancelarEAoExpirar() {
        PedidoDeDossie cancelado = criarPedido();
        cancelar.executar(cancelado.id());
        assertThat(pasta.resolve(cancelado.id())).doesNotExist();

        PedidoDeDossie vencido = criarPedido();
        relogio.avancarMinutos(31);
        limpar.expirarVencidos();
        assertThat(pasta.resolve(vencido.id())).doesNotExist();
    }

    @Test
    void continuacaoForaDePendenteDeDecisaoEh409() {
        PedidoDeDossie concluido = criarPedido();
        assertThatThrownBy(() -> continuar.executar(concluido.id(), "TENTAR_NOVAMENTE"))
                .isInstanceOf(DossieSemDecisaoPendenteException.class);

        fila.adiar = true;
        String naFila = criar.executar(101, refs());
        assertThatThrownBy(() -> continuar.executar(naFila, "REMOVER_E_CONTINUAR"))
                .isInstanceOf(DossieSemDecisaoPendenteException.class);
    }

    @Test
    void acaoDesconhecidaOuVaziaEhParametroInvalido() {
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();

        for (String acao : new String[] {"REINICIAR", "", null, "tentar_novamente"}) {
            assertThatThrownBy(() -> continuar.executar(pedido.id(), acao)).isInstanceOf(ParametroInvalidoException.class);
        }
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);
    }

    @Test
    void soQuemCriouGestorEAdminDecidem() {
        usuario = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "12.345.678/0001-90", true);
        PedidoDeDossie pedido = pedidoComFalhasEm3E7();

        usuario = new Usuario("outro.operador", "Outro", Perfil.OPERADOR, "98.765.432/0001-10", true);
        assertThatThrownBy(() -> continuar.executar(pedido.id(), "TENTAR_NOVAMENTE"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(pedido.status()).isEqualTo(StatusDossie.PENDENTE_DECISAO);

        usuario = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
        continuar.executar(pedido.id(), "REMOVER_E_CONTINUAR");
        assertThat(pedido.status()).isEqualTo(StatusDossie.CONCLUIDO);
    }

    // --- "Dossiês gerados" (S4, bloco 7)

    private static final Usuario OPERADOR = new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR,
            "12.345.678/0001-90", true);
    private static final Usuario OUTRO_OPERADOR = new Usuario("outro.operador", "Outro", Perfil.OPERADOR,
            "12.345.678/0001-90", true);
    private static final Usuario GESTOR = new Usuario("gestor.demo", "Gestor Demo", Perfil.OPERADOR_SIGILO, null, true);
    private static final Usuario ADMIN = new Usuario("admin.demo", "Admin", Perfil.ADMIN, null, true);

    private String criarComo(Usuario quem) {
        usuario = quem;
        String id = criar.executar(101, refs());
        relogio.avancarSegundos(1);
        return id;
    }

    private List<String> idsListados(Usuario quem, boolean somenteMeus) {
        usuario = quem;
        return listarDossies.executar(somenteMeus, null, null).itens().stream()
                .map(ListarDossiesGerados.DossieGerado::id).toList();
    }

    @Test
    void operadorVeSoOsSeusEGestorEAdministradorVeemTodosOuSoOsSeus() {
        String doOperador = criarComo(OPERADOR);
        String doOutro = criarComo(OUTRO_OPERADOR);
        String doGestor = criarComo(GESTOR);
        String doAdmin = criarComo(ADMIN);

        assertThat(idsListados(OPERADOR, false)).containsExactly(doOperador);
        assertThat(idsListados(OPERADOR, true)).containsExactly(doOperador);
        assertThat(idsListados(OUTRO_OPERADOR, false)).containsExactly(doOutro);
        assertThat(idsListados(GESTOR, false)).containsExactly(doAdmin, doGestor, doOutro, doOperador);
        assertThat(idsListados(GESTOR, true)).containsExactly(doGestor);
        assertThat(idsListados(ADMIN, false)).hasSize(4);
        assertThat(idsListados(ADMIN, true)).containsExactly(doAdmin);
    }

    @Test
    void quemGerouSoParaGestorEAdministrador() {
        criarComo(OPERADOR);

        usuario = OPERADOR;
        assertThat(listarDossies.executar(false, null, null).itens()).singleElement()
                .satisfies(d -> assertThat(d.geradoPor()).isNull());
        usuario = GESTOR;
        assertThat(listarDossies.executar(false, null, null).itens()).singleElement()
                .satisfies(d -> assertThat(d.geradoPor()).isEqualTo("Operador Demo"));
    }

    @Test
    void operadorQueDeixouDeSerOInteressadoNaoVeOProprioPedido() {
        criarComo(OPERADOR);

        assertThat(idsListados(new Usuario("operador.demo", "Operador Demo", Perfil.OPERADOR, "98.765.432/0001-10",
                true), false)).isEmpty();
    }

    private ListarDossiesGerados.DossieGerado unico(String id) {
        usuario = GESTOR;
        return listarDossies.executar(false, null, 50).itens().stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("não listado: " + id));
    }

    @Test
    void todasAsSituacoesComArquivoETamanho() {
        fila.adiar = true;
        String naFila = criarComo(GESTOR);
        String obtendo = criarComo(GESTOR);
        String montando = criarComo(GESTOR);
        repositorio.buscar(obtendo).orElseThrow().iniciar();
        PedidoDeDossie emMontagem = repositorio.buscar(montando).orElseThrow();
        emMontagem.iniciar();
        emMontagem.iniciarMontagem();
        fila.adiar = false;
        String concluido = criarComo(GESTOR);
        String cancelado = criarComo(GESTOR);
        cancelar.executar(cancelado);
        arquivos.comportamentos.put("K2", Arquivos.falhaDoDocumento("arquivo não encontrado no repositório"));
        String pendente = criarComo(GESTOR);
        arquivos.comportamentos.remove("K2");
        String baixado = criarComo(GESTOR);
        obterArquivo.concluirDownload(baixado);

        assertThat(unico(naFila).situacao()).isEqualTo(ListarDossiesGerados.Situacao.AGUARDANDO);
        assertThat(unico(obtendo).situacao()).isEqualTo(ListarDossiesGerados.Situacao.OBTENDO);
        assertThat(unico(montando).situacao()).isEqualTo(ListarDossiesGerados.Situacao.MONTANDO);
        assertThat(unico(cancelado).situacao()).isEqualTo(ListarDossiesGerados.Situacao.CANCELADO);
        ListarDossiesGerados.DossieGerado comFalha = unico(pendente);
        assertThat(comFalha.situacao()).isEqualTo(ListarDossiesGerados.Situacao.PENDENTE_DECISAO);
        assertThat(comFalha.falhas()).isEqualTo(1);
        assertThat(comFalha.temArquivo()).isFalse();
        ListarDossiesGerados.DossieGerado pronto = unico(concluido);
        assertThat(pronto.situacao()).isEqualTo(ListarDossiesGerados.Situacao.CONCLUIDO);
        assertThat(pronto.temArquivo()).isTrue();
        assertThat(pronto.tamanhoBytes()).isPositive();
        assertThat(pronto.documentos()).isEqualTo(2);
        assertThat(pronto.falhas()).isZero();
        assertThat(pronto.rotulo()).isEqualTo("Processo");
        assertThat(pronto.numero()).isEqualTo("2825/2025");
        assertThat(pronto.idRequerimento()).isEqualTo(101);
        ListarDossiesGerados.DossieGerado entregue = unico(baixado);
        assertThat(entregue.situacao()).isEqualTo(ListarDossiesGerados.Situacao.BAIXADO);
        assertThat(entregue.temArquivo()).isFalse();
        assertThat(entregue.tamanhoBytes()).isNull();
        assertThat(entregue.arquivoGerado()).isTrue();

        // vence: a pendente expira sem arquivo gerado; a concluída expira depois de gerar
        relogio.avancarMinutos(31);
        assertThat(unico(pendente).situacao()).isEqualTo(ListarDossiesGerados.Situacao.EXPIRADO);
        assertThat(unico(pendente).arquivoGerado()).isFalse();
        assertThat(unico(concluido).situacao()).isEqualTo(ListarDossiesGerados.Situacao.EXPIRADO);
        assertThat(unico(concluido).arquivoGerado()).isTrue();
        assertThat(unico(concluido).temArquivo()).isFalse();
    }

    @Test
    void historicoFicaDossieHistoricoHorasDepoisDoVencimentoESomeDepois() {
        parametros.historicoHoras = 2;
        String id = criarComo(GESTOR);

        relogio.avancarMinutos(31);
        limpar.expirarVencidos();
        assertThat(unico(id).situacao()).isEqualTo(ListarDossiesGerados.Situacao.EXPIRADO);

        relogio.avancarMinutos(2 * 60 - 5);
        limpar.expirarVencidos();
        assertThat(repositorio.buscar(id)).isPresent();
        assertThat(idsListados(GESTOR, false)).containsExactly(id);

        relogio.avancarMinutos(10);
        // a lista já esconde antes da limpeza agendada passar
        assertThat(idsListados(GESTOR, false)).isEmpty();
        limpar.expirarVencidos();
        assertThat(repositorio.buscar(id)).isEmpty();
    }

    @Test
    void paginacaoComMaisRecentePrimeiroELimites() {
        List<String> criados = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            criados.add(criarComo(GESTOR));
        }
        java.util.Collections.reverse(criados);

        usuario = GESTOR;
        ListarDossiesGerados.Pagina primeira = listarDossies.executar(false, 0, 2);
        ListarDossiesGerados.Pagina terceira = listarDossies.executar(false, 2, 2);
        assertThat(primeira.total()).isEqualTo(5);
        assertThat(primeira.itens()).extracting(ListarDossiesGerados.DossieGerado::id)
                .containsExactly(criados.get(0), criados.get(1));
        assertThat(terceira.itens()).extracting(ListarDossiesGerados.DossieGerado::id).containsExactly(criados.get(4));
        assertThat(listarDossies.executar(false, null, null).tamanho()).isEqualTo(20);
        assertThatThrownBy(() -> listarDossies.executar(false, 0, 51))
                .isInstanceOf(br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException.class);
        assertThatThrownBy(() -> listarDossies.executar(false, 0, 0))
                .isInstanceOf(br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException.class);
        assertThatThrownBy(() -> listarDossies.executar(false, -1, 10))
                .isInstanceOf(br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException.class);
    }
}
