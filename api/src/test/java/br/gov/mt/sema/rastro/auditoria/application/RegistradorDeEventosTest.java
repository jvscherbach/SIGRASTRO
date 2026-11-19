package br.gov.mt.sema.rastro.auditoria.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.auditoria.application.port.RepositorioDeEventos;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.NovoEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.UsuarioDoEvento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class RegistradorDeEventosTest {

    private static final NovoEvento LOGIN =
            NovoEvento.de(TipoDeEvento.LOGIN, new UsuarioDoEvento("gestor.demo", "Gestor Demo", "Gestor")).criar();

    private static class RepositorioFalso implements RepositorioDeEventos {

        private final List<NovoEvento> incluidos = new ArrayList<>();

        @Override
        public EventoDeAuditoria incluir(NovoEvento evento) {
            incluidos.add(evento);
            return null;
        }

        @Override
        public Stream<EventoDeAuditoria> doPeriodo(GrupoDeEvento grupo, Instant desde, Instant ate) {
            return Stream.empty();
        }

        @Override
        public Optional<EventoDeAuditoria> porId(GrupoDeEvento grupo, long id) {
            return Optional.empty();
        }
    }

    @Test
    void repassa_o_evento_ao_repositorio() {
        RepositorioFalso repositorio = new RepositorioFalso();

        new RegistradorDeEventos(repositorio).registrar(LOGIN);

        assertThat(repositorio.incluidos).containsExactly(LOGIN);
    }

    @Test
    void repositorio_falhando_nao_chega_ao_chamador_nem_ao_log_com_dados(CapturedOutput saida) {
        RegistrarEvento registrar = new RegistradorDeEventos(new RepositorioFalso() {
            @Override
            public EventoDeAuditoria incluir(NovoEvento evento) {
                throw new IllegalStateException("gestor.demo dado-do-evento");
            }
        });

        assertThatCode(() -> registrar.registrar(LOGIN)).doesNotThrowAnyException();

        assertThat(saida).contains("Falha ao registrar evento de auditoria").doesNotContain("dado-do-evento", "gestor.demo");
    }

    @Test
    void evento_nulo_nao_chega_ao_chamador() {
        RegistrarEvento registrar = new RegistradorDeEventos(new RepositorioFalso() {
            @Override
            public EventoDeAuditoria incluir(NovoEvento evento) {
                return EventoDeAuditoria.de(1, null, evento);
            }
        });

        assertThatCode(() -> registrar.registrar(null)).doesNotThrowAnyException();
    }
}
