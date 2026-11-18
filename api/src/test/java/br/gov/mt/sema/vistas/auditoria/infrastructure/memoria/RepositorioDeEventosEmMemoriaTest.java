package br.gov.mt.sema.vistas.auditoria.infrastructure.memoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import br.gov.mt.sema.vistas.auditoria.application.port.ParametrosDaAuditoria;
import br.gov.mt.sema.vistas.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.vistas.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.vistas.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.vistas.auditoria.domain.NovoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.vistas.auditoria.domain.UsuarioDoEvento;
import org.junit.jupiter.api.Test;

class RepositorioDeEventosEmMemoriaTest {

    private static final Instant INICIO = Instant.parse("2026-03-10T15:00:00Z");
    private static final UsuarioDoEvento USUARIO = new UsuarioDoEvento("gestor.demo", "Gestor Demo", "Gestor");

    private static class RelogioControlado extends Clock {

        private Instant agora = INICIO;

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private record Parametros(int maximoDeEventos, int retencaoDocumentosAnos, int retencaoAcessosAnos,
            int maximoDeLinhasDoCsv)
            implements ParametrosDaAuditoria {
    }

    private final RelogioControlado relogio = new RelogioControlado();

    private RepositorioDeEventosEmMemoria repositorio(int teto) {
        return new RepositorioDeEventosEmMemoria(new Parametros(teto, 5, 1, 50_000), relogio);
    }

    private static NovoEvento login() {
        return NovoEvento.de(TipoDeEvento.LOGIN, USUARIO).criar();
    }

    private static NovoEvento consulta() {
        return NovoEvento.de(TipoDeEvento.CONSULTA, USUARIO).requerimento(1L, "2825/2025", null).criar();
    }

    private static List<Long> ids(RepositorioDeEventosEmMemoria repositorio, GrupoDeEvento grupo) {
        return repositorio.doPeriodo(grupo, null, null).map(EventoDeAuditoria::id).toList();
    }

    @Test
    void numera_data_e_separa_por_grupo_do_mais_recente_para_o_mais_antigo() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);

        EventoDeAuditoria primeiro = repositorio.incluir(consulta());
        relogio.avancar(Duration.ofMinutes(1));
        EventoDeAuditoria segundo = repositorio.incluir(login());
        repositorio.incluir(consulta());

