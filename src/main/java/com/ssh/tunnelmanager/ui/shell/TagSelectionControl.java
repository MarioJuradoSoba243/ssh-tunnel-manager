package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

/** A compact, searchable, multi-select tag picker shared by tunnel and group forms. */
final class TagSelectionControl extends VBox {
  private final TagSelectionModel model;
  private final List<TagChoice> choices;
  private final FilteredList<TagChoice> filteredChoices;
  private final FlowPane selectedTags = new FlowPane(6, 6);
  private final Button add = new Button("+ Añadir");

  TagSelectionControl(List<TagRecord> tags, Set<UUID> selectedIds) {
    getStyleClass().add("tag-selection");
    model = new TagSelectionModel(tags, selectedIds);
    choices =
        tags.stream()
            .map(tag -> new TagChoice(tag, selectedIds.contains(tag.id())))
            .collect(Collectors.toCollection(java.util.ArrayList::new));
    filteredChoices = new FilteredList<>(FXCollections.observableArrayList(choices));

    choices.forEach(
        choice ->
            choice
                .selected()
                .addListener(
                    (observable, oldValue, value) -> {
                      model.setSelected(choice.tag().id(), value);
                      refreshChips();
                    }));

    selectedTags.getStyleClass().add("tag-chips");
    selectedTags.setMaxWidth(Double.MAX_VALUE);
    selectedTags.setFocusTraversable(false);
    add.getStyleClass().add("secondary");
    add.setOnAction(event -> toggleTagPicker(add));
    refreshChips();
    getChildren().addAll(new Label("Etiquetas"), selectedTags);
  }

  Set<UUID> selectedTagIds() {
    return model.selectedIds();
  }

  private void refreshChips() {
    List<? extends Node> chips =
        choices.stream().filter(choice -> choice.selected().get()).map(this::chip).toList();
    selectedTags.getChildren().setAll(chips);
    selectedTags.getChildren().add(add);
  }

  private HBox chip(TagChoice choice) {
    Label label = new Label(choice.tag().name());
    label.setMaxWidth(220);
    label.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
    label.setTooltip(new Tooltip(choice.tag().name()));
    Button remove = new Button("×");
    remove.getStyleClass().add("chip-remove");
    remove.setAccessibleText("Quitar etiqueta " + choice.tag().name());
    remove.setTooltip(new Tooltip("Quitar " + choice.tag().name()));
    remove.setOnAction(event -> choice.selected().set(false));
    HBox chip = new HBox(4, label, remove);
    chip.setAlignment(Pos.CENTER_LEFT);
    chip.getStyleClass().add("tag-chip");
    return chip;
  }

  private void toggleTagPicker(Button owner) {
    Popup existing = (Popup) owner.getProperties().get(TagSelectionControl.class);
    if (existing != null && existing.isShowing()) {
      existing.hide();
      owner.getProperties().remove(TagSelectionControl.class);
      return;
    }

    model.setQuery("");
    filteredChoices.setPredicate(null);
    Popup popup = createTagPicker();
    owner.getProperties().put(TagSelectionControl.class, popup);
    popup.setOnHidden(
        event -> {
          model.setQuery("");
          filteredChoices.setPredicate(null);
          owner.getProperties().remove(TagSelectionControl.class);
        });
    javafx.geometry.Point2D location = owner.localToScreen(0, owner.getHeight());
    popup.show(owner, location.getX(), location.getY() + 6);
  }

  private Popup createTagPicker() {
    TextField search = new TextField();
    search.setPromptText("Buscar etiquetas…");
    search.setAccessibleText("Buscar etiquetas");
    search.setMaxWidth(Double.MAX_VALUE);

    ListView<TagChoice> list = new ListView<>(filteredChoices);
    list.getStyleClass().add("tag-list");
    list.setPlaceholder(new Label("No hay etiquetas que coincidan."));
    list.setPrefHeight(240);
    list.setMinHeight(150);
    list.setMaxHeight(360);
    list.setCellFactory(
        ignored ->
            new ListCell<>() {
              @Override
              protected void updateItem(TagChoice item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  setGraphic(null);
                  return;
                }
                CheckBox checkBox = new CheckBox(item.tag().name());
                checkBox.setMaxWidth(Double.MAX_VALUE);
                checkBox.setTooltip(new Tooltip(item.tag().name()));
                checkBox.selectedProperty().bindBidirectional(item.selected());
                setGraphic(checkBox);
                setText(null);
              }
            });
    list.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          if ((event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER)
              && list.getSelectionModel().getSelectedItem() != null) {
            TagChoice choice = list.getSelectionModel().getSelectedItem();
            choice.selected().set(!choice.selected().get());
            event.consume();
          }
        });
    search
        .textProperty()
        .addListener(
            (observable, oldValue, value) -> {
              model.setQuery(value);
              filteredChoices.setPredicate(
                  choice ->
                      model.filteredTags().stream()
                          .anyMatch(tag -> tag.id().equals(choice.tag().id())));
            });
    search.addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          if (event.getCode() == KeyCode.TAB && !event.isShiftDown()) {
            list.requestFocus();
            if (!list.getItems().isEmpty()) list.getSelectionModel().select(0);
            event.consume();
          }
        });

    Label selectedCount = new Label();
    Runnable updateCount =
        () -> selectedCount.setText(model.selectedIds().size() + " seleccionadas");
    choices.forEach(
        choice -> choice.selected().addListener((observable, old, value) -> updateCount.run()));
    updateCount.run();

    Button close = new Button("Cerrar");
    close.getStyleClass().add("ghost");
    close.setOnAction(event -> ((Popup) close.getScene().getWindow()).hide());
    HBox footer = new HBox(8, selectedCount, close);
    footer.setAlignment(Pos.CENTER_RIGHT);
    HBox.setHgrow(selectedCount, Priority.ALWAYS);

    VBox content = new VBox(8, new Label("Etiquetas"), search, list, footer);
    content.getStyleClass().add("tag-picker");
    content.setFillWidth(true);
    Popup popup = new Popup();
    popup.setAutoHide(true);
    popup.setHideOnEscape(true);
    popup.setAutoFix(true);
    popup.getContent().add(content);
    popup.setOnShown(event -> search.requestFocus());
    return popup;
  }

  private record TagChoice(TagRecord tag, BooleanProperty selected) {
    private TagChoice(TagRecord tag, boolean selected) {
      this(tag, new SimpleBooleanProperty(selected));
    }

    @Override
    public String toString() {
      return tag.name();
    }
  }
}
