package com.meada.admin.dashboard;

/**
 * Alerta de plataforma exibido no hub do super-admin (camada 6.0). severity "warning" |
 * "error"; message é o texto humano; link é a rota do painel para investigar (ou null).
 */
public record AlertDto(String severity, String message, String link) {
}
