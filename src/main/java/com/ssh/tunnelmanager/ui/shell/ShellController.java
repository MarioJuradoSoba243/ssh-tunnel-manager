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
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class ShellController implements AutoCloseable {
  @FXML private ListView<NavigationItem> navigationList;
  @FXML private Label sectionTitle;
  @FXML private Label sectionDescription;
  @FXML private VBox contentContainer;
  @FXML private Button globalSearchButton;
  @FXML private ToastHost toastHost;

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
  private TableView<SshHostRecord> hostTable;
  private Node tunnelView;
  private VBox tunnelDetailPanel;
  private FlowPane tunnelFilterChips;
  private VBox contextualSelectionBar;
  private Label selectionSummary;
  private final TunnelViewState tunnelViewState = new TunnelViewState();
  private final javafx.collections.ObservableList<TunnelRecord> tunnelRows = tunnelViewState.rows();
  private final FilteredList<TunnelRecord> filteredTunnelRows = tunnelViewState.filteredRows();
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
  private String density = "COMFORTABLE";

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
    if (components != null) {
      density = components.settingsRepository().findValue("ui.density").orElse("COMFORTABLE");
      density = density.equals("COMPACT") ? "COMPACT" : "COMFORTABLE";
    }
    applyDensity();
    navigationList.getItems().setAll(shellViewModel.navigationItems());
    navigationList.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(NavigationItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  setGraphic(null);
                  setMouseTransparent(true);
                  setFocusTraversable(false);
                  return;
                }
                setMouseTransparent(false);
                setFocusTraversable(true);
                setText(UiMessages.get("navigation." + item.id()));
                Label icon = new Label(navigationIcon(item.id()));
                icon.getStyleClass().add("navigation-icon");
                setGraphic(icon);
              }
            });
    globalSearchButton.setOnAction(event -> showGlobalSearch());
    navigationList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, old, current) -> {
              if (current != null) {
                if (components != null)
                  components.settingsRepository().put("ui.lastSection", current.id());
                showSection(current);
              }
            });
    String lastSection =
        components == null
            ? "home"
            : components.settingsRepository().findValue("ui.lastSection").orElse("home");
    navigationList.getItems().stream()
        .filter(item -> item.id().equals(lastSection))
        .findFirst()
        .ifPresentOrElse(
            item -> navigationList.getSelectionModel().select(item),
            () -> navigationList.getSelectionModel().selectFirst());
    if (tunnels != null) tunnels.subscribe(event -> Platform.runLater(this::refreshCurrentSection));
    navigationList
        .sceneProperty()
        .addListener(
            (observable, oldScene, scene) -> {
              if (scene != null) {
                applyDensityToScene(scene);
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

  private static String navigationIcon(String id) {
    return switch (id) {
      case "home" -> "⌂";
      case "tunnels" -> "⇄";
      case "groups" -> "☷";
      case "hosts" -> "⌁";
      case "organization" -> "▦";
      case "settings" -> "⚙";
      default -> "•";
    };
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
    Label summary = new Label("Resumen del espacio de trabajo");
    summary.getStyleClass().add("section-heading");
    Label emptyFlow = new Label();
    ListView<String> favorites = new ListView<>();
    ListView<String> recent = new ListView<>();
    ListView<String> active = new ListView<>();
    List.of(favorites, recent, active).forEach(list -> list.getStyleClass().add("dashboard-list"));
    favorites.setPlaceholder(new Label("Aún no hay favoritos"));
    recent.setPlaceholder(new Label("Aún no hay actividad reciente"));
    active.setPlaceholder(new Label("No hay túneles activos"));
    Button stopAll = new Button("Detener todos");
    buttonStyle(stopAll, "danger");
    stopAll.setOnAction(event -> stopAllTunnels());
    Button configured = metricCard("Configurados\n—", "metric-neutral");
    Button open = metricCard("Abiertos\n—", "metric-success");
    Button reconnecting = metricCard("Reconectando\n—", "metric-warning");
    Button errors = metricCard("Errores\n—", "metric-danger");
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
            flowPane(configured, open, reconnecting, errors),
            sectionHeading("Favoritos"),
            favorites,
            sectionHeading("Recientes"),
            recent,
            sectionHeading("Túneles activos"),
            active,
            stopAll);
    VBox.setVgrow(favorites, Priority.NEVER);
    VBox.setVgrow(recent, Priority.NEVER);
    VBox.setVgrow(active, Priority.NEVER);
    favorites.setPrefHeight(120);
    recent.setPrefHeight(120);
    active.setPrefHeight(120);
    favorites.setMaxHeight(180);
    recent.setMaxHeight(180);
    active.setMaxHeight(180);
    contentContainer.getChildren().add(content);
    workers.submit(
        () -> {
          search.refresh();
          hub.refresh();
          HubViewModel.HubSnapshot snapshot = hub.snapshot();
          Platform.runLater(
              () -> {
                summary.setText(
                    "Resumen del espacio de trabajo · "
                        + snapshot.configuredTunnels()
                        + " túneles configurados");
                configured.setText("Configurados\n" + snapshot.configuredTunnels());
                open.setText("Abiertos\n" + snapshot.openTunnels());
                reconnecting.setText("Reconectando\n" + snapshot.reconnectingTunnels());
                errors.setText("Errores\n" + snapshot.errorTunnels());
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

  private static Button metricCard(String text, String styleClass) {
    Button card = new Button(text);
    card.getStyleClass().addAll("metric-card", styleClass);
    card.setMinWidth(132);
    card.setPrefWidth(160);
    card.setMaxWidth(Double.MAX_VALUE);
    card.setMinHeight(68);
    card.setWrapText(true);
    return card;
  }

  private static Label sectionHeading(String text) {
    Label heading = new Label(text);
    heading.getStyleClass().add("section-heading");
    return heading;
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
    Dialog<CommandPaletteEntry> dialog = new Dialog<>();
    dialog.setTitle("Buscar en el espacio de trabajo");
    dialog.setHeaderText("Ctrl+K · comandos y entidades del espacio de trabajo");
    TextField query = new TextField();
    query.setPromptText("Escribe un comando, túnel, grupo o host…");
    ListView<CommandPaletteEntry> entries = new ListView<>();
    entries.setPrefSize(560, 360);
    entries.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(CommandPaletteEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title() + "  ·  " + item.subtitle());
                setTooltip(
                    empty || item == null
                        ? null
                        : new javafx.scene.control.Tooltip(item.title() + " — " + item.subtitle()));
              }
            });
    VBox palette = new VBox(10, query, entries);
    palette.getStyleClass().add("command-palette");
    dialog.getDialogPane().setContent(palette);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
    dialog.setResultConverter(button -> entries.getSelectionModel().getSelectedItem());
    List<CommandPaletteEntry> commands = commandPaletteEntries();
    entries.getItems().setAll(commands);
    workers.submit(search::refresh);
    query
        .textProperty()
        .addListener(
            (observable, old, value) -> {
              String currentQuery = value;
              workers.submit(
                  () -> {
                    List<CommandPaletteEntry> matches =
                        new java.util.ArrayList<>(
                            commands.stream()
                                .filter(entry -> entry.matches(currentQuery))
                                .toList());
                    matches.addAll(
                        search.search(currentQuery).stream()
                            .map(this::commandPaletteEntry)
                            .toList());
                    Platform.runLater(() -> entries.getItems().setAll(matches));
                  });
            });
    query.setOnKeyPressed(
        event -> {
          switch (event.getCode()) {
            case DOWN -> {
              entries.requestFocus();
              entries.getSelectionModel().selectNext();
              event.consume();
            }
            case ENTER -> {
              if (entries.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(entries.getSelectionModel().getSelectedItem());
                dialog.close();
              }
              event.consume();
            }
            default -> {}
          }
        });
    entries.setOnKeyPressed(
        event -> {
          if (event.getCode() == javafx.scene.input.KeyCode.ENTER
              && entries.getSelectionModel().getSelectedItem() != null) {
            dialog.setResult(entries.getSelectionModel().getSelectedItem());
            dialog.close();
          }
        });
    dialog.setOnShown(
        event -> {
          query.requestFocus();
          entries.getSelectionModel().selectFirst();
        });
    dialog.showAndWait().ifPresent(entry -> entry.action().run());
  }

  private List<CommandPaletteEntry> commandPaletteEntries() {
    return List.of(
        new CommandPaletteEntry(
            "Nuevo túnel",
            "Túneles · Ctrl+N",
            () -> {
              selectSection("tunnels");
              Platform.runLater(() -> openTunnelEditor(tunnelTable, null));
            }),
        new CommandPaletteEntry(
            "Nuevo host SSH",
            "Hosts SSH",
            () -> {
              selectSection("hosts");
              Platform.runLater(() -> openHostEditor(hostTable, null));
            }),
        new CommandPaletteEntry(
            "Abrir ajustes", "Apariencia y configuración", () -> selectSection("settings")),
        new CommandPaletteEntry("Refrescar vista", "F5", this::refreshCurrentSection),
        new CommandPaletteEntry("Detener todos", "Operación de emergencia", this::stopAllTunnels));
  }

  private CommandPaletteEntry commandPaletteEntry(SearchResult result) {
    return new CommandPaletteEntry(
        result.title(),
        result.kind().name() + " · " + result.subtitle(),
        () -> selectSection(navigationId(result.kind())));
  }

  private void showSearchResults(String query, List<SearchResult> results) {
    if (results.isEmpty()) {
      showInfo("Resultados de búsqueda", "No se encontraron túneles, grupos ni hosts.");
      return;
    }
    javafx.scene.control.Dialog<SearchResult> dialog =
        choiceDialog(
            "Resultados de búsqueda",
            "Resultados para: " + query + " · elige uno para navegar",
            results.getFirst(),
            results,
            result -> result.title() + " — " + result.subtitle());
    dialog.setTitle("Resultados de búsqueda");
    dialog
        .showAndWait()
        .ifPresent(
            result ->
                navigationList.getItems().stream()
                    .filter(item -> item.id().equals(navigationId(result.kind())))
                    .findFirst()
                    .ifPresent(item -> navigationList.getSelectionModel().select(item)));
  }

  private static String navigationId(SearchResult.Kind kind) {
    return switch (kind) {
      case TUNNEL -> "tunnels";
      case GROUP -> "groups";
      case HOST -> "hosts";
    };
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
    VBox projectPanel =
        classificationPanel(
            projects,
            components.projectRepository()::findAll,
            ProjectRecord::name,
            ProjectRecord::id,
            (id, name) ->
                components
                    .saveProject()
                    .execute(new com.ssh.tunnelmanager.domain.organization.Project(id, name)),
            (id, confirmed) -> components.deleteProject().execute(id, confirmed));
    VBox environmentPanel =
        classificationPanel(
            environments,
            components.environmentRepository()::findAll,
            EnvironmentRecord::name,
            EnvironmentRecord::id,
            (id, name) ->
                components
                    .saveEnvironment()
                    .execute(new com.ssh.tunnelmanager.domain.organization.Environment(id, name)),
            (id, confirmed) -> components.deleteEnvironment().execute(id, confirmed));
    VBox tagPanel =
        classificationPanel(
            tags,
            components.tagRepository()::findAll,
            TagRecord::name,
            TagRecord::id,
            (id, name) ->
                components
                    .saveTag()
                    .execute(new com.ssh.tunnelmanager.domain.organization.Tag(id, name)),
            (id, confirmed) -> components.deleteTag().execute(id, confirmed));
    projectPanel.getChildren().add(0, sectionLabel("Proyectos"));
    environmentPanel.getChildren().add(0, sectionLabel("Entornos"));
    tagPanel.getChildren().add(0, sectionLabel("Etiquetas"));
    FlowPane columns = new FlowPane(12, 12, projectPanel, environmentPanel, tagPanel);
    columns.getStyleClass().add("organization-columns");
    columns.setMaxWidth(Double.MAX_VALUE);
    contentContainer.getChildren().add(columns);
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
    theme.setMaxWidth(Double.MAX_VALUE);
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
    logging.setMaxWidth(Double.MAX_VALUE);
    logging.setOnAction(
        event -> {
          components.settings().setLoggingLevel(logging.getValue());
          components.loggingLevel().apply(logging.getValue());
        });
    GridPane shortcuts = new GridPane();
    shortcuts.getStyleClass().add("settings-shortcuts");
    shortcuts.setHgap(8);
    shortcuts.setVgap(6);
    ColumnConstraints shortcutLabels = new ColumnConstraints();
    shortcutLabels.setMinWidth(170);
    ColumnConstraints shortcutInputs = new ColumnConstraints();
    shortcutInputs.setHgrow(Priority.ALWAYS);
    shortcutInputs.setFillWidth(true);
    shortcuts.getColumnConstraints().addAll(shortcutLabels, shortcutInputs);
    Map<ShortcutAction, TextField> shortcutFields = new java.util.EnumMap<>(ShortcutAction.class);
    int row = 0;
    for (ShortcutAction action : ShortcutAction.values()) {
      TextField field = new TextField(components.shortcuts().load().get(action));
      field.setMaxWidth(Double.MAX_VALUE);
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
    Label exportWarning =
        new Label(
            "La exportación completa contiene contraseñas recuperables. "
                + "Guárdala solo en un lugar seguro.");
    exportWarning.setWrapText(true);
    exportWarning.getStyleClass().add("warning-text");
    VBox appearance = new VBox(8, new Label("Tema"), theme);
    ComboBox<String> density = new ComboBox<>();
    density.getItems().setAll("COMFORTABLE", "COMPACT");
    density.setValue(this.density);
    density.setMaxWidth(Double.MAX_VALUE);
    density.setOnAction(
        event -> {
          this.density = density.getValue();
          components.settingsRepository().put("ui.density", this.density);
          applyDensity();
        });
    appearance.getChildren().addAll(new Label("Densidad"), density);
    VBox notificationsSection = new VBox(8, notifications);
    VBox keyboard = new VBox(8, shortcuts, flowPane(saveShortcuts));
    VBox data = new VBox(8, exportWarning, flowPane(export, importButton, backups));
    VBox diagnostics =
        new VBox(8, new Label("Nivel de logging"), logging, flowPane(diagnostic, logs));
    contentContainer
        .getChildren()
        .add(
            new VBox(
                10,
                settingsSection("Apariencia", appearance),
                settingsSection("Notificaciones", notificationsSection),
                settingsSection("Atajos de teclado", keyboard),
                settingsSection("Datos", data),
                settingsSection("Diagnóstico", diagnostics)));
  }

  private void applyDensity() {
    if (navigationList == null) return;
    navigationList.getStyleClass().removeAll("density-comfortable", "density-compact");
    navigationList.getStyleClass().add("density-" + density.toLowerCase());
    if (tunnelTable != null) tunnelTable.getStyleClass().add("density-" + density.toLowerCase());
  }

  private void applyDensityToScene(javafx.scene.Scene scene) {
    scene.getRoot().getStyleClass().removeAll("density-comfortable", "density-compact");
    scene.getRoot().getStyleClass().add("density-" + density.toLowerCase());
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
    configureComboPresentation(applyAll, ShellController::conflictActionText);
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
      configureComboPresentation(choice, ShellController::conflictActionText);
      choice.getItems().addAll(actionsFor(conflict.key().type()));
      choice.setValue(ConflictAction.KEEP_CURRENT);
      choices.put(conflict.key(), choice);
      Label conflictLabel =
          new Label(
              conflict.key().type()
                  + " · "
                  + conflict.name()
                  + "\nActual: "
                  + conflict.currentSummary()
                  + "\nImportado: "
                  + conflict.importedSummary());
      conflictLabel.setWrapText(true);
      conflictLabel.setMaxWidth(560);
      FlowPane conflictRow = flowPane(conflictLabel, choice);
      rows.getChildren().add(conflictRow);
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
    javafx.scene.control.Dialog<java.nio.file.Path> dialog =
        choiceDialog(
            "Restaurar backup",
            "Selecciona un snapshot SQLite válido",
            values.getFirst(),
            values,
            path -> path.getFileName().toString());
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
                setTooltip(
                    empty || item == null
                        ? null
                        : new javafx.scene.control.Tooltip(display.apply(item)));
              }
            });
    Button add = new Button("Nuevo");
    Button edit = new Button("Editar");
    Button remove = new Button("Eliminar");
    buttonStyle(add, "primary");
    buttonStyle(edit, "secondary");
    buttonStyle(remove, "danger");
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
    VBox panel = new VBox(8, list, flowPane(add, edit, remove));
    panel.getStyleClass().add("classification-panel");
    panel.setMinWidth(220);
    panel.setPrefWidth(280);
    panel.setMaxWidth(420);
    list.setPrefHeight(260);
    list.setMinHeight(140);
    list.setMaxHeight(420);
    VBox.setVgrow(list, Priority.ALWAYS);
    return panel;
  }

  private static String conflictActionText(ConflictAction action) {
    return switch (action) {
      case KEEP_CURRENT -> "Mantener actual";
      case REPLACE -> "Reemplazar con importado";
      case CREATE_COPY -> "Importar como copia";
    };
  }

  private static TitledPane settingsSection(String title, Node content) {
    TitledPane section = new TitledPane(title, content);
    section.setExpanded(true);
    section.setMaxWidth(Double.MAX_VALUE);
    section.getStyleClass().add("settings-section");
    return section;
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text);
    label.getStyleClass().add("classification-title");
    return label;
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
    Button start = new Button("Iniciar grupo");
    Button stop = new Button("Detener grupo");
    MenuItem duplicate = new MenuItem("Duplicar");
    MenuItem remove = new MenuItem("Eliminar");
    buttonStyle(add, "primary");
    buttonStyle(edit, "secondary");
    buttonStyle(start, "primary");
    add.setOnAction(event -> openGroupEditor(null));
    edit.setOnAction(event -> editSelectedGroup());
    duplicate.setOnAction(event -> duplicateSelectedGroup());
    remove.setOnAction(event -> deleteSelectedGroup());
    start.setOnAction(event -> operateSelectedGroup(groups::start));
    stop.setOnAction(event -> operateSelectedGroup(groups::stop));
    MenuButton more = new MenuButton("Más");
    more.getItems().addAll(duplicate, new SeparatorMenuItem(), remove);
    groupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    groupTable.setPlaceholder(new Label("No hay grupos. Usa Nuevo grupo para crear el primero."));
    groupTable.setPrefHeight(300);
    groupTable.setMaxHeight(420);
    name.setMinWidth(180);
    members.setMinWidth(90);
    state.setMinWidth(180);
    contentContainer.getChildren().addAll(groupTable, flowPane(add, edit, start, stop, more));
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
    TagSelectionControl tagSelection = new TagSelectionControl(tagRecords, initial.tagIds());
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
    choices.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    choices.setEditable(true);
    selected.setEditable(true);
    selected.setMinWidth(75);
    tunnelName.setMinWidth(180);
    endpoint.setMinWidth(180);
    choices.setItems(filtered);
    Button save = new Button("Guardar");
    Button cancel = new Button("Cancelar");
    buttonStyle(save, "primary");
    buttonStyle(cancel, "ghost");
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
            flowPane(save, cancel));
    VBox.setVgrow(choices, Priority.ALWAYS);
    contentContainer.getChildren().add(editor);
  }

  private void saveGroupEditor(
      TunnelGroupRecord existing,
      TextField name,
      List<GroupChoice> rows,
      ComboBox<ProjectRecord> project,
      ComboBox<EnvironmentRecord> environment,
      TagSelectionControl tags,
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
            tags.selectedTagIds());
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
    hostTable = table;
    TableColumn<SshHostRecord, String> name = new TableColumn<>("Nombre");
    name.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(data.getValue().name()));
    TableColumn<SshHostRecord, String> endpoint = new TableColumn<>("Endpoint");
    endpoint.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().hostname() + ":" + data.getValue().sshPort()));
    table.getColumns().addAll(name, endpoint);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    table.setPlaceholder(new Label("No hay hosts SSH. Usa Nuevo host para crear el primero."));
    table.setPrefHeight(300);
    table.setMaxHeight(420);
    name.setMinWidth(180);
    endpoint.setMinWidth(220);
    FlowPane actions = flowPane();
    Button add = new Button("Nuevo host");
    Button edit = new Button("Editar");
    MenuItem remove = new MenuItem("Eliminar");
    MenuItem forget = new MenuItem("Eliminar confianza");
    buttonStyle(add, "primary");
    buttonStyle(edit, "secondary");
    add.setOnAction(event -> openHostEditor(table, null));
    edit.setOnAction(event -> openHostEditor(table, table.getSelectionModel().getSelectedItem()));
    remove.setOnAction(event -> deleteHost(table.getSelectionModel().getSelectedItem(), table));
    forget.setOnAction(event -> forgetHostKey(table.getSelectionModel().getSelectedItem(), table));
    MenuButton more = new MenuButton("Más");
    more.getItems().addAll(remove, new SeparatorMenuItem(), forget);
    actions.getChildren().addAll(add, edit, more);
    contentContainer.getChildren().addAll(table, actions);
    refreshHosts(table);
  }

  private void showTunnels() {
    stopStatusRefresh();
    editingTunnel = false;
    sectionDescription.setText(
        "Local Port Forwarding: configura, prueba, inicia, detén y reintenta túneles individuales.");
    if (tunnels == null) return;
    if (tunnelView != null) {
      contentContainer.getChildren().add(tunnelView);
      refreshTunnels(tunnelTable);
      return;
    }
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
    state.setCellFactory(
        ignored ->
            new javafx.scene.control.TableCell<>() {
              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                  setText(null);
                  setGraphic(null);
                  return;
                }
                TunnelRecord record = getTableView().getItems().get(getIndex());
                setText(null);
                setGraphic(
                    new StatusBadge(item, tunnels.runtime(record.id()).actualState().name()));
              }
            });
    TableColumn<TunnelRecord, String> active = new TableColumn<>("Tiempo activo");
    active.setCellValueFactory(
        data -> new javafx.beans.property.SimpleStringProperty(activeTime(data.getValue())));
    TableColumn<TunnelRecord, String> favorite = new TableColumn<>("★");
    favorite.setCellValueFactory(
        data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().favorite() ? "★" : ""));
    favorite.getStyleClass().add("favorite-column");
    favorite.setCellFactory(
        ignored ->
            new TableCell<>() {
              {
                setAlignment(Pos.CENTER);
              }

              @Override
              protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAccessibleText(
                    empty ? null : item == null || item.isBlank() ? "No favorito" : "Favorito");
                setTooltip(
                    empty
                        ? null
                        : new Tooltip(item == null || item.isBlank() ? "No favorito" : "Favorito"));
              }
            });
    TableColumn<TunnelRecord, Void> quickActions = quickActionsColumn();
    tunnelTable.getColumns().addAll(name, endpoint, state, active, favorite, quickActions);
    tunnelTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    tunnelTable.setPrefHeight(300);
    tunnelTable.setMaxHeight(420);
    name.setMinWidth(180);
    endpoint.setMinWidth(150);
    state.setMinWidth(170);
    active.setMinWidth(125);
    favorite.setMinWidth(55);
    quickActions.setMinWidth(142);
    tunnelTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
    tunnelTable
        .getSelectionModel()
        .getSelectedItems()
        .addListener(
            (javafx.collections.ListChangeListener<TunnelRecord>)
                change -> {
                  updateTunnelSelectionContext();
                  updateTunnelDetail(tunnelTable.getSelectionModel().getSelectedItem());
                });
    tunnelTable
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((observable, old, current) -> updateTunnelDetail(current));
    tunnelTable.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2
              && tunnelTable.getSelectionModel().getSelectedItem() != null) {
            openTunnelEditor(tunnelTable, tunnelTable.getSelectionModel().getSelectedItem());
          }
        });
    Button add = new Button("Nuevo túnel");
    Button edit = new Button("Editar");
    Button start = new Button("Iniciar");
    Button stop = new Button("Detener");
    buttonStyle(add, "primary");
    buttonStyle(edit, "secondary");
    buttonStyle(start, "primary");
    buttonStyle(stop, "ghost");
    MenuItem duplicate = new MenuItem("Duplicar");
    MenuItem remove = new MenuItem("Eliminar");
    MenuItem test = new MenuItem("Probar conexión");
    MenuItem retry = new MenuItem("Reintentar");
    MenuItem details = new MenuItem("Ver detalles");
    MenuItem bulkStart = new MenuItem("Iniciar selección");
    MenuItem bulkStop = new MenuItem("Detener selección");
    MenuItem favoriteOn = new MenuItem("Marcar selección como favorita");
    MenuItem favoriteOff = new MenuItem("Quitar favorito a la selección");
    MenuItem assignProject = new MenuItem("Asignar proyecto/entorno");
    MenuItem assignGroup = new MenuItem("Añadir selección a un grupo");
    MenuItem removeGroup = new MenuItem("Quitar selección de un grupo");
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
    Menu bulk = new Menu("Operaciones sobre la selección");
    bulk.getItems()
        .addAll(
            bulkStart,
            bulkStop,
            new SeparatorMenuItem(),
            favoriteOn,
            favoriteOff,
            assignProject,
            assignGroup,
            removeGroup);
    MenuButton more = new MenuButton("Más");
    more.getItems()
        .addAll(
            test,
            retry,
            details,
            new SeparatorMenuItem(),
            bulk,
            new SeparatorMenuItem(),
            duplicate,
            remove);
    tunnelSearch = new TextField();
    tunnelSearch.setPromptText("Filtrar nombre o destino");
    tunnelProjectFilter = new ComboBox<>();
    configureComboPresentation(tunnelProjectFilter, ProjectRecord::name);
    tunnelProjectFilter.setPromptText("Proyecto");
    tunnelEnvironmentFilter = new ComboBox<>();
    configureComboPresentation(tunnelEnvironmentFilter, EnvironmentRecord::name);
    tunnelEnvironmentFilter.setPromptText("Entorno");
    tunnelGroupFilter = new ComboBox<>();
    configureComboPresentation(tunnelGroupFilter, TunnelGroupRecord::name);
    tunnelGroupFilter.setPromptText("Grupo");
    tunnelStateFilter =
        new ComboBox<>(
            FXCollections.observableArrayList(
                com.ssh.tunnelmanager.domain.tunnel.ActualState.values()));
    tunnelStateFilter.setPromptText("Estado");
    tunnelTagFilter = new ListView<>();
    tunnelTagFilter.setPlaceholder(new Label("Etiquetas"));
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
    tunnelFilterChips = new FlowPane(6, 6);
    tunnelFilterChips.getStyleClass().add("filter-chips");
    MenuButton addFilter = new MenuButton("+ Filtro");
    addFilter
        .getItems()
        .addAll(
            new MenuItem("Proyecto (selector visible)"),
            new MenuItem("Entorno (selector visible)"),
            new MenuItem("Grupo (selector visible)"),
            new MenuItem("Estado (selector visible)"));
    FlowPane filters =
        flowPane(
            tunnelSearch,
            tunnelProjectFilter,
            tunnelEnvironmentFilter,
            tunnelGroupFilter,
            tunnelStateFilter,
            tagFilterBox(),
            addFilter,
            tunnelFilterChips);
    filters.getStyleClass().add("filter-bar");
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
    FlowPane actions = flowPane(start, stop, add, edit, more);
    contextualSelectionBar = new VBox(6);
    contextualSelectionBar.getStyleClass().add("contextual-selection-bar");
    selectionSummary = new Label();
    Button contextualStart = new Button("Iniciar selección");
    Button contextualStop = new Button("Detener selección");
    buttonStyle(contextualStart, "primary");
    buttonStyle(contextualStop, "ghost");
    contextualStart.setOnAction(event -> bulkOperate(true));
    contextualStop.setOnAction(event -> bulkOperate(false));
    contextualSelectionBar
        .getChildren()
        .addAll(selectionSummary, flowPane(contextualStart, contextualStop));
    contextualSelectionBar.setManaged(false);
    contextualSelectionBar.setVisible(false);
    VBox tableSide = new VBox(8, filters, tunnelTable, contextualSelectionBar, actions);
    VBox.setVgrow(tunnelTable, Priority.ALWAYS);
    tunnelDetailPanel = createTunnelDetailPanel();
    javafx.scene.control.SplitPane split =
        new javafx.scene.control.SplitPane(tableSide, tunnelDetailPanel);
    split.setDividerPositions(0.70);
    split.getStyleClass().add("tunnel-master-detail");
    VBox.setVgrow(split, Priority.ALWAYS);
    tunnelView = split;
    javafx.collections.transformation.SortedList<TunnelRecord> sorted =
        new javafx.collections.transformation.SortedList<>(filteredTunnelRows);
    sorted.comparatorProperty().bind(tunnelTable.comparatorProperty());
    tunnelTable.setItems(sorted);
    contentContainer.getChildren().add(split);
    refreshTunnels(tunnelTable);
  }

  private TableColumn<TunnelRecord, Void> quickActionsColumn() {
    TableColumn<TunnelRecord, Void> column = new TableColumn<>("Acciones");
    column.setCellFactory(
        ignored ->
            new javafx.scene.control.TableCell<>() {
              private final Button start = compactAction("▶", "Iniciar túnel");
              private final Button stop = compactAction("■", "Detener túnel");
              private final Button edit = compactAction("✎", "Editar túnel");
              private final javafx.scene.layout.HBox actions =
                  new javafx.scene.layout.HBox(2, start, stop, edit);

              {
                actions.getStyleClass().add("row-actions");
                start.setOnAction(event -> operateTunnel(getTableRow().getItem(), tunnels::start));
                stop.setOnAction(event -> operateTunnel(getTableRow().getItem(), tunnels::stop));
                edit.setOnAction(event -> openTunnelEditor(tunnelTable, getTableRow().getItem()));
              }

              @Override
              protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : actions);
              }
            });
    return column;
  }

  private static Button compactAction(String glyph, String accessibleText) {
    Button button = new Button(glyph);
    button.getStyleClass().addAll("button", "icon", "row-action");
    button.setAccessibleText(accessibleText);
    button.setTooltip(new javafx.scene.control.Tooltip(accessibleText));
    return button;
  }

  private VBox createTunnelDetailPanel() {
    VBox panel = new VBox(10);
    panel.getStyleClass().add("tunnel-detail-panel");
    panel.setMinWidth(260);
    panel.setPrefWidth(330);
    panel.setMaxWidth(420);
    panel
        .getChildren()
        .addAll(
            sectionHeading("Detalle del túnel"),
            new Label("Selecciona un túnel para ver su ruta, estado y actividad."));
    return panel;
  }

  private void updateTunnelDetail(TunnelRecord selected) {
    if (tunnelDetailPanel == null) return;
    tunnelDetailPanel.getChildren().clear();
    if (selected == null) {
      tunnelDetailPanel
          .getChildren()
          .addAll(
              sectionHeading("Detalle del túnel"),
              new Label("Selecciona un túnel para ver su ruta, estado y actividad."));
      return;
    }
    TunnelRuntime runtime = tunnels.runtime(selected.id());
    Label name = new Label(selected.name());
    name.getStyleClass().add("detail-title");
    Label endpoint =
        new Label(
            selected.bindAddress()
                + ":"
                + selected.localPort()
                + "  →  "
                + selected.destinationHost()
                + ":"
                + selected.destinationPort());
    endpoint.setWrapText(true);
    Label route = new Label("Ruta SSH\n" + readableRoute(selected));
    route.setWrapText(true);
    Label activity =
        new Label(
            "Tiempo activo: " + activeTime(selected) + "\nIntento: " + runtime.retryAttempt());
    activity.setWrapText(true);
    Button details = new Button("Ver detalles completos");
    buttonStyle(details, "secondary");
    details.setOnAction(event -> showTunnelDetails(selected));
    tunnelDetailPanel
        .getChildren()
        .addAll(
            name,
            new StatusBadge(stateText(selected), runtime.actualState().name()),
            endpoint,
            route,
            activity,
            sectionHeading("Eventos de sesión"));
    Label events =
        new Label(
            runtime.sessionEvents().isEmpty()
                ? "Sin eventos en esta sesión."
                : String.join("\n", runtime.sessionEvents()));
    events.setWrapText(true);
    tunnelDetailPanel.getChildren().addAll(events, details);
  }

  private void updateTunnelSelectionContext() {
    if (contextualSelectionBar == null) return;
    int count = selectedTunnels().size();
    boolean visible = count > 1;
    contextualSelectionBar.setManaged(visible);
    contextualSelectionBar.setVisible(visible);
    if (selectionSummary != null) selectionSummary.setText(count + " túneles seleccionados");
  }

  private void refreshCurrentSection() {
    if (editingTunnel) return;
    NavigationItem selected = navigationList.getSelectionModel().getSelectedItem();
    if (selected == null) return;
    if ("hosts".equals(selected.id())) showHosts();
    if ("tunnels".equals(selected.id())) {
      if (tunnelTable != null) {
        tunnelTable.refresh();
        refreshTunnels(tunnelTable);
      } else showTunnels();
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
                tunnelViewState.replaceAll(values);
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
    refreshFilterChips();
    if (tunnelTable != null) {
      tunnelTable.setPlaceholder(
          new Label(
              tunnelRows.isEmpty()
                  ? "Aún no hay túneles. Usa Nuevo túnel para crear el primero."
                  : "Ningún túnel coincide con estos filtros."));
    }
  }

  private void refreshFilterChips() {
    if (tunnelFilterChips == null) return;
    List<Node> chips = new java.util.ArrayList<>();
    if (tunnelSearch != null && !tunnelSearch.getText().isBlank()) {
      chips.add(
          new FilterChip("Texto: " + tunnelSearch.getText().trim(), () -> tunnelSearch.clear()));
    }
    if (tunnelProjectFilter != null && tunnelProjectFilter.getValue() != null) {
      chips.add(
          new FilterChip(
              "Proyecto: " + tunnelProjectFilter.getValue().name(),
              () -> tunnelProjectFilter.setValue(null)));
    }
    if (tunnelEnvironmentFilter != null && tunnelEnvironmentFilter.getValue() != null) {
      chips.add(
          new FilterChip(
              "Entorno: " + tunnelEnvironmentFilter.getValue().name(),
              () -> tunnelEnvironmentFilter.setValue(null)));
    }
    if (tunnelGroupFilter != null && tunnelGroupFilter.getValue() != null) {
      chips.add(
          new FilterChip(
              "Grupo: " + tunnelGroupFilter.getValue().name(),
              () -> tunnelGroupFilter.setValue(null)));
    }
    if (tunnelStateFilter != null && tunnelStateFilter.getValue() != null) {
      chips.add(
          new FilterChip(
              "Estado: " + tunnelStateFilter.getValue().name(),
              () -> tunnelStateFilter.setValue(null)));
    }
    if (tunnelTagFilter != null) {
      tunnelTagFilter
          .getSelectionModel()
          .getSelectedItems()
          .forEach(
              tag ->
                  chips.add(
                      new FilterChip(
                          "Etiqueta: " + tag.name(),
                          () ->
                              tunnelTagFilter
                                  .getSelectionModel()
                                  .clearSelection(tunnelTagFilter.getItems().indexOf(tag)))));
    }
    tunnelFilterChips.getChildren().setAll(chips);
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
    javafx.scene.control.Dialog<ProjectRecord> dialog =
        choiceDialog(
            "Asignar proyecto",
            "Selecciona un proyecto",
            projects.getFirst(),
            projects,
            ProjectRecord::name);
    dialog
        .showAndWait()
        .ifPresent(
            project -> {
              List<EnvironmentRecord> environments = components.environmentRepository().findAll();
              if (environments.isEmpty()) {
                bulkAssignClassification(project.id(), null);
                return;
              }
              javafx.scene.control.Dialog<EnvironmentRecord> environmentDialog =
                  choiceDialog(
                      "Asignar entorno",
                      "Selecciona un entorno",
                      environments.getFirst(),
                      environments,
                      EnvironmentRecord::name);
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
    javafx.scene.control.Dialog<TunnelGroupRecord> dialog =
        choiceDialog(
            "Añadir a grupo",
            "Selecciona un grupo",
            groups.getFirst(),
            groups,
            TunnelGroupRecord::name);
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
    javafx.scene.control.Dialog<TunnelGroupRecord> dialog =
        choiceDialog(
            "Quitar de grupo",
            "Selecciona un grupo",
            groups.getFirst(),
            groups,
            TunnelGroupRecord::name);
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
    password.textProperty().bindBidirectional(visiblePassword.textProperty());
    CheckBox showPassword = new CheckBox("Mostrar contraseña");
    showPassword
        .selectedProperty()
        .addListener(
            (obs, old, selected) -> {
              visiblePassword.setVisible(selected);
              password.setVisible(!selected);
            });
    visiblePassword.setVisible(false);
    StackPane passwordStack = new StackPane(password, visiblePassword);
    passwordStack.getStyleClass().add("password-stack");
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
            passwordStack);
    grid.add(showPassword, 1, 5);
    dialog.getDialogPane().setContent(grid);
    dialog.getDialogPane().setMinWidth(520);
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
        existing == null ? TunnelEditorViewModel.newForm() : tunnelEditor.fromRecord(existing);
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
    TagSelectionControl tagSelection = new TagSelectionControl(tagRecords, initial.tagIds());
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

    Tab primary =
        new Tab(
            "Túnel",
            formGrid(
                "Nombre",
                name,
                UiMessages.get("tunnel.editor.bind"),
                bind,
                "Puerto local",
                localPort,
                "Host destino",
                destinationHost,
                "Puerto destino",
                destinationPort,
                "SSH final",
                finalHost,
                UiMessages.get("tunnel.editor.jump1"),
                jump1,
                UiMessages.get("tunnel.editor.jump2"),
                jump2,
                "Proyecto",
                project,
                "Entorno",
                environment,
                "Etiquetas",
                tagSelection,
                "",
                favorite));
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
    List.of(primary, advanced).forEach(tab -> tab.setClosable(false));
    TabPane tabs = new TabPane(primary, advanced);
    Label validation = new Label();
    validation.getStyleClass().add("error-text");
    Button saveButton = new Button("Guardar");
    Button cancelButton = new Button("Cancelar");
    buttonStyle(saveButton, "primary");
    buttonStyle(cancelButton, "ghost");
    FlowPane actions = flowPane(saveButton, cancelButton);
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
    showTechnicalDialog(
        "Detalles del túnel",
        selected.name() + " — " + runtime.actualState(),
        "Historial de sesión:\n" + events + "\n\nDetalle técnico:\n" + error,
        error);
  }

  private void operateTunnel(TunnelRecord selected, java.util.function.Consumer<UUID> operation) {
    if (selected != null) {
      workers.submit(() -> operation.accept(selected.id()));
      if (toastHost != null) toastHost.show("Operación enviada: " + selected.name(), "success");
    }
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

  private String readableRoute(TunnelRecord tunnel) {
    List<String> hops =
        java.util.stream.Stream.of(tunnel.jump1HostId(), tunnel.jump2HostId(), tunnel.finalHostId())
            .filter(Objects::nonNull)
            .map(this::hostLabel)
            .toList();
    return String.join("  →  ", hops)
        + "  →  "
        + tunnel.destinationHost()
        + ":"
        + tunnel.destinationPort();
  }

  private String hostLabel(UUID id) {
    if (id == null) return "(sin salto)";
    if (hostTable != null) {
      return hostTable.getItems().stream()
          .filter(host -> host.id().equals(id))
          .findFirst()
          .map(host -> host.name() + " (" + host.hostname() + ")")
          .orElse(id.toString());
    }
    return id.toString();
  }

  private static ComboBox<SshHostRecord> hostCombo(List<SshHostRecord> hosts, UUID selected) {
    ComboBox<SshHostRecord> combo = new ComboBox<>(FXCollections.observableArrayList(hosts));
    configureComboPresentation(combo, host -> host.name() + " (" + host.hostname() + ")");
    hosts.stream()
        .filter(host -> host.id().equals(selected))
        .findFirst()
        .ifPresent(combo::setValue);
    return combo;
  }

  private static <T> void configureComboPresentation(
      ComboBox<T> combo, Function<T, String> display) {
    combo.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : display.apply(item));
              }
            });
    combo.setButtonCell(
        new ListCell<>() {
          @Override
          protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : display.apply(item));
            setTooltip(
                empty || item == null
                    ? null
                    : new javafx.scene.control.Tooltip(display.apply(item)));
          }
        });
    combo.setMaxWidth(Double.MAX_VALUE);
  }

  private static <T> javafx.scene.control.Dialog<T> choiceDialog(
      String title, String header, T selected, List<T> values, Function<T, String> display) {
    javafx.scene.control.Dialog<T> dialog = new javafx.scene.control.Dialog<>();
    ComboBox<T> combo = new ComboBox<>(FXCollections.observableArrayList(values));
    configureComboPresentation(combo, display);
    combo.setValue(selected);
    dialog.setTitle(title);
    dialog.setHeaderText(header);
    dialog.getDialogPane().setContent(combo);
    ButtonType ok = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().setAll(ok, ButtonType.CANCEL);
    dialog.setResultConverter(button -> button == ok ? combo.getValue() : null);
    return dialog;
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
      TagSelectionControl tags,
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
        tags.selectedTagIds(),
        favorite.isSelected());
  }

  private static <T> ComboBox<T> classificationCombo(
      List<T> values, UUID selected, Function<T, UUID> id, Function<T, String> name) {
    ComboBox<T> combo = new ComboBox<>(FXCollections.observableArrayList(values));
    configureComboPresentation(combo, name);
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

  private static FlowPane flowPane(Node... children) {
    FlowPane pane = new FlowPane(8, 8);
    pane.getStyleClass().add("action-bar");
    pane.setMaxWidth(Double.MAX_VALUE);
    pane.getChildren().addAll(children);
    return pane;
  }

  private static void buttonStyle(Button button, String variant) {
    button.getStyleClass().add(variant);
  }

  private VBox tagFilterBox() {
    VBox box = new VBox(4, new Label("Etiquetas"), tunnelTagFilter);
    box.getStyleClass().add("tag-filter");
    return box;
  }

  private static GridPane formGrid(Object... values) {
    GridPane grid = new GridPane();
    grid.getStyleClass().add("form-grid");
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(12));
    ColumnConstraints labels = new ColumnConstraints();
    labels.setMinWidth(120);
    labels.setPrefWidth(170);
    ColumnConstraints controls = new ColumnConstraints();
    controls.setHgrow(Priority.ALWAYS);
    controls.setFillWidth(true);
    grid.getColumnConstraints().addAll(labels, controls);
    for (int index = 0; index < values.length; index += 2) {
      grid.add(new Label((String) values[index]), 0, index / 2);
      Node control = (Node) values[index + 1];
      if (control instanceof javafx.scene.layout.Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
      grid.add(control, 1, index / 2);
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
    showTechnicalDialog("Detalles técnicos", "Información para diagnóstico", details, details);
  }

  private void showTechnicalDialog(String title, String header, String content, String copyValue) {
    Alert technical = new Alert(Alert.AlertType.INFORMATION);
    technical.setTitle(title);
    technical.setHeaderText(header);
    TextArea text = new TextArea(content == null ? "" : content);
    text.setEditable(false);
    text.setWrapText(true);
    text.setPrefColumnCount(80);
    text.setPrefRowCount(20);
    text.setMaxWidth(Double.MAX_VALUE);
    text.setMaxHeight(Double.MAX_VALUE);
    text.getStyleClass().add("technical-text");
    technical.getDialogPane().setContent(text);
    technical.getDialogPane().getStyleClass().add("technical-dialog");
    ButtonType close = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
    ButtonType copy = new ButtonType("Copiar", ButtonBar.ButtonData.OTHER);
    technical.getButtonTypes().setAll(close, copy);
    technical.setResizable(true);
    technical.setOnCloseRequest(event -> technical.close());
    technical.setOnShown(
        event -> {
          Window window = technical.getDialogPane().getScene().getWindow();
          Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
          double width = Math.min(760, Math.max(600, bounds.getWidth() * 0.75));
          double height = Math.min(560, Math.max(420, bounds.getHeight() * 0.75));
          window.setWidth(Math.min(width, bounds.getWidth() * 0.95));
          window.setHeight(Math.min(height, bounds.getHeight() * 0.90));
          text.requestFocus();
        });
    technical
        .showAndWait()
        .ifPresent(
            result -> {
              if (result == copy) copyToClipboard(copyValue == null ? "" : copyValue);
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
