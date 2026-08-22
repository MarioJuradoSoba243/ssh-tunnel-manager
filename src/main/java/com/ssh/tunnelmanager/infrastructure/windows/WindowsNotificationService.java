package com.ssh.tunnelmanager.infrastructure.windows;

import com.ssh.tunnelmanager.application.notifications.NotificationPort;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Best-effort Windows toast adapter; notification failures never affect tunnel lifecycle. */
public final class WindowsNotificationService implements NotificationPort {
  private static final Logger LOGGER = LoggerFactory.getLogger(WindowsNotificationService.class);
  private final BooleanSupplier enabled;
  private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

  public WindowsNotificationService(BooleanSupplier enabled) {
    this.enabled = Objects.requireNonNull(enabled);
  }

  @Override
  public void notify(String title, String message) {
    if (!enabled.getAsBoolean()) return;
    worker.submit(
        () -> {
          if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
          String safeTitle = escapePowerShell(title);
          String safeMessage = escapePowerShell(message);
          String command =
              "$xml = [Windows.Data.Xml.Dom.XmlDocument]::new();"
                  + "$xml.LoadXml('<toast><visual><binding template=\"ToastText02\">"
                  + "<text>"
                  + safeTitle
                  + "</text><text>"
                  + safeMessage
                  + "</text></binding></visual></toast>');"
                  + "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml);"
                  + "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('SSH Tunnel Manager').Show($toast)";
          try {
            Process process =
                new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command).start();
            process.waitFor();
          } catch (Exception exception) {
            LOGGER.warn("No se pudo mostrar una notificación Windows", exception);
          }
        });
  }

  private static String escapePowerShell(String value) {
    return value.replace("'", "''").replace("<", "&lt;").replace(">", "&gt;");
  }

  @Override
  public void close() {
    worker.close();
  }
}
