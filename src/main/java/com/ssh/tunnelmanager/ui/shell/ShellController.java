package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationComponents;
import com.ssh.tunnelmanager.application.host.HostListViewModel;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntime;
import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.application.tunnel.TunnelEditorViewModel;
import com.ssh.tunnelmanager.application.tunnel.TunnelListViewModel;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
  private TunnelEditorViewModel tunnelEditor;
  private TableView<TunnelRecord> tunnelTable;
  private Timeline statusRefresh;
  private boolean editingTunnel;

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
              components.testTunnelConnection(),
              components.deleteTunnel(),
              components.duplicateTunnel());
      tunnelEditor =
          new TunnelEditorViewModel(
              components.hostRepository(),
              components.credentialRepository(),
              components.tunnelRepository(),
              components.saveTunnel());
    }
  }

  @FXML
  private void initialize() {
    navigationList.getItems().setAll(shellViewModel.navigationItems());
    navigationList.setCellFactory(
        ignored ->
            new ListCell<>() {
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
    stopStatusRefresh();
    editingTunnel = false;
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
    sectionDescription.setText("Hosts SSH, credenciales y confianza de host keys.");
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
    HBox actions = new HBox(8);
    Button add = new Button("Nuevo host");
    Button edit = new Button("Editar");
    Button remove = new Button("Eliminar");
    Button forget = new Button("Eliminar confianza");
    add.setOnAction(event -> openHostEditor(table, null));
    edit.setOnAction(event -> openHostEditor(table, table.getSelectionModel().getSelectedItem()));
    remove.setOnAction(event -> deleteHost(table.getSelectionModel().getSelectedItem(), table));
    forget.setOnAction(event -> forgetHostKey(table.getSelectionModel().getSelectedItem(), table));
    actions.getChildren().addAll(add, edit, remove, forget);
    contentContainer.getChildren().addAll(table, actions);
    refreshHosts(table);
  }

  private void showTunnels() {
    stopStatusRefresh();
    editingTunnel = false;
    sectionDescription.setText(
        "Local Port Forwarding: configura, prueba, inicia, detén y reintenta túneles individuales.");
    if (tunnels == null) return;
    tunnelTable = new TableView<>();
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
        data -> new javafx.beans.property.SimpleStringProperty(stateText(data.getValue())));
    TableColumn<TunnelRecord, String> active = new TableColumn<>("Tiempo activo");
    active.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(activeTime(data.getValue())));
    tunnelTable.getColumns().addAll(name, endpoint, state, active);
    tunnelTable.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2
              && tunnelTable.getSelectionModel().getSelectedItem() != null) {
            openTunnelEditor(tunnelTable, tunnelTable.getSelectionModel().getSelectedItem());
          }
        });
    Button add = new Button("Nuevo túnel");
    Button edit = new Button("Editar");
    Button duplicate = new Button("Duplicar");
    Button remove = new Button("Eliminar");
    Button test = new Button("Probar conexión");
    Button start = new Button("Iniciar");
    Button stop = new Button("Detener");
    Button retry = new Button("Reintentar");
    Button details = new Button("Ver detalles");
    add.setOnAction(event -> openTunnelEditor(tunnelTable, null));
    edit.setOnAction(
        event -> openTunnelEditor(tunnelTable, tunnelTable.getSelectionModel().getSelectedItem()));
    duplicate.setOnAction(
        event -> duplicateTunnel(tunnelTable.getSelectionModel().getSelectedItem()));
    remove.setOnAction(event -> deleteTunnel(tunnelTable.getSelectionModel().getSelectedItem()));
    test.setOnAction(event -> testTunnel(tunnelTable.getSelectionModel().getSelectedItem()));
    start.setOnAction(
        event -> operateTunnel(tunnelTable.getSelectionModel().getSelectedItem(), tunnels::start));
    stop.setOnAction(
        event -> operateTunnel(tunnelTable.getSelectionModel().getSelectedItem(), tunnels::stop));
    retry.setOnAction(
        event -> operateTunnel(tunnelTable.getSelectionModel().getSelectedItem(), tunnels::retry));
    details.setOnAction(
        event -> showTunnelDetails(tunnelTable.getSelectionModel().getSelectedItem()));
    HBox actions = new HBox(8, add, edit, duplicate, remove, test, start, stop, retry, details);
    contentContainer.getChildren().addAll(tunnelTable, actions);
    refreshTunnels(tunnelTable);
    statusRefresh =
        new Timeline(
            new KeyFrame(javafx.util.Duration.seconds(1), event -> refreshTunnels(tunnelTable)));
    statusRefresh.setCycleCount(Timeline.INDEFINITE);
    statusRefresh.play();
  }

  private void refreshCurrentSection() {
    if (editingTunnel) return;
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
          Platform.runLater(
              () -> {
                if (table == tunnelTable) table.setItems(FXCollections.observableArrayList(values));
              });
        });
  }

  private void openHostEditor(TableView<SshHostRecord> table, SshHostRecord existing) {
    if (existing == null) {
      showHostEditor(table, null, null);
      return;
    }
    workers.submit(
        () ->
            hosts
                .find(existing.id())
                .ifPresent(
                    domain -> Platform.runLater(() -> showHostEditor(table, existing, domain))));
  }

  private void showHostEditor(
      TableView<SshHostRecord> table, SshHostRecord existing, SshHost domain) {
    Alert dialog = new Alert(Alert.AlertType.NONE);
    dialog.setTitle(existing == null ? "Nuevo host SSH" : "Editar host SSH");
    TextField name = new TextField(existing == null ? "" : existing.name());
    TextField hostname = new TextField(existing == null ? "" : existing.hostname());
    TextField port = new TextField(existing == null ? "22" : Integer.toString(existing.sshPort()));
    TextField username = new TextField(domain == null ? "" : domain.credential().username());
    javafx.scene.control.PasswordField password = new javafx.scene.control.PasswordField();
    if (domain != null) password.setText(domain.credential().password());
    TextField visiblePassword = new TextField(password.getText());
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
            "Nombre", name,
            "Hostname", hostname,
            "Puerto SSH", port,
            "Usuario", username,
            "Password", password);
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

  private void forgetHostKey(SshHostRecord selected, TableView<SshHostRecord> table) {
    if (selected == null || components == null) return;
    workers.submit(
        () -> {
          components.trustedHostKeys().deleteByHost(selected.hostname(), selected.sshPort());
          Platform.runLater(
              () ->
                  showInfo(
                      "Confianza eliminada",
                      "La siguiente conexión volverá a solicitar aprobación."));
        });
  }

  private void openTunnelEditor(TableView<TunnelRecord> table, TunnelRecord existing) {
    if (tunnelEditor == null) return;
    workers.submit(
        () -> {
          List<SshHostRecord> hostRecords = tunnelEditor.hosts();
          Platform.runLater(() -> showTunnelEditor(table, existing, hostRecords));
        });
  }

  private void showTunnelEditor(
      TableView<TunnelRecord> table, TunnelRecord existing, List<SshHostRecord> hostRecords) {
    editingTunnel = true;
    stopStatusRefresh();
    contentContainer
        .getChildren()
        .removeIf(node -> node != sectionTitle && node != sectionDescription);
    sectionDescription.setText("Editor de túnel: valida localmente antes de guardar.");
    TunnelEditorViewModel.Form initial =
        existing == null
            ? new TunnelEditorViewModel.Form(
                null, "", "127.0.0.1", "", "", "", null, null, null, "", "", "", "")
            : tunnelEditor.fromRecord(existing);
    TextField name = new TextField(initial.name());
    TextField bind = new TextField(initial.bindAddress());
    TextField localPort = new TextField(initial.localPort());
    TextField destinationHost = new TextField(initial.destinationHost());
    TextField destinationPort = new TextField(initial.destinationPort());
    ComboBox<SshHostRecord> jump1 = hostCombo(hostRecords, initial.jump1HostId());
    ComboBox<SshHostRecord> jump2 = hostCombo(hostRecords, initial.jump2HostId());
    ComboBox<SshHostRecord> finalHost = hostCombo(hostRecords, initial.finalHostId());
    TextField keepAlive = new TextField(initial.keepAliveSeconds());
    keepAlive.setPromptText("Global: 30 segundos");
    TextField timeout = new TextField(initial.connectionTimeoutSeconds());
    timeout.setPromptText("Global: 15 segundos");
    TextField retries = new TextField(initial.retryCount());
    retries.setPromptText("Global: 5");
    TextArea options = new TextArea(initial.sshOptions());
    options.setPromptText("Una opción por línea, por ejemplo: ServerAliveCountMax=3");
    options.setPrefRowCount(7);

    Tab general = new Tab("General", formGrid("Nombre", name));
    Tab forwarding =
        new Tab(
            "Local forwarding",
            formGrid(
                "Bind address", bind,
                "Puerto local", localPort,
                "Host destino", destinationHost,
                "Puerto destino", destinationPort));
    Tab route =
        new Tab("Ruta SSH", formGrid("Jump 1", jump1, "Jump 2", jump2, "SSH final", finalHost));
    Tab advanced =
        new Tab(
            "Opciones avanzadas",
            formGrid(
                "Keep-alive (s)", keepAlive,
                "Timeout por tramo (s)", timeout,
                "Reintentos", retries,
                "Opciones SSH", options));
    List.of(general, forwarding, route, advanced).forEach(tab -> tab.setClosable(false));
    TabPane tabs = new TabPane(general, forwarding, route, advanced);
    Label validation = new Label();
    validation.getStyleClass().add("error-text");
    Button saveButton = new Button("Guardar");
    Button cancelButton = new Button("Cancelar");
    HBox actions = new HBox(8, saveButton, cancelButton);
    VBox editor = new VBox(12, tabs, validation, actions);
    VBox.setVgrow(tabs, Priority.ALWAYS);
    ScrollPane scroll = new ScrollPane(editor);
    scroll.setFitToWidth(true);
    contentContainer.getChildren().add(scroll);

    Runnable validate =
        () -> {
          List<String> errors =
              tunnelEditor.validate(
                  form(
                      initial.id(),
                      name,
                      bind,
                      localPort,
                      destinationHost,
                      destinationPort,
                      finalHost,
                      jump1,
                      jump2,
                      keepAlive,
                      timeout,
                      retries,
                      options));
          validation.setText(String.join("\n", errors));
          saveButton.setDisable(!errors.isEmpty());
        };
    List.of(
            name,
            bind,
            localPort,
            destinationHost,
            destinationPort,
            keepAlive,
            timeout,
            retries,
            options)
        .forEach(
            control -> control.textProperty().addListener((obs, old, value) -> validate.run()));
    List.of(jump1, jump2, finalHost)
        .forEach(
            control -> control.valueProperty().addListener((obs, old, value) -> validate.run()));
    validate.run();
    saveButton.setOnAction(
        event ->
            saveTunnelFromEditor(
                table,
                existing,
                form(
                    initial.id(),
                    name,
                    bind,
                    localPort,
                    destinationHost,
                    destinationPort,
                    finalHost,
                    jump1,
                    jump2,
                    keepAlive,
                    timeout,
                    retries,
                    options)));
    cancelButton.setOnAction(event -> showTunnels());
  }

  private void saveTunnelFromEditor(
      TableView<TunnelRecord> table, TunnelRecord existing, TunnelEditorViewModel.Form form) {
    boolean restart = existing != null && tunnels.runtime(existing.id()).actualState().isActive();
    if (restart) {
      Alert confirmation =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "El túnel está activo. Guardar los cambios requiere reiniciarlo.");
      confirmation.setHeaderText("Guardar y reiniciar");
      ButtonType saveRestart = new ButtonType("Guardar y reiniciar", ButtonBar.ButtonData.OK_DONE);
      confirmation.getButtonTypes().setAll(saveRestart, ButtonType.CANCEL);
      if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != saveRestart) return;
    }
    workers.submit(
        () -> {
          try {
            if (restart) tunnels.stop(existing.id());
            tunnelEditor.save(form);
            if (restart) tunnels.start(existing.id());
            Platform.runLater(this::showTunnels);
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo guardar el túnel", exception));
          }
        });
  }

  private void duplicateTunnel(TunnelRecord selected) {
    if (selected == null) return;
    TextInputDialog dialog = new TextInputDialog(selected.name() + " (copia)");
    dialog.setTitle("Duplicar túnel");
    dialog.setHeaderText("Nombre de la copia");
    dialog
        .showAndWait()
        .ifPresent(name -> workers.submit(() -> tunnels.duplicate(selected.id(), name)));
  }

  private void deleteTunnel(TunnelRecord selected) {
    if (selected == null) return;
    Alert confirmation =
        new Alert(Alert.AlertType.CONFIRMATION, "Se eliminará el túnel seleccionado.");
    confirmation.setHeaderText("Eliminar túnel");
    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    workers.submit(
        () -> {
          try {
            tunnels.delete(selected.id());
            Platform.runLater(this::showTunnels);
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo eliminar el túnel", exception));
          }
        });
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
                if (!result.successful()) {
                  ButtonType copy = new ButtonType("Copiar error");
                  alert.getButtonTypes().add(copy);
                  alert
                      .showAndWait()
                      .ifPresent(
                          button -> {
                            if (button == copy) copyToClipboard(result.technicalDetails());
                          });
                } else {
                  alert.showAndWait();
                }
              });
        });
  }

  private void showTunnelDetails(TunnelRecord selected) {
    if (selected == null) return;
    TunnelRuntime runtime = tunnels.runtime(selected.id());
    String error =
        runtime.lastError() == null ? "Sin error técnico." : stackTrace(runtime.lastError());
    String events =
        runtime.sessionEvents().isEmpty()
            ? "Sin eventos en esta sesión."
            : String.join("\n", runtime.sessionEvents());
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Detalles del túnel");
    alert.setHeaderText(selected.name() + " — " + runtime.actualState());
    alert.setContentText("Historial de sesión:\n" + events + "\n\nDetalle técnico:\n" + error);
    ButtonType copy = new ButtonType("Copiar error");
    alert.getButtonTypes().add(copy);
    alert
        .showAndWait()
        .ifPresent(
            button -> {
              if (button == copy) copyToClipboard(error);
            });
  }

  private void operateTunnel(TunnelRecord selected, java.util.function.Consumer<UUID> operation) {
    if (selected != null) workers.submit(() -> operation.accept(selected.id()));
  }

  private static ComboBox<SshHostRecord> hostCombo(List<SshHostRecord> hosts, UUID selected) {
    ComboBox<SshHostRecord> combo = new ComboBox<>(FXCollections.observableArrayList(hosts));
    combo.setCellFactory(ignored -> hostCell());
    combo.setButtonCell(hostCell());
    hosts.stream()
        .filter(host -> host.id().equals(selected))
        .findFirst()
        .ifPresent(combo::setValue);
    return combo;
  }

  private static ListCell<SshHostRecord> hostCell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(SshHostRecord item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? null : item.name() + " (" + item.hostname() + ")");
      }
    };
  }

  private static TunnelEditorViewModel.Form form(
      UUID id,
      TextField name,
      TextField bind,
      TextField localPort,
      TextField destinationHost,
      TextField destinationPort,
      ComboBox<SshHostRecord> finalHost,
      ComboBox<SshHostRecord> jump1,
      ComboBox<SshHostRecord> jump2,
      TextField keepAlive,
      TextField timeout,
      TextField retries,
      TextArea options) {
    return new TunnelEditorViewModel.Form(
        id,
        name.getText(),
        bind.getText(),
        localPort.getText(),
        destinationHost.getText(),
        destinationPort.getText(),
        idOf(finalHost),
        idOf(jump1),
        idOf(jump2),
        keepAlive.getText(),
        timeout.getText(),
        retries.getText(),
        options.getText());
  }

  private static UUID idOf(ComboBox<SshHostRecord> combo) {
    return combo.getValue() == null ? null : combo.getValue().id();
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

  private String stateText(TunnelRecord record) {
    TunnelRuntime runtime = tunnels.runtime(record.id());
    return runtime.actualState() == com.ssh.tunnelmanager.domain.tunnel.ActualState.RECONNECTING
        ? "RECONNECTING " + runtime.retryAttempt() + "/" + retryLimit(record)
        : runtime.actualState().name();
  }

  private String activeTime(TunnelRecord record) {
    TunnelRuntime runtime = tunnels.runtime(record.id());
    if (runtime.startedAt() == null) return "—";
    Duration elapsed = Duration.between(runtime.startedAt(), java.time.Instant.now());
    return "%02d:%02d:%02d"
        .formatted(elapsed.toHours(), elapsed.toMinutesPart(), elapsed.toSecondsPart());
  }

  private static int retryLimit(TunnelRecord record) {
    return record.retryCountOverride() == null ? 5 : record.retryCountOverride();
  }

  private void showError(String header, Throwable exception) {
    Alert alert = new Alert(Alert.AlertType.ERROR, exception.getMessage());
    alert.setHeaderText(header);
    alert.showAndWait();
  }

  private void showInfo(String header, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
    alert.setHeaderText(header);
    alert.showAndWait();
  }

  private static void copyToClipboard(String value) {
    javafx.scene.input.Clipboard.getSystemClipboard()
        .setContent(java.util.Map.of(javafx.scene.input.DataFormat.PLAIN_TEXT, value));
  }

  private static String stackTrace(Throwable error) {
    java.io.StringWriter buffer = new java.io.StringWriter();
    error.printStackTrace(new java.io.PrintWriter(buffer));
    return buffer.toString();
  }

  private void stopStatusRefresh() {
    if (statusRefresh != null) statusRefresh.stop();
    statusRefresh = null;
  }

  @Override
  public void close() {
    stopStatusRefresh();
    workers.close();
  }
}
