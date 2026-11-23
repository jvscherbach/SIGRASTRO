package br.gov.mt.sema.rastro.painel.api;

import br.gov.mt.sema.rastro.painel.api.dto.PainelResponse;
import br.gov.mt.sema.rastro.painel.application.AbrirPainel;
import br.gov.mt.sema.rastro.painel.application.SolicitarAtualizacao;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.api.error.ProblemDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/painel")
@Tag(name = "Painel", description = "Painel do Gestor: protocolados e concluídos no ano, fila por etapa e carga por responsável (S10)")
public class PainelController {

    private final AbrirPainel abrirPainel;

    public PainelController(AbrirPainel abrirPainel) {
        this.abrirPainel = abrirPainel;
    }

    @GetMapping
    @Operation(summary = "Instantâneo do painel",
            description = "Devolve o instantâneo em memória, sempre de imediato: esta rota nunca consulta o SIGA nem o "
                    + "Camunda. Estados PREPARANDO (a primeira leitura roda em segundo plano), PRONTO, DESATUALIZADO "
                    + "(a última leitura falhou; mostra a última boa) e INDISPONIVEL. Só Gestor e Administrador. Com os "
                    + "nomes ocultos por configuração, a carga traz só as categorias sem nome. CPF nunca.")
    @ApiResponse(responseCode = "200", description = "Instantâneo do painel (qualquer estado).")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public PainelResponse painel() {
        return PainelResponse.de(abrirPainel.abrir());
    }

    @PostMapping("/atualizacao")
    @Operation(summary = "Pede uma atualização manual do painel",
            description = "Dispara a leitura em segundo plano e responde 202. Antes do intervalo mínimo desde a última "
                    + "atualização, responde 429 ATUALIZACAO_RECENTE com tentarEmSegundos. Já havendo uma em curso, "
                    + "responde 202 sem iniciar outra.")
    @ApiResponse(responseCode = "202", description = "Atualização aceita (ou já em curso).")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "429", description = "Atualização recente (ATUALIZACAO_RECENTE); tentarEmSegundos.")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public ResponseEntity<Object> atualizar(HttpServletRequest request) {
        SolicitarAtualizacao.Resultado resultado = abrirPainel.atualizar();
        if (resultado.aceita()) {
            return ResponseEntity.accepted().build();
        }
        ProblemDetail problema = ProblemDetails.criar(ErrorCode.ATUALIZACAO_RECENTE,
                "O painel foi atualizado há pouco. Tente de novo em " + resultado.tentarEmSegundos() + " segundos.",
                request);
        problema.setProperty("tentarEmSegundos", resultado.tentarEmSegundos());
        return ResponseEntity.status(problema.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(resultado.tentarEmSegundos()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
