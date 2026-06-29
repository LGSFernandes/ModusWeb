package com.lfernandes.modusweb.dtos;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserProfileDTO {

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150)
    private String name;

    @Size(max = 1000, message = "Bio muito longa")
    private String bio;

    /** Nova senha (opcional — deixar em branco para não alterar) */
    private String newPassword;

    private String confirmNewPassword;

    /** Nova foto de perfil (opcional) */
    private MultipartFile avatar;
}
