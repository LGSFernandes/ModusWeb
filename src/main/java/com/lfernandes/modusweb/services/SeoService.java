package com.lfernandes.modusweb.services;

import com.lfernandes.modusweb.models.Template;
import com.lfernandes.modusweb.repositories.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço de SEO do ModusWeb.
 *
 * Fornece:
 *  - Meta tags dinâmicas (title, description, OG, Twitter Card)
 *  - JSON-LD Schema.org para produtos (Product + Offer)
 *  - Geração de sitemap.xml
 *  - Geração de robots.txt
 */
@Service
@RequiredArgsConstructor
public class SeoService {

    private final TemplateRepository templateRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final String SITE_NAME    = "ModusWeb";
    private static final String DEFAULT_DESC =
            "ModusWeb é um Marketplace de templates digitais. "
                    + "Descubra Landing Pages, Dashboards, E-Commerce e muito mais.";

    // ── Meta Tags ────────────────────────────────────────────────────

    public SeoMeta homeMeta() {
        return SeoMeta.builder()
                .title("ModusWeb — Marketplace de Templates Digitais")
                .description(DEFAULT_DESC)
                .ogTitle("ModusWeb — Templates Digitais")
                .ogDescription(DEFAULT_DESC)
                .ogUrl(baseUrl)
                .ogImage(baseUrl + "/assets/images/og-default.png")
                .canonical(baseUrl)
                .build();
    }

    public SeoMeta shopMeta(String query, String category) {
        String title = "Loja de Templates";
        if (category != null && !category.isBlank())
            title = "Templates de " + capitalize(category);
        if (query != null && !query.isBlank())
            title = "Busca: \"" + query + "\" — ModusWeb";

        return SeoMeta.builder()
                .title(title + " — ModusWeb")
                .description("Explore nossa coleção de templates digitais premium. " + DEFAULT_DESC)
                .ogTitle(title)
                .ogDescription("Templates premium para cada projeto.")
                .ogUrl(baseUrl + "/shop")
                .canonical(baseUrl + "/shop")
                .build();
    }

    public SeoMeta templateDetailMeta(Template template) {
        String title       = template.getTitle() + " — ModusWeb";
        String description = template.getDescription() != null
                ? truncate(template.getDescription(), 160)
                : DEFAULT_DESC;
        String url         = baseUrl + "/shop/" + template.getId();
        String imageUrl    = template.getPreviewImage() != null
                ? baseUrl + "/uploads/" + template.getPreviewImage()
                : baseUrl + "/assets/images/og-default.png";

        return SeoMeta.builder()
                .title(title)
                .description(description)
                .ogTitle(template.getTitle())
                .ogDescription(description)
                .ogUrl(url)
                .ogImage(imageUrl)
                .canonical(url)
                .jsonLd(buildProductJsonLd(template, url, imageUrl))
                .build();
    }

    // ── JSON-LD Schema.org Product ───────────────────────────────────

    private String buildProductJsonLd(Template template, String url, String imageUrl) {
        BigDecimal price = template.getPrice() != null ? template.getPrice() : BigDecimal.ZERO;
        String availability = "https://schema.org/InStock";

        return """
            {
              "@context": "https://schema.org",
              "@type": "Product",
              "name": "%s",
              "description": "%s",
              "image": "%s",
              "url": "%s",
              "brand": { "@type": "Brand", "name": "ModusWeb" },
              "offers": {
                "@type": "Offer",
                "price": "%s",
                "priceCurrency": "BRL",
                "availability": "%s",
                "url": "%s"
              }
            }
            """.formatted(
                escapeJson(template.getTitle()),
                escapeJson(template.getDescription() != null ? truncate(template.getDescription(), 200) : ""),
                imageUrl, url,
                price.toPlainString(),
                availability, url
        );
    }

    // ── Sitemap.xml ──────────────────────────────────────────────────

    @Cacheable("categorias")
    @Transactional(readOnly = true)
    public String generateSitemap() {
        List<Template> templates =
                templateRepository.findByApprovedTrueAndActiveTrue(
                        org.springframework.data.domain.Pageable.ofSize(500)
                ).getContent();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"https://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Páginas estáticas
        for (String path : List.of("/", "/shop", "/auth/login", "/auth/register")) {
            sb.append("  <url><loc>").append(baseUrl).append(path)
                    .append("</loc><changefreq>weekly</changefreq><priority>0.8</priority></url>\n");
        }

        // Templates dinâmicos
        for (Template t : templates) {
            sb.append("  <url><loc>").append(baseUrl).append("/shop/").append(t.getId())
                    .append("</loc><changefreq>monthly</changefreq><priority>0.6</priority></url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    // ── robots.txt ───────────────────────────────────────────────────

    public String generateRobotsTxt() {
        return """
            User-agent: *
            Allow: /
            Disallow: /admin/
            Disallow: /seller/
            Disallow: /auth/
            Disallow: /uploads/templates/
            Disallow: /error

            Sitemap: %s/sitemap.xml
            """.formatted(baseUrl);
    }

    // ── DTO interno ──────────────────────────────────────────────────

    @lombok.Builder
    @lombok.Getter
    public static class SeoMeta {
        private String title;
        private String description;
        private String ogTitle;
        private String ogDescription;
        private String ogUrl;
        private String ogImage;
        private String canonical;
        private String jsonLd;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace("-", " ");
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
