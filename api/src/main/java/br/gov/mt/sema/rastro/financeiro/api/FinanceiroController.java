package br.gov.mt.sema.rastro.financeiro.api;

import java.util.List;

import br.gov.mt.sema.rastro.financeiro.api.dto.FinanceiroResponse;
import br.gov.mt.sema.rastro.financeiro.application.AbrirFinanceiro;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Financeiro", description = "Taxas, DAR, vencimento, pagamento e isenção do requerimento (S9)")
public class FinanceiroController {

    private final AbrirFinanceiro abrirFinanceiro;

    public FinanceiroController(AbrirFinanceiro abrirFinanceiro) {
        this.abrirFinanceiro = abrirFinanceiro;
    }

    @GetMapping("/requerimentos/{id}/financeiro")
    @Operation(summary = "Histórico financeiro do requerimento",
            description = "As taxas emitidas (1ª taxa, 2ª taxa, taxa da correção), o número do DAR, a emissão, o "
                    + "vencimento, o pagamento, as isenções (\"Isento de Taxa\" com data) e os avisos, na ordem de "
                    + "emissão. A situação é a do Licenciamento, como está; a taxa vencida por data é uma marca à parte "
                    + "(o SIGA não marca a taxa como vencida quando o prazo passa). Valor, desconto e UPF conforme o "
                    + "parâmetro de valores. Banco do SIGA fora do ar ou lento responde 200 com estado INDISPONIVEL. "
                    + "Ids de lançamento e diagnóstico só para o Administrador; CPF e chave de arquivo nunca.")
    @ApiResponse(responseCode = "200", description = "Histórico financeiro (inclusive INDISPONIVEL e SEM_TAXA).")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível ao conferir o acesso (SIGA_INDISPONIVEL).")
    public FinanceiroResponse doRequerimento(@PathVariable long id) {
        return FinanceiroResponse.de(abrirFinanceiro.doRequerimento(id));
    }

    @GetMapping("/processos/{numero}/financeiro")
    @Operation(summary = "Histórico financeiro de cada requerimento do processo",
            description = "Um histórico por requerimento vinculado ao número do processo (1:N), no mesmo contrato do "
                    + "histórico do requerimento. Operador só recebe os requerimentos em que é o interessado (RN07).")
    @ApiResponse(responseCode = "200", description = "Lista de históricos financeiros.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível ao conferir o acesso (SIGA_INDISPONIVEL).")
    public List<FinanceiroResponse> doProcesso(
            @Parameter(description = "Número do processo com hífen no lugar da barra (N-AAAA, ex.: 7900-2022)")
            @PathVariable String numero) {
        return abrirFinanceiro.doProcesso(numero.replace('-', '/')).stream().map(FinanceiroResponse::de).toList();
    }
}
