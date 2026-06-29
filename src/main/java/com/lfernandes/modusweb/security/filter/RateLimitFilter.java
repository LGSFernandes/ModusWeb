package com.lfernandes.modusweb.security.filter;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.security.ratelimit.RateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Filtro de Rate Limiting Global e por Endpoint sensível.
 * - Rota /auth/login: bucket restrito (anti-brute-force)
 * - Todas as outras rotas: bucket global por IP
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter implements Filter {

    private final long loginCapacity;
    private final long loginRefillSeconds;
    private final long globalCapacity;
    private final long globalRefillSeconds;
    private final AuditLog auditLog;

    // Mapas ConcurrentHashMap para buckets por IP
    private final Map<String, RateLimiter> loginBuckets  = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> globalBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.ratelimit.login.capacity:5}") long loginCapacity,
            @Value("${app.ratelimit.login.refill-seconds:60}") long loginRefillSeconds,
            @Value("${app.ratelimit.global.capacity:100}") long globalCapacity,
            @Value("${app.ratelimit.global.refill-seconds:60}") long globalRefillSeconds,
            AuditLog auditLog) {
        this.loginCapacity      = loginCapacity;
        this.loginRefillSeconds = loginRefillSeconds;
        this.globalCapacity     = globalCapacity;
        this.globalRefillSeconds = globalRefillSeconds;
        this.auditLog = auditLog;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String ip  = extractIp(request);
        String uri = request.getRequestURI();

        // Ignora recursos estáticos (CSS, JS, imagens)
        if (isStaticResource(uri)) {
            chain.doFilter(req, res);
            return;
        }

        boolean allowed;

        RateLimiter bucket;
        if (uri.startsWith("/auth/login")) {
            // Rate limit estrito para login (anti-brute-force)
            bucket = loginBuckets.computeIfAbsent(ip,
                    k -> new RateLimiter(loginCapacity, loginCapacity,
                            TimeUnit.SECONDS.toNanos(loginRefillSeconds)));
        } else {
            // Rate limit global para demais rotas
            bucket = globalBuckets.computeIfAbsent(ip,
                    k -> new RateLimiter(globalCapacity, globalCapacity,
                            TimeUnit.SECONDS.toNanos(globalRefillSeconds)));
        }
        allowed = bucket.tryAcquire();

        if (!allowed) {
            auditLog.logAnonymous(AuditLog.Action.RATE_LIMIT_TRIGGERED, ip,
                    "URI=" + uri + " METHOD=" + request.getMethod());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(tooManyRequestsPage());
            return;
        }

        // Detecção básica de bots por User-Agent
        detectBot(request, ip);

        chain.doFilter(req, res);
    }

    // ---- Helpers ----

    private String extractIp(HttpServletRequest req) {
        // Suporta proxy reverso (Nginx/CloudFlare)
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private boolean isStaticResource(String uri) {
        return uri.startsWith("/css/") || uri.startsWith("/js/")
                || uri.startsWith("/assets/") || uri.startsWith("/favicon");
    }

    private void detectBot(HttpServletRequest req, String ip) {
        String ua = req.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            auditLog.logAnonymous(AuditLog.Action.BOT_DETECTED, ip,
                    "User-Agent ausente em " + req.getRequestURI());
        }
    }

    private String tooManyRequestsPage() {
        return """
            <html><body style="font-family:sans-serif;text-align:center;padding:80px">
            <h1 style="color:#7c3aed">Muitas Requisições</h1>
            <p>Você excedeu o limite de requisições. Aguarde um momento e tente novamente.</p>
            </body></html>
            """;
    }
}