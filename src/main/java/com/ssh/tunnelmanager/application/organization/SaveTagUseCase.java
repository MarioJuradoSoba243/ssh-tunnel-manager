package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.TagRepository;
import com.ssh.tunnelmanager.application.persistence.model.TagRecord;
import com.ssh.tunnelmanager.domain.organization.Tag;

public final class SaveTagUseCase {
  private final TagRepository tags;

  public SaveTagUseCase(TagRepository tags) {
    this.tags = tags;
  }

  public void execute(Tag tag) {
    if (tags.findAll().stream()
        .anyMatch(
            existing ->
                existing.name().equalsIgnoreCase(tag.name()) && !existing.id().equals(tag.id()))) {
      throw new IllegalArgumentException("El nombre de la etiqueta ya está utilizado");
    }
    tags.save(new TagRecord(tag.id(), tag.name()));
  }
}
