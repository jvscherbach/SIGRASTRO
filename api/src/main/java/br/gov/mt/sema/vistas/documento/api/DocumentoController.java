package br.gov.mt.sema.vistas.documento.api;

import br.gov.mt.sema.vistas.documento.api.dto.DocumentosResponse;
import br.gov.mt.sema.vistas.documento.application.ConteudoDoDocumento;
import br.gov.mt.sema.vistas.documento.application.ListarDocumentos;
import br.gov.mt.sema.vistas.documento.application.ObterConteudoDoDocumento;
import br.gov.mt.sema.vistas.documento.application.ObterConteudoDoDocumento.Formato;
import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Documentos", description = "Lista de documentos do requerimento e conteúdo para o modal")
public class DocumentoController {

    private final ListarDocumentos listarDocumentos;
    private final ObterConteudoDoDocumento obterConteudo;

    public DocumentoController(ListarDocumentos listarDocumentos, ObterConteudoDoDocumento obterConteudo) {
        this.listarDocumentos = listarDocumentos;
        this.obterConteudo = obterConteudo;
    }

    @GetMapping("/requerimentos/{id}/documentos")
    @Operation(summary = "Documentos do requerimento",
            description = "Todos os documentos das origens ligadas, deduplicados e em ordem cronológica, com os totais. "
                    + "A chave do arquivo nunca é devolvida. Sigilosos aparecem para todos; para o Operador vêm mascarados e "
                    + "bloqueados (RF20).")
    @ApiResponse(responseCode = "200", description = "Documentos e totais.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalError")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível (SIGA_INDISPONIVEL).")
    public DocumentosResponse listar(@PathVariable long id) {
        return DocumentosResponse.de(listarDocumentos.executar(id));
    }

    @GetMapping("/documentos/{origem}/{id}/conteudo")
    @Operation(summary = "Conteúdo do documento para o modal (S3)",
            description = "O documento é relido da lista do requerimento a cada requisição (inclusive com o arquivo em "
                    + "cache): origem e id precisam pertencer a ele (404); RN07 (403 NAO_INTERESSADO); sigiloso sem "
                    + "perfil, 403 DOCUMENTO_SIGILOSO; indisponível, 404. formato=pdf (padrão) devolve o PDF, convertido "
                    + "quando necessário com o mesmo conversor do dossiê; tipo sem conversão, 415 FORMATO_NAO_SUPORTADO. "
                    + "formato=original devolve o arquivo como veio; o que não é PDF nem imagem vai como attachment. "
                    + "A chave do arquivo nunca é devolvida.")
    @ApiResponse(responseCode = "200", description = "Binário. X-Vistas-Convertido-De traz o formato de origem quando o PDF veio de uma conversão.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "415", description = "Sem conversão para PDF (FORMATO_NAO_SUPORTADO).")
    @ApiResponse(responseCode = "422", description = "Senha, arquivo corrompido ou conversão recusada pelos limites.")
    @ApiResponse(responseCode = "429", description = "Fila de conversões do modal cheia (MUITAS_CONVERSOES).")
    @ApiResponse(responseCode = "502", description = "API de arquivos recusou a credencial ou está indisponível.")
    @ApiResponse(responseCode = "503", description = "SIGA ou conversor indisponível.")
    @ApiResponse(responseCode = "504", description = "Tempo limite da obtenção ou da conversão (TEMPO_LIMITE_EXCEDIDO).")
    public ResponseEntity<InputStreamResource> conteudo(@PathVariable String origem, @PathVariable String id,
            @Parameter(description = "requerimento da lista em que o documento aparece (idRequerimento do item)")
            @RequestParam long requerimento,
            @Parameter(description = "pdf (padrão) ou original") @RequestParam(defaultValue = "pdf") String formato) {
        Formato pedido = switch (formato) {
            case "pdf" -> Formato.PDF;
            case "original" -> Formato.ORIGINAL;
            default -> throw new ParametroInvalidoException("formato", "Use pdf ou original.");
        };
        ConteudoDoDocumento conteudo = obterConteudo.executar(requerimento, origem, id, pedido);
        ContentDisposition disposicao = (conteudo.anexo() ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(conteudo.nomeDoArquivo(), StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder resposta = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(conteudo.tipoDeConteudo()))
                .contentLength(conteudo.tamanho())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer");
        if (conteudo.convertidoDe() != null) {
            resposta.header("X-Vistas-Convertido-De", conteudo.convertidoDe());
        }
        // Streaming do arquivo já aberto: nunca é carregado inteiro na memória.
        return resposta.body(new InputStreamResource(conteudo.corpo()));
    }
}
