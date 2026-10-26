package br.gov.mt.sema.vistas.dossie.infrastructure.agendamento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** AGENDAMENTO_ENABLED liga e desliga as tarefas de limpeza do dossiê (docs/padroes/agendamento.md). */
class AgendamentoDaLimpezaTest {

    @Nested
    @SpringBootTest(properties = {"vistas.demo.senha=x", "vistas.datasource.siga.url=", "vistas.arquivos.url=",
            "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-agendamento"})
    class Ligado {

        @Autowired
        ApplicationContext contexto;

        @Test
        void porPadraoALimpezaEstaAgendada() {
            assertThat(contexto.getBeansOfType(LimpezaAgendada.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = {"vistas.demo.senha=x", "vistas.datasource.siga.url=", "vistas.arquivos.url=",
            "vistas.agendamento.enabled=false", "vistas.dossie.diretorio=${java.io.tmpdir}/vistas-teste-agendamento"})
    class Desligado {

        @Autowired
        ApplicationContext contexto;

        @Test
        void comAgendamentoDesligadoNenhumaTarefaExiste() {
            assertThat(contexto.getBeansOfType(LimpezaAgendada.class)).isEmpty();
        }
    }
}
