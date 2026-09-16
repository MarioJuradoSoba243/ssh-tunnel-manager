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
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** A searchable, multi-select tag editor shared by tunnel and group forms. */
final class TagSelectionControl extends VBox {
  private final List<TagChoice> choices;
  private final FilteredList<TagChoice> filteredChoices;
  private final FlowPane selectedTags = new FlowPane(6, 6);

  TagSelectionControl(List<TagRecord> tags, Set<UUID> selectedIds) {
    getStyleClass().add("tag-selection");
    choices =
        tags.stream()
            .map(tag -> new TagChoice(tag, selectedIds.contains(tag.id())))
            .collect(Collectors.toCollection(java.util.ArrayList::new));
    filteredChoices = new FilteredList<>(FXCollections.observableArrayList(choices));

    choices.forEach(
        choice -> choice.selected().addListener((observable, oldValue, value) -> refreshChips()));

    selectedTags.getStyleClass().add("tag-chips");
    selectedTags.setMaxWidth(Double.MAX_VALUE);
    refreshChips();
    Button add = new Button("+ Añadir");
    add.getStyleClass().add("secondary");
    add.setOnAction(event -> showTagPicker());
    getChildren().addAll(new Label("Tags"), selectedTags, add);
  }

  Set<UUID> selectedTagIds() {
    return choices.stream()
        .filter(choice -> choice.selected().get())
        .map(choice -> choice.tag().id())
        .collect(Collectors.toUnmodifiableSet());
  }

  private void refreshChips() {
    selectedTags
        .getChildren()
        .setAll(
            choices.stream().filter(choice -> choice.selected().get()).map(this::chip).toList());
  }

  private HBox chip(TagChoice choice) {
    Label label = new Label(choice.tag().name());
    label.setTooltip(new Tooltip(choice.tag().name()));
    Button remove = new Button("×");
    remove.getStyleClass().add("chip-remove");
    remove.setAccessibleText("Quitar etiqueta " + choice.tag().name());
    remove.setTooltip(new Tooltip("Quitar " + choice.tag().name()));
    remove.setOnAction(event -> choice.selected().set(false));
    HBox chip = new HBox(4, label, remove);
    chip.getStyleClass().add("tag-chip");
    return chip;
  }

  private void showTagPicker() {
    filteredChoices.setPredicate(null);
    Dialog<Void> dialog = new Dialog<>();
    dialog.setTitle("Añadir etiquetas");
    dialog.setHeaderText("Busca y selecciona una o varias etiquetas");
    TextField search = new TextField();
    search.setPromptText("Buscar etiquetas…");
    search.setAccessibleText("Buscar etiquetas");
    ListView<TagChoice> list = new ListView<>(filteredChoices);
    list.getStyleClass().add("tag-list");
    list.setPlaceholder(new Label("No hay etiquetas que coincidan."));
    list.setPrefHeight(260);
    list.setMinHeight(180);
    list.setCellFactory(CheckBoxListCell.forListView(TagChoice::selected));
    search
        .textProperty()
        .addListener(
            (observable, oldValue, value) -> {
              String query = value == null ? "" : value.trim().toLowerCase();
              filteredChoices.setPredicate(
                  choice -> query.isEmpty() || choice.tag().name().toLowerCase().contains(query));
            });
    VBox content = new VBox(8, search, list);
    content.getStyleClass().add("tag-picker");
    dialog.getDialogPane().setContent(content);
    dialog
        .getDialogPane()
        .getButtonTypes()
        .add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
    dialog.setOnShown(event -> search.requestFocus());
    dialog.showAndWait();
    filteredChoices.setPredicate(null);
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
