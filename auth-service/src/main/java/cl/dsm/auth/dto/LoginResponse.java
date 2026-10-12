package cl.dsm.auth.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private String message;
    private String stage;
    private String sessionToken;
    private String token;
    private String tokenType;
    private Long expiresIn;
    private UsuarioInfo usuario;
    
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UsuarioInfo {
        private String rut;
        private String nombreCompleto;
        private String email;
        private String rol;
        private String dependencia;
    }
}