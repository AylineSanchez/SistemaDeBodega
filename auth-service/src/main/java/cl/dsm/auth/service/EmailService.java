package cl.dsm.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${app.email.from-email}")
    private String fromEmail;
    
    @Value("${app.email.from-name}")
    private String fromName;
    
    // ============================================================
    // MÉTODO 1: Enviar código MFA
    // ============================================================
    @Async
    public void enviarEmailCodigoMfa(String destinatario, String nombre,
                                      String codigo, int minutosValidez) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, fromName);
            helper.setTo(destinatario);
            helper.setSubject("🔐 Código de verificación - Sistema Bodega DSM");
            helper.setText(construirHtmlMfa(nombre, codigo, minutosValidez), true);
            
            mailSender.send(message);
            log.info("✓ Email MFA enviado a {}", destinatario);
            
        } catch (MessagingException e) {
            log.error("✗ Error al enviar email MFA a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("No se pudo enviar el código de verificación", e);
        } catch (Exception e) {
            log.error("✗ Error inesperado al enviar MFA a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("Error inesperado al enviar email", e);
        }
    }
    
    // ============================================================
    // MÉTODO 2: Enviar email de reseteo de contraseña
    // ============================================================
    @Async
    public void enviarEmailResetPassword(String destinatario, String nombre,
                                          String enlaceReset, int minutosValidez) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, fromName);
            helper.setTo(destinatario);
            helper.setSubject("🔑 Restablecer contraseña - Sistema Bodega DSM");
            helper.setText(construirHtmlReset(nombre, enlaceReset, minutosValidez), true);
            
            mailSender.send(message);
            log.info("✓ Email de reset enviado a {}", destinatario);
            
        } catch (MessagingException e) {
            log.error("✗ Error al enviar email de reset a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("No se pudo enviar el email de reseteo", e);
        } catch (Exception e) {
            log.error("✗ Error inesperado al enviar reset a {}: {}", destinatario, e.getMessage());
            throw new RuntimeException("Error inesperado al enviar email", e);
        }
    }
    
    // ============================================================
    // TEMPLATES HTML
    // ============================================================
    
    private String construirHtmlMfa(String nombre, String codigo, int minutos) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0ece4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
                    .header { background: linear-gradient(135deg, #e67e22, #d35400); padding: 30px; text-align: center; color: #ffffff; }
                    .header h1 { margin: 0; font-size: 1.4em; }
                    .content { padding: 35px 30px; color: #2c3e50; line-height: 1.6; text-align: center; }
                    .content h2 { color: #2c3e50; margin-top: 0; }
                    .code-box { display: inline-block; background: #faf9f7; border: 2px dashed #e67e22; border-radius: 12px; padding: 20px 40px; margin: 25px 0; }
                    .code { font-family: 'Courier New', monospace; font-size: 2.5em; font-weight: 700; letter-spacing: 12px; color: #d35400; }
                    .expires { color: #95a5a6; font-size: 0.9em; }
                    .warning { background: #fef9e7; border-left: 4px solid #f39c12; padding: 12px 18px; border-radius: 8px; font-size: 0.85em; color: #7d6608; margin: 20px 0; text-align: left; }
                    .footer { background: #f8f5f0; padding: 20px 30px; text-align: center; font-size: 0.8em; color: #7f8c8d; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🏥 Sistema Bodega DSM Santa Cruz</h1>
                    </div>
                    <div class="content">
                        <h2>Hola %s,</h2>
                        <p>Tu código de verificación es:</p>
                        <div class="code-box">
                            <div class="code">%s</div>
                        </div>
                        <p class="expires">⏱ Este código expira en <strong>%d minutos</strong></p>
                        <div class="warning">
                            ⚠️ <strong>Por seguridad:</strong> nunca compartas este código con nadie. El personal del DSM jamás te lo pedirá.
                        </div>
                    </div>
                    <div class="footer">
                        © 2026 Ilustre Municipalidad de Santa Cruz - DSM<br>
                        Si no solicitaste este código, ignora este mensaje.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(nombre, codigo, minutos);
    }
    
    private String construirHtmlReset(String nombre, String enlace, int minutos) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0ece4; margin: 0; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
                    .header { background: linear-gradient(135deg, #e67e22, #d35400); padding: 30px; text-align: center; color: #ffffff; }
                    .header h1 { margin: 0; font-size: 1.4em; }
                    .content { padding: 35px 30px; color: #2c3e50; line-height: 1.6; }
                    .content h2 { color: #2c3e50; margin-top: 0; }
                    .btn { display: inline-block; padding: 14px 35px; background: linear-gradient(135deg, #e67e22, #d35400); color: #ffffff !important; text-decoration: none; border-radius: 50px; font-weight: 700; margin: 20px 0; }
                    .warning { background: #fef9e7; border-left: 4px solid #f39c12; padding: 12px 18px; border-radius: 8px; font-size: 0.9em; color: #7d6608; margin: 20px 0; }
                    .link-fallback { word-break: break-all; color: #e67e22; font-size: 0.85em; background: #faf9f7; padding: 12px; border-radius: 8px; }
                    .footer { background: #f8f5f0; padding: 20px 30px; text-align: center; font-size: 0.8em; color: #7f8c8d; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🏥 Sistema Bodega DSM Santa Cruz</h1>
                    </div>
                    <div class="content">
                        <h2>Hola %s,</h2>
                        <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en el Sistema de Gestión Integral de Bodega.</p>
                        <p style="text-align:center;">
                            <a href="%s" class="btn">Restablecer mi contraseña</a>
                        </p>
                        <p style="text-align:center; font-size: 0.85em; color: #95a5a6;">
                            Este enlace expira en <strong>%d minutos</strong>.
                        </p>
                        <div class="warning">
                            ⚠️ <strong>Si no solicitaste este cambio</strong>, puedes ignorar este correo. Tu contraseña actual seguirá siendo válida.
                        </div>
                        <p style="font-size: 0.85em; color: #7f8c8d;">Si el botón no funciona, copia y pega este enlace:</p>
                        <div class="link-fallback">%s</div>
                    </div>
                    <div class="footer">
                        © 2026 Ilustre Municipalidad de Santa Cruz - DSM<br>
                        Dirección de Salud Municipal - Diego Portales N° 625
                    </div>
                </div>
            </body>
            </html>
            """.formatted(nombre, enlace, minutos, enlace);
    }
}