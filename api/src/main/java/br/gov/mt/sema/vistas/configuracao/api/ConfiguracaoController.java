package br.gov.mt.sema.vistas.configuracao.api;

import java.util.List;

import br.gov.mt.sema.vistas.configuracao.api.dto.AlterarUsuarioRequest;
import br.gov.mt.sema.vistas.configuracao.api.dto.IncluirUsuarioRequest;
import br.gov.mt.sema.vistas.configuracao.api.dto.UsuarioDaConfiguracaoResponse;
import br.gov.mt.sema.vistas.configuracao.application.GerirUsuarios;
import br.gov.mt.sema.vistas.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuracoes")
@Tag(name = "Configurações", description = "Administração do serviço, somente Administrador")
public class ConfiguracaoController {

    private final GerirUsuarios gerirUsuarios;

    public ConfiguracaoController(GerirUsuarios gerirUsuarios) {
        this.gerirUsuarios = gerirUsuarios;
    }

    @GetMapping("/usuarios")
    @Operation(summary = "Usuários e perfis", description = "Todos, por nome, numa página só. CPF/CNPJ só mascarado.")
    @ApiResponse(responseCode = "200", description = "Usuários.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    public PageResponse<UsuarioDaConfiguracaoResponse> usuarios() {
        List<UsuarioDaConfiguracaoResponse> usuarios =
                gerirUsuarios.listar().stream().map(UsuarioDaConfiguracaoResponse::de).toList();
        return PageResponse.of(usuarios, 0, usuarios.size(), usuarios.size());
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inclui usuário",
            description = "Login único e normalizado. CPF/CNPJ obrigatório e válido para o Operador. Gera evento USUARIO, sem "
                    + "o CPF/CNPJ. 409 LOGIN_JA_EXISTE.")
    @ApiResponse(responseCode = "201", description = "Usuário incluído.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "409", description = "Login já existe (LOGIN_JA_EXISTE).")
    public UsuarioDaConfiguracaoResponse incluir(@RequestBody IncluirUsuarioRequest pedido) {
        return UsuarioDaConfiguracaoResponse.de(
                gerirUsuarios.incluir(pedido.nome(), pedido.login(), pedido.cpfCnpj(), pedido.perfil()));
    }

    @PatchMapping("/usuarios/{id}")
    @Operation(summary = "Altera perfil ou situação",
            description = "Vale para as sessões abertas já na próxima requisição. Gera um evento USUARIO se algo mudou. 409 "
                    + "ULTIMO_ADMINISTRADOR ou PROPRIO_USUARIO.")
    @ApiResponse(responseCode = "200", description = "Usuário como ficou.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", description = "Proteção da administração (ULTIMO_ADMINISTRADOR, PROPRIO_USUARIO).")
    public UsuarioDaConfiguracaoResponse alterar(@PathVariable String id, @RequestBody AlterarUsuarioRequest pedido) {
        return UsuarioDaConfiguracaoResponse.de(gerirUsuarios.alterar(id, pedido.perfil(), pedido.ativo()));
    }
}
