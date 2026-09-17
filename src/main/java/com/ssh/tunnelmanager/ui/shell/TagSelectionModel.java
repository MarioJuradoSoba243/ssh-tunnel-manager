package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure selection and local filtering state shared by the tag picker UI. */
final class TagSelectionModel {
  private final List<TagRecord> availableTags;
  private final Set<UUID> availableIds;
  private final Set<UUID> selectedIds;
  private String query = "";

  TagSelectionModel(List<TagRecord> availableTags, Set<UUID> selectedIds) {
    this.availableTags = List.copyOf(availableTags);
    availableIds =
        this.availableTags.stream().map(TagRecord::id).collect(Collectors.toUnmodifiableSet());
    this.selectedIds =
        selectedIds.stream()
            .filter(availableIds::contains)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  List<TagRecord> filteredTags() {
    String normalizedQuery = query.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalizedQuery.isEmpty()) return availableTags;
    return availableTags.stream()
        .filter(tag -> tag.name().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
        .toList();
  }

  void setQuery(String query) {
    this.query = query == null ? "" : query;
  }

  void setSelected(UUID tagId, boolean selected) {
    if (!availableIds.contains(tagId)) return;
    if (selected) selectedIds.add(tagId);
    else selectedIds.remove(tagId);
  }

  Set<UUID> selectedIds() {
    return Set.copyOf(selectedIds);
  }
}
