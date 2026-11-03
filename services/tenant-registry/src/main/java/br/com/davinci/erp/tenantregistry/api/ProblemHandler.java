package br.com.davinci.erp.tenantregistry.api;

import br.com.davinci.erp.tenantregistry.application.ConflictException;
import br.com.davinci.erp.tenantregistry.application.TenantNotFoundException;
import br.com.davinci.erp.tenantregistry.domain.InvalidTenantDataException;
import br.com.davinci.erp.tenantregistry.domain.InvalidTransitionException;
import br.com.davinci.erp.tenantregistry.persistence.ConcurrentUpdateException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Erros no formato RFC 9457. Cada erro de negócio tem um {@code code} estável; o {@code type} é a URN
 * {@code urn:davinci:problem:<code>}.
 */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail notFound(TenantNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "tenant-not-found", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidTransition(InvalidTransitionException e) {
        return problem(HttpStatus.CONFLICT, "invalid-transition", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail concurrentUpdate(ConcurrentUpdateException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", e.getMessage());
    }

    /** Duas requisições simultâneas com a mesma chave ou o mesmo slug: a segunda perde na restrição única. */
    @ExceptionHandler
    ProblemDetail duplicateKey(DuplicateKeyException e) {
        ConflictException conflict = ConflictException.concurrentRequest();
        return problem(HttpStatus.CONFLICT, conflict.code(), conflict.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidData(InvalidTenantDataException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "invalid-field", e.getMessage());
        problem.setProperty("field", e.field());
        return problem;
    }

    /** Formatos inválidos detectados pelos tipos do kernel (região, célula, isolamento). */
    @ExceptionHandler
    ProblemDetail invalidArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-argument", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalidCursor(Cursor.InvalidCursorException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-cursor", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail constraintViolation(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:davinci:problem:" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
