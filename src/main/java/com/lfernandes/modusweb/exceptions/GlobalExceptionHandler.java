package com.lfernandes.modusweb.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Handler global de Exceções — Garante que nenhuma stack trace vaze para o Usuário.
 *
 * Todas as exceções são capturadas aqui e convertidas em páginas de Erro.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("404 Not Found: {} — URI: {}", ex.getMessage(), req.getRequestURI());
        return errorView("404", "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("Business error: {} — URI: {}", ex.getMessage(), req.getRequestURI());
        return errorView("400", "Ação não permitida", ex.getMessage());
    }

    @ExceptionHandler(FileStorageException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleFileStorage(FileStorageException ex, HttpServletRequest req) {
        log.error("Erro de storage: {} — URI: {}", ex.getMessage(), req.getRequestURI(), ex);
        return errorView("500", "Erro de armazenamento", "Ocorreu um problema ao processar o arquivo.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView handleAccessDenied(HttpServletRequest req) {
        log.warn("403 Forbidden: {}", req.getRequestURI());
        return errorView("403", "Acesso negado",
                "Você não tem permissão para acessar este recurso.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleStaticNotFound(HttpServletRequest req) {
        return errorView("404", "Página não encontrada",
                "A página que você procura não existe.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGeneral(Exception ex, HttpServletRequest req) {
        // Log com stack trace completo — nunca expor ao usuário
        log.error("Erro interno inesperado — URI: {} | Mensagem: {}",
                req.getRequestURI(), ex.getMessage(), ex);
        return errorView("500", "Erro interno",
                "Ocorreu um problema inesperado. Nossa equipe foi notificada.");
    }

    private ModelAndView errorView(String code, String title, String message) {
        ModelAndView mav = new ModelAndView("error/generic");
        mav.addObject("errorCode", code);
        mav.addObject("errorTitle", title);
        mav.addObject("errorMessage", message);
        return mav;
    }
}
