package br.gov.mt.sema.vistas.shared.api.error;

import java.util.List;

import br.gov.mt.sema.vistas.shared.application.exception.VistasException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DETALHE_INTERNO =
            "Ocorreu um erro inesperado. Informe o código de correlação ao suporte.";

    @ExceptionHandler(VistasException.class)
    ResponseEntity<Object> aoFalharNegocio(VistasException ex, HttpServletRequest request) {
        return resposta(ProblemDetails.criar(ex.errorCode(), ex.getMessage(), request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> aoViolarRestricao(ConstraintViolationException ex, HttpServletRequest request) {
        List<CampoInvalido> campos = ex.getConstraintViolations().stream()
                .map(v -> new CampoInvalido(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return resposta(invalido("Há parâmetros inválidos na requisição.", request.getRequestURI(), campos));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Object> aoConverterParametro(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return resposta(invalido("Há parâmetros inválidos na requisição.", request.getRequestURI(),
                List.of(new CampoInvalido(ex.getName(), "Valor inválido."))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> aoFalharGenerico(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado em {}", request.getRequestURI(), ex);
        return resposta(ProblemDetails.criar(ErrorCode.ERRO_INTERNO, DETALHE_INTERNO, request));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new CampoInvalido(e.getField(), e.getDefaultMessage()))
                .toList();
        return resposta(invalido("Há campos inválidos na requisição.", caminho(request), campos));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return resposta(invalido("Há parâmetros inválidos na requisição.", caminho(request), List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return resposta(invalido("Há parâmetros inválidos na requisição.", caminho(request),
                List.of(new CampoInvalido(ex.getParameterName(), "Parâmetro obrigatório."))));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return resposta(invalido("O corpo da requisição está ilegível ou mal formado.", caminho(request), List.of()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetails.criar(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.PARAMETRO_INVALIDO,
                "Método não permitido", "O método HTTP não é permitido para esta rota.", caminho(request), null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetails.criar(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.PARAMETRO_INVALIDO,
                "Tipo de conteúdo não suportado", "O tipo de conteúdo da requisição não é suportado.",
                caminho(request), null);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return resposta(ProblemDetails.criar(ErrorCode.NAO_ENCONTRADO, "A rota informada não existe.",
                caminho(request)));
    }

    /** Qualquer outra exceção padrão do Spring MVC: resposta genérica e segura, sem detalhe técnico. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        boolean erroDoServidor = statusCode.is5xxServerError();
        if (erroDoServidor) {
            log.error("Erro de infraestrutura MVC em {}", caminho(request), ex);
        }
        ErrorCode codigo = erroDoServidor ? ErrorCode.ERRO_INTERNO : ErrorCode.PARAMETRO_INVALIDO;
        String detalhe = erroDoServidor ? DETALHE_INTERNO : "A requisição não pôde ser atendida.";
        ProblemDetail problema = ProblemDetails.criar(statusCode, codigo, codigo.tituloPadrao(), detalhe,
                caminho(request), null);
        return ResponseEntity.status(statusCode).headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private static ProblemDetail invalido(String detalhe, String path, List<CampoInvalido> campos) {
        return ProblemDetails.criar(HttpStatus.BAD_REQUEST, ErrorCode.PARAMETRO_INVALIDO,
                ErrorCode.PARAMETRO_INVALIDO.tituloPadrao(), detalhe, path, campos);
    }

    private static ResponseEntity<Object> resposta(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }

    private static String caminho(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "";
    }
}
