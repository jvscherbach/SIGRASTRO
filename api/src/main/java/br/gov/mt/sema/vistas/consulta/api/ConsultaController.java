package br.gov.mt.sema.vistas.consulta.api;

import br.gov.mt.sema.vistas.consulta.api.dto.RequerimentoResponse;
import br.gov.mt.sema.vistas.consulta.api.dto.ResultadoBuscaResponse;
import br.gov.mt.sema.vistas.consulta.application.AbrirRequerimento;
import br.gov.mt.sema.vistas.consulta.application.BuscarRequerimentos;
import br.gov.mt.sema.vistas.consulta.application.PedidoDeBusca;
import br.gov.mt.sema.vistas.consulta.domain.Pagina;
import br.gov.mt.sema.vistas.consulta.domain.ResumoRequerimento;
import br.gov.mt.sema.vistas.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Consulta", description = "Busca de requerimentos e processos e dados do requerimento")
public class ConsultaController {

    private final BuscarRequerimentos buscarRequerimentos;
    private final AbrirRequerimento abrirRequerimento;

    public ConsultaController(BuscarRequerimentos buscarRequerimentos, AbrirRequerimento abrirRequerimento) {
        this.buscarRequerimentos = buscarRequerimentos;
        this.abrirRequerimento = abrirRequerimento;
    }

    @GetMapping("/busca")
    @Operation(summary = "Busca requerimentos e processos",
            description = "Só por número (tipo + numero): de requerimento é exata (1 item); de processo lista todos os requerimentos "
                    + "daquele número (1:N). 404 se não existir. "
                    + "Com CPF/CNPJ e/ou nome: lista paginada. Operador só recebe os vinculados a ele (RN07).")
    @ApiResponse(responseCode = "200", description = "Página de resultados.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível (SIGA_INDISPONIVEL).")
    public PageResponse<ResultadoBuscaResponse> buscar(
            @Parameter(description = "REQUERIMENTO ou PROCESSO; obrigatório com numero") @RequestParam(required = false) String tipo,
            @Parameter(description = "Formato N/AAAA") @RequestParam(required = false) String numero,
            @Parameter(description = "CPF ou CNPJ, com ou sem máscara") @RequestParam(required = false) String cpfCnpj,
            @Parameter(description = "Parte do nome do interessado (mínimo 3 caracteres)") @RequestParam(required = false) String nome,
            @Parameter(description = "Começa em 0") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Padrão 20, máximo 100") @RequestParam(required = false) Integer tamanho) {
        Pagina<ResumoRequerimento> resultado = buscarRequerimentos.executar(
                new PedidoDeBusca(tipo, numero, cpfCnpj, nome, pagina, tamanho));
        return PageResponse.of(resultado.itens().stream().map(ResultadoBuscaResponse::de).toList(),
                resultado.pagina(), resultado.tamanho(), resultado.total());
    }

    @GetMapping("/requerimentos/{id}")
    @Operation(summary = "Dados do requerimento (ou do processo) e tarefa atual")
    @ApiResponse(responseCode = "200", description = "Requerimento.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível (SIGA_INDISPONIVEL).")
    public RequerimentoResponse obter(@PathVariable long id) {
        return RequerimentoResponse.de(abrirRequerimento.executar(id));
    }
}
