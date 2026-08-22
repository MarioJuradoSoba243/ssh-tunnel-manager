package com.ssh.tunnelmanager.application.ssh;

public record ConnectionTestResult(boolean successful, String message, String technicalDetails) {
  public static ConnectionTestResult success() {
    return new ConnectionTestResult(true, "Conexión correcta", "");
  }

  public static ConnectionTestResult failure(String message, Throwable cause) {
    String details = cause == null ? "" : cause.toString();
    return new ConnectionTestResult(false, message, details);
  }
}
