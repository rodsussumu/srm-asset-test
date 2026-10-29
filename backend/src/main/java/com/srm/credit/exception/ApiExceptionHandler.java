package com.srm.credit.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final ErrorResponses errors;

  public ApiExceptionHandler(ErrorResponses errors) {
    this.errors = errors;
  }

  @ExceptionHandler(InvalidInputException.class)
  ResponseEntity<ProblemDetail> invalidInput(
      InvalidInputException exception, HttpServletRequest request) {
    return validation(Map.of(exception.field(), exception.getMessage()), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalidBody(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            error ->
                fields.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage() == null
                        ? "Valor inválido."
                        : error.getDefaultMessage()));
    return validation(fields, request);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ProblemDetail> invalidParameters(
      HandlerMethodValidationException exception, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    exception
        .getParameterValidationResults()
        .forEach(
            result -> {
              String name = result.getMethodParameter().getParameterName();
              fields.put(
                  name == null ? "parameter" : name, "Parâmetro fora dos limites permitidos.");
            });
    return validation(fields, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ProblemDetail> invalidType(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return validation(Map.of(exception.getName(), "Formato inválido."), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> invalidJson(
      HttpMessageNotReadableException exception, HttpServletRequest request) {
    String field = "body";
    if (exception.getCause() instanceof JsonMappingException mapping
        && !mapping.getPath().isEmpty()) {
      String name = mapping.getPath().getLast().getFieldName();
      if (name != null) {
        field = name;
      }
    }
    return validation(
        Map.of(field, "JSON inválido: verifique o formato e os tipos dos campos."), request);
  }

  @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
  ResponseEntity<ProblemDetail> notFound(Exception exception, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Recurso não encontrado.", request);
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "A operação conflita com o estado atual dos dados.",
        request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> methodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    var problem =
        errors.problem(
            HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Método não permitido.", request);
    var builder =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (exception.getSupportedHttpMethods() != null) {
      builder.allow(
          exception.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
    }
    return builder.body(problem);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> unsupportedMediaType(HttpServletRequest request) {
    return response(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "Envie o corpo como application/json.",
        request);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ProblemDetail> unacceptableMediaType(HttpServletRequest request) {
    return response(
        HttpStatus.NOT_ACCEPTABLE,
        "NOT_ACCEPTABLE",
        "Representação solicitada indisponível.",
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
    LOG.error(
        "event=request_failed code=INTERNAL_ERROR exception={}",
        exception.getClass().getSimpleName());
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Não foi possível concluir a operação.",
        request);
  }

  private ResponseEntity<ProblemDetail> validation(
      Map<String, String> fields, HttpServletRequest request) {
    var problem =
        errors.problem(
            HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Verifique os campos informados.", request);
    problem.setProperty("errors", fields);
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(errors.problem(status, code, detail, request));
  }
}
