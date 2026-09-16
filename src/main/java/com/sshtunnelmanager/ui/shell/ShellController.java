package com.sshtunnelmanager.ui.shell;

import com.sshtunnelmanager.application.ApplicationInfo;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Thin controller for the initial application shell. */
public final class ShellController {
  private final ApplicationInfo applicationInfo;

  @FXML private Label sectionTitle;

  public ShellController(ApplicationInfo applicationInfo) {
    this.applicationInfo = Objects.requireNonNull(applicationInfo, "applicationInfo");
  }

  public String applicationTitle() {
    return applicationInfo.displayName();
  }

  @FXML
  private void showHome() {
    showSection("Inicio");
  }

  @FXML
  private void showTunnels() {
    showSection("Túneles");
  }

  @FXML
  private void showGroups() {
    showSection("Grupos");
  }

  @FXML
  private void showHosts() {
    showSection("Hosts SSH");
  }

  @FXML
  private void showOrganization() {
    showSection("Organización");
  }

  @FXML
  private void showSettings() {
    showSection("Ajustes");
  }

  private void showSection(String section) {
    sectionTitle.setText(section);
  }
}
