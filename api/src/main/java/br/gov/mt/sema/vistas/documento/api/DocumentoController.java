package br.gov.mt.sema.vistas.documento.api;

import br.gov.mt.sema.vistas.documento.api.dto.DocumentosResponse;
import br.gov.mt.sema.vistas.documento.application.ListarDocumentos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Documentos", description = "Lista de documentos do requerimento")
public class DocumentoController {

    private final ListarDocumentos listarDocumentos;

    public DocumentoController(ListarDocumentos listarDocumentos) {
        this.listarDocumentos = listarDocumentos;
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
}
