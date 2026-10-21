package br.gov.mt.sema.vistas.consulta.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentificacaoESituacaoTest {

    @Test
    void comProcessoAIdentificacaoEOProcessoMesmoSeORequerimentoExiste() {
        Identificacao id = Identificacao.de("3331/2025", "2825/2025");

        assertThat(id.rotulo()).isEqualTo("Processo");
        assertThat(id.numero()).isEqualTo("2825/2025");
        assertThat(id.protocolado()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void semProcessoAIdentificacaoEOORequerimentoNaoProtocolado(String processo) {
        Identificacao id = Identificacao.de("1187/2026", processo);

        assertThat(id.rotulo()).isEqualTo("Requerimento");
        assertThat(id.numero()).isEqualTo("1187/2026");
        assertThat(id.protocolado()).isFalse();
    }

    @Test
    void todosOsValoresDoApendiceAEstaoNoEnumComDescricaoDiferenteDoCodigo() {
        List<String> apendiceA = List.of("EM_CADASTRO", "EM_CORRECAO_CADASTRO", "AGUARDANDO_CONFERENCIA", "EM_CONFERENCIA",
                "CHECKLIST_REPROVADO", "ENCAMINHADO_PARA_ARRECADACAO", "CANCELADO_POR_SOLICITACAO_DO_CADASTRANTE",
                "CANCELADO_POR_EXPIRACAO_DE_PRAZO_DE_CADASTRO", "CANCELADO_POR_FALTA_DE_PAGAMENTO",
                "AGUARDANDO_EMISSAO_DE_TAXAS", "AGUARDANDO_PAGAMENTO_DE_TAXAS", "AGUARDANDO_ASSINATURA_LAC",
                "AGUARDANDO_ATRIBUICAO", "AGUARDANDO_ANALISE", "AGUARDANDO_ELABORACAO", "AGUARDANDO_ACEITE_ATRIBUICAO",
                "EM_ANALISE", "AGUARDANDO_PUBLICACAO", "AGUARDANDO_CHECKLIST_DOCUMENTOS", "ARQUIVADO_DEFINITIVAMENTE",
                "ARQUIVADO_TEMPORARIAMENTE", "CONCLUIDO", "CANCELADO", "AGUARDANDO_REVISAO", "EM_REVISAO",
                "REQUERIMENTO_REVISADO", "AGUARDANDO_CORRECAO", "EM_CORRECAO", "REQUERIMENTO_CORRIGIDO");

        assertThat(apendiceA).hasSize(29);
        for (String codigo : apendiceA) {
            Situacao situacao = Situacao.de(codigo);
            assertThat(situacao.tipo()).as(codigo).isNotEqualTo(SituacaoRequerimento.DESCONHECIDA);
            assertThat(situacao.codigo()).isEqualTo(codigo);
            assertThat(situacao.descricao()).as(codigo).isNotEqualTo(codigo);
        }
        assertThat(Arrays.stream(SituacaoRequerimento.values()).filter(s -> s != SituacaoRequerimento.DESCONHECIDA))
                .hasSize(29);
    }

    @Test
    void descricoesConformeOApendiceA() {
        assertThat(Situacao.de("EM_ANALISE").descricao()).isEqualTo("Em análise");
        assertThat(Situacao.de("AGUARDANDO_PAGAMENTO_DE_TAXAS").descricao()).isEqualTo("Aguardando pagamento de taxas");
        assertThat(Situacao.de("CANCELADO_POR_FALTA_DE_PAGAMENTO").descricao())
                .isEqualTo("Cancelado por falta de pagamento");
    }

    @Test
    void valorDesconhecidoNaoQuebraEMostraOTextoCru() {
        Situacao situacao = Situacao.de("SITUACAO_NOVA_DO_SIGA");

        assertThat(situacao.tipo()).isEqualTo(SituacaoRequerimento.DESCONHECIDA);
        assertThat(situacao.codigo()).isEqualTo("SITUACAO_NOVA_DO_SIGA");
        assertThat(situacao.descricao()).isEqualTo("SITUACAO_NOVA_DO_SIGA");
    }

    @Test
    void situacaoNulaOuVaziaVirouDesconhecidaComTextoPadrao() {
        assertThat(Situacao.de(null).descricao()).isEqualTo("Situação não reconhecida");
        assertThat(Situacao.de(" ").descricao()).isEqualTo("Situação não reconhecida");
    }
}
