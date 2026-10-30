package br.gov.mt.sema.vistas.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EventoDeAuditoriaTest {

    private static final ZonedDateTime AGORA = ZonedDateTime.of(2026, 3, 10, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final UsuarioDoEvento USUARIO = new UsuarioDoEvento("gestor.demo", "Gestor Demo", "Gestor");
    private static final DocumentoDoEvento DOCUMENTO =
            new DocumentoDoEvento("Requerimento", "10", "Licença prévia", "licenca.pdf", false, 1);
    private static final AlteracaoDoEvento ALTERACAO = new AlteracaoDoEvento("Perfil", "Operador", "Gestor");

    private static NovoEvento.Construtor completo(TipoDeEvento tipo) {
        return NovoEvento.de(tipo, USUARIO).requerimento(1L, "2825/2025", "100/2025").arquivo("dossie.pdf")
                .pedido("pedido-1").detalhe("detalhe").documentos(List.of(DOCUMENTO)).alteracoes(List.of(ALTERACAO));
    }

    private static EventoDeAuditoria evento(NovoEvento novo) {
        return EventoDeAuditoria.de(1, AGORA, novo);
    }

    @ParameterizedTest
    @EnumSource(TipoDeEvento.class)
    void aceita_cada_tipo_com_os_campos_completos(TipoDeEvento tipo) {
        EventoDeAuditoria evento = evento(completo(tipo).criar());

        assertThat(evento.tipo()).isEqualTo(tipo);
        assertThat(evento.grupo()).isEqualTo(tipo.grupo());
    }

    @Test
    void grupos_de_docs_11() {
        assertThat(Arrays.stream(TipoDeEvento.values()).filter(t -> t.grupo() == GrupoDeEvento.ACESSOS))
                .containsExactlyInAnyOrder(TipoDeEvento.LOGIN, TipoDeEvento.PERFIL, TipoDeEvento.USUARIO,
                        TipoDeEvento.CONFIG);
    }

    @Test
    void data_e_hora_no_fuso_de_cuiaba() {
        ZonedDateTime dataHora = evento(completo(TipoDeEvento.LOGIN).criar()).dataHora();

        assertThat(dataHora.getZone()).isEqualTo(EventoDeAuditoria.FUSO);
        assertThat(dataHora.toInstant()).isEqualTo(AGORA.toInstant());
    }

    @Test
    void campos_obrigatorios_por_tipo() {
        assertRecusa(NovoEvento.de(TipoDeEvento.LOGIN, null).criar(), "usuario.login");
        assertRecusa(NovoEvento.de(TipoDeEvento.LOGIN, new UsuarioDoEvento("  ", "x", "y")).criar(), "usuario.login");
        assertRecusa(NovoEvento.de(null, USUARIO).criar(), "tipo");
        assertRecusa(NovoEvento.de(TipoDeEvento.CONSULTA, USUARIO).criar(), "idRequerimento");
        assertRecusa(completo(TipoDeEvento.VISUALIZACAO).documentos(List.of()).criar(), "documentos");
        assertRecusa(completo(TipoDeEvento.DOSSIE).documentos(List.of()).criar(), "documentos");
        assertRecusa(completo(TipoDeEvento.DOSSIE).arquivo(null).criar(), "arquivo");
        assertRecusa(completo(TipoDeEvento.DOSSIE).pedido(null).criar(), "idPedido");
        assertRecusa(completo(TipoDeEvento.FALHA).detalhe(" ").criar(), "detalhe");
        assertRecusa(completo(TipoDeEvento.FALHA).pedido(null).criar(), "idPedido");
        assertRecusa(completo(TipoDeEvento.DOWNLOAD).arquivo(null).criar(), "arquivo");
        assertRecusa(completo(TipoDeEvento.GEOMETRIAS).arquivo(null).criar(), "arquivo");
        assertRecusa(completo(TipoDeEvento.PERFIL).detalhe(null).criar(), "detalhe");
        assertRecusa(completo(TipoDeEvento.USUARIO).alteracoes(List.of()).criar(), "alteracoes");
        assertRecusa(completo(TipoDeEvento.CONFIG).alteracoes(null).criar(), "alteracoes");
        assertRecusa(completo(TipoDeEvento.DOSSIE)
                .documentos(List.of(new DocumentoDoEvento("Requerimento", "10", null, "a.pdf", false, 1))).criar(),
                "documento");
    }

    private static void assertRecusa(NovoEvento novo, String campo) {
        assertThatThrownBy(() -> evento(novo)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(campo);
    }

    @Test
    void recusa_nao_cita_o_valor() {
        NovoEvento novo = completo(TipoDeEvento.USUARIO).alteracoes(List.of()).detalhe("valor-que-nao-pode-sair").criar();

        assertThatThrownBy(() -> evento(novo)).hasMessageNotContaining("valor-que-nao-pode-sair");
    }

    @Test
    void montar_novo_evento_nunca_lanca() {
        assertThatCode(() -> NovoEvento.de(null, null).interessado("abc").documentos(null).alteracoes(null).criar())
                .doesNotThrowAnyException();
        assertThatCode(() -> new DocumentoDoEvento(null, null, null, null, false, null)).doesNotThrowAnyException();
        assertThatCode(() -> new AlteracaoDoEvento(null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> new UsuarioDoEvento(null, null, null)).doesNotThrowAnyException();
    }

    @Test
    void imutavel_listas_sao_copiadas_e_nao_aceitam_mudanca() {
        List<DocumentoDoEvento> documentos = new ArrayList<>(List.of(DOCUMENTO));
        List<AlteracaoDoEvento> alteracoes = new ArrayList<>(List.of(ALTERACAO));
        EventoDeAuditoria evento =
                evento(completo(TipoDeEvento.DOSSIE).documentos(documentos).alteracoes(alteracoes).criar());

        documentos.clear();
        alteracoes.clear();

        assertThat(evento.documentos()).containsExactly(DOCUMENTO);
        assertThat(evento.alteracoes()).containsExactly(ALTERACAO);
        assertThatThrownBy(() -> evento.documentos().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evento.alteracoes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void sigiloso_quando_qualquer_documento_for() {
        DocumentoDoEvento sigiloso = new DocumentoDoEvento("Requerimento", "11", "Parecer", "parecer.pdf", true, 2);

        assertThat(evento(completo(TipoDeEvento.DOSSIE).documentos(List.of(DOCUMENTO, sigiloso)).criar()).sigiloso())
                .isTrue();
        assertThat(evento(completo(TipoDeEvento.DOSSIE).criar()).sigiloso()).isFalse();
        assertThat(evento(completo(TipoDeEvento.GEOMETRIAS).sigiloso(true).criar()).sigiloso()).isTrue();
    }

    @Test
    void aparar_cortar_e_tirar_controle() {
        EventoDeAuditoria evento = evento(completo(TipoDeEvento.CONSULTA)
                .detalhe("  linha 1\nlinha\u0000 2\u202E \t".concat("x".repeat(2000))).criar());

        assertThat(evento.detalhe()).startsWith("linha 1 linha 2").hasSizeLessThanOrEqualTo(1000);
        assertThat(evento.detalhe()).doesNotContain("\n", "\u0000", "\u202E", "\t");
    }

    @Test
    void nenhum_campo_guarda_token_cookie_caminho_chave_ou_documento_pessoal() {
        String chave = "9f8e7d6c-5b4a-4321-8765-0123456789ab";
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3MDAwMDAwMDB9.c2VncmVkby1kZS10ZXN0ZQ";
        String sujo = "motivo chave=" + chave + " " + chave + " Bearer abc123 " + jwt + " /var/tmp/vistas/pedido/a.pdf"
                + " C:\\temp\\b.pdf https://host.invalido/api/arquivo/1?token=abc jdbc:oracle:thin:@//host:1521/x"
                + " 123.456.789-09 12.345.678/0001-90 12345678909 Cookie: sessao=abc; outro=def";

        EventoDeAuditoria evento = evento(completo(TipoDeEvento.DOSSIE)
                .requerimento(1L, sujo, sujo).arquivo("/var/tmp/vistas/" + chave + "/dossie.pdf").detalhe(sujo)
                .documentos(List.of(new DocumentoDoEvento("Requerimento", "10", sujo, "/var/tmp/vistas/a.pdf", false, 1)))
                .alteracoes(List.of(new AlteracaoDoEvento(sujo, sujo, sujo)))
                .criar());

        DocumentoDoEvento documento = evento.documentos().get(0);
        AlteracaoDoEvento alteracao = evento.alteracoes().get(0);
        assertThat(List.of(evento.toString(), evento.numeroProcesso(), evento.numeroRequerimento(), evento.detalhe(),
                evento.arquivo(), documento.titulo(), documento.nomeDoArquivo(), alteracao.item(),
                alteracao.valorAnterior(), alteracao.valorNovo())).allSatisfy(texto -> assertThat(texto)
                        .doesNotContain(chave, "abc123", "eyJ", "/var/tmp", "C:\\", "host.invalido", "jdbc:",
                                "123.456.789-09", "12.345.678/0001-90", "12345678909", "sessao=abc", "outro=def"));
        assertThat(evento.arquivo()).isEqualTo("dossie.pdf");
        assertThat(documento.nomeDoArquivo()).isEqualTo("a.pdf");
    }

    @Test
    void texto_comum_nao_e_alterado() {
        String texto = "Licença/Autorização 2825/2025 de 12/03/2024 - Protocolo nº 123456/2024 - Planta A / B";

        assertThat(TextoDoEvento.limpar(texto, 200)).isEqualTo(texto);
        assertThat(TextoDoEvento.nomeDeArquivo("relatorio_de_impacto_ambiental_versao_final_2024.pdf", 200))
                .isEqualTo("relatorio_de_impacto_ambiental_versao_final_2024.pdf");
    }

    @Test
    void identificadores_tecnicos_nao_sao_trocados_por_removido() {
        String id = "9f8e7d6c-5b4a-4321-8765-0123456789ab";

        EventoDeAuditoria evento = evento(completo(TipoDeEvento.DOSSIE).pedido(id).correlacao(id + "\n<x>").criar());

        assertThat(evento.idPedido()).isEqualTo(id);
        assertThat(evento.correlationId()).isEqualTo(id + "x");
    }

    @Test
    void vinculo_nao_aparece_em_texto_e_so_sai_para_a_regra() {
        EventoDeAuditoria evento = evento(completo(TipoDeEvento.CONSULTA).interessado("12.345.678/0001-90").criar());

        assertThat(evento.toString()).doesNotContain("12345678000190", "12.345.678/0001-90");
        assertThat(evento.vinculo().atende("12345678000190"::equals)).isTrue();
        assertThat(evento.vinculo().atende("00000000000000"::equals)).isFalse();
    }

    @Test
    void valor_que_nao_e_cpf_nem_cnpj_vira_sem_vinculo() {
        assertThat(VinculoDoInteressado.de("123").atende(String::isEmpty)).isTrue();
        assertThat(VinculoDoInteressado.de(null).atende(String::isEmpty)).isTrue();
        assertThat(evento(completo(TipoDeEvento.CONSULTA).criar()).vinculo().atende(String::isEmpty)).isTrue();
    }

    @Test
    void origem_usa_o_ultimo_endereco_do_proxy_e_descarta_o_que_nao_e_ip() {
        assertThat(OrigemDoEvento.de("1.1.1.1, 10.0.0.9", "172.18.0.2")).isEqualTo("10.0.0.9");
        assertThat(OrigemDoEvento.de("10.0.0.9, lixo<script>", "172.18.0.2")).isEqualTo("10.0.0.9");
        assertThat(OrigemDoEvento.de(null, "172.18.0.2")).isEqualTo("172.18.0.2");
        assertThat(OrigemDoEvento.de("", "2001:db8::1")).isEqualTo("2001:db8::1");
        assertThat(OrigemDoEvento.de("lixo", "tambem-lixo")).isNull();
        assertThat(evento(completo(TipoDeEvento.LOGIN).origem("não é ip").criar()).origem()).isNull();
    }
}
