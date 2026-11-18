package br.gov.mt.sema.vistas.auditoria.api;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import br.gov.mt.sema.vistas.auditoria.api.dto.EventoDetalheResponse;
import br.gov.mt.sema.vistas.auditoria.api.dto.EventosResponse;
import br.gov.mt.sema.vistas.auditoria.application.ConsultarAuditoria;
import br.gov.mt.sema.vistas.auditoria.application.ExportacaoDeEventos;
import br.gov.mt.sema.vistas.auditoria.application.FiltroDeEventos;
import br.gov.mt.sema.vistas.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.vistas.shared.application.exception.RecursoNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Só leitura: nenhuma rota altera ou apaga evento (docs/11). */
@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoria", description = "Consulta e exportação dos eventos de documentos e de acessos")
public class AuditoriaController {

    private static final String DOCUMENTOS = "documentos";
    private static final String ACESSOS = "acessos";
    private static final String BOM = "﻿";
    private static final String FIM_DE_LINHA = "\r\n";

    private final ConsultarAuditoria consultar;

    public AuditoriaController(ConsultarAuditoria consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/" + DOCUMENTOS)
    @Operation(summary = "Eventos de documentos",
            description = "Do mais recente para o mais antigo, com os indicadores do período. Gestor e Administrador veem "
                    + "todos. O Operador vê os eventos, de qualquer usuário, dos requerimentos em que é o interessado "
                    + "(RN07), com documento sigiloso mascarado em todos os campos e na busca; somenteSigilosos é 400 para ele.")
    @ApiResponse(responseCode = "200", description = "Página de eventos e indicadores.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    public EventosResponse documentos(
            @Parameter(description = "hoje, 7d (padrão), 30d ou todo") @RequestParam(required = false) String periodo,
            @Parameter(description = "Usuário, número, documento, arquivo ou código do evento") @RequestParam(required = false) String texto,
            @Parameter(description = "Código do evento") @RequestParam(required = false) String tipo,
            @Parameter(description = "Login") @RequestParam(required = false) String usuario,
            @RequestParam(defaultValue = "false") boolean somenteSigilosos,
            @Parameter(description = "Começa em 0") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Padrão 20, máximo 100") @RequestParam(required = false) Integer tamanho) {
        return EventosResponse.de(GrupoDeEvento.DOCUMENTOS, consultar.listar(GrupoDeEvento.DOCUMENTOS,
                new FiltroDeEventos(periodo, texto, tipo, usuario, somenteSigilosos), pagina, tamanho));
    }

    @GetMapping("/" + ACESSOS)
    @Operation(summary = "Eventos de acessos", description = "Somente Administrador (403 PERFIL_SEM_PERMISSAO).")
    @ApiResponse(responseCode = "200", description = "Página de eventos e indicadores.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    public EventosResponse acessos(
            @Parameter(description = "hoje, 7d (padrão), 30d ou todo") @RequestParam(required = false) String periodo,
            @Parameter(description = "Usuário ou código do evento") @RequestParam(required = false) String texto,
            @Parameter(description = "Código do evento") @RequestParam(required = false) String tipo,
            @Parameter(description = "Login") @RequestParam(required = false) String usuario,
            @Parameter(description = "Começa em 0") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Padrão 20, máximo 100") @RequestParam(required = false) Integer tamanho) {
        return EventosResponse.de(GrupoDeEvento.ACESSOS, consultar.listar(GrupoDeEvento.ACESSOS,
                new FiltroDeEventos(periodo, texto, tipo, usuario, false), pagina, tamanho));
    }

    @GetMapping("/{grupo}/exportacao")
    @Operation(summary = "CSV dos eventos filtrados",
            description = "Mesmos filtros e regras da lista. Separador ponto e vírgula, UTF-8 com BOM. Acima de "
                    + "AUDITORIA_CSV_MAX_LINHAS corta e avisa no cabeçalho X-Vistas-Truncado.")
    @ApiResponse(responseCode = "200", description = "CSV (attachment).")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public void exportar(@PathVariable String grupo, @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String texto, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String usuario, @RequestParam(defaultValue = "false") boolean somenteSigilosos,
            HttpServletResponse resposta) throws IOException {
        ExportacaoDeEventos exportacao = consultar.exportar(grupo(grupo),
                new FiltroDeEventos(periodo, texto, tipo, usuario, somenteSigilosos));
        resposta.setContentType("text/csv;charset=UTF-8");
        resposta.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(exportacao.nomeDoArquivo()).build().toString());
        resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader("Referrer-Policy", "no-referrer");
        if (exportacao.truncado()) {
            resposta.setHeader("X-Vistas-Truncado", "true");
        }
        // Linha a linha: o arquivo nunca é montado inteiro na memória.
        Writer saida = new BufferedWriter(new OutputStreamWriter(resposta.getOutputStream(), StandardCharsets.UTF_8));
        saida.write(BOM);
        saida.write(exportacao.cabecalho());
        saida.write(FIM_DE_LINHA);
        for (Iterator<String> linhas = exportacao.linhas().iterator(); linhas.hasNext();) {
            saida.write(linhas.next());
            saida.write(FIM_DE_LINHA);
        }
        saida.flush();
    }

    @GetMapping("/{grupo}/{id}")
    @Operation(summary = "Detalhe do evento",
            description = "Todos os campos permitidos, a origem da requisição, os documentos com o sigilo de cada um e o antes "
                    + "e depois. Evento que o usuário não vê é 404.")
    @ApiResponse(responseCode = "200", description = "Evento.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public EventoDetalheResponse detalhe(@PathVariable String grupo, @PathVariable long id) {
        return EventoDetalheResponse.de(consultar.detalhar(grupo(grupo), id));
    }

    private static GrupoDeEvento grupo(String grupo) {
        return switch (grupo) {
            case DOCUMENTOS -> GrupoDeEvento.DOCUMENTOS;
            case ACESSOS -> GrupoDeEvento.ACESSOS;
            default -> throw new RecursoNaoEncontradoException("Grupo de auditoria não encontrado.");
        };
    }
}
