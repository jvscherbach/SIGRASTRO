package br.gov.mt.sema.rastro.acesso.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Custo baixo nos testes (o de produção é 600.000); o que se prova é o formato e o comportamento. */
class HashDeSenhaTest {

    private static final String SENHA = "senha-de-teste-123";

    private final HashDeSenha hashes = new HashDeSenha(1000);

    @Test
    void formato_e_autodescritivo_e_nao_contem_a_senha() {
        String hash = hashes.gerar(SENHA);

        String[] partes = hash.split("\\$");
        assertThat(partes).hasSize(4);
        assertThat(partes[0]).isEqualTo("pbkdf2-sha256");
        assertThat(partes[1]).isEqualTo("1000");
        assertThat(java.util.Base64.getDecoder().decode(partes[2])).hasSizeGreaterThanOrEqualTo(16);
        assertThat(java.util.Base64.getDecoder().decode(partes[3])).hasSize(32);
        assertThat(hash).doesNotContain(SENHA);
    }

    @Test
    void sal_e_diferente_a_cada_vez() {
        assertThat(hashes.gerar(SENHA)).isNotEqualTo(hashes.gerar(SENHA));
    }

    @Test
    void verifica_senha_certa_e_recusa_a_errada() {
        String hash = hashes.gerar(SENHA);

        assertThat(hashes.confere(SENHA, hash)).isTrue();
        assertThat(hashes.confere(SENHA + "x", hash)).isFalse();
        assertThat(hashes.confere("", hash)).isFalse();
        assertThat(hashes.confere(null, hash)).isFalse();
    }

    @Test
    void hash_antigo_continua_verificando_depois_que_o_custo_muda() {
        String antigo = new HashDeSenha(1000).gerar(SENHA);
        HashDeSenha novo = new HashDeSenha(2000);

        assertThat(novo.confere(SENHA, antigo)).isTrue();
        assertThat(novo.gerar(SENHA)).contains("$2000$");
    }

    @Test
    void hash_ausente_ou_malformado_nunca_confere() {
        assertThat(hashes.confere(SENHA, null)).isFalse();
        assertThat(hashes.confere(SENHA, "")).isFalse();
        assertThat(hashes.confere(SENHA, "texto-qualquer")).isFalse();
        assertThat(hashes.confere(SENHA, "pbkdf2-sha256$abc$@@$@@")).isFalse();
        assertThat(hashes.confere(SENHA, "pbkdf2-sha256$0$AAAA$AAAA")).isFalse();
        assertThat(hashes.confere(SENHA, "outro-algoritmo$1000$AAAAAAAAAAAAAAAAAAAAAA==$AAAA")).isFalse();
    }

    @Test
    void hash_falso_tem_o_mesmo_formato_e_nunca_confere() {
        String falso = hashes.hashFalso();

        assertThat(falso).startsWith("pbkdf2-sha256$1000$");
        assertThat(hashes.confere(SENHA, falso)).isFalse();
        assertThat(hashes.confere("", falso)).isFalse();
        assertThat(hashes.hashFalso()).isEqualTo(falso);
    }

    @Test
    void custo_invalido_e_recusado() {
        assertThatThrownBy(() -> new HashDeSenha(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usuario_nao_expoe_o_hash_no_to_string() {
        Usuario usuario = new Usuario("u", "U", Perfil.ADMIN, "000.000.000-01", true, hashes.gerar(SENHA));

        assertThat(usuario.toString()).doesNotContain("pbkdf2").doesNotContain("000.000.000-01");
    }
}
