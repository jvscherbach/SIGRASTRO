package br.gov.mt.sema.rastro.tramitacao.api;

import java.util.List;

import br.gov.mt.sema.rastro.tramitacao.api.dto.PosicaoResponse;
import br.gov.mt.sema.rastro.tramitacao.application.AbrirPosicao;
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
@Tag(name = "Tramitação", description = "Onde o requerimento está agora, desde quando e com quem (S7)")
public class TramitacaoController {

    private final AbrirPosicao abrirPosicao;

    public TramitacaoController(AbrirPosicao abrirPosicao) {
        this.abrirPosicao = abrirPosicao;
    }

    @GetMapping("/requerimentos/{id}/posicao")
    @Operation(summary = "Posição atual do requerimento no fluxo",
            description = "Etapa atual (rótulo de negócio), desde quando, com quem, pontos em paralelo, complementação, "
                    + "\"x de y\" da multi-instância e avisos. Camunda fora do ar ou lento responde 200 com "
                    + "estado INDISPONIVEL (o Rastro degrada). Ids técnicos só para o Administrador; CPF nunca.")
    @ApiResponse(responseCode = "200", description = "Posição (inclusive INDISPONIVEL).")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível ao conferir o acesso (SIGA_INDISPONIVEL).")
    public PosicaoResponse doRequerimento(@PathVariable long id) {
        return PosicaoResponse.de(abrirPosicao.doRequerimento(id));
    }

    @GetMapping("/processos/{numero}/posicao")
    @Operation(summary = "Posição de cada requerimento do processo",
            description = "Uma posição por requerimento vinculado ao número do processo (1:N), no mesmo contrato da "
                    + "posição do requerimento. Operador só recebe os requerimentos em que é o interessado (RN07).")
    @ApiResponse(responseCode = "200", description = "Lista de posições.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível ao conferir o acesso (SIGA_INDISPONIVEL).")
    public List<PosicaoResponse> doProcesso(
            @Parameter(description = "Número do processo com hífen no lugar da barra (N-AAAA, ex.: 7900-2022): a barra "
                    + "não cabe num segmento de caminho") @PathVariable String numero) {
        return abrirPosicao.doProcesso(numero.replace('-', '/')).stream().map(PosicaoResponse::de).toList();
    }
}
