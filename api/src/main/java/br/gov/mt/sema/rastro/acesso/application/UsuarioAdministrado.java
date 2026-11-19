package br.gov.mt.sema.rastro.acesso.application;

import java.time.Instant;

/**
 * Usuário como a administração o enxerga. Sem CPF/CNPJ completo: só a máscara sai do módulo.
 *
 * @param perfil código do perfil
 * @param perfilRotulo texto do perfil
 * @param ultimoAcesso {@code null} se ainda não entrou desde a última subida do serviço
 */
public record UsuarioAdministrado(String login, String nome, String perfil, String perfilRotulo, boolean ativo,
        Instant ultimoAcesso, String cpfCnpjMascarado) {
}
