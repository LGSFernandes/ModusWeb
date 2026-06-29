package com.lfernandes.modusweb.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

import java.nio.file.Paths;

/**
 * Configuração MVC: servir uploads estaticamente e interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Mapeia /uploads/** para o diretório físico de uploads.
     * Apenas previews e avatars são expostos publicamente.
     * Templates (.zip) são servidos apenas via Controller autenticado.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();

        // Previews e avatars — públicos
        registry.addResourceHandler("/uploads/previews/**")
                .addResourceLocations(uploadPath + "previews/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/uploads/avatars/**")
                .addResourceLocations(uploadPath + "avatars/")
                .setCachePeriod(3600);

        // CSS, JS, imagens estáticas da app
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(86400);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(86400);

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(86400);
    }
}
