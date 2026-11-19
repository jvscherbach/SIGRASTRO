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
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String autorizacao) {
        SessaoAberta sessao = autenticarUsuario.executar(pedido.usuario(), pedido.senha(), tokenDe(autorizacao));
        return new LoginResponse(sessao.token(), "Bearer",
                sessao.expiraEm().atZone(ProblemDetails.FUSO).toOffsetDateTime(),
                UsuarioResponse.de(sessao.usuario()));
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
