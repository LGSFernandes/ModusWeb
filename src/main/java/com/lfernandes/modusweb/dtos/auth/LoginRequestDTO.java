package com.lfernandes.modusweb.dtos.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    @NotBlank(message = "O e-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    private String email;

    @NotBlank(message = "A senha e obrigatoria")
    private String password;
}