package br.gov.mt.sema.vistas.dossie.api;

import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import br.gov.mt.sema.vistas.documento.application.ReferenciaDeDocumento;
import br.gov.mt.sema.vistas.dossie.api.dto.AndamentoResponse;
import br.gov.mt.sema.vistas.dossie.api.dto.ContinuacaoRequest;
import br.gov.mt.sema.vistas.dossie.api.dto.CriarDossieRequest;
import br.gov.mt.sema.vistas.dossie.api.dto.DossieCriadoResponse;
import br.gov.mt.sema.vistas.dossie.api.dto.DossieGeradoResponse;
import br.gov.mt.sema.vistas.dossie.api.dto.TicketResponse;
import br.gov.mt.sema.vistas.dossie.application.CancelarDossie;
import br.gov.mt.sema.vistas.dossie.application.ConsultarDossie;
import br.gov.mt.sema.vistas.dossie.application.ContinuarDossie;
import br.gov.mt.sema.vistas.dossie.application.CriarDossie;
import br.gov.mt.sema.vistas.dossie.application.EmitirTicketDeDownload;
import br.gov.mt.sema.vistas.dossie.application.ListarDossiesGerados;
import br.gov.mt.sema.vistas.dossie.application.ObterArquivoDoDossie;
import br.gov.mt.sema.vistas.dossie.application.ObterArquivoDoDossie.ArquivoDoDossie;
import br.gov.mt.sema.vistas.shared.api.PageResponse;
import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossies")
@Tag(name = "Dossiê", description = "Geração assíncrona do dossiê em PDF")
public class DossieController {

    private final CriarDossie criarDossie;
    private final ConsultarDossie consultarDossie;
    private final ObterArquivoDoDossie obterArquivo;
    private final CancelarDossie cancelarDossie;
    private final ContinuarDossie continuarDossie;
    private final EmitirTicketDeDownload emitirTicket;
    private final ListarDossiesGerados listarDossies;

    public DossieController(CriarDossie criarDossie, ConsultarDossie consultarDossie, ObterArquivoDoDossie obterArquivo,
            CancelarDossie cancelarDossie, ContinuarDossie continuarDossie, EmitirTicketDeDownload emitirTicket,
            ListarDossiesGerados listarDossies) {
        this.criarDossie = criarDossie;
        this.consultarDossie = consultarDossie;
        this.obterArquivo = obterArquivo;
        this.cancelarDossie = cancelarDossie;
        this.continuarDossie = continuarDossie;
        this.emitirTicket = emitirTicket;
        this.listarDossies = listarDossies;
    }

