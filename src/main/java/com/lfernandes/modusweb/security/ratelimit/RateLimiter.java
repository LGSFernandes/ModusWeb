package com.lfernandes.modusweb.security.ratelimit;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação de Rate Limiter com algoritmo Token Bucket.
 *
 * Thread-safe via AtomicLong — suporta alta concorrência sem lock explícito.
 * Cada instância representa o bucket de uma chave (ex: IP de origem).
 */
public class RateLimiter {

    private final long capacity;          // Máximo de tokens no bucket
    private final long refillTokens;      // Tokens adicionados por período
    private final long refillPeriodNanos; // Período de recarga em nanossegundos

    private final AtomicLong availableTokens;
    private volatile long lastRefillTimestamp;

    public RateLimiter(long capacity, long refillTokens, long refillPeriodNanos) {
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriodNanos;
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillTimestamp = Instant.now().toEpochMilli() * 1_000_000L;
    }

    /**
     * Tenta consumir 1 token.
     *
     * @return true se o token foi consumido (requisição permitida),
     *         false se o bucket está vazio (requisição bloqueada).
     */
    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens.get() > 0) {
            availableTokens.decrementAndGet();
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTimestamp;
        if (elapsed >= refillPeriodNanos) {
            long periods = elapsed / refillPeriodNanos;
            long tokensToAdd = periods * refillTokens;
            long newTokens = Math.min(capacity, availableTokens.get() + tokensToAdd);
            availableTokens.set(newTokens);
            lastRefillTimestamp = now;
        }
    }
}
