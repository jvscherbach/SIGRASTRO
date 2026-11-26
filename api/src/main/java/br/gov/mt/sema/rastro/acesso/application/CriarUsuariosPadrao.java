package br.gov.mt.sema.rastro.acesso.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import br.gov.mt.sema.rastro.acesso.domain.CpfCnpjDoUsuario;
import br.gov.mt.sema.rastro.acesso.domain.HashDeSenha;
import br.gov.mt.sema.rastro.acesso.domain.Perfil;
import br.gov.mt.sema.rastro.acesso.domain.Usuario;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Inicializador dos três usuários padrão (L1, bloco 2): {@code operador}, {@code analista} e {@code admin}. Cada um só é
 * criado se a variável da senha estiver preenchida e valer pela {@link PoliticaDeSenha}; nenhuma senha-padrão existe no
 * código. Duas ou mais variáveis com o mesmo valor (igual ou só com diferença de caixa) não criam nenhum dos usuários
 * envolvidos. Os avisos trazem <b>só o nome da variável</b>, nunca o valor.
 *
 * <p>Como os usuários vivem em memória (D09, H1 traz a persistência), ao reiniciar tudo volta ao que as variáveis dizem.
 */
@Component
public class CriarUsuariosPadrao {

    static final String VAR_SENHA_OPERADOR = "RASTRO_SENHA_OPERADOR";
    static final String VAR_SENHA_ANALISTA = "RASTRO_SENHA_ANALISTA";
    static final String VAR_SENHA_ADMIN = "RASTRO_SENHA_ADMIN";
    static final String VAR_CPF_OPERADOR = "RASTRO_OPERADOR_CPF_CNPJ";
    static final String VAR_CPF_ANALISTA = "RASTRO_ANALISTA_CPF";

    private static final Logger LOG = LoggerFactory.getLogger(CriarUsuariosPadrao.class);

    private final CadastroDeUsuarios cadastro;
    private final HashDeSenha hashes;
    private final String senhaOperador;
    private final String cpfOperador;
    private final String senhaAnalista;
    private final String cpfAnalista;
    private final String senhaAdmin;

    public CriarUsuariosPadrao(CadastroDeUsuarios cadastro, HashDeSenha hashes,
            @Value("${rastro.usuarios-padrao.operador.senha:}") String senhaOperador,
            @Value("${rastro.usuarios-padrao.operador.cpf-cnpj:}") String cpfOperador,
            @Value("${rastro.usuarios-padrao.analista.senha:}") String senhaAnalista,
            @Value("${rastro.usuarios-padrao.analista.cpf:}") String cpfAnalista,
            @Value("${rastro.usuarios-padrao.admin.senha:}") String senhaAdmin) {
        this.cadastro = cadastro;
        this.hashes = hashes;
        this.senhaOperador = senhaOperador;
        this.cpfOperador = cpfOperador;
        this.senhaAnalista = senhaAnalista;
        this.cpfAnalista = cpfAnalista;
        this.senhaAdmin = senhaAdmin;
    }

    private record Candidato(String login, String nome, Perfil perfil, String variavelSenha, String senha,
            String variavelCpf, String cpf) {
    }

    @PostConstruct
    void criar() {
        List<Candidato> candidatos = List.of(
                new Candidato("operador", "Operador", Perfil.OPERADOR, VAR_SENHA_OPERADOR, senhaOperador,
                        VAR_CPF_OPERADOR, cpfOperador),
                new Candidato("analista", "Analista", Perfil.ANALISTA, VAR_SENHA_ANALISTA, senhaAnalista,
                        VAR_CPF_ANALISTA, cpfAnalista),
                new Candidato("admin", "Administrador", Perfil.ADMIN, VAR_SENHA_ADMIN, senhaAdmin, null, null));
        Set<String> repetidas = variaveisRepetidas(candidatos);
        if (!repetidas.isEmpty()) {
            LOG.warn("Variáveis de senha com valor repetido (cada usuário padrão precisa de senha própria; os usuários "
                    + "envolvidos não foram criados): {}", String.join(", ", repetidas));
        }
        for (Candidato candidato : candidatos) {
            if (!repetidas.contains(candidato.variavelSenha())) {
                criar(candidato);
            }
        }
        boolean haAdmin = cadastro.listar().stream().anyMatch(u -> u.ativo() && u.perfil() == Perfil.ADMIN);
        if (!haAdmin) {
            LOG.error("ATENÇÃO: nenhum administrador pôde ser criado ({} ausente, fraca ou repetida). Sem administrador "
                    + "não é possível gerir usuários e parâmetros.", VAR_SENHA_ADMIN);
        }
    }

    private void criar(Candidato c) {
        if (c.senha() == null || c.senha().isBlank()) {
            LOG.warn("Usuário padrão '{}' não criado: {} ausente.", c.login(), c.variavelSenha());
            return;
        }
        if (!PoliticaDeSenha.atende(c.login(), c.senha())) {
            LOG.warn("Usuário padrão '{}' não criado: {} não atende à política de senha (mínimo {} caracteres, sem o "
                    + "login).", c.login(), c.variavelSenha(), PoliticaDeSenha.MINIMO);
            return;
        }
        String digitos = CpfCnpjDoUsuario.somenteDigitos(c.cpf());
        boolean documentoValido = !digitos.isEmpty() && CpfCnpjDoUsuario.valido(digitos);
        if (!digitos.isEmpty() && !documentoValido) {
            LOG.warn("{} inválido: o valor foi ignorado.", c.variavelCpf());
            digitos = "";
        }
        if (digitos.isEmpty() && c.perfil().restritoAoInteressado()) {
            LOG.warn("Usuário padrão '{}' não criado: {} ausente ou inválido (obrigatório para o perfil).", c.login(),
                    c.variavelCpf());
            return;
        }
        if (digitos.isEmpty() && c.perfil().podeVerCaixa()) {
            LOG.warn("Usuário padrão '{}' criado sem CPF ({}): a caixa ficará vazia.", c.login(), c.variavelCpf());
        }
        Usuario usuario = new Usuario(c.login(), c.nome(), c.perfil(), digitos.isEmpty() ? null : digitos, true,
                hashes.gerar(c.senha()));
        cadastro.salvar(usuario);
    }

    /** Nomes das variáveis de senha cujo valor aparece em mais de uma (igual ou sem distinguir maiúscula/minúscula). */
    private static Set<String> variaveisRepetidas(List<Candidato> candidatos) {
        Set<String> repetidas = new HashSet<>();
        List<Candidato> preenchidas = new ArrayList<>();
        for (Candidato c : candidatos) {
            if (c.senha() != null && !c.senha().isBlank()) {
                preenchidas.add(c);
            }
        }
        for (int i = 0; i < preenchidas.size(); i++) {
            for (int j = i + 1; j < preenchidas.size(); j++) {
                Candidato a = preenchidas.get(i);
                Candidato b = preenchidas.get(j);
                if (a.senha().equals(b.senha()) || a.senha().toLowerCase(Locale.ROOT)
                        .equals(b.senha().toLowerCase(Locale.ROOT))) {
                    repetidas.add(a.variavelSenha());
                    repetidas.add(b.variavelSenha());
                }
            }
        }
        return repetidas;
    }
}
