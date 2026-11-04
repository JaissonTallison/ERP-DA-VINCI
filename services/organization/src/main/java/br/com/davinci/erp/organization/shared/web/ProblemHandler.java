package br.com.davinci.erp.organization.shared.web;

import br.com.davinci.erp.organization.shared.domain.ConflictException;
import br.com.davinci.erp.organization.shared.domain.InvalidValueException;
import br.com.davinci.erp.organization.shared.domain.NotFoundException;
import br.com.davinci.erp.organization.shared.security.ForbiddenException;
import java.net.URI;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros RFC 9457 com {@code code} estável; {@code type} = {@code urn:davinci:problem:<code>}. */
@RestControllerAdvice
class ProblemHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalid(InvalidValueException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
        problem.setProperty("field", e.field());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail forbidden(ForbiddenException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden-scope", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail versionMismatch(VersionMismatchException e) {
        return problem(HttpStatus.PRECONDITION_FAILED, "version-mismatch", e.getMessage());
    }

    /** Duas criações simultâneas com a mesma chave ou o mesmo código: a segunda perde na restrição única. */
    @ExceptionHandler
    ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "duplicate-code", "Já existe um registro com o mesmo código ou chave");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:davinci:problem:" + code));
        problem.setProperty("code", code);
        return problem;
    }
}
