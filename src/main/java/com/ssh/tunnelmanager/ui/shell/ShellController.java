package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.bootstrap.ApplicationComponents;
import com.ssh.tunnelmanager.application.error.ErrorPresentation;
import com.ssh.tunnelmanager.application.group.GroupConflictException;
import com.ssh.tunnelmanager.application.group.GroupEditorViewModel;
import com.ssh.tunnelmanager.application.group.GroupListViewModel;
import com.ssh.tunnelmanager.application.group.GroupRuntimeStatus;
import com.ssh.tunnelmanager.application.host.HostListViewModel;
import com.ssh.tunnelmanager.application.organization.ClassificationInUseException;
import com.ssh.tunnelmanager.application.persistence.model.EnvironmentRecord;
import com.ssh.tunnelmanager.application.persistence.model.ProjectRecord;
import com.ssh.tunnelmanager.application.persistence.model.SshHostRecord;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelGroupRecord;
import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import com.ssh.tunnelmanager.application.portability.ConflictAction;
import com.ssh.tunnelmanager.application.portability.ConflictKey;
import com.ssh.tunnelmanager.application.portability.ImportConflict;
import com.ssh.tunnelmanager.application.portability.ImportSession;
import com.ssh.tunnelmanager.application.runtime.TunnelRuntime;
import com.ssh.tunnelmanager.application.settings.ShortcutAction;
import com.ssh.tunnelmanager.application.shell.NavigationItem;
import com.ssh.tunnelmanager.application.shell.ShellViewModel;
import com.ssh.tunnelmanager.application.tunnel.TunnelEditorViewModel;
import com.ssh.tunnelmanager.application.tunnel.TunnelListViewModel;
import com.ssh.tunnelmanager.application.workspace.HubViewModel;
import com.ssh.tunnelmanager.application.workspace.SearchResult;
import com.ssh.tunnelmanager.application.workspace.WorkspaceSearchViewModel;
import com.ssh.tunnelmanager.domain.credential.Credential;
import com.ssh.tunnelmanager.domain.host.SshHost;
import com.ssh.tunnelmanager.ui.i18n.UiMessages;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
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
import javafx.scene.control.cell.CheckBoxTableCell;
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
  private GroupListViewModel groups;
  private GroupEditorViewModel groupEditor;
  private WorkspaceSearchViewModel search;
  private HubViewModel hub;
  private TableView<TunnelRecord> tunnelTable;
  private javafx.collections.ObservableList<TunnelRecord> tunnelRows;
  private FilteredList<TunnelRecord> filteredTunnelRows;
  private TextField tunnelSearch;
  private ComboBox<ProjectRecord> tunnelProjectFilter;
  private ComboBox<EnvironmentRecord> tunnelEnvironmentFilter;
  private ComboBox<TunnelGroupRecord> tunnelGroupFilter;
  private ComboBox<com.ssh.tunnelmanager.domain.tunnel.ActualState> tunnelStateFilter;
  private ListView<TagRecord> tunnelTagFilter;
  private java.util.Map<UUID, Set<UUID>> tunnelGroupMembers = java.util.Map.of();
  private TableView<GroupRow> groupTable;
  private Timeline statusRefresh;
  private boolean editingTunnel;
  private boolean editingGroup;
  private Consumer<com.ssh.tunnelmanager.ui.theme.ThemePreference> themeApplier = ignored -> {};

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
              components.saveTunnel(),
              components.projectRepository(),
              components.environmentRepository(),
              components.tagRepository());
      groups =
          new GroupListViewModel(
              components.groupRepository(),
              components.groupStatus(),
              components.saveGroup(),
              components.deleteGroup(),
              components.duplicateGroup(),
              components.startGroup(),
              components.stopGroup());
      groupEditor =
          new GroupEditorViewModel(
              components.tunnelRepository(),
              components.saveGroup(),
              components.projectRepository(),
              components.environmentRepository(),
              components.tagRepository());
      search =
          new WorkspaceSearchViewModel(
              components.tunnelRepository(),
              components.groupRepository(),
              components.hostRepository());
      hub =
          new HubViewModel(
              components.tunnelRepository(),
              components.groupRepository(),
              components.recentItemRepository(),
              components.runtimeManager());
    }
  }

  public void setThemeApplier(
      Consumer<com.ssh.tunnelmanager.ui.theme.ThemePreference> themeApplier) {
    this.themeApplier = Objects.requireNonNull(themeApplier);
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
                setText(empty || item == null ? null : UiMessages.get("navigation." + item.id()));
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
    navigationList
        .sceneProperty()
        .addListener(
            (observable, oldScene, scene) -> {
              if (scene != null) {
                scene.addEventFilter(
                    javafx.scene.input.KeyEvent.KEY_PRESSED,
                    event -> {
                      if (components == null) return;
                      if (components.shortcuts().matches(ShortcutAction.GLOBAL_SEARCH, event)) {
                        event.consume();
                        showGlobalSearch();
                      } else if (components.shortcuts().matches(ShortcutAction.NEW_TUNNEL, event)) {
                        event.consume();
                        selectSection("tunnels");
                        Platform.runLater(() -> openTunnelEditor(tunnelTable, null));
                      } else if (components
                          .shortcuts()
                          .matches(ShortcutAction.FILTER_VIEW, event)) {
                        event.consume();
                        if (tunnelSearch != null) tunnelSearch.requestFocus();
                      } else if (components
                          .shortcuts()
                          .matches(ShortcutAction.REFRESH_VIEW, event)) {
                        event.consume();
                        refreshCurrentSection();
                      } else if (components
                          .shortcuts()
                          .matches(ShortcutAction.OPEN_SELECTION, event)) {
                        event.consume();
                        if (tunnelTable != null)
                          openTunnelEditor(
                              tunnelTable, tunnelTable.getSelectionModel().getSelectedItem());
                      } else if (components
                          .shortcuts()
                          .matches(ShortcutAction.CLOSE_OVERLAY, event)) {
                        event.consume();
                        if (editingTunnel) showTunnels();
                        else if (editingGroup) showGroups();
                      }
                    });
              }
            });
  }

  private void selectSection(String id) {
    navigationList.getItems().stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .ifPresent(item -> navigationList.getSelectionModel().select(item));
  }

  private void showSection(NavigationItem item) {
    stopStatusRefresh();
    editingTunnel = false;
    editingGroup = false;
    sectionTitle.setText(UiMessages.get("navigation." + item.id()));
    contentContainer
        .getChildren()
        .removeIf(node -> node != sectionTitle && node != sectionDescription);
    switch (item.id()) {
      case "hosts" -> showHosts();
      case "tunnels" -> showTunnels();
      case "groups" -> showGroups();
      case "home" -> showHome();
      case "organization" -> showOrganization();
      case "settings" -> showSettings();
      default -> sectionDescription.setText(UiMessages.get("section.unavailable"));
    }
  }

  private void showHome() {
    sectionDescription.setText(UiMessages.get("home.description.active"));
    if (hub == null) return;
    Label summary = new Label("Cargando resumen…");
    Label emptyFlow = new Label();
    ListView<String> favorites = new ListView<>();
    ListView<String> recent = new ListView<>();
    ListView<String> active = new ListView<>();
    Button stopAll = new Button("Detener todos");
    stopAll.setOnAction(event -> stopAllTunnels());
    Button open = new Button("Abiertos");
    Button reconnecting = new Button("Reconectando");
    Button errors = new Button("Errores");
    open.setOnAction(
        event -> navigateToTunnelState(com.ssh.tunnelmanager.domain.tunnel.ActualState.OPEN));
    reconnecting.setOnAction(
        event ->
            navigateToTunnelState(com.ssh.tunnelmanager.domain.tunnel.ActualState.RECONNECTING));
    errors.setOnAction(
        event -> navigateToTunnelState(com.ssh.tunnelmanager.domain.tunnel.ActualState.ERROR));
    VBox content =
        new VBox(
            10,
            summary,
            emptyFlow,
            new HBox(8, open, reconnecting, errors),
            new Label("Favoritos"),
            favorites,
            new Label("Recientes"),
            recent,
            new Label("Túneles activos"),
            active,
            stopAll);
    VBox.setVgrow(favorites, Priority.ALWAYS);
    VBox.setVgrow(recent, Priority.ALWAYS);
    VBox.setVgrow(active, Priority.ALWAYS);
    contentContainer.getChildren().add(content);
    workers.submit(
        () -> {
          search.refresh();
          hub.refresh();
          HubViewModel.HubSnapshot snapshot = hub.snapshot();
          Platform.runLater(
              () -> {
                summary.setText(
                    "Configurados: "
                        + snapshot.configuredTunnels()
                        + " · Abiertos: "
                        + snapshot.openTunnels()
                        + " · Reconectando: "
                        + snapshot.reconnectingTunnels()
                        + " · Errores: "
                        + snapshot.errorTunnels());
                emptyFlow.setText(
                    snapshot.configuredTunnels() == 0
                        ? "Primera ejecución: crea un Host SSH, después un túnel y, si lo necesitas, un grupo."
                        : "");
                favorites
                    .getItems()
                    .setAll(snapshot.favorites().stream().map(item -> item.name()).toList());
                recent
                    .getItems()
                    .setAll(snapshot.recent().stream().map(item -> item.name()).toList());
                active
                    .getItems()
                    .setAll(snapshot.active().stream().map(item -> item.name()).toList());
              });
        });
    favorites.setOnMouseClicked(
        event -> {
          if (event.getClickCount() != 2) return;
          int index = favorites.getSelectionModel().getSelectedIndex();
          if (index < 0 || index >= hub.snapshot().favorites().size()) return;
          HubViewModel.HubItem item = hub.snapshot().favorites().get(index);
          workers.submit(
              () -> {
                if (item.kind() == SearchResult.Kind.TUNNEL)
                  components.startTunnel().execute(item.id());
                if (item.kind() == SearchResult.Kind.GROUP)
                  components.startGroup().execute(item.id());
              });
        });
  }

  private void stopAllTunnels() {
    Alert confirmation =
        new Alert(Alert.AlertType.CONFIRMATION, "Se detendrán todos los túneles activos.");
    confirmation.setHeaderText("Detener todos");
    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    workers.submit(() -> components.runtimeManager().stopAll());
  }

  private void showGlobalSearch() {
    if (search == null) return;
    TextInputDialog dialog = new TextInputDialog();
    dialog.setTitle("Buscar en el espacio de trabajo");
    dialog.setHeaderText("Ctrl+K · túneles, grupos y hosts SSH");
    dialog.setContentText("Texto:");
    dialog
        .showAndWait()
        .filter(value -> !value.isBlank())
        .ifPresent(
            value -> {
              workers.submit(
                  () -> {
                    search.refresh();
                    List<SearchResult> results = search.search(value);
                    Platform.runLater(() -> showSearchResults(value, results));
                  });
            });
  }

  private void showSearchResults(String query, List<SearchResult> results) {
    if (results.isEmpty()) {
      showInfo("Resultados de búsqueda", "No se encontraron túneles, grupos ni hosts.");
      return;
    }
    javafx.scene.control.ChoiceDialog<SearchResult> dialog =
        new javafx.scene.control.ChoiceDialog<>(results.getFirst(), results);
    dialog.setTitle("Resultados de búsqueda");
    dialog.setHeaderText("Resultados para: " + query + " · elige uno para navegar");
    dialog.setContentText("Resultado:");
    dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
    dialog
        .showAndWait()
        .ifPresent(
            result ->
                navigationList.getItems().stream()
                    .filter(item -> item.id().equals(result.kind().name().toLowerCase()))
                    .findFirst()
                    .ifPresent(item -> navigationList.getSelectionModel().select(item)));
  }

  private void navigateToTunnelState(com.ssh.tunnelmanager.domain.tunnel.ActualState state) {
    navigationList.getItems().stream()
        .filter(item -> item.id().equals("tunnels"))
        .findFirst()
        .ifPresent(
            item -> {
              navigationList.getSelectionModel().select(item);
              Platform.runLater(
                  () -> {
                    if (tunnelStateFilter != null) tunnelStateFilter.setValue(state);
                  });
            });
  }

  private void showOrganization() {
    sectionDescription.setText(
        "Gestiona proyectos, entornos y etiquetas para organizar túneles y grupos.");
    if (components == null) return;
    ListView<ProjectRecord> projects = new ListView<>();
    ListView<EnvironmentRecord> environments = new ListView<>();
    ListView<TagRecord> tags = new ListView<>();
    contentContainer
        .getChildren()
        .add(
            new TabPane(
                new Tab(
                    "Proyectos",
                    classificationPanel(
                        projects,
                        components.projectRepository()::findAll,
                        ProjectRecord::name,
                        ProjectRecord::id,
                        (id, name) ->
                            components
                                .saveProject()
                                .execute(
                                    new com.ssh.tunnelmanager.domain.organization.Project(
                                        id, name)),
                        (id, confirmed) -> components.deleteProject().execute(id, confirmed))),
                new Tab(
                    "Entornos",
                    classificationPanel(
                        environments,
                        components.environmentRepository()::findAll,
                        EnvironmentRecord::name,
                        EnvironmentRecord::id,
                        (id, name) ->
                            components
                                .saveEnvironment()
                                .execute(
                                    new com.ssh.tunnelmanager.domain.organization.Environment(
                                        id, name)),
                        (id, confirmed) -> components.deleteEnvironment().execute(id, confirmed))),
                new Tab(
                    "Etiquetas",
                    classificationPanel(
                        tags,
                        components.tagRepository()::findAll,
                        TagRecord::name,
                        TagRecord::id,
                        (id, name) ->
                            components
                                .saveTag()
                                .execute(
                                    new com.ssh.tunnelmanager.domain.organization.Tag(id, name)),
                        (id, confirmed) -> components.deleteTag().execute(id, confirmed)))));
    contentContainer.getChildren().stream()
        .filter(TabPane.class::isInstance)
        .map(TabPane.class::cast)
        .findFirst()
        .ifPresent(pane -> pane.getTabs().forEach(tab -> tab.setClosable(false)));
  }

  private void showSettings() {
    sectionDescription.setText(
        "Ajustes de apariencia, notificaciones, teclado, diagnóstico y configuración.");
    if (components == null) return;
    Button export = new Button("Exportar configuración JSON");
    Button importButton = new Button("Importar configuración JSON");
    Button backups = new Button("Ver backups y restaurar");
    Button diagnostic = new Button("Exportar diagnóstico ZIP");
    Button logs = new Button("Abrir carpeta de logs");
    ComboBox<String> theme = new ComboBox<>();
    theme.getItems().setAll("LIGHT", "DARK", "SYSTEM");
    theme.setValue(components.settings().theme());
    theme.setOnAction(
        event -> {
          components.settings().setTheme(theme.getValue());
          themeApplier.accept(
              com.ssh.tunnelmanager.ui.theme.ThemePreference.valueOf(theme.getValue()));
        });
    CheckBox notifications = new CheckBox("Notificaciones Windows");
    notifications.setSelected(components.settings().notificationsEnabled());
    notifications.setOnAction(
        event -> components.settings().setNotificationsEnabled(notifications.isSelected()));
    ComboBox<String> logging = new ComboBox<>();
    logging.getItems().setAll("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    logging.setValue(components.settings().loggingLevel());
    logging.setOnAction(
        event -> {
          components.settings().setLoggingLevel(logging.getValue());
          components.loggingLevel().apply(logging.getValue());
        });
    GridPane shortcuts = new GridPane();
    shortcuts.setHgap(8);
    shortcuts.setVgap(6);
    Map<ShortcutAction, TextField> shortcutFields = new java.util.EnumMap<>(ShortcutAction.class);
    int row = 0;
    for (ShortcutAction action : ShortcutAction.values()) {
      TextField field = new TextField(components.shortcuts().load().get(action));
      shortcutFields.put(action, field);
      shortcuts.add(new Label(action.label()), 0, row);
      shortcuts.add(field, 1, row++);
    }
    Button saveShortcuts = new Button("Guardar atajos");
    saveShortcuts.setOnAction(
        event -> {
          try {
            Map<ShortcutAction, String> values = new java.util.EnumMap<>(ShortcutAction.class);
            shortcutFields.forEach((action, field) -> values.put(action, field.getText()));
            components.settings().setShortcuts(values);
            showInfo("Atajos guardados", "Los cambios se aplicarán a la navegación.");
          } catch (RuntimeException exception) {
            showError("No se pudieron guardar los atajos", exception);
          }
        });
    export.setOnAction(event -> exportConfiguration());
    importButton.setOnAction(event -> importConfiguration());
    backups.setOnAction(event -> restoreBackup());
    diagnostic.setOnAction(event -> exportDiagnostic());
    logs.setOnAction(event -> openLogsFolder());
    contentContainer
        .getChildren()
        .add(
            new VBox(
                12,
                new Label("Tema"),
                theme,
                notifications,
                new Label("Nivel de logging"),
                logging,
                new Label("Atajos de teclado"),
                shortcuts,
                saveShortcuts,
                new Label(
                    "La exportación completa contiene contraseñas recuperables. "
                        + "Guárdala solo en un lugar seguro."),
                export,
                importButton,
                backups,
                diagnostic,
                logs));
  }

  private void exportDiagnostic() {
    javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
    chooser.setTitle("Exportar diagnóstico");
    chooser.setInitialFileName("ssh-tunnel-manager-diagnostic.zip");
    chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("ZIP", "*.zip"));
    java.io.File selected = chooser.showSaveDialog(contentContainer.getScene().getWindow());
    if (selected == null) return;
    workers.submit(
        () -> {
          try {
            components.diagnostics().export(selected.toPath());
            Platform.runLater(() -> showInfo("Diagnóstico exportado", selected.getPath()));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo exportar el diagnóstico", exception));
          }
        });
  }

  private void openLogsFolder() {
    workers.submit(
        () -> {
          try {
            java.awt.Desktop.getDesktop().open(components.diagnostics().logsDirectory().toFile());
          } catch (java.io.IOException | UnsupportedOperationException exception) {
            Platform.runLater(() -> showError("No se pudo abrir la carpeta de logs", exception));
          }
        });
  }

  private void exportConfiguration() {
    javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
    chooser.setTitle("Exportar configuración");
    chooser.setInitialFileName("ssh-tunnel-manager-export.json");
    chooser
        .getExtensionFilters()
        .add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
    java.io.File selected = chooser.showSaveDialog(contentContainer.getScene().getWindow());
    if (selected == null) return;
    Alert warning =
        new Alert(
            Alert.AlertType.WARNING,
            "El archivo contendrá contraseñas SSH recuperables. ¿Quieres continuar?");
    warning.setHeaderText("Exportación completa");
    ButtonType continueButton = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
    warning.getButtonTypes().setAll(continueButton, ButtonType.CANCEL);
    if (warning.showAndWait().orElse(ButtonType.CANCEL) != continueButton) return;
    workers.submit(
        () -> {
          try {
            components.exportConfiguration().execute(selected.toPath());
            Platform.runLater(() -> showInfo("Exportación completada", selected.getPath()));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo exportar", exception));
          }
        });
  }

  private void importConfiguration() {
    javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
    chooser.setTitle("Importar configuración");
    chooser
        .getExtensionFilters()
        .add(new javafx.stage.FileChooser.ExtensionFilter("JSON", "*.json"));
    java.io.File selected = chooser.showOpenDialog(contentContainer.getScene().getWindow());
    if (selected == null) return;
    workers.submit(
        () -> {
          try {
            ImportSession session = components.importConfiguration().analyze(selected.toPath());
            Platform.runLater(() -> showImportPreview(session));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo analizar la importación", exception));
          }
        });
  }

  private void showImportPreview(ImportSession session) {
    var preview = session.preview();
    if (preview.conflictCount() == 0) {
      Alert confirmation =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Nuevos: "
                  + preview.newEntities()
                  + " · Sin cambios: "
                  + preview.unchangedEntities()
                  + ". ¿Continuar?");
      confirmation.setHeaderText("Vista previa de importación");
      if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
      commitImport(session, Map.of(), Map.of());
      return;
    }
    javafx.scene.control.Dialog<Map<ConflictKey, ConflictAction>> dialog =
        new javafx.scene.control.Dialog<>();
    dialog.setTitle("Resolver conflictos de importación");
    dialog.setHeaderText(
        "Nuevos: "
            + preview.newEntities()
            + " · Sin cambios: "
            + preview.unchangedEntities()
            + " · Conflictos: "
            + preview.conflictCount());
    ButtonType continueButton = new ButtonType("Continuar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().setAll(continueButton, ButtonType.CANCEL);
    ComboBox<ConflictAction> applyAll = new ComboBox<>();
    applyAll
        .getItems()
        .addAll(
            preview.conflicts().stream().allMatch(conflict -> supportsCopy(conflict.key().type()))
                ? List.of(ConflictAction.values())
                : List.of(ConflictAction.KEEP_CURRENT, ConflictAction.REPLACE));
    applyAll.setPromptText("Aplicar a todos (opcional)");
    VBox rows = new VBox(8, new Label("Decisión global opcional"), applyAll);
    Map<ConflictKey, ComboBox<ConflictAction>> choices = new java.util.LinkedHashMap<>();
    for (ImportConflict conflict : preview.conflicts()) {
      ComboBox<ConflictAction> choice = new ComboBox<>();
      choice.getItems().addAll(actionsFor(conflict.key().type()));
      choice.setValue(ConflictAction.KEEP_CURRENT);
      choices.put(conflict.key(), choice);
      rows.getChildren()
          .add(
              new HBox(
                  8,
                  new Label(
                      conflict.key().type()
                          + " · "
                          + conflict.name()
                          + "\nActual: "
                          + conflict.currentSummary()
                          + "\nImportado: "
                          + conflict.importedSummary()),
                  choice));
    }
    javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(rows);
    scroll.setFitToWidth(true);
    scroll.setPrefViewportHeight(450);
    dialog.getDialogPane().setContent(scroll);
    dialog.setResultConverter(
        button -> {
          if (button != continueButton) return null;
          Map<ConflictKey, ConflictAction> decisions = new java.util.LinkedHashMap<>();
          choices.forEach((key, choice) -> decisions.put(key, choice.getValue()));
          return decisions;
        });
    dialog
        .showAndWait()
        .ifPresent(
            decisions -> {
              Map<com.ssh.tunnelmanager.application.portability.ConflictType, ConflictAction> all =
                  new java.util.EnumMap<>(
                      com.ssh.tunnelmanager.application.portability.ConflictType.class);
              if (applyAll.getValue() != null) {
                preview.conflicts().stream()
                    .map(conflict -> conflict.key().type())
                    .distinct()
                    .forEach(type -> all.put(type, applyAll.getValue()));
              }
              commitImport(session, decisions, all);
            });
  }

  private static List<ConflictAction> actionsFor(
      com.ssh.tunnelmanager.application.portability.ConflictType type) {
    return supportsCopy(type)
        ? List.of(ConflictAction.values())
        : List.of(ConflictAction.KEEP_CURRENT, ConflictAction.REPLACE);
  }

  private static boolean supportsCopy(
      com.ssh.tunnelmanager.application.portability.ConflictType type) {
    return type != com.ssh.tunnelmanager.application.portability.ConflictType.TRUSTED_HOST_KEY
        && type != com.ssh.tunnelmanager.application.portability.ConflictType.SETTING
        && type != com.ssh.tunnelmanager.application.portability.ConflictType.KEYBOARD_SHORTCUT;
  }

  private void commitImport(
      ImportSession session,
      Map<ConflictKey, ConflictAction> decisions,
      Map<com.ssh.tunnelmanager.application.portability.ConflictType, ConflictAction> applyToAll) {
    boolean active =
        components.runtimeManager().runtimes().values().stream()
            .anyMatch(
                runtime ->
                    runtime.actualState()
                        != com.ssh.tunnelmanager.domain.tunnel.ActualState.CLOSED);
    boolean stopActive = false;
    if (active) {
      Alert confirmation =
          new Alert(
              Alert.AlertType.CONFIRMATION,
              "Hay túneles activos. Puedes cancelar o detener todos y continuar.");
      confirmation.setHeaderText("Importación con túneles activos");
      ButtonType stop = new ButtonType("Detener todos y continuar", ButtonBar.ButtonData.OK_DONE);
      confirmation.getButtonTypes().setAll(stop, ButtonType.CANCEL);
      if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != stop) return;
      stopActive = true;
    }
    boolean finalStopActive = stopActive;
    workers.submit(
        () -> {
          try {
            components
                .importConfiguration()
                .commit(session, decisions, applyToAll, finalStopActive);
            Platform.runLater(
                () -> showInfo("Importación completada", "La configuración fue importada."));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo importar", exception));
          }
        });
  }

  private void restoreBackup() {
    workers.submit(
        () -> {
          try {
            List<java.nio.file.Path> values = components.backups().listBackups();
            Platform.runLater(() -> chooseBackup(values));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudieron listar los backups", exception));
          }
        });
  }

  private void chooseBackup(List<java.nio.file.Path> values) {
    if (values.isEmpty()) {
      showInfo("Backups", "No hay backups SQLite utilizables.");
      return;
    }
    javafx.scene.control.ChoiceDialog<java.nio.file.Path> dialog =
        new javafx.scene.control.ChoiceDialog<>(values.getFirst(), values);
    dialog.setTitle("Restaurar backup");
    dialog.setHeaderText("Selecciona un snapshot SQLite válido");
    dialog.setContentText("Backup:");
    dialog
        .showAndWait()
        .ifPresent(
            selected -> {
              boolean active =
                  components.runtimeManager().runtimes().values().stream()
                      .anyMatch(
                          runtime ->
                              runtime.actualState()
                                  != com.ssh.tunnelmanager.domain.tunnel.ActualState.CLOSED);
              boolean stopActive = false;
              if (active) {
                Alert activeWarning =
                    new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Hay túneles activos. Puedes cancelar o detener todos y continuar.");
                activeWarning.setHeaderText("Restauración con túneles activos");
                ButtonType stop =
                    new ButtonType("Detener todos y continuar", ButtonBar.ButtonData.OK_DONE);
                activeWarning.getButtonTypes().setAll(stop, ButtonType.CANCEL);
                if (activeWarning.showAndWait().orElse(ButtonType.CANCEL) != stop) return;
                stopActive = true;
              }
              boolean finalStopActive = stopActive;
              Alert warning =
                  new Alert(
                      Alert.AlertType.WARNING,
                      "Se creará un backup de seguridad antes de restaurar. ¿Continuar?");
              warning.setHeaderText("Restaurar configuración");
              if (warning.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
              workers.submit(
                  () -> {
                    try {
                      components.restoreBackup().execute(selected, finalStopActive);
                      Platform.runLater(
                          () ->
                              showInfo(
                                  "Restauración completada", "Los túneles permanecen cerrados."));
                    } catch (RuntimeException exception) {
                      Platform.runLater(() -> showError("No se pudo restaurar", exception));
                    }
                  });
            });
  }

  private <T> VBox classificationPanel(
      ListView<T> list,
      Supplier<List<T>> loader,
      Function<T, String> display,
      Function<T, UUID> id,
      java.util.function.BiConsumer<UUID, String> save,
      java.util.function.BiConsumer<UUID, Boolean> delete) {
    list.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : display.apply(item));
              }
            });
    Button add = new Button("Nuevo");
    Button edit = new Button("Editar");
    Button remove = new Button("Eliminar");
    Runnable refresh =
        () ->
            workers.submit(
                () -> {
                  List<T> values = loader.get();
                  Platform.runLater(() -> list.getItems().setAll(values));
                });
    add.setOnAction(event -> editClassification(null, display, id, save, refresh));
    edit.setOnAction(
        event ->
            editClassification(
                list.getSelectionModel().getSelectedItem(), display, id, save, refresh));
    remove.setOnAction(
        event -> {
          T selected = list.getSelectionModel().getSelectedItem();
          if (selected == null) return;
          deleteClassification(selected, id, delete, refresh);
        });
    refresh.run();
    VBox panel = new VBox(8, list, new HBox(8, add, edit, remove));
    VBox.setVgrow(list, Priority.ALWAYS);
    return panel;
  }

  private <T> void editClassification(
      T selected,
      Function<T, String> display,
      Function<T, UUID> id,
      java.util.function.BiConsumer<UUID, String> save,
      Runnable refresh) {
    TextInputDialog dialog = new TextInputDialog(selected == null ? "" : display.apply(selected));
    dialog.setTitle(selected == null ? "Nueva clasificación" : "Editar clasificación");
    dialog.setHeaderText("Nombre");
    dialog
        .showAndWait()
        .filter(value -> !value.isBlank())
        .ifPresent(
            value ->
                workers.submit(
                    () -> {
                      try {
                        save.accept(
                            selected == null ? UUID.randomUUID() : id.apply(selected), value);
                        Platform.runLater(refresh);
                      } catch (RuntimeException exception) {
                        Platform.runLater(() -> showError("No se pudo guardar", exception));
                      }
                    }));
  }

  private <T> void deleteClassification(
      T selected,
      Function<T, UUID> id,
      java.util.function.BiConsumer<UUID, Boolean> delete,
      Runnable refresh) {
    workers.submit(
        () -> {
          try {
            delete.accept(id.apply(selected), false);
            Platform.runLater(refresh);
          } catch (ClassificationInUseException exception) {
            Platform.runLater(
                () -> {
                  Alert confirmation =
                      new Alert(
                          Alert.AlertType.CONFIRMATION,
                          "Está en uso por "
                              + exception.usage().tunnelCount()
                              + " túneles y "
                              + exception.usage().groupCount()
                              + " grupos. Las referencias quedarán sin clasificación.");
                  confirmation.setHeaderText("Confirmar eliminación");
                  if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK)
                    workers.submit(
                        () -> {
                          delete.accept(exception.id(), true);
                          Platform.runLater(refresh);
                        });
                });
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo eliminar", exception));
          }
        });
  }

  private void showGroups() {
    stopStatusRefresh();
    editingGroup = false;
    sectionDescription.setText(
        "Agrupa túneles reutilizables y opera sus conexiones sin ownership exclusivo.");
    if (groups == null) return;
    groupTable = new TableView<>();
    TableColumn<GroupRow, String> name = new TableColumn<>("Nombre");
    name.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().group().name()));
    TableColumn<GroupRow, String> members = new TableColumn<>("Túneles");
    members.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                Integer.toString(data.getValue().group().tunnelIds().size())));
    TableColumn<GroupRow, String> state = new TableColumn<>("Estado");
    state.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                groupStateText(data.getValue().status())));
    groupTable.getColumns().addAll(name, members, state);
    groupTable.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2
              && groupTable.getSelectionModel().getSelectedItem() != null)
            openGroupEditor(groupTable.getSelectionModel().getSelectedItem().group());
        });
    Button add = new Button("Nuevo grupo");
    Button edit = new Button("Editar");
    Button duplicate = new Button("Duplicar");
    Button remove = new Button("Eliminar");
    Button start = new Button("Iniciar grupo");
    Button stop = new Button("Detener grupo");
    add.setOnAction(event -> openGroupEditor(null));
    edit.setOnAction(event -> editSelectedGroup());
    duplicate.setOnAction(event -> duplicateSelectedGroup());
    remove.setOnAction(event -> deleteSelectedGroup());
    start.setOnAction(event -> operateSelectedGroup(groups::start));
    stop.setOnAction(event -> operateSelectedGroup(groups::stop));
    contentContainer
        .getChildren()
        .addAll(groupTable, new HBox(8, add, edit, duplicate, remove, start, stop));
    refreshGroups(groupTable);
    statusRefresh =
        new Timeline(
            new KeyFrame(javafx.util.Duration.seconds(1), event -> refreshGroups(groupTable)));
    statusRefresh.setCycleCount(Timeline.INDEFINITE);
    statusRefresh.play();
  }

  private void refreshGroups(TableView<GroupRow> table) {
    workers.submit(
        () -> {
          List<GroupRow> values =
              groups.groups().stream()
                  .map(group -> new GroupRow(group, groups.status(group.id())))
                  .toList();
          Platform.runLater(
              () -> {
                if (table == groupTable) table.setItems(FXCollections.observableArrayList(values));
              });
        });
  }

  private void editSelectedGroup() {
    GroupRow selected =
        groupTable == null ? null : groupTable.getSelectionModel().getSelectedItem();
    if (selected != null) openGroupEditor(selected.group());
  }

  private void duplicateSelectedGroup() {
    GroupRow selected =
        groupTable == null ? null : groupTable.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    TextInputDialog dialog = new TextInputDialog(selected.group().name() + " (copia)");
    dialog.setTitle("Duplicar grupo");
    dialog.setHeaderText("Nombre de la copia");
    dialog
        .showAndWait()
        .ifPresent(name -> workers.submit(() -> groups.duplicate(selected.group().id(), name)));
  }

  private void deleteSelectedGroup() {
    GroupRow selected =
        groupTable == null ? null : groupTable.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    Alert confirmation =
        new Alert(
            Alert.AlertType.CONFIRMATION, "Se eliminará el grupo; sus túneles no se borrarán.");
    confirmation.setHeaderText("Eliminar grupo");
    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    workers.submit(() -> groups.delete(selected.group().id()));
  }

  private void operateSelectedGroup(java.util.function.Function<UUID, Object> operation) {
    GroupRow selected =
        groupTable == null ? null : groupTable.getSelectionModel().getSelectedItem();
    if (selected != null) workers.submit(() -> operation.apply(selected.group().id()));
  }

  private void openGroupEditor(TunnelGroupRecord existing) {
    if (groupEditor == null) return;
    workers.submit(
        () -> {
          List<TunnelRecord> available = groupEditor.availableTunnels();
          List<ProjectRecord> projectRecords = groupEditor.projects();
          List<EnvironmentRecord> environmentRecords = groupEditor.environments();
          List<TagRecord> tagRecords = groupEditor.tags();
          Platform.runLater(
              () ->
                  showGroupEditor(
                      existing, available, projectRecords, environmentRecords, tagRecords));
        });
  }

  private void showGroupEditor(
      TunnelGroupRecord existing,
      List<TunnelRecord> available,
      List<ProjectRecord> projectRecords,
      List<EnvironmentRecord> environmentRecords,
      List<TagRecord> tagRecords) {
    editingGroup = true;
    stopStatusRefresh();
    contentContainer
        .getChildren()
        .removeIf(node -> node != sectionTitle && node != sectionDescription);
    sectionDescription.setText(
        "Editor de grupo: busca túneles, revisa conflictos y guarda la selección.");
    GroupEditorViewModel.Form initial =
        existing == null
            ? new GroupEditorViewModel.Form(null, "", Set.of())
            : groupEditor.fromRecord(existing);
    TextField name = new TextField(initial.name());
    ComboBox<ProjectRecord> project =
        classificationCombo(
            projectRecords, initial.projectId(), ProjectRecord::id, ProjectRecord::name);
    ComboBox<EnvironmentRecord> environment =
        classificationCombo(
            environmentRecords,
            initial.environmentId(),
            EnvironmentRecord::id,
            EnvironmentRecord::name);
    ListView<TagRecord> tagSelection =
        new ListView<>(FXCollections.observableArrayList(tagRecords));
    tagSelection.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    tagRecords.stream()
        .filter(tag -> initial.tagIds().contains(tag.id()))
        .forEach(tag -> tagSelection.getSelectionModel().select(tag));
    tagSelection.setPrefHeight(90);
    tagSelection.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(TagRecord item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
              }
            });
    CheckBox favorite = new CheckBox("Favorito");
    favorite.setSelected(initial.favorite());
    TextField search = new TextField();
    search.setPromptText("Buscar túneles por nombre o endpoint");
    TableView<GroupChoice> choices = new TableView<>();
    List<GroupChoice> rows =
        available.stream()
            .map(tunnel -> new GroupChoice(tunnel, initial.tunnelIds().contains(tunnel.id())))
            .toList();
    FilteredList<GroupChoice> filtered =
        new FilteredList<>(FXCollections.observableArrayList(rows), ignored -> true);
    search
        .textProperty()
        .addListener(
            (observable, old, value) ->
                filtered.setPredicate(
                    row -> {
                      String query = value == null ? "" : value.trim().toLowerCase();
                      return query.isEmpty()
                          || row.tunnel().name().toLowerCase().contains(query)
                          || row.endpoint().toLowerCase().contains(query);
                    }));
    TableColumn<GroupChoice, Boolean> selected = new TableColumn<>("Incluir");
    selected.setCellValueFactory(data -> data.getValue().selected());
    selected.setCellFactory(CheckBoxTableCell.forTableColumn(selected));
    TableColumn<GroupChoice, String> tunnelName = new TableColumn<>("Túnel");
    tunnelName.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().tunnel().name()));
    TableColumn<GroupChoice, String> endpoint = new TableColumn<>("Endpoint local");
    endpoint.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().endpoint()));
    choices.getColumns().addAll(selected, tunnelName, endpoint);
    choices.setItems(filtered);
    Button save = new Button("Guardar");
    Button cancel = new Button("Cancelar");
    save.setOnAction(
        event ->
            saveGroupEditor(existing, name, rows, project, environment, tagSelection, favorite));
    cancel.setOnAction(event -> showGroups());
    VBox editor =
        new VBox(
            10,
            formGrid(
                "Nombre",
                name,
                "Buscar túneles",
                search,
                "Proyecto",
                project,
                "Entorno",
                environment,
                "Etiquetas",
                tagSelection,
                "",
                favorite),
            choices,
            new HBox(8, save, cancel));
    VBox.setVgrow(choices, Priority.ALWAYS);
    contentContainer.getChildren().add(editor);
  }

  private void saveGroupEditor(
      TunnelGroupRecord existing,
      TextField name,
      List<GroupChoice> rows,
      ComboBox<ProjectRecord> project,
      ComboBox<EnvironmentRecord> environment,
      ListView<TagRecord> tags,
      CheckBox favorite) {
    UUID id = existing == null ? UUID.randomUUID() : existing.id();
    GroupEditorViewModel.Form form =
        new GroupEditorViewModel.Form(
            id,
            name.getText(),
            rows.stream()
                .filter(row -> row.selected().get())
                .map(row -> row.tunnel().id())
                .collect(java.util.stream.Collectors.toSet()),
            project.getValue() == null ? null : project.getValue().id(),
            environment.getValue() == null ? null : environment.getValue().id(),
            favorite.isSelected(),
            tags.getSelectionModel().getSelectedItems().stream()
                .map(TagRecord::id)
                .collect(java.util.stream.Collectors.toSet()));
    workers.submit(
        () -> {
          try {
            groupEditor.save(form, false);
            Platform.runLater(this::showGroups);
          } catch (GroupConflictException conflict) {
            Platform.runLater(() -> confirmGroupConflict(form, conflict));
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo guardar el grupo", exception));
          }
        });
  }

  private void confirmGroupConflict(
      GroupEditorViewModel.Form form, GroupConflictException conflict) {
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setHeaderText("Conflicto de endpoints locales");
    alert.setContentText(
        conflict.getMessage() + "\nPodrán competir por el mismo puerto al iniciar.");
    if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
    workers.submit(
        () -> {
          try {
            groupEditor.save(form, true);
            Platform.runLater(this::showGroups);
          } catch (RuntimeException exception) {
            Platform.runLater(() -> showError("No se pudo guardar el grupo", exception));
          }
        });
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
    TableColumn<TunnelRecord, String> favorite = new TableColumn<>("★");
    favorite.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().favorite() ? "★" : ""));
    tunnelTable.getColumns().addAll(name, endpoint, state, active, favorite);
    tunnelTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
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
    Button bulkStart = new Button("Iniciar selección");
    Button bulkStop = new Button("Detener selección");
    Button favoriteOn = new Button("Favorito");
    Button favoriteOff = new Button("Quitar favorito");
    Button assignProject = new Button("Asignar proyecto/entorno");
    Button assignGroup = new Button("Añadir a grupo");
    Button removeGroup = new Button("Quitar de grupo");
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
    bulkStart.setOnAction(event -> bulkOperate(true));
    bulkStop.setOnAction(event -> bulkOperate(false));
    favoriteOn.setOnAction(event -> bulkFavorite(true));
    favoriteOff.setOnAction(event -> bulkFavorite(false));
    assignProject.setOnAction(event -> bulkAssignProject());
    assignGroup.setOnAction(event -> bulkAssignGroup());
    removeGroup.setOnAction(event -> bulkRemoveGroup());
    tunnelSearch = new TextField();
    tunnelSearch.setPromptText("Filtrar túneles por nombre o destino");
    tunnelProjectFilter = new ComboBox<>();
    tunnelProjectFilter.setPromptText("Proyecto");
    tunnelEnvironmentFilter = new ComboBox<>();
    tunnelEnvironmentFilter.setPromptText("Entorno");
    tunnelGroupFilter = new ComboBox<>();
    tunnelGroupFilter.setPromptText("Grupo");
    tunnelStateFilter =
        new ComboBox<>(
            FXCollections.observableArrayList(
                com.ssh.tunnelmanager.domain.tunnel.ActualState.values()));
    tunnelStateFilter.setPromptText("Estado");
    tunnelTagFilter = new ListView<>();
    tunnelTagFilter
        .getSelectionModel()
        .setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    tunnelTagFilter.setPrefHeight(70);
    tunnelTagFilter.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(TagRecord item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
              }
            });
    HBox filters =
        new HBox(
            8,
            tunnelSearch,
            tunnelProjectFilter,
            tunnelEnvironmentFilter,
            tunnelGroupFilter,
            tunnelStateFilter,
            tunnelTagFilter);
    tunnelSearch.textProperty().addListener((obs, old, value) -> applyTunnelFilters());
    tunnelProjectFilter.valueProperty().addListener((obs, old, value) -> applyTunnelFilters());
    tunnelEnvironmentFilter.valueProperty().addListener((obs, old, value) -> applyTunnelFilters());
    tunnelGroupFilter.valueProperty().addListener((obs, old, value) -> applyTunnelFilters());
    tunnelStateFilter.valueProperty().addListener((obs, old, value) -> applyTunnelFilters());
    tunnelTagFilter
        .getSelectionModel()
        .getSelectedItems()
        .addListener(
            (javafx.collections.ListChangeListener<TagRecord>) change -> applyTunnelFilters());
    HBox actions = new HBox(8, add, edit, duplicate, remove, test, start, stop, retry, details);
    HBox bulkActions =
        new HBox(
            8,
            bulkStart,
            bulkStop,
            favoriteOn,
            favoriteOff,
            assignProject,
            assignGroup,
            removeGroup);
    contentContainer.getChildren().addAll(filters, tunnelTable, actions, bulkActions);
    refreshTunnels(tunnelTable);
  }

  private void refreshCurrentSection() {
    if (editingTunnel) return;
    NavigationItem selected = navigationList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if ("hosts".equals(selected.id())) showHosts();
    if ("tunnels".equals(selected.id())) {
      if (tunnelTable != null) tunnelTable.refresh();
      else showTunnels();
    }
    if ("home".equals(selected.id()) && hub != null) showHome();
    if ("groups".equals(selected.id())) {
      if (groupTable != null) groupTable.refresh();
      else showGroups();
    }
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
                if (table != tunnelTable) return;
                if (tunnelRows == null) {
                  tunnelRows = FXCollections.observableArrayList();
                  filteredTunnelRows = new FilteredList<>(tunnelRows, ignored -> true);
                  javafx.collections.transformation.SortedList<TunnelRecord> sorted =
                      new javafx.collections.transformation.SortedList<>(filteredTunnelRows);
                  sorted.comparatorProperty().bind(table.comparatorProperty());
                  table.setItems(sorted);
                }
                tunnelRows.setAll(values);
                if (components != null) {
                  tunnelProjectFilter.getItems().setAll(components.projectRepository().findAll());
                  tunnelEnvironmentFilter
                      .getItems()
                      .setAll(components.environmentRepository().findAll());
                  List<TunnelGroupRecord> groups = components.groupRepository().findAll();
                  tunnelGroupFilter.getItems().setAll(groups);
                  tunnelGroupMembers =
                      groups.stream()
                          .collect(
                              java.util.stream.Collectors.toUnmodifiableMap(
                                  TunnelGroupRecord::id, TunnelGroupRecord::tunnelIds));
                  tunnelTagFilter.getItems().setAll(components.tagRepository().findAll());
                }
                applyTunnelFilters();
              });
        });
  }

  private void applyTunnelFilters() {
    if (filteredTunnelRows == null) return;
    String query = tunnelSearch == null ? "" : tunnelSearch.getText().trim().toLowerCase();
    UUID projectId =
        tunnelProjectFilter.getValue() == null ? null : tunnelProjectFilter.getValue().id();
    UUID environmentId =
        tunnelEnvironmentFilter.getValue() == null ? null : tunnelEnvironmentFilter.getValue().id();
    UUID groupId = tunnelGroupFilter.getValue() == null ? null : tunnelGroupFilter.getValue().id();
    com.ssh.tunnelmanager.domain.tunnel.ActualState state = tunnelStateFilter.getValue();
    Set<UUID> tagIds =
        tunnelTagFilter == null
            ? Set.of()
            : tunnelTagFilter.getSelectionModel().getSelectedItems().stream()
                .map(TagRecord::id)
                .collect(java.util.stream.Collectors.toSet());
    filteredTunnelRows.setPredicate(
        tunnel -> {
          if (!query.isEmpty()
              && !tunnel.name().toLowerCase().contains(query)
              && !tunnel.destinationHost().toLowerCase().contains(query)) return false;
          if (projectId != null && !projectId.equals(tunnel.projectId())) return false;
          if (environmentId != null && !environmentId.equals(tunnel.environmentId())) return false;
          if (!tunnel.tagIds().containsAll(tagIds)) return false;
          if (groupId != null
              && !tunnelGroupMembers.getOrDefault(groupId, Set.of()).contains(tunnel.id()))
            return false;
          return state == null || tunnels.runtime(tunnel.id()).actualState() == state;
        });
  }

  private List<TunnelRecord> selectedTunnels() {
    return tunnelTable == null
        ? List.of()
        : List.copyOf(tunnelTable.getSelectionModel().getSelectedItems());
  }

  private void bulkOperate(boolean start) {
    List<TunnelRecord> selected = selectedTunnels();
    workers.submit(
        () ->
            selected.forEach(
                tunnel -> {
                  if (start) components.startTunnel().execute(tunnel.id());
                  else components.stopTunnel().execute(tunnel.id());
                }));
  }

  private void bulkFavorite(boolean favorite) {
    Set<UUID> selected =
        selectedTunnels().stream()
            .map(TunnelRecord::id)
            .collect(java.util.stream.Collectors.toSet());
    if (selected.isEmpty()) return;
    workers.submit(
        () -> {
          components.bulkWorkspaceRepository().setTunnelFavorites(selected, favorite);
          Platform.runLater(this::showTunnels);
        });
  }

  private void bulkAssignProject() {
    List<ProjectRecord> projects = components.projectRepository().findAll();
    if (projects.isEmpty()) return;
    javafx.scene.control.ChoiceDialog<ProjectRecord> dialog =
        new javafx.scene.control.ChoiceDialog<>(projects.getFirst(), projects);
    dialog.setTitle("Asignar proyecto");
    dialog.setHeaderText("Selecciona un proyecto");
    dialog
        .showAndWait()
        .ifPresent(
            project -> {
              List<EnvironmentRecord> environments = components.environmentRepository().findAll();
              if (environments.isEmpty()) {
                bulkAssignClassification(project.id(), null);
                return;
              }
              javafx.scene.control.ChoiceDialog<EnvironmentRecord> environmentDialog =
                  new javafx.scene.control.ChoiceDialog<>(environments.getFirst(), environments);
              environmentDialog.setTitle("Asignar entorno");
              environmentDialog.setHeaderText("Selecciona un entorno");
              environmentDialog
                  .showAndWait()
                  .ifPresent(
                      environment -> bulkAssignClassification(project.id(), environment.id()));
            });
  }

  private void bulkAssignClassification(UUID projectId, UUID environmentId) {
    Set<UUID> selected =
        selectedTunnels().stream()
            .map(TunnelRecord::id)
            .collect(java.util.stream.Collectors.toSet());
    if (selected.isEmpty()) return;
    workers.submit(
        () -> {
          components
              .bulkWorkspaceRepository()
              .setTunnelClassification(selected, projectId, environmentId);
          Platform.runLater(this::showTunnels);
        });
  }

  private void bulkAssignGroup() {
    List<TunnelGroupRecord> groups = components.groupRepository().findAll();
    if (groups.isEmpty()) return;
    javafx.scene.control.ChoiceDialog<TunnelGroupRecord> dialog =
        new javafx.scene.control.ChoiceDialog<>(groups.getFirst(), groups);
    dialog.setTitle("Añadir a grupo");
    dialog.setHeaderText("Selecciona un grupo");
    dialog
        .showAndWait()
        .ifPresent(
            group -> {
              Set<UUID> selected =
                  selectedTunnels().stream()
                      .map(TunnelRecord::id)
                      .collect(java.util.stream.Collectors.toSet());
              workers.submit(
                  () ->
                      components.bulkWorkspaceRepository().addTunnelsToGroup(selected, group.id()));
            });
  }

  private void bulkRemoveGroup() {
    List<TunnelGroupRecord> groups = components.groupRepository().findAll();
    if (groups.isEmpty()) return;
    javafx.scene.control.ChoiceDialog<TunnelGroupRecord> dialog =
        new javafx.scene.control.ChoiceDialog<>(groups.getFirst(), groups);
    dialog.setTitle("Quitar de grupo");
    dialog.setHeaderText("Selecciona un grupo");
    dialog
        .showAndWait()
        .ifPresent(
            group -> {
              Set<UUID> selected =
                  selectedTunnels().stream()
                      .map(TunnelRecord::id)
                      .collect(java.util.stream.Collectors.toSet());
              workers.submit(
                  () ->
                      components
                          .bulkWorkspaceRepository()
                          .removeTunnelsFromGroup(selected, group.id()));
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
            "Nombre",
            name,
            "Nombre de host",
            hostname,
            "Puerto SSH",
            port,
            "Usuario",
            username,
            UiMessages.get("tunnel.editor.password"),
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
          List<ProjectRecord> projectRecords = tunnelEditor.projects();
          List<EnvironmentRecord> environmentRecords = tunnelEditor.environments();
          List<TagRecord> tagRecords = tunnelEditor.tags();
          Platform.runLater(
              () ->
                  showTunnelEditor(
                      table,
                      existing,
                      hostRecords,
                      projectRecords,
                      environmentRecords,
                      tagRecords));
        });
  }

  private void showTunnelEditor(
      TableView<TunnelRecord> table,
      TunnelRecord existing,
      List<SshHostRecord> hostRecords,
      List<ProjectRecord> projectRecords,
      List<EnvironmentRecord> environmentRecords,
      List<TagRecord> tagRecords) {
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
    ComboBox<ProjectRecord> project =
        classificationCombo(
            projectRecords, initial.projectId(), ProjectRecord::id, ProjectRecord::name);
    ComboBox<EnvironmentRecord> environment =
        classificationCombo(
            environmentRecords,
            initial.environmentId(),
            EnvironmentRecord::id,
            EnvironmentRecord::name);
    ListView<TagRecord> tagSelection =
        new ListView<>(FXCollections.observableArrayList(tagRecords));
    tagSelection.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    tagRecords.stream()
        .filter(tag -> initial.tagIds().contains(tag.id()))
        .forEach(tag -> tagSelection.getSelectionModel().select(tag));
    tagSelection.setPrefHeight(90);
    tagSelection.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(TagRecord item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
              }
            });
    CheckBox favorite = new CheckBox("Favorito");
    favorite.setSelected(initial.favorite());
    TextField keepAlive = new TextField(initial.keepAliveSeconds());
    keepAlive.setPromptText("Global: 30 segundos");
    TextField timeout = new TextField(initial.connectionTimeoutSeconds());
    timeout.setPromptText("Global: 15 segundos");
    TextField retries = new TextField(initial.retryCount());
    retries.setPromptText("Global: 5");
    TextArea options = new TextArea(initial.sshOptions());
    options.setPromptText("Una opción por línea, por ejemplo: ServerAliveCountMax=3");
    options.setPrefRowCount(7);

    Tab general = new Tab(UiMessages.get("tunnel.editor.tab.general"), formGrid("Nombre", name));
    Tab forwarding =
        new Tab(
            UiMessages.get("tunnel.editor.tab.forwarding"),
            formGrid(
                UiMessages.get("tunnel.editor.bind"),
                bind,
                "Puerto local",
                localPort,
                "Host destino",
                destinationHost,
                "Puerto destino",
                destinationPort));
    Tab route =
        new Tab(
            UiMessages.get("tunnel.editor.tab.route"),
            formGrid(
                UiMessages.get("tunnel.editor.jump1"),
                jump1,
                UiMessages.get("tunnel.editor.jump2"),
                jump2,
                "SSH final",
                finalHost));
    Tab advanced =
        new Tab(
            UiMessages.get("tunnel.editor.tab.advanced"),
            formGrid(
                UiMessages.get("tunnel.editor.keepalive"),
                keepAlive,
                UiMessages.get("tunnel.editor.timeout"),
                timeout,
                "Reintentos",
                retries,
                "Opciones SSH",
                options));
    Tab organization =
        new Tab(
            UiMessages.get("tunnel.editor.tab.organization"),
            formGrid(
                "Proyecto",
                project,
                "Entorno",
                environment,
                "Etiquetas",
                tagSelection,
                "",
                favorite));
    List.of(general, forwarding, route, advanced, organization)
        .forEach(tab -> tab.setClosable(false));
    TabPane tabs = new TabPane(general, forwarding, route, advanced, organization);
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
                      options,
                      project,
                      environment,
                      tagSelection,
                      favorite));
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
    List.of(jump1, jump2, finalHost, project, environment)
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
                    options,
                    project,
                    environment,
                    tagSelection,
                    favorite)));
    cancelButton.setOnAction(event -> showTunnels());
  }

  private void saveTunnelFromEditor(
      TableView<TunnelRecord> table, TunnelRecord existing, TunnelEditorViewModel.Form form) {
    boolean restart =
        existing != null
            && tunnels.runtime(existing.id()).actualState().isActive()
            && requiresRuntimeRestart(existing, form);
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
                alert.setHeaderText(
                    result.successful()
                        ? result.message()
                        : ErrorPresentation.friendly(new IllegalStateException(result.message())));
                alert.setContentText(
                    result.successful()
                        ? result.technicalDetails()
                        : ErrorPresentation.sanitize(result.technicalDetails()));
                if (!result.successful()) {
                  ButtonType copy = new ButtonType("Copiar error");
                  alert.getButtonTypes().add(copy);
                  alert
                      .showAndWait()
                      .ifPresent(
                          button -> {
                            if (button == copy)
                              copyToClipboard(
                                  ErrorPresentation.sanitize(result.technicalDetails()));
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
        runtime.lastError() == null
            ? "Sin error técnico."
            : ErrorPresentation.technical(
                selected.id(),
                runtime.actualState().name(),
                routeDescription(selected),
                runtime.lastError());
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

  private static String routeDescription(TunnelRecord tunnel) {
    return "jump1="
        + tunnel.jump1HostId()
        + ",jump2="
        + tunnel.jump2HostId()
        + ",final="
        + tunnel.finalHostId()
        + ",destination="
        + tunnel.destinationHost()
        + ":"
        + tunnel.destinationPort();
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
      TextArea options,
      ComboBox<ProjectRecord> project,
      ComboBox<EnvironmentRecord> environment,
      ListView<TagRecord> tags,
      CheckBox favorite) {
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
        options.getText(),
        project.getValue() == null ? null : project.getValue().id(),
        environment.getValue() == null ? null : environment.getValue().id(),
        tags.getSelectionModel().getSelectedItems().stream()
            .map(TagRecord::id)
            .collect(java.util.stream.Collectors.toSet()),
        favorite.isSelected());
  }

  private static <T> ComboBox<T> classificationCombo(
      List<T> values, UUID selected, Function<T, UUID> id, Function<T, String> name) {
    ComboBox<T> combo = new ComboBox<>(FXCollections.observableArrayList(values));
    combo.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : name.apply(item));
              }
            });
    combo.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : name.apply(item));
          }
        });
    values.stream()
        .filter(value -> id.apply(value).equals(selected))
        .findFirst()
        .ifPresent(combo::setValue);
    return combo;
  }

  private static boolean requiresRuntimeRestart(
      TunnelRecord existing, TunnelEditorViewModel.Form form) {
    return !existing.bindAddress().equals(form.bindAddress().trim())
        || existing.localPort() != Integer.parseInt(form.localPort().trim())
        || !existing.destinationHost().equals(form.destinationHost().trim())
        || existing.destinationPort() != Integer.parseInt(form.destinationPort().trim())
        || !java.util.Objects.equals(existing.finalHostId(), form.finalHostId())
        || !java.util.Objects.equals(existing.jump1HostId(), form.jump1HostId())
        || !java.util.Objects.equals(existing.jump2HostId(), form.jump2HostId())
        || !java.util.Objects.equals(
            existing.keepAliveOverrideSeconds(), parseLongOrNull(form.keepAliveSeconds()))
        || !java.util.Objects.equals(
            existing.connectionTimeoutOverrideSeconds(),
            parseLongOrNull(form.connectionTimeoutSeconds()))
        || !java.util.Objects.equals(
            existing.retryCountOverride(), parseIntegerOrNull(form.retryCount()))
        || !existing.sshOptions().equals(parseOptionsForComparison(form.sshOptions()));
  }

  private static Long parseLongOrNull(String value) {
    return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
  }

  private static Integer parseIntegerOrNull(String value) {
    return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
  }

  private static java.util.Map<String, String> parseOptionsForComparison(String value) {
    if (value == null || value.isBlank()) return java.util.Map.of();
    return value
        .lines()
        .filter(line -> !line.isBlank())
        .map(line -> line.split("=", 2))
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                pair -> pair[0].trim(), pair -> pair.length > 1 ? pair[1].trim() : ""));
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

  private static String groupStateText(GroupRuntimeStatus status) {
    return switch (status.state()) {
      case OPEN -> "OPEN " + status.open() + "/" + status.members();
      case STARTING ->
          "STARTING O=" + status.open() + " C=" + status.connecting() + " Q=" + status.queued();
      case PARTIAL -> "PARTIAL O=" + status.open() + " E=" + status.errors();
      case ERROR -> "ERROR " + status.errors() + "/" + status.members();
      case CLOSED -> "CLOSED";
    };
  }

  private static final class GroupRow {
    private final TunnelGroupRecord group;
    private final GroupRuntimeStatus status;

    private GroupRow(TunnelGroupRecord group, GroupRuntimeStatus status) {
      this.group = group;
      this.status = status;
    }

    private TunnelGroupRecord group() {
      return group;
    }

    private GroupRuntimeStatus status() {
      return status;
    }
  }

  private static final class GroupChoice {
    private final TunnelRecord tunnel;
    private final BooleanProperty selected = new SimpleBooleanProperty();

    private GroupChoice(TunnelRecord tunnel, boolean selected) {
      this.tunnel = tunnel;
      this.selected.set(selected);
    }

    private TunnelRecord tunnel() {
      return tunnel;
    }

    private BooleanProperty selected() {
      return selected;
    }

    private String endpoint() {
      return tunnel.bindAddress() + ":" + tunnel.localPort();
    }
  }

  private void showError(String header, Throwable exception) {
    Alert alert = new Alert(Alert.AlertType.ERROR, ErrorPresentation.friendly(exception));
    alert.setHeaderText(header);
    ButtonType copy = new ButtonType("Copiar error");
    ButtonType details = new ButtonType("Ver detalles", ButtonBar.ButtonData.HELP);
    alert.getButtonTypes().addAll(details, copy);
    alert
        .showAndWait()
        .ifPresent(
            result -> {
              if (result == copy) copyToClipboard(ErrorPresentation.technical(exception));
              if (result == details) showTechnicalError(ErrorPresentation.technical(exception));
            });
  }

  private void showTechnicalError(String details) {
    Alert technical = new Alert(Alert.AlertType.INFORMATION, details);
    technical.setHeaderText("Detalles técnicos");
    ButtonType copy = new ButtonType("Copiar error");
    technical.getButtonTypes().add(copy);
    technical
        .showAndWait()
        .ifPresent(
            result -> {
              if (result == copy) copyToClipboard(details);
            });
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
