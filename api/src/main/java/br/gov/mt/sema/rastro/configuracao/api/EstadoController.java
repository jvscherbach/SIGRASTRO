package br.gov.mt.sema.rastro.configuracao.api;

import br.gov.mt.sema.rastro.configuracao.application.ConsultarEstadoDoServico;
import br.gov.mt.sema.rastro.configuracao.application.ConsultarEstadoDoServico.EstadoDoServico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Estado", description = "Estado e modo do serviço")
public class EstadoController {

    private final ConsultarEstadoDoServico consultar;

    public EstadoController(ConsultarEstadoDoServico consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/estado")
    @Operation(summary = "Estado e modo do serviço (S6)",
            description = "Para qualquer usuário autenticado. modo = demonstracao só com RASTRO_MODO=demonstracao e nenhum "
                    + "sistema externo configurado (SIGA, API de arquivos, SCORP); senão padrao. Sem URL, host ou "
                    + "credencial. Usado pelo servidor MCP do Rastro, que só opera em demonstração.")
    @ApiResponse(responseCode = "200", description = "Estado.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    public EstadoDoServico estado() {
        return consultar.consultar();
    }
}
