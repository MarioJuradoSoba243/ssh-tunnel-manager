package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import java.util.Objects;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

public final class ShellController {
  @FXML private ListView<NavigationItem> navigationList;
  @FXML private Label sectionTitle;
  @FXML private Label sectionDescription;

  private final ShellViewModel viewModel;

  public ShellController(ShellViewModel viewModel) {
    this.viewModel = Objects.requireNonNull(viewModel);
  }

  @FXML
  private void initialize() {
    navigationList.getItems().setAll(viewModel.navigationItems());
    navigationList.setCellFactory(
        ignored ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(NavigationItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
              }
            });
    navigationList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, old, current) -> {
              if (current != null) {
                sectionTitle.setText(current.label());
                sectionDescription.setText(
                    "Sección preparada para la siguiente fase de implementación.");
              }
            });
    navigationList.getSelectionModel().selectFirst();
  }
}