    @GetMapping
    @Operation(summary = "Dossiês gerados (S4)",
            description = "Pedidos do histórico em memória, mais recente primeiro. Operador: só os seus. Gestor e "
                    + "Administrador: todos, ou só os seus com somenteMeus=true; só para eles vem geradoPor. Encerrados "
                    + "(EXPIRADO, BAIXADO, CANCELADO) ficam DOSSIE_HISTORICO_HORAS depois do vencimento, sem arquivo. "
                    + "Nenhum dado de documento. Some num reinício do serviço.")
    @ApiResponse(responseCode = "200", description = "Página de pedidos.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    public PageResponse<DossieGeradoResponse> listar(
            @Parameter(description = "Só os pedidos do próprio usuário (Gestor e Administrador; o Operador já só vê os seus)")
            @RequestParam(defaultValue = "false") boolean somenteMeus,
            @Parameter(description = "Começa em 0") @RequestParam(required = false) Integer pagina,
            @Parameter(description = "Padrão 20, máximo 50") @RequestParam(required = false) Integer tamanho) {
        ListarDossiesGerados.Pagina resultado = listarDossies.executar(somenteMeus, pagina, tamanho);
        return PageResponse.of(resultado.itens().stream().map(DossieGeradoResponse::de).toList(), resultado.pagina(),
                resultado.tamanho(), resultado.total());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Cria o pedido de geração",
            description = "Valida o requerimento, o acesso (RN07) e a seleção contra a lista relida no servidor, resolve as "
                    + "chaves uma vez e enfileira a geração. Responde 202 com o id do pedido.")
    @ApiResponse(responseCode = "202", description = "Pedido aceito.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "503", description = "Banco do SIGA indisponível (SIGA_INDISPONIVEL).")
    public DossieCriadoResponse criar(@Valid @RequestBody CriarDossieRequest pedido) {
        String id = criarDossie.executar(pedido.idRequerimento(), pedido.documentos().stream()
                .map(d -> new ReferenciaDeDocumento(d.origem(), d.id())).toList());
        return new DossieCriadoResponse(id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Andamento do pedido")
    @ApiResponse(responseCode = "200", description = "Andamento.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public AndamentoResponse andamento(@PathVariable String id) {
        return AndamentoResponse.de(consultarDossie.executar(id));
    }

    @GetMapping("/{id}/arquivo")
    @Operation(summary = "PDF final",
            description = "inline para pré-visualização, attachment para download (o arquivo é apagado ao fim do download). "
                    + "Com ticket (dossiê grande, D49): attachment direto pelo navegador, sem cabeçalho de sessão; "
                    + "ticket inválido, usado, vencido ou de outro pedido dá 403 TICKET_INVALIDO. "
                    + "409 se não há arquivo; 410 se expirou ou já foi baixado.")
    @ApiResponse(responseCode = "200", description = "PDF.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "403", description = "Ticket inválido (TICKET_INVALIDO).")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", description = "Sem arquivo (DOSSIE_SEM_ARQUIVO).")
    @ApiResponse(responseCode = "410", description = "Expirado (DOSSIE_EXPIRADO).")
    public ResponseEntity<InputStreamResource> arquivo(@PathVariable String id,
            @Parameter(description = "inline ou attachment") @RequestParam(defaultValue = "inline") String disposicao,
            @Parameter(description = "ticket de uso único (POST .../arquivo/ticket); só com attachment")
            @RequestParam(required = false) String ticket) throws IOException {
        boolean download;
        if ("attachment".equals(disposicao)) {
            download = true;
        } else if ("inline".equals(disposicao) && ticket == null) {
            download = false;
        } else {
            throw new ParametroInvalidoException("disposicao", "Use inline ou attachment (com ticket, só attachment).");
        }
        ArquivoDoDossie arquivo = ticket == null ? obterArquivo.executar(id) : obterArquivo.executarPorTicket(id, ticket);
        // Streaming direto do disco: o arquivo nunca é carregado inteiro na memória.
        InputStream entrada = new FileInputStream(arquivo.caminho().toFile());
        InputStream corpo = download
                ? new AvisaAoTerminar(entrada, arquivo.tamanhoBytes(), () -> obterArquivo.concluirDownload(arquivo))
                : entrada;
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(arquivo.nome(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(arquivo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(new InputStreamResource(corpo));
    }

    @PostMapping("/{id}/arquivo/ticket")
    @Operation(summary = "Ticket de uso único para baixar um dossiê grande direto pelo navegador",
            description = "256 bits aleatórios, válido por 60 s, preso ao pedido e ao usuário (D49). Não é o token de "
                    + "sessão. Mesmas regras do arquivo: 409 sem arquivo, 410 expirado.")
    @ApiResponse(responseCode = "200", description = "Ticket.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", description = "Sem arquivo (DOSSIE_SEM_ARQUIVO).")
    @ApiResponse(responseCode = "410", description = "Expirado (DOSSIE_EXPIRADO).")
    public ResponseEntity<TicketResponse> ticket(@PathVariable String id) {
        EmitirTicketDeDownload.TicketEmitido emitido = emitirTicket.executar(id);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TicketResponse(emitido.ticket(), emitido.expiraEm()));
    }

    @PostMapping("/{id}/continuacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Decide um pedido que aguarda decisão",
            description = "TENTAR_NOVAMENTE obtém de novo só os documentos com problema; REMOVER_E_CONTINUAR os marca "
                    + "como removidos e monta com os já obtidos. Nada já obtido é baixado de novo. 409 se o pedido não "
                    + "está em PENDENTE_DECISAO.")
    @ApiResponse(responseCode = "202", description = "Decisão aceita; a geração continua.")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", description = "Sem decisão pendente (DOSSIE_SEM_DECISAO_PENDENTE).")
    public void continuar(@PathVariable String id, @RequestBody ContinuacaoRequest decisao) {
        continuarDossie.executar(id, decisao.acao());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela o pedido ou descarta o arquivo")
    @ApiResponse(responseCode = "204", description = "Cancelado.")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public void cancelar(@PathVariable String id) {
        cancelarDossie.executar(id);
    }

    /** Só dispara o aviso se o cliente recebeu o arquivo inteiro: download interrompido não apaga o dossiê. */
    private static final class AvisaAoTerminar extends FilterInputStream {

        private final long esperado;
        private final Runnable aoTerminar;
        private long lidos;
        private boolean avisado;

        AvisaAoTerminar(InputStream origem, long esperado, Runnable aoTerminar) {
            super(origem);
            this.esperado = esperado;
            this.aoTerminar = aoTerminar;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                lidos++;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int inicio, int tamanho) throws IOException {
            int n = super.read(buffer, inicio, tamanho);
            if (n > 0) {
                lidos += n;
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (lidos >= esperado && !avisado) {
                avisado = true;
                aoTerminar.run();
            }
        }
    }
}
