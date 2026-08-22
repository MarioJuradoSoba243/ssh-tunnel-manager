package com.ssh.tunnelmanager.application.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class ErrorPresentation {
  private ErrorPresentation() {}

  public static String friendly(Throwable error) {
    String message = raw(error).toLowerCase(Locale.ROOT);
    if (message.contains("address already in use") || message.contains("port is already"))
      return "El puerto local ya está ocupado. Libera el puerto configurado y vuelve a intentarlo.";
    if (message.contains("authentication") || message.contains("auth"))
      return "No se pudo autenticar en uno de los hosts de la ruta SSH.";
    if (message.contains("timeout") || message.contains("timed out"))
      return "La conexión ha superado el tiempo de espera configurado.";
    if (message.contains("fingerprint") || message.contains("host key"))
      return "La identidad del host SSH requiere aprobación o ha cambiado.";
    if (message.contains("destination") || message.contains("connection refused"))
      return "No se pudo alcanzar el destino TCP del túnel.";
    if (message.contains("reintentos") || message.contains("retry"))
      return "Se agotaron los reintentos automáticos del túnel.";
    return "No se pudo completar la operación. Consulta los detalles técnicos.";
  }

  public static String technical(Throwable error) {
    return technical(null, null, null, error);
  }

  public static String technical(UUID tunnelId, String state, String route, Throwable error) {
    StringWriter buffer = new StringWriter();
    error.printStackTrace(new PrintWriter(buffer));
    return "timestamp="
        + Instant.now()
        + "\ntunnelId="
        + (tunnelId == null ? "n/a" : tunnelId)
        + "\nstate="
        + (state == null ? "n/a" : state)
        + "\nroute="
        + (route == null ? "n/a" : route)
        + "\nerror="
        + sanitize(buffer.toString());
  }

  public static String sanitize(String value) {
    return value.replaceAll("(?i)(password|passwd|secret)\\s*[=:]\\s*[^\\s,;]+", "$1=[REDACTED]");
  }

  private static String raw(Throwable error) {
    return error.getMessage() == null ? error.getClass().getName() : error.getMessage();
  }
}
