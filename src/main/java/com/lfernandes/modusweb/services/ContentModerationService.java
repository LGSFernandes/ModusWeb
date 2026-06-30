package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.audit.AuditLog;
import com.lfernandes.modusweb.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Serviço de Moderação de Conteúdo do ModusWeb.
 *
 * Camadas de verificação:
 *   1. Textos  → blocklist de palavras proibidas + patterns NSFW/ódio
 *   2. Imagens → validação real de Mime-type via Apache Tika (magic bytes)
 *               + checagem de tamanho mínimo/máximo
 *   3. Arquivos → validação Mime-type via Tika + magic bytes de .zip
 *
 * Integração com OpenAI Moderation API
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentModerationService {

    private final AuditLog auditLog;
    private final Tika tika = new Tika();

    @Value("${app.moderation.openai.enabled:false}")
    private boolean openAiEnabled;

    @Value("${app.moderation.openai.api-key:}")
    private String openAiApiKey;

    // ── Magic bytes: cabeçalho de arquivo ZIP (PK\x03\x04) ──
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    // ── Mime-types aceitos para imagens ──
    private static final Set<String> ALLOWED_IMAGE_MIMES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // ── Limite de tamanho de imagem: 10 MB ──
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    // ── Limite de tamanho de template: 50 MB ──
    private static final long MAX_TEMPLATE_SIZE = 50L * 1024 * 1024;

    /**
     * Lista de padrões de texto proibidos (blocklist local).
     */
    private static final List<String> BLOCKED_PATTERNS = List.of(
            // Padrões de spam/phishing
            "clique aqui para ganhar", "você foi selecionado", "oferta exclusiva grátis",
            // Discurso de ódio (exemplos simplificados)
            "content_hate_1", "content_hate_2",
            // NSFW keywords (censurado intencionalmente)
            "porn", "xxx", "nude", "nsfw",
            // Injeção de scripts
            "<script", "javascript:", "onerror=", "onload="
    );

    // ════════════════════════════════════════
    //  MODERAÇÃO DE TEXTO
    // ════════════════════════════════════════

    /**
     * Valida título e descrição de um template antes do upload.
     * Lança BusinessException se o conteúdo for inapropriado.
     */
    public void moderateText(String title, String description, Long userId, String ip) {
        String combined = (title + " " + description).toLowerCase();
        checkBlockedPatterns(combined, userId, ip);

        if (openAiEnabled && !openAiApiKey.isBlank()) {
            callOpenAiModeration(combined, userId, ip);
        }
    }

    /**
     * Valida um campo de texto genérico (bio, tags, comentários).
     */
    public void moderateField(String fieldName, String value, Long userId, String ip) {
        if (value == null || value.isBlank()) return;
        checkBlockedPatterns(value.toLowerCase(), userId, ip);
    }

    // ════════════════════════════════════════
    //  MODERAÇÃO DE IMAGEM
    // ════════════════════════════════════════

    /**
     * Valida uma imagem de preview ou avatar.
     * Usa Apache Tika para detectar o tipo REAL do arquivo (magic bytes),
     * ignorando o Content-Type declarado pelo cliente.
     */
    public void moderateImage(MultipartFile file, Long userId, String ip) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo de imagem ausente ou vazio.");
        }

        // Verifica tamanho
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("Imagem excede o tamanho máximo permitido de 10 MB.");
        }

        // Detecta Mime-type real via Tika
        String realMime = detectRealMime(file);
        if (!ALLOWED_IMAGE_MIMES.contains(realMime)) {
            auditLog.log(AuditLog.Action.CONTENT_MODERATION_BLOCK, userId, ip,
                    "Imagem bloqueada — Mime real: " + realMime
                            + " arquivo: " + file.getOriginalFilename());
            throw new BusinessException(
                    "Tipo de arquivo não permitido. Envie imagens no formato JPG, PNG ou WebP.");
        }

        log.debug("Imagem aprovada pela moderação: {} ({})", file.getOriginalFilename(), realMime);
    }

    // ════════════════════════════════════════
    //  MODERAÇÃO DE ARQUIVO (TEMPLATE .ZIP)
    // ════════════════════════════════════════

    /**
     * Valida um arquivo de template .zip.
     * Verifica: extensão, magic bytes, tamanho e Mime-type real.
     */
    public void moderateTemplateFile(MultipartFile file, Long userId, String ip) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Arquivo de template ausente ou vazio.");
        }

        // Verifica extensão explicitamente
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new BusinessException("Apenas arquivos .zip são aceitos como template.");
        }

        // Verifica tamanho
        if (file.getSize() > MAX_TEMPLATE_SIZE) {
            throw new BusinessException("Arquivo excede o tamanho máximo de 50 MB.");
        }

        // Verifica magic bytes (PK\x03\x04 = ZIP real)
        verifyZipMagicBytes(file, userId, ip);

        // Verifica Mime-type via Tika
        String realMime = detectRealMime(file);
        if (!realMime.contains("zip") && !realMime.contains("octet-stream")) {
            auditLog.log(AuditLog.Action.CONTENT_MODERATION_BLOCK, userId, ip,
                    "Template bloqueado — Mime real: " + realMime
                            + " arquivo: " + originalName);
            throw new BusinessException(
                    "O arquivo enviado não é um ZIP válido. Verifique e tente novamente.");
        }

        log.debug("Arquivo de template aprovado: {} ({})", originalName, realMime);
    }

    // ════════════════════════════════════════
    //  MÉTODOS PRIVADOS
    // ════════════════════════════════════════

    private void checkBlockedPatterns(String text, Long userId, String ip) {
        for (String pattern : BLOCKED_PATTERNS) {
            if (text.contains(pattern)) {
                auditLog.log(AuditLog.Action.CONTENT_MODERATION_BLOCK, userId, ip,
                        "Texto bloqueado por padrão proibido: [" + pattern + "]");
                throw new BusinessException(
                        "Conteúdo não permitido detectado. "
                                + "Revise seu texto e remova termos inapropriados.");
            }
        }
    }

    private String detectRealMime(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("Falha ao detectar Mime-type via Tika: {}", e.getMessage());
            // Em caso de erro na detecção, bloqueia por segurança
            return "application/octet-stream-unknown";
        }
    }

    private void verifyZipMagicBytes(MultipartFile file, Long userId, String ip) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(4);
            if (header.length < 4
                    || header[0] != ZIP_MAGIC[0] || header[1] != ZIP_MAGIC[1]
                    || header[2] != ZIP_MAGIC[2] || header[3] != ZIP_MAGIC[3]) {

                String hexHeader = HexFormat.of().formatHex(header);
                auditLog.log(AuditLog.Action.CONTENT_MODERATION_BLOCK, userId, ip,
                        "Magic bytes inválidos para ZIP: 0x" + hexHeader
                                + " arquivo: " + file.getOriginalFilename());
                throw new BusinessException(
                        "O arquivo enviado não possui assinatura ZIP válida. "
                                + "Certifique-se de enviar um arquivo .zip legítimo.");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("Não foi possível verificar a integridade do arquivo.");
        }
    }

    /**
     * Integração com OpenAI Moderation API.
     * Documentação: https://platform.openai.com/docs/api-reference/moderations
     */
    private void callOpenAiModeration(String text, Long userId, String ip) {
        // Implementação via java.net.http.HttpClient para não adicionar dependência extra
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            String body = "{\"input\":\"" + escapeJson(text) + "\"}";

            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.openai.com/v1/moderations"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                // Verifica se alguma categoria foi flagged=true
                if (responseBody.contains("\"flagged\":true")) {
                    auditLog.log(AuditLog.Action.CONTENT_MODERATION_BLOCK, userId, ip,
                            "Bloqueado pela OpenAI Moderation API");
                    throw new BusinessException(
                            "O conteúdo enviado foi identificado como inapropriado "
                                    + "pela nossa política de moderação.");
                }
            } else {
                log.warn("OpenAI Moderation API retornou status {}: {}",
                        response.statusCode(), response.body());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Falha na API de moderação não deve bloquear o upload (fail-open)
            log.warn("OpenAI Moderation API indisponível: {}", e.getMessage());
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
