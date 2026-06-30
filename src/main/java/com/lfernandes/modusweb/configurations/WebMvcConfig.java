package com.lfernandes.modusweb.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Configuração MVC residual da API REST.
 * Responsável por expor arquivos estáticos de upload. (previews de templates e avatars de usuarios)
 * através do caminho "/uploads/**".
 * Arquivos de template (.zip) não são expostos aqui — permanecem acessíveis apenas via endpoint autenticado de download.
 * O CORS desta API é configurado centralmente pelo SecurityConfig.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();

        registry.addResourceHandler("/uploads/previews/**")
                .addResourceLocations(uploadPath + "previews/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(uploadPath + "avatars/")
                .setCachePeriod(3600);
    }
}