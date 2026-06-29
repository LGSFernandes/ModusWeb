package com.lfernandes.modusweb.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de Logs de Auditoria.
 * Registra todas as ações sensíveis da plataforma.
 */
@Component
@Slf4j
public class AuditLog {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Action {
        LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT,
        REGISTER, PROFILE_UPDATE,
        TEMPLATE_UPLOAD, TEMPLATE_APPROVE, TEMPLATE_REJECT, TEMPLATE_DELETE,
        ORDER_CREATE, ORDER_COMPLETE, ORDER_CANCEL, ORDER_REFUND,
        DOWNLOAD_FILE,
        ADMIN_BAN_USER, ADMIN_UNBAN_USER,
        CONTENT_MODERATION_BLOCK,
        RATE_LIMIT_TRIGGERED,
        SUSPICIOUS_REQUEST, BOT_DETECTED
    }

    /**
     * Registra uma Ação de Auditoria com contexto completo.
     *
     * @param action   Tipo de ação realizada
     * @param userId   ID do usuário (ou "anonymous")
     * @param ip       Endereço IP da requisição
     * @param detail   Detalhes adicionais do evento
     */
    public void log(Action action, String userId, String ip, String detail) {
        String timestamp = LocalDateTime.now().format(FMT);
        // Formato estruturado para fácil parsing por ferramentas de observabilidade
        log.info("[AUDIT] ts={} action={} user={} ip={} detail={}",
                timestamp, action.name(), userId, ip, sanitize(detail));
    }

    public void log(Action action, Long userId, String ip, String detail) {
        log(action, userId != null ? userId.toString() : "anonymous", ip, detail);
    }

    public void logAnonymous(Action action, String ip, String detail) {
        log(action, "anonymous", ip, detail);
    }

    /** Remove caracteres de controle que poderiam causar log injection */
    private String sanitize(String input) {
        if (input == null) return "N/A";
        return input.replaceAll("[\r\n\t]", " ").trim();
    }
}
