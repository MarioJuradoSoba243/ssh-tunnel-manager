package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationComponents;
import com.ssh.tunnelmanager.application.host.HostListViewModel;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.application.tunnel.TunnelListViewModel;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import com.ssh.tunnelmanager.domain.tunnel.LocalForward;
import com.ssh.tunnelmanager.domain.tunnel.RemoteDestination;
import com.ssh.tunnelmanager.domain.tunnel.SshRoute;
import com.ssh.tunnelmanager.domain.tunnel.Tunnel;
import com.ssh.tunnelmanager.domain.tunnel.TunnelOptions;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public final class ShellController implements AutoCloseable {
  @FXML private ListView<NavigationItem> navigationList;
  @FXML private Label sectionTitle;
  @FXML private Label sectionDescription;
  @FXML private VBox contentContainer;

  private final ShellViewModel shellViewModel;
  private final ApplicationComponents components;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private HostListViewModel hosts;
  private TunnelListViewModel tunnels;

  public ShellController(ShellViewModel viewModel) {
    this(viewModel, null);
  }

  public ShellController(ShellViewModel viewModel, ApplicationComponents components) {
    this.shellViewModel = Objects.requireNonNull(viewModel);
    this.components = components;
    if (components != null) {
      hosts =
          new HostListViewModel(
              components.hostRepository(),
              components.credentialRepository(),
              components.saveSshHost(),
              components.deleteSshHost());
      tunnels =
          new TunnelListViewModel(
              components.tunnelRepository(),
              components.runtimeManager(),
              components.startTunnel(),
              components.stopTunnel(),
              components.retryTunnel(),
              components.testTunnelConnection());
    }
  }

  @FXML
  private void initialize() {
    navigationList.getItems().setAll(shellViewModel.navigationItems());
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
              if (current != null) showSection(current);
            });
    navigationList.getSelectionModel().selectFirst();
    if (tunnels != null) tunnels.subscribe(event -> Platform.runLater(this::refreshCurrentSection));
  }

  private void showSection(NavigationItem item) {
    sectionTitle.setText(item.label());
    contentContainer
        .getChildren()
        .removeIf(node -> node != sectionTitle && node != sectionDescription);
    switch (item.id()) {
      case "hosts" -> showHosts();
      case "tunnels" -> showTunnels();
      default ->
          sectionDescription.setText("Esta sección estará disponible en una fase posterior.");
    }
  }

  private void showHosts() {
    sectionDescription.setText("Hosts SSH y sus credenciales independientes.");
    if (hosts == null) return;
    TableView<SshHostRecord> table = new TableView<>();
    TableColumn<SshHostRecord, String> name = new TableColumn<>("Nombre");
    name.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
    TableColumn<SshHostRecord, String> endpoint = new TableColumn<>("Endpoint");
    endpoint.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().hostname() + ":" + data.getValue().sshPort()));
    table.getColumns().addAll(name, endpoint);
    ButtonBar actions = new ButtonBar();
    javafx.scene.control.Button add = new javafx.scene.control.Button("Nuevo host");
    javafx.scene.control.Button edit = new javafx.scene.control.Button("Editar");
    javafx.scene.control.Button remove = new javafx.scene.control.Button("Eliminar");
    add.setOnAction(event -> openHostEditor(table, null));
    edit.setOnAction(event -> openHostEditor(table, table.getSelectionModel().getSelectedItem()));
    remove.setOnAction(event -> deleteHost(table.getSelectionModel().getSelectedItem(), table));
    actions.getButtons().addAll(add, edit, remove);
    contentContainer.getChildren().addAll(table, actions);
    refreshHosts(table);
  }

  private void showTunnels() {
    sectionDescription.setText(
        "Local Port Forwarding: prueba, inicia, detén y reintenta túneles individuales.");
    if (tunnels == null) return;
    TableView<TunnelRecord> table = new TableView<>();
    TableColumn<TunnelRecord, String> name = new TableColumn<>("Nombre");
    name.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
    TableColumn<TunnelRecord, String> endpoint = new TableColumn<>("Local");
    endpoint.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().bindAddress() + ":" + data.getValue().localPort()));
    TableColumn<TunnelRecord, String> state = new TableColumn<>("Estado");
    state.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                tunnels.runtime(data.getValue().id()).actualState().name()));
    table.getColumns().addAll(name, endpoint, state);
    javafx.scene.control.Button add = new javafx.scene.control.Button("Nuevo túnel");
    javafx.scene.control.Button test = new javafx.scene.control.Button("Probar conexión");
    javafx.scene.control.Button start = new javafx.scene.control.Button("Iniciar");
    javafx.scene.control.Button stop = new javafx.scene.control.Button("Detener");
    javafx.scene.control.Button retry = new javafx.scene.control.Button("Reintentar");
    add.setOnAction(event -> openTunnelEditor(table));
    test.setOnAction(event -> testTunnel(table.getSelectionModel().getSelectedItem()));
    start.setOnAction(
        event -> operateTunnel(table.getSelectionModel().getSelectedItem(), tunnels::start));
    stop.setOnAction(
        event -> operateTunnel(table.getSelectionModel().getSelectedItem(), tunnels::stop));
    retry.setOnAction(
        event -> operateTunnel(table.getSelectionModel().getSelectedItem(), tunnels::retry));
    ButtonBar actions = new ButtonBar();
    actions.getButtons().addAll(add, test, start, stop, retry);
    contentContainer.getChildren().addAll(table, actions);
    refreshTunnels(table);
  }

  private void refreshCurrentSection() {
    NavigationItem selected = navigationList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if ("hosts".equals(selected.id())) showHosts();
    if ("tunnels".equals(selected.id())) showTunnels();
  }

  private void refreshHosts(TableView<SshHostRecord> table) {
    workers.submit(
        () -> {
          List<SshHostRecord> values = hosts.hosts();
          Platform.runLater(() -> table.setItems(FXCollections.observableArrayList(values)));
        });
  }

  private void refreshTunnels(TableView<TunnelRecord> table) {
    workers.submit(
        () -> {
          List<TunnelRecord> values = tunnels.tunnels();
          Platform.runLater(() -> table.setItems(FXCollections.observableArrayList(values)));
        });
  }

  private void openHostEditor(TableView<SshHostRecord> table, SshHostRecord existing) {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle(existing == null ? "Nuevo host SSH" : "Editar host SSH");
    TextField name = new TextField(existing == null ? "" : existing.name());
    TextField hostname = new TextField(existing == null ? "" : existing.hostname());
    TextField port = new TextField(existing == null ? "22" : Integer.toString(existing.sshPort()));
    TextField username = new TextField();
    PasswordField password = new PasswordField();
    TextField visiblePassword = new TextField();
    CheckBox showPassword = new CheckBox("Mostrar contraseña");
    showPassword
        .selectedProperty()
        .addListener(
            (obs, old, selected) -> {
              if (selected) visiblePassword.setText(password.getText());
              visiblePassword.setVisible(selected);
              visiblePassword.setManaged(selected);
              password.setVisible(!selected);
              password.setManaged(!selected);
            });
    visiblePassword.setVisible(false);
    visiblePassword.setManaged(false);
    GridPane grid =
        formGrid(
            "Nombre",
            name,
            "Hostname",
            hostname,
            "Puerto SSH",
            port,
            "Usuario",
            username,
            "Password",
            password);
    grid.add(showPassword, 1, 5);
    grid.add(visiblePassword, 1, 6);
    dialog.getDialogPane().setContent(grid);
    ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
    dialog.setResultConverter(button -> button == saveButton ? button : null);
    dialog
        .showAndWait()
        .ifPresent(
            ignored -> {
              String selectedPassword =
                  showPassword.isSelected() ? visiblePassword.getText() : password.getText();
              UUID id = existing == null ? UUID.randomUUID() : existing.id();
              workers.submit(
                  () -> {
                    try {
                      hosts.save(
                          new SshHost(
                              id,
                              name.getText(),
                              hostname.getText(),
                              Integer.parseInt(port.getText()),
                              new Credential(username.getText(), selectedPassword)));
                      Platform.runLater(() -> refreshHosts(table));
                    } catch (RuntimeException exception) {
                      Platform.runLater(() -> showError("No se pudo guardar el host", exception));
                    }
                  });
            });
  }

  private void deleteHost(SshHostRecord selected, TableView<SshHostRecord> table) {
    if (selected == null) return;
    workers.submit(
        () -> {
          try {
            hosts.delete(selected.id());
            Platform.runLater(() -> refreshHosts(table));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se puede eliminar el host", exception));
          }
        });
  }

  private void openTunnelEditor(TableView<TunnelRecord> table) {
    List<SshHostRecord> hostRecords = hosts.hosts();
    if (hostRecords.isEmpty()) {
      showError("No hay hosts SSH", new IllegalStateException("Crea primero un host SSH"));
      return;
    }
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.setTitle("Nuevo túnel");
    TextField name = new TextField();
    TextField bind = new TextField("127.0.0.1");
    TextField localPort = new TextField();
    TextField destinationHost = new TextField();
    TextField destinationPort = new TextField();
    ComboBox<SshHostRecord> finalHost =
        new ComboBox<>(FXCollections.observableArrayList(hostRecords));
    ComboBox<SshHostRecord> jump1 = new ComboBox<>(FXCollections.observableArrayList(hostRecords));
    ComboBox<SshHostRecord> jump2 = new ComboBox<>(FXCollections.observableArrayList(hostRecords));
    finalHost.setPromptText("SSH final (obligatorio)");
    jump1.setPromptText("Jump 1 (opcional)");
    jump2.setPromptText("Jump 2 (opcional)");
    GridPane grid =
        formGrid(
            "Nombre",
            name,
            "Bind",
            bind,
            "Puerto local",
            localPort,
            "Destino",
            destinationHost,
            "Puerto destino",
            destinationPort);
    grid.addRow(5, new Label("SSH final"), finalHost);
    grid.addRow(6, new Label("Jump 1"), jump1);
    grid.addRow(7, new Label("Jump 2"), jump2);
    dialog.getDialogPane().setContent(grid);
    ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
    dialog.setResultConverter(button -> button == saveButton ? button : null);
    dialog
        .showAndWait()
        .ifPresent(
            ignored ->
                workers.submit(
                    () -> {
                      try {
                        List<SshHost> jumps = new java.util.ArrayList<>();
                        if (jump1.getValue() != null)
                          jumps.add(hosts.find(jump1.getValue().id()).orElseThrow());
                        if (jump2.getValue() != null)
                          jumps.add(hosts.find(jump2.getValue().id()).orElseThrow());
                        SshHost finalDomain = hosts.find(finalHost.getValue().id()).orElseThrow();
                        Tunnel tunnel =
                            new Tunnel(
                                UUID.randomUUID(),
                                name.getText(),
                                new LocalForward(
                                    bind.getText(), Integer.parseInt(localPort.getText())),
                                new RemoteDestination(
                                    destinationHost.getText(),
                                    Integer.parseInt(destinationPort.getText())),
                                new SshRoute(jumps, finalDomain),
                                TunnelOptions.defaults());
                        components.saveTunnel().execute(tunnel);
                        Platform.runLater(() -> refreshTunnels(table));
                      } catch (RuntimeException exception) {
                        Platform.runLater(
                            () -> showError("No se pudo guardar el túnel", exception));
                      }
                    }));
  }

  private void testTunnel(TunnelRecord selected) {
    if (selected == null) return;
    workers.submit(
        () -> {
          var result = tunnels.test(selected.id());
          Platform.runLater(
              () -> {
                Alert alert =
                    new Alert(
                        result.successful() ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setTitle("Probar conexión");
                alert.setHeaderText(result.message());
                alert.setContentText(result.technicalDetails());
                ButtonType copy = new ButtonType("Copiar error");
                if (!result.successful()) alert.getButtonTypes().add(copy);
                alert
                    .showAndWait()
                    .ifPresent(
                        button -> {
                          if (button == copy)
                            javafx.scene.input.Clipboard.getSystemClipboard()
                                .setContent(
                                    java.util.Map.of(
                                        javafx.scene.input.DataFormat.PLAIN_TEXT,
                                        result.technicalDetails()));
                        });
              });
        });
  }

  private void operateTunnel(TunnelRecord selected, java.util.function.Consumer<UUID> operation) {
    if (selected != null) workers.submit(() -> operation.accept(selected.id()));
  }

  private static GridPane formGrid(Object... values) {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(12));
    for (int index = 0; index < values.length; index += 2) {
      grid.add(new Label((String) values[index]), 0, index / 2);
      grid.add((Node) values[index + 1], 1, index / 2);
    }
    return grid;
  }

  private void showError(String header, Throwable exception) {
    Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage());
    alert.setHeaderText(header);
    alert.showAndWait();
  }

  @Override
  public void close() {
    workers.close();
  }
}
