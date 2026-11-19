package br.gov.mt.sema.rastro.consulta.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ObjetoPrioridadeEResponsavelTest {

    // --- prioridade

    @Test
    void semCriteriosEhAtendimentoNormal() {
        PrioridadeAtendimento p = PrioridadeAtendimento.de(List.of());

        assertThat(p.tipo()).isEqualTo(PrioridadeAtendimento.Tipo.NORMAL);
        assertThat(p.descricao()).isEqualTo("Atendimento normal");
        assertThat(p.criterios()).isEmpty();
    }

    @Test
    void umCriterioEhAtendimentoPrioritario() {
        PrioridadeAtendimento p = PrioridadeAtendimento.de(List.of(1));

        assertThat(p.tipo()).isEqualTo(PrioridadeAtendimento.Tipo.PRIORITARIO);
        assertThat(p.descricao()).isEqualTo("Atendimento prioritário");
        assertThat(p.criterios()).containsExactly(new CriterioPrioridade(1, "Decisão Administrativa"));
    }

    @Test
    void variosCriteriosMantemAOrdemEContamUmaVezCada() {
        PrioridadeAtendimento p = PrioridadeAtendimento.de(List.of(2, 9, 2));

        assertThat(p.tipo()).isEqualTo(PrioridadeAtendimento.Tipo.PRIORITARIO);
        assertThat(p.criterios()).extracting(CriterioPrioridade::descricao).containsExactly(
                "Pessoa com idade igual ou superior a 80 anos", "Risco de grave prejuízo");
    }

    @Test
    void codigoDesconhecidoViraCriterioN() {
        assertThat(CriterioPrioridade.de(42).descricao()).isEqualTo("Critério 42");
        assertThat(PrioridadeAtendimento.de(List.of(42)).tipo()).isEqualTo(PrioridadeAtendimento.Tipo.PRIORITARIO);
    }

    @Test
    void todosOsCodigosConhecidosTemDescricao() {
        for (int codigo = 1; codigo <= 9; codigo++) {
            assertThat(CriterioPrioridade.de(codigo).descricao()).doesNotStartWith("Critério ");
        }
    }

    // --- tipo de outorga

    @Test
    void tiposConhecidosTemDescricaoComAcento() {
        assertThat(TipoOutorga.descricaoDe("SUBTERRANEA")).isEqualTo("Subterrânea");
        assertThat(TipoOutorga.descricaoDe("SUPERFICIAL")).isEqualTo("Superficial");
        assertThat(TipoOutorga.descricaoDe("BARRAMENTO")).isEqualTo("Barramento");
    }

    @Test
    void tipoDesconhecidoMantemOTextoCru() {
        assertThat(TipoOutorga.descricaoDe("LANCAMENTO")).isEqualTo("LANCAMENTO");
        assertThat(TipoOutorga.descricaoDe(null)).isNull();
        assertThat(ObjetoDoRequerimento.TipoObjetivo.de("LANCAMENTO", "Efluente").tipoDescricao()).isEqualTo("LANCAMENTO");
    }

    @Test
    void objetoVazioNaoTemNenhumBloco() {
        ObjetoDoRequerimento vazio = ObjetoDoRequerimento.vazio();

        assertThat(vazio.objetivos()).isEmpty();
        assertThat(vazio.finalidadesAtividades()).isEmpty();
        assertThat(vazio.tiposObjetivo()).isEmpty();
        assertThat(vazio.atividadeLicenciamento()).isNull();
    }

    // --- responsável pela tarefa

    @Test
    void responsavelNasTresSituacoes() {
        assertThat(ResponsavelTarefa.atribuido(" Maria ")).isEqualTo(
                new ResponsavelTarefa(ResponsavelTarefa.Situacao.ATRIBUIDO, "Maria"));
        assertThat(ResponsavelTarefa.naoAtribuida().situacao()).isEqualTo(ResponsavelTarefa.Situacao.NAO_ATRIBUIDA);
        assertThat(ResponsavelTarefa.naoAtribuida().nome()).isNull();
        assertThat(ResponsavelTarefa.naoIdentificado().situacao()).isEqualTo(ResponsavelTarefa.Situacao.NAO_IDENTIFICADO);
        assertThat(ResponsavelTarefa.naoIdentificado().nome()).isNull();
    }

    @Test
    void atribuidoSemNomeViraNaoIdentificado() {
        assertThat(ResponsavelTarefa.atribuido("  ")).isEqualTo(ResponsavelTarefa.naoIdentificado());
        assertThat(ResponsavelTarefa.atribuido(null)).isEqualTo(ResponsavelTarefa.naoIdentificado());
    }

    @Test
    void semResponsavelRemoveSoOResponsavel() {
        TarefaAtual tarefa = TarefaAtual.ativa("Analisar", LocalDateTime.of(2026, 1, 1, 8, 0),
                ResponsavelTarefa.atribuido("Maria"), "Outorga");

        TarefaAtual sem = tarefa.semResponsavel();

        assertThat(sem.responsavel()).isNull();
        assertThat(sem.nome()).isEqualTo("Analisar");
        assertThat(sem.desde()).isEqualTo(tarefa.desde());
        assertThat(sem.estado()).isEqualTo(TarefaAtual.Estado.ATIVA);
    }
}
