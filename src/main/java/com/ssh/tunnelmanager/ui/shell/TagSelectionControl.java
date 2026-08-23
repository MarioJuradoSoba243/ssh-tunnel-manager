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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.FlowPane;
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

    TextField search = new TextField();
    search.setPromptText("Buscar etiquetas");
    search
        .textProperty()
        .addListener(
            (observable, oldValue, value) -> {
              String query = value == null ? "" : value.trim().toLowerCase();
              filteredChoices.setPredicate(
                  choice -> query.isEmpty() || choice.tag().name().toLowerCase().contains(query));
            });

    ListView<TagChoice> list = new ListView<>(filteredChoices);
    list.getStyleClass().add("tag-list");
    list.setPlaceholder(new Label("No hay etiquetas creadas."));
    list.setPrefHeight(150);
    list.setMinHeight(100);
    list.setMaxHeight(240);
    list.setCellFactory(CheckBoxListCell.forListView(TagChoice::selected));
    choices.forEach(
        choice -> choice.selected().addListener((observable, oldValue, value) -> refreshChips()));

    selectedTags.getStyleClass().add("tag-chips");
    refreshChips();
    getChildren().addAll(new Label("Seleccionadas"), selectedTags, search, list);
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
            choices.stream()
                .filter(choice -> choice.selected().get())
                .map(choice -> new Label(choice.tag().name()))
                .peek(label -> label.getStyleClass().add("tag-chip"))
                .toList());
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
