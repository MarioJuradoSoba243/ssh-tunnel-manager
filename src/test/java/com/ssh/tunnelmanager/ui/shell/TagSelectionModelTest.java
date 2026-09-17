package com.ssh.tunnelmanager.ui.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagSelectionModelTest {
  @Test
  void supportsMultipleSelectionAndDirectDeselection() {
    TagRecord api = tag("API");
    TagRecord production = tag("Producción");
    TagSelectionModel model = new TagSelectionModel(List.of(api, production), Set.of());

    model.setSelected(api.id(), true);
    model.setSelected(production.id(), true);
    model.setSelected(api.id(), false);

    assertThat(model.selectedIds()).containsExactly(production.id());
  }

  @Test
  void filtersLocallyWithoutDroppingSelectedTags() {
    TagRecord api = tag("API");
    TagRecord database = tag("Database");
    TagRecord production = tag("Producción");
    TagSelectionModel model =
        new TagSelectionModel(List.of(api, database, production), Set.of(production.id()));

    model.setQuery("data");

    assertThat(model.filteredTags()).containsExactly(database);
    assertThat(model.selectedIds()).containsExactly(production.id());

    model.setQuery("");
    assertThat(model.filteredTags()).containsExactly(api, database, production);
    assertThat(model.selectedIds()).containsExactly(production.id());
  }

  @Test
  void matchingIsCaseInsensitiveAndUnknownSelectionsAreIgnored() {
    TagRecord vpn = tag("VPN");
    UUID unknown = UUID.randomUUID();
    TagSelectionModel model = new TagSelectionModel(List.of(vpn), Set.of(unknown));

    model.setQuery("vpn");
    model.setSelected(unknown, true);

    assertThat(model.filteredTags()).containsExactly(vpn);
    assertThat(model.selectedIds()).isEmpty();
  }

  private static TagRecord tag(String name) {
    return new TagRecord(UUID.randomUUID(), name);
  }
}
