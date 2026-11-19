package br.gov.mt.sema.rastro.configuracao.api;

import java.util.List;

import br.gov.mt.sema.rastro.configuracao.api.dto.AlterarUsuarioRequest;
import br.gov.mt.sema.rastro.configuracao.api.dto.ConexoesResponse;
import br.gov.mt.sema.rastro.configuracao.api.dto.IncluirUsuarioRequest;
import br.gov.mt.sema.rastro.configuracao.api.dto.ParametrosResponse;
import br.gov.mt.sema.rastro.configuracao.api.dto.SalvarParametrosRequest;
import br.gov.mt.sema.rastro.configuracao.api.dto.UsuarioDaConfiguracaoResponse;
import br.gov.mt.sema.rastro.configuracao.application.GerirConexoes;
import br.gov.mt.sema.rastro.configuracao.application.GerirParametros;
import br.gov.mt.sema.rastro.configuracao.application.GerirUsuarios;
import br.gov.mt.sema.rastro.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuracoes")
@Tag(name = "Configurações", description = "Administração do serviço, somente Administrador")
public class ConfiguracaoController {

    private final GerirUsuarios gerirUsuarios;
    private final GerirParametros gerirParametros;
    private final GerirConexoes gerirConexoes;

    public ConfiguracaoController(GerirUsuarios gerirUsuarios, GerirParametros gerirParametros,
            GerirConexoes gerirConexoes) {
        this.gerirUsuarios = gerirUsuarios;
        this.gerirParametros = gerirParametros;
        this.gerirConexoes = gerirConexoes;
    }

    @GetMapping("/parametros")
    @Operation(summary = "Parâmetros editáveis",
            description = "Valor atual, padrão do ambiente, faixa, classe de aplicação e se está sobreposto. Credenciais e "
                    + "SCORP_SIGILO_PADRAO não fazem parte da lista.")
    @ApiResponse(responseCode = "200", description = "Parâmetros.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    public ParametrosResponse parametros() {
        return ParametrosResponse.de(gerirParametros.listar());
    }

    @PutMapping("/parametros")
    @Operation(summary = "Salva todas as alterações pendentes",
            description = "Tudo ou nada: um valor inválido ou um nome desconhecido dá 400 PARAMETRO_INVALIDO e nada é aplicado. "
                    + "Gera um único evento CONFIG com o antes e o depois; sem mudança real, nenhum.")
    @ApiResponse(responseCode = "200", description = "Parâmetros como ficaram.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    public ParametrosResponse salvarParametros(@RequestBody SalvarParametrosRequest pedido) {
        return ParametrosResponse.de(gerirParametros.salvar(pedido.emTexto()));
    }

    @DeleteMapping("/parametros/{nome}")
    @Operation(summary = "Volta o parâmetro ao padrão do ambiente", description = "Gera evento CONFIG se havia sobreposição.")
    @ApiResponse(responseCode = "200", description = "Parâmetros como ficaram.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public ParametrosResponse restaurarParametro(@PathVariable String nome) {
        return ParametrosResponse.de(gerirParametros.restaurar(nome));
    }

    @GetMapping("/conexoes")
    @Operation(summary = "Estado das conexões", description = "Sem URL, host nem credencial. Não chama nenhum sistema externo.")
    @ApiResponse(responseCode = "200", description = "Conexões.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    public ConexoesResponse conexoes() {
        return ConexoesResponse.de(gerirConexoes.listar());
    }

    @PostMapping("/conexoes/{nome}/teste")
    @Operation(summary = "Testa uma conexão",
            description = "O destino vem só da configuração do servidor, com o mesmo cliente, URL e autenticação do "
                    + "consumo real (S6). Classificação: OK, ALCANCAVEL_COM_RESSALVA, FALHA_DE_AUTENTICACAO ou FALHA; "
                    + "503 isolado ganha uma nova tentativa. Um teste por conexão de cada vez (409 "
                    + "TESTE_DE_CONEXAO_EM_ANDAMENTO). A falha da conexão é resposta 200 com sucesso=false.")
    @ApiResponse(responseCode = "200", description = "Resultado, com tempo de resposta e mensagem.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", description = "Teste em andamento (TESTE_DE_CONEXAO_EM_ANDAMENTO).")
    public ConexoesResponse.Teste testarConexao(@PathVariable String nome) {
        return ConexoesResponse.Teste.de(gerirConexoes.testar(nome));
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
