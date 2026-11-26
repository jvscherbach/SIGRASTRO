package br.gov.mt.sema.rastro.acesso.api;

import br.gov.mt.sema.rastro.acesso.api.dto.LoginRequest;
import br.gov.mt.sema.rastro.acesso.api.dto.LoginResponse;
import br.gov.mt.sema.rastro.acesso.api.dto.UsuarioResponse;
import br.gov.mt.sema.rastro.acesso.application.AutenticarUsuario;
import br.gov.mt.sema.rastro.acesso.application.EncerrarSessao;
import br.gov.mt.sema.rastro.acesso.application.SessaoAberta;
import br.gov.mt.sema.rastro.acesso.application.UsuarioAtual;
import br.gov.mt.sema.rastro.shared.api.error.ProblemDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Acesso", description = "Login simulado do protótipo")
public class AuthController {

    private final AutenticarUsuario autenticarUsuario;
    private final EncerrarSessao encerrarSessao;
    private final UsuarioAtual usuarioAtual;

    public AuthController(AutenticarUsuario autenticarUsuario, EncerrarSessao encerrarSessao,
            UsuarioAtual usuarioAtual) {
        this.autenticarUsuario = autenticarUsuario;
        this.encerrarSessao = encerrarSessao;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Login simulado", description = "Devolve um token opaco de sessão (8 h).")
    @ApiResponse(responseCode = "200", description = "Sessão aberta.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "429", ref = "#/components/responses/TooManyRequests")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacao,
            HttpServletRequest requisicao) {
        SessaoAberta sessao = autenticarUsuario.executar(pedido.usuario(), pedido.senha(), tokenDe(autorizacao),
                origemDe(requisicao));
        return new LoginResponse(sessao.token(), "Bearer",
                sessao.expiraEm().atZone(ProblemDetails.FUSO).toOffsetDateTime(),
                UsuarioResponse.de(sessao.usuario()));
    }

    /**
     * Origem do pedido para o limite de tentativas. Segue o que o projeto já faz (auditoria): o nginx do front acrescenta
     * o endereço real ao final de {@code X-Forwarded-For}; o que vem antes foi escrito pelo cliente e é ignorado. Sem o
     * cabeçalho, vale o endereço remoto.
     */
    static String origemDe(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null) {
            String[] enderecos = encaminhado.split(",");
            String ultimo = enderecos[enderecos.length - 1].strip();
            if (!ultimo.isEmpty() && ultimo.length() <= 45) {
                return ultimo;
            }
        }
        return requisicao.getRemoteAddr();
    }

    /** Token Bearer que o pedido já trazia (sessão aberta antes do login, na troca de usuário); só informação. */
    private static String tokenDe(String autorizacao) {
        String prefixo = "Bearer ";
        return autorizacao != null && autorizacao.regionMatches(true, 0, prefixo, 0, prefixo.length())
                ? autorizacao.substring(prefixo.length()).trim()
                : null;
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário e perfil atuais")
    @ApiResponse(responseCode = "200", description = "Usuário autenticado.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public UsuarioResponse me() {
        return UsuarioResponse.de(usuarioAtual.obter());
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão atual")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public ResponseEntity<Void> logout() {
        encerrarSessao.executar(usuarioAtual.token());
        return ResponseEntity.noContent().build();
    }
}
