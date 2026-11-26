package br.gov.mt.sema.rastro.caixa.api;

import java.util.Locale;
import java.util.Optional;

import br.gov.mt.sema.rastro.caixa.api.dto.AvisosResponse;
import br.gov.mt.sema.rastro.caixa.api.dto.CaixaResponse;
import br.gov.mt.sema.rastro.caixa.api.dto.VistoRequest;
import br.gov.mt.sema.rastro.caixa.application.AbrirCaixa;
import br.gov.mt.sema.rastro.caixa.application.SolicitarAtualizacaoDaCaixa;
import br.gov.mt.sema.rastro.caixa.domain.DefinicaoDaPosse;
import br.gov.mt.sema.rastro.caixa.domain.FiltroDaCaixa;
import br.gov.mt.sema.rastro.shared.api.error.ErrorCode;
import br.gov.mt.sema.rastro.shared.api.error.ProblemDetails;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caixa de entrada do analista (S11, bloco 6). Só o perfil Analista ({@code verCaixa}); a identidade vem da sessão, nunca de
 * parâmetro: o analista só vê a própria caixa. Nenhuma rota consulta o SIGA nem o Camunda.
 */
@RestController
@RequestMapping("/api/caixa")
@Tag(name = "Caixa", description = "Caixa de entrada do analista: requerimentos na posse dele na fase de análise e aviso de recebimento (S11)")
public class CaixaController {

    private static final int TAMANHO_MAXIMO_DA_BUSCA = 100;
    private static final int MAXIMO_DE_IDS_DO_VISTO = 10_000;

    private final AbrirCaixa abrirCaixa;

    public CaixaController(AbrirCaixa abrirCaixa) {
        this.abrirCaixa = abrirCaixa;
    }

    @GetMapping
    @Operation(summary = "Minha caixa",
            description = "Resumo e lista dos requerimentos na posse do analista da sessão, sempre de imediato: nunca consulta "
                    + "o SIGA nem o Camunda. Filtros: etapa (analise_do_processo, revisao_do_parecer_tecnico ou "
                    + "correcao_do_requerimento; vazio = visão geral), busca (nome do interessado ou número, até 100 "
                    + "caracteres) e ordem (MAIS_PARADO, o padrão, ou MAIS_RECENTE). Estados PREPARANDO, PRONTO, "
                    + "DESATUALIZADO e INDISPONIVEL. Usuário sem CPF cadastrado: semVinculo=true e a caixa vazia. "
                    + "Só o perfil Analista. CPF nunca.")
    @ApiResponse(responseCode = "200", description = "A caixa (qualquer estado).")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public CaixaResponse caixa(@RequestParam(required = false) String etapa, @RequestParam(required = false) String busca,
            @RequestParam(required = false) String ordem) {
        abrirCaixa.exigirPerfil();
        FiltroDaCaixa.Ordem ordenacao = ordem(ordem);
        Optional<String> etapaDoFiltro = etapa(etapa);
        String termo = busca(busca);
        return CaixaResponse.de(abrirCaixa.abrir(etapaDoFiltro, termo, ordenacao));
    }

    @GetMapping("/avisos")
    @Operation(summary = "Lista de mudanças (o que chegou)",
            description = "Os requerimentos que chegaram ao analista desde o último visto, com de onde vieram (Novo na sua "
                    + "caixa, Voltou da correção, ...; sem como dizer, Mudou de etapa). Não grava o visto: quem grava é "
                    + "POST /api/caixa/avisos/visto. Registra o evento CAIXA com só a quantidade. Só o perfil Analista.")
    @ApiResponse(responseCode = "200", description = "A lista de mudanças (pode estar vazia).")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public AvisosResponse avisos() {
        return AvisosResponse.de(abrirCaixa.abrirAvisos());
    }

    @PostMapping("/avisos/visto")
    @Operation(summary = "Grava o visto da lista de mudanças",
            description = "Grava o visto só do conjunto devolvido (ids): o que chegar depois, ou que esta aba não viu, continua "
                    + "avisando. O visto fica em memória e some ao reiniciar o sistema. Responde 204.")
    @ApiResponse(responseCode = "204", description = "Visto gravado.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public ResponseEntity<Void> visto(@RequestBody(required = false) VistoRequest pedido) {
        // O 403 vem antes de qualquer 400 de conteúdo.
        abrirCaixa.exigirPerfil();
        if (pedido == null || pedido.ids() == null) {
            throw new ParametroInvalidoException("ids", "Informe os ids do conjunto visto.");
        }
        if (pedido.ids().size() > MAXIMO_DE_IDS_DO_VISTO || pedido.ids().stream().anyMatch(id -> id == null)) {
            throw new ParametroInvalidoException("ids", "Lista de ids inválida.");
        }
        abrirCaixa.marcarVisto(pedido.ids());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/atualizar")
    @Operation(summary = "Pede uma atualização manual da caixa",
            description = "Dispara a leitura em segundo plano e responde 202. Antes do intervalo mínimo desde a última "
                    + "atualização, responde 429 ATUALIZACAO_RECENTE com tentarEmSegundos. Já havendo uma em curso, "
                    + "responde 202 sem iniciar outra. Só o perfil Analista.")
    @ApiResponse(responseCode = "202", description = "Atualização aceita (ou já em curso).")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "429", description = "Atualização recente (ATUALIZACAO_RECENTE); tentarEmSegundos.")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    public ResponseEntity<Object> atualizar(HttpServletRequest request) {
        SolicitarAtualizacaoDaCaixa.Resultado resultado = abrirCaixa.atualizar();
        if (resultado.aceita()) {
            return ResponseEntity.accepted().build();
        }
        ProblemDetail problema = ProblemDetails.criar(ErrorCode.ATUALIZACAO_RECENTE,
                "A caixa foi atualizada há pouco. Tente de novo em " + resultado.tentarEmSegundos() + " segundos.",
                request);
        problema.setProperty("tentarEmSegundos", resultado.tentarEmSegundos());
        return ResponseEntity.status(problema.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(resultado.tentarEmSegundos()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private static FiltroDaCaixa.Ordem ordem(String valor) {
        if (valor == null || valor.isBlank()) {
            return FiltroDaCaixa.Ordem.MAIS_PARADO;
        }
        try {
            return FiltroDaCaixa.Ordem.valueOf(valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParametroInvalidoException("ordem", "Ordem inválida: use MAIS_PARADO ou MAIS_RECENTE.");
        }
    }

    private static Optional<String> etapa(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String id = valor.strip();
        if (!DefinicaoDaPosse.ETAPAS_DE_POSSE.contains(id) && !DefinicaoDaPosse.ETAPA_CORRECAO.equals(id)) {
            throw new ParametroInvalidoException("etapa", "Etapa desconhecida.");
        }
        return Optional.of(id);
    }

    private static String busca(String valor) {
        String termo = valor == null ? "" : valor.strip();
        if (termo.length() > TAMANHO_MAXIMO_DA_BUSCA) {
            throw new ParametroInvalidoException("busca", "A busca aceita até 100 caracteres.");
        }
        return termo;
    }
}
