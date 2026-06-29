package com.lfernandes.modusweb.dtos;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TemplateUploadDTO {

    @NotBlank(message = "Título é obrigatório")
    @Size(max = 255, message = "Título muito longo")
    private String title;

    @NotBlank(message = "Descrição é obrigatória")
    private String description;

    @NotNull(message = "Preço é obrigatório")
    @DecimalMin(value = "0.00", message = "Preço não pode ser negativo")
    private BigDecimal price;

    private String tags;

    private Long categoryId;

    /** Arquivo .zip do template */
    private MultipartFile templateFile;

    /** Imagem de preview do template */
    private MultipartFile previewImage;
}
