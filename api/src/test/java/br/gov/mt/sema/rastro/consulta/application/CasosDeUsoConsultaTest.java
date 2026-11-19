package br.gov.mt.sema.rastro.consulta.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import br.gov.mt.sema.rastro.consulta.domain.CriterioBusca;
import br.gov.mt.sema.rastro.consulta.domain.Interessado;
import br.gov.mt.sema.rastro.consulta.domain.NumeroRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Pagina;
import br.gov.mt.sema.rastro.consulta.domain.PrioridadeAtendimento;
import br.gov.mt.sema.rastro.consulta.domain.ResponsavelTarefa;
import br.gov.mt.sema.rastro.consulta.domain.Requerimento;
import br.gov.mt.sema.rastro.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.rastro.consulta.domain.Situacao;
import br.gov.mt.sema.rastro.consulta.domain.TarefaAtual;
import br.gov.mt.sema.rastro.shared.application.exception.NaoInteressadoException;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import br.gov.mt.sema.rastro.shared.application.exception.SigaIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CasosDeUsoConsultaTest {

    private static final String CNPJ_DO_OPERADOR = "12.345.678/0001-90";

    private static Requerimento requerimento(long id, String numero, String processo, String cpfCnpj) {
        return new Requerimento(id, numero, processo, null, Situacao.de("EM_ANALISE"),
                LocalDateTime.of(2025, 3, 31, 10, 0), null,
                cpfCnpj == null ? null : new Interessado("Fulano", cpfCnpj), "Uso de Recursos Hídricos");
    }

    /** Porta falsa: guarda os requerimentos e o último critério recebido. */
    private static final class SigaFalso
            implements RequerimentosDoSiga, TarefaAtualDoRequerimento, ObjetoEPrioridadeDoRequerimento {
        final List<Requerimento> todos = new ArrayList<>();
        CriterioBusca ultimoCriterio;
        int consultasDeLista;
        TarefaAtual tarefa = TarefaAtual.semTarefa();
        RuntimeException falhaNaTarefa;

        @Override
        public Optional<Requerimento> buscarPorNumeroDeRequerimento(NumeroRequerimento numero) {
            return todos.stream().filter(r -> numero.texto().equals(r.numeroRequerimento())).findFirst();
        }

        @Override
        public ObjetoDoRequerimento objeto(long idRequerimento) {
            return ObjetoDoRequerimento.vazio();
        }

        @Override
        public PrioridadeAtendimento prioridade(long idRequerimento) {
            return PrioridadeAtendimento.de(List.of());
        }

        @Override
        public Optional<Requerimento> buscarPorId(long id) {
            return todos.stream().filter(r -> r.id() == id).findFirst();
        }

        @Override
        public Pagina<ResumoRequerimento> buscarPorCriterios(CriterioBusca criterio) {
            ultimoCriterio = criterio;
            consultasDeLista++;
            // Filtra por número de processo e pela restrição, como a consulta real; sem esses critérios devolve tudo.
            List<ResumoRequerimento> itens = todos.stream()
                    .filter(r -> criterio.numero() == null || criterio.numero().texto().equals(r.numeroProcesso()))
                    .filter(r -> criterio.restricaoDigitos() == null || criterio.restricaoDigitos()
                            .equals(r.cpfCnpjDoInteressado() == null ? "" : r.cpfCnpjDoInteressado().replaceAll("\\D", "")))
                    .map(Requerimento::resumo).toList();
            return new Pagina<>(itens, itens.size(), criterio.pagina(), criterio.tamanho());
        }

        @Override
        public TarefaAtual obter(long idRequerimento) {
            if (falhaNaTarefa != null) {
                throw falhaNaTarefa;
            }
            return tarefa;
        }
    }

    private SigaFalso siga;
    private Usuario usuario;
    private BuscarRequerimentos buscar;
    private ObterRequerimento obter;

    @BeforeEach
    void preparar() {
        siga = new SigaFalso();
        siga.todos.add(requerimento(1, "3331/2025", "2825/2025", CNPJ_DO_OPERADOR));
        siga.todos.add(requerimento(2, "1187/2026", null, "45.678.901/0001-23"));
        siga.todos.add(requerimento(3, "10/2026", null, null));
        como(Perfil.OPERADOR, CNPJ_DO_OPERADOR);
    }

    private void como(Perfil perfil, String cpfCnpj) {
        usuario = new Usuario("u", "U", perfil, cpfCnpj, true);
        UsuarioAtual atual = new UsuarioAtual() {
            @Override
            public Usuario obter() {
                return usuario;
            }

            @Override
            public String token() {
                return "t";
            }
        };
        PoliticaDeAcesso politica = new PoliticaDeAcesso(atual);
        buscar = new BuscarRequerimentos(siga, politica);
        obter = new ObterRequerimento(siga, siga, siga, politica);
    }

    private static PedidoDeBusca porNumero(String tipo, String numero) {
        return new PedidoDeBusca(tipo, numero, null, null, null, null);
    }

    private static PedidoDeBusca porNome(String nome) {
        return new PedidoDeBusca(null, null, null, nome, null, null);
    }

    // --- critérios

    @Test
    void criterioInvalidoEhRejeitadoAntesDeConsultarOSiga() {
        assertThatThrownBy(() -> buscar.executar(new PedidoDeBusca(null, null, null, null, null, null)))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> buscar.executar(porNumero("REQUERIMENTO", "3331")))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThat(siga.consultasDeLista).isZero();
    }

    // --- busca por número

    @Test
    void numeroDeRequerimentoDoProprioOperadorDevolvePaginaComUmItem() {
        Pagina<ResumoRequerimento> pagina = buscar.executar(porNumero("REQUERIMENTO", "3331/2025"));

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.itens()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(1);
            assertThat(r.identificacao().numero()).isEqualTo("2825/2025"); // RN04
        });
    }

    @Test
    void numeroDeProcessoUnicoDevolveORequerimentoCorrespondente() {
        Pagina<ResumoRequerimento> pagina = buscar.executar(porNumero("PROCESSO", "2825/2025"));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(1L);
    }

    @Test
    void numeroInexistenteEh404() {
        assertThatThrownBy(() -> buscar.executar(porNumero("PROCESSO", "9/2020")))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Processo 9/2020");
        assertThatThrownBy(() -> buscar.executar(porNumero("REQUERIMENTO", "2825/2025")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void operadorBuscandoNumeroDeOutroInteressadoEh403() {
        assertThatThrownBy(() -> buscar.executar(porNumero("REQUERIMENTO", "1187/2026")))
                .isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void operadorBuscandoRequerimentoSemInteressadoEh403() {
        assertThatThrownBy(() -> buscar.executar(porNumero("REQUERIMENTO", "10/2026")))
                .isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void operadorSemCpfCnpjNaoAcessaNenhumNumero() {
        como(Perfil.OPERADOR, null);

        assertThatThrownBy(() -> buscar.executar(porNumero("REQUERIMENTO", "3331/2025")))
                .isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void gestorEAdministradorAcessamNumeroDeQualquerInteressado() {
        for (Perfil perfil : new Perfil[] {Perfil.OPERADOR_SIGILO, Perfil.ADMIN}) {
            como(perfil, null);

            assertThat(buscar.executar(porNumero("REQUERIMENTO", "1187/2026")).total()).isEqualTo(1);
            assertThat(buscar.executar(porNumero("REQUERIMENTO", "10/2026")).total()).isEqualTo(1);
        }
    }

    // --- listas

    @Test
    void operadorTemARestricaoAplicadaNaConsultaDaLista() {
        buscar.executar(porNome("fulano"));

        assertThat(siga.ultimoCriterio.restricaoDigitos()).isEqualTo("12345678000190");
    }

    @Test
    void operadorSemCpfCnpjRecebeListaVaziaSemConsultarOSiga() {
        como(Perfil.OPERADOR, "");

        Pagina<ResumoRequerimento> pagina = buscar.executar(porNome("fulano"));

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.total()).isZero();
        assertThat(siga.consultasDeLista).isZero();
    }

    @Test
    void gestorNaoTemRestricaoENaoPassaDigitos() {
        como(Perfil.OPERADOR_SIGILO, null);

        buscar.executar(porNome("fulano"));

        assertThat(siga.ultimoCriterio.restricaoDigitos()).isNull();
    }

    @Test
    void paginacaoDoPedidoChegaAoSiga() {
        como(Perfil.ADMIN, null);

        buscar.executar(new PedidoDeBusca(null, null, null, "fulano", 3, 50));

        assertThat(siga.ultimoCriterio.pagina()).isEqualTo(3);
        assertThat(siga.ultimoCriterio.tamanho()).isEqualTo(50);
    }

    // --- detalhe

    @Test
    void detalheDoProprioRequerimentoTrazATarefa() {
        siga.tarefa = TarefaAtual.ativa("Analisar requerimento", LocalDateTime.of(2025, 4, 29, 9, 14),
                ResponsavelTarefa.atribuido("Maria Nova"), "Outorga");

        DetalheDoRequerimento detalhe = obter.executar(1);

        assertThat(detalhe.requerimento().numeroRequerimento()).isEqualTo("3331/2025");
        assertThat(detalhe.tarefaAtual().estado()).isEqualTo(TarefaAtual.Estado.ATIVA);
    }

    @Test
    void detalheInexistenteEh404EDeOutroInteressadoEh403() {
        assertThatThrownBy(() -> obter.executar(999)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> obter.executar(2)).isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void falhaAoLerATarefaNaoDerrubaODetalhe() {
        siga.falhaNaTarefa = new SigaIndisponivelException();

        DetalheDoRequerimento detalhe = obter.executar(1);

        assertThat(detalhe.requerimento().id()).isEqualTo(1);
        assertThat(detalhe.tarefaAtual().estado()).isEqualTo(TarefaAtual.Estado.INDISPONIVEL);
    }

    @Test
    void tarefaSemInstanciaVirouSemTarefa() {
        assertThat(obter.executar(1).tarefaAtual().estado()).isEqualTo(TarefaAtual.Estado.SEM_TAREFA);
    }

    // --- S1.1: número de processo repetido (1:N)

    private void comProcessoRepetido() {
        siga.todos.add(requerimento(11, "3391/2022", "445/2022", CNPJ_DO_OPERADOR));
        siga.todos.add(requerimento(12, "3798/2023", "445/2022", "45.678.901/0001-23"));
    }

    @Test
    void numeroDeProcessoRepetidoDevolveTodosOsRequerimentosParaOGestor() {
        comProcessoRepetido();
        como(Perfil.OPERADOR_SIGILO, null);

        Pagina<ResumoRequerimento> pagina = buscar.executar(porNumero("PROCESSO", "445/2022"));

        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(11L, 12L);
        assertThat(siga.ultimoCriterio.restricaoDigitos()).isNull();
    }

    @Test
    void numeroDeProcessoRepetidoDevolveSoOsDoOperador() {
        comProcessoRepetido();

        Pagina<ResumoRequerimento> pagina = buscar.executar(porNumero("PROCESSO", "445/2022"));

        assertThat(pagina.itens()).extracting(ResumoRequerimento::id).containsExactly(11L);
        assertThat(siga.ultimoCriterio.restricaoDigitos()).isEqualTo("12345678000190");
    }

    @Test
    void numeroDeProcessoQueExisteSoParaOutrosInteressadosEh403() {
        siga.todos.add(requerimento(13, "1/2022", "500/2022", "45.678.901/0001-23"));

        assertThatThrownBy(() -> buscar.executar(porNumero("PROCESSO", "500/2022")))
                .isInstanceOf(NaoInteressadoException.class);
    }

    @Test
    void operadorSemCpfCnpjBuscandoProcessoExistenteEh403EInexistenteEh404() {
        como(Perfil.OPERADOR, null);

        assertThatThrownBy(() -> buscar.executar(porNumero("PROCESSO", "2825/2025")))
                .isInstanceOf(NaoInteressadoException.class);
        assertThatThrownBy(() -> buscar.executar(porNumero("PROCESSO", "9/2020")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // --- S1.1: responsável pela tarefa por perfil

    @Test
    void operadorNaoRecebeOResponsavelDaTarefa() {
        siga.tarefa = TarefaAtual.ativa("Analisar", LocalDateTime.of(2026, 1, 1, 8, 0),
                ResponsavelTarefa.atribuido("Maria Nova"), "Outorga");

        TarefaAtual tarefa = obter.executar(1).tarefaAtual();

        assertThat(tarefa.estado()).isEqualTo(TarefaAtual.Estado.ATIVA);
        assertThat(tarefa.nome()).isEqualTo("Analisar");
        assertThat(tarefa.responsavel()).isNull();
    }

    @Test
    void gestorEAdministradorRecebemOResponsavelDaTarefa() {
        siga.tarefa = TarefaAtual.ativa("Analisar", LocalDateTime.of(2026, 1, 1, 8, 0),
                ResponsavelTarefa.atribuido("Maria Nova"), "Outorga");
        for (Perfil perfil : new Perfil[] {Perfil.OPERADOR_SIGILO, Perfil.ADMIN}) {
            como(perfil, null);

            assertThat(obter.executar(1).tarefaAtual().responsavel())
                    .isEqualTo(ResponsavelTarefa.atribuido("Maria Nova"));
        }
    }

    @Test
    void detalheTrazObjetoEPrioridadeDaPorta() {
        DetalheDoRequerimento detalhe = obter.executar(1);

        assertThat(detalhe.objeto()).isEqualTo(ObjetoDoRequerimento.vazio());
        assertThat(detalhe.prioridade().tipo()).isEqualTo(PrioridadeAtendimento.Tipo.NORMAL);
    }
}
