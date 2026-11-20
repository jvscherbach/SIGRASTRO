package br.gov.mt.sema.rastro.dossie.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/** S6, bloco 1: nome mostrado, título/tipo do retrato da auditoria e texto do cabeçalho da planilha. */
class ItemDoPedidoNomeTest {

    private static final LocalDateTime DATA = LocalDateTime.of(2025, 1, 1, 10, 0);

    @Test
    void guardaNomeExibidoDescricaoDoSigaECabecalhoSemExtensao() {
        ItemDoPedido item = new ItemDoPedido("DOCUMENTO_REQUERIMENTO", "Documento do requerimento", "7",
                "Laudo vazão.xlsx", DATA, "K", 0, "xlsx", "Laudo vazão.xlsx", false, "Teste de bombeamento.",
                "Laudo vazão");

        assertThat(item.titulo()).isEqualTo("Laudo vazão.xlsx");
        assertThat(item.descricao()).isEqualTo("Teste de bombeamento.");
        assertThat(item.tituloDeCabecalho()).isEqualTo("Laudo vazão");
    }

    @Test
    void semOsNovosCamposDescricaoECabecalhoCaemNoTituloComoAntes() {
        ItemDoPedido item = new ItemDoPedido("DOCUMENTO_REQUERIMENTO", "Documento do requerimento", "7", "Título",
                DATA, "K", 0, "pdf", "a.pdf", false);

        assertThat(item.descricao()).isEqualTo("Título");
        assertThat(item.tituloDeCabecalho()).isEqualTo("Título");
    }
}