        assertThat(primeiro.id()).isEqualTo(1);
        assertThat(primeiro.dataHora().toInstant()).isEqualTo(INICIO);
        assertThat(primeiro.dataHora().getZone()).isEqualTo(EventoDeAuditoria.FUSO);
        assertThat(segundo.id()).isEqualTo(2);
        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS)).containsExactly(3L, 1L);
        assertThat(ids(repositorio, GrupoDeEvento.ACESSOS)).containsExactly(2L);
    }

    @Test
    void filtra_por_periodo_com_inicio_inclusive_e_fim_exclusive() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);
        for (int i = 0; i < 4; i++) {
            repositorio.incluir(consulta());
            relogio.avancar(Duration.ofHours(1));
        }

        assertThat(repositorio.doPeriodo(GrupoDeEvento.DOCUMENTOS, INICIO.plus(Duration.ofHours(1)),
                INICIO.plus(Duration.ofHours(3))).map(EventoDeAuditoria::id)).containsExactly(3L, 2L);
        assertThat(repositorio.doPeriodo(GrupoDeEvento.DOCUMENTOS, INICIO.plus(Duration.ofHours(3)), null)
                .map(EventoDeAuditoria::id)).containsExactly(4L);
    }

    @Test
    void busca_por_id_so_dentro_do_grupo() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);
        long id = repositorio.incluir(login()).id();

        assertThat(repositorio.porId(GrupoDeEvento.ACESSOS, id)).isPresent();
        assertThat(repositorio.porId(GrupoDeEvento.DOCUMENTOS, id)).isEmpty();
        assertThat(repositorio.porId(GrupoDeEvento.ACESSOS, 999)).isEmpty();
    }

    @Test
    void evento_invalido_nao_entra_nem_gasta_id() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);

        assertThatThrownBy(() -> repositorio.incluir(NovoEvento.de(TipoDeEvento.CONSULTA, USUARIO).criar()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS)).isEmpty();
        assertThat(repositorio.incluir(consulta()).id()).isEqualTo(1);
    }

    @Test
    void teto_descarta_os_mais_antigos_somando_os_grupos() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(3);

        repositorio.incluir(login());
        relogio.avancar(Duration.ofSeconds(1));
        repositorio.incluir(consulta());
        relogio.avancar(Duration.ofSeconds(1));
        repositorio.incluir(consulta());
        relogio.avancar(Duration.ofSeconds(1));
        repositorio.incluir(login());
        relogio.avancar(Duration.ofSeconds(1));
        repositorio.incluir(consulta());

        assertThat(ids(repositorio, GrupoDeEvento.ACESSOS)).containsExactly(4L);
        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS)).containsExactly(5L, 3L);
        assertThat(repositorio.porId(GrupoDeEvento.ACESSOS, 1)).isEmpty();
    }

    @Test
    void retencao_por_grupo_com_relogio_controlado() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);
        repositorio.incluir(consulta());
        repositorio.incluir(login());

        relogio.avancar(Duration.ofDays(365 + 1));
        assertThat(repositorio.aplicar()).isEqualTo(1);
        assertThat(ids(repositorio, GrupoDeEvento.ACESSOS)).isEmpty();
        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS)).containsExactly(1L);
        assertThat(repositorio.porId(GrupoDeEvento.ACESSOS, 2)).isEmpty();

        relogio.avancar(Duration.ofDays(365 * 4 + 5));
        assertThat(repositorio.aplicar()).isEqualTo(1);
        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS)).isEmpty();
        assertThat(repositorio.aplicar()).isZero();
    }

    @Test
    void evento_dentro_da_retencao_fica() {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100);
        repositorio.incluir(login());

        relogio.avancar(Duration.ofDays(300));

        assertThat(repositorio.aplicar()).isZero();
        assertThat(ids(repositorio, GrupoDeEvento.ACESSOS)).containsExactly(1L);
    }

    @Test
    void inclusao_concorrente_sem_perda_nem_id_repetido() throws Exception {
        RepositorioDeEventosEmMemoria repositorio = repositorio(100_000);
        int threads = 8;
        int porThread = 500;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> tarefas = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                tarefas.add(executor.submit(() -> {
                    largada.await();
                    for (int i = 0; i < porThread; i++) {
                        repositorio.incluir(consulta());
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> tarefa : tarefas) {
                tarefa.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(ids(repositorio, GrupoDeEvento.DOCUMENTOS))
                .containsExactlyElementsOf(LongStream.rangeClosed(1, threads * porThread).map(i -> threads * porThread + 1 - i).boxed().toList());
    }

    @Test
    void a_interface_do_repositorio_nao_oferece_alterar_nem_apagar() {
        assertThat(Arrays.stream(RepositorioDeEventos.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("incluir", "doPeriodo", "porId");
    }

    @Test
    void o_evento_nao_tem_metodo_que_altere_estado() {
        assertThat(EventoDeAuditoria.class.isRecord()).isTrue();
        assertThat(Arrays.stream(EventoDeAuditoria.class.getDeclaredFields())
                .filter(campo -> !Modifier.isStatic(campo.getModifiers())))
                .allMatch(campo -> Modifier.isFinal(campo.getModifiers()));
        assertThat(Arrays.stream(RepositorioDeEventosEmMemoria.class.getMethods()).map(Method::getName))
                .noneMatch(nome -> nome.matches("(?i)(alterar|atualizar|apagar|excluir|remover|delete|update|set).*"));
    }
}
