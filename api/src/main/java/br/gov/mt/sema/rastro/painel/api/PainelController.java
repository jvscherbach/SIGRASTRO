package br.gov.mt.sema.rastro.painel.api;

import java.util.Optional;

import br.gov.mt.sema.rastro.painel.api.dto.AnalistasDoPainelResponse;
import br.gov.mt.sema.rastro.painel.api.dto.PainelResponse;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final int TAMANHO_MAXIMO_DA_BUSCA = 100;
    private static final int TAMANHO_MAXIMO_DA_CHAVE = 64;
    private static final int TAMANHO_MAXIMO_DA_ETAPA = 200;

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

    @GetMapping("/analistas")
    @Operation(summary = "Analistas do painel com o total de cada um",
            description = "Lista os analistas (as mesmas linhas e os mesmos totais das barras da carga por responsável), "
                    + "a partir do instantâneo do painel, de imediato: nunca consulta o SIGA nem o Camunda. Cada um traz uma "
                    + "chave opaca (nunca CPF), que muda a cada reinício. Com os nomes ocultos por configuração, só as "
                    + "categorias sem nome. Só Gestor e Administrador (S11).")
    @ApiResponse(responseCode = "200", description = "Os analistas (qualquer estado do instantâneo).")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public AnalistasDoPainelResponse analistas() {
        return AnalistasDoPainelResponse.de(abrirPainel.analistas());
    }

    @GetMapping("/analistas/{chave}/requerimentos")
    @Operation(summary = "Requerimentos de um analista no painel",
            description = "Os requerimentos do analista da chave, cada um com o interessado (só o nome), as etapas e a data "
                    + "de protocolo. Filtros: etapa (rótulo exato, como em /api/painel/analistas) e busca (nome do "
                    + "interessado ou número, até 100 caracteres). total é o da barra; no máximo 1000 linhas na resposta. "
                    + "Chave desconhecida, ou de servidor com os nomes ocultos: 404. Só Gestor e Administrador.")
    @ApiResponse(responseCode = "200", description = "Os requerimentos do analista.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public AnalistasDoPainelResponse.RequerimentosResponse requerimentosDoAnalista(@PathVariable String chave,
            @RequestParam(required = false) String etapa, @RequestParam(required = false) String busca) {
        abrirPainel.exigirPerfil();
        if (chave == null || chave.length() > TAMANHO_MAXIMO_DA_CHAVE) {
            throw new ParametroInvalidoException("chave", "Chave inválida.");
        }
        if (busca != null && busca.length() > TAMANHO_MAXIMO_DA_BUSCA) {
            throw new ParametroInvalidoException("busca", "Busca com mais de " + TAMANHO_MAXIMO_DA_BUSCA + " caracteres.");
        }
        if (etapa != null && etapa.length() > TAMANHO_MAXIMO_DA_ETAPA) {
            throw new ParametroInvalidoException("etapa", "Etapa inválida.");
        }
        Optional<String> etapaDoFiltro = etapa == null || etapa.isBlank() ? Optional.empty() : Optional.of(etapa.strip());
        return AnalistasDoPainelResponse.RequerimentosResponse.de(
                abrirPainel.requerimentosDoAnalista(chave, etapaDoFiltro, busca == null ? "" : busca));
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
