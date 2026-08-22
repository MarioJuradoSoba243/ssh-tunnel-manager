package com.ssh.tunnelmanager.application.organization;

import com.ssh.tunnelmanager.application.persistence.TagRepository;
import java.util.UUID;

public final class DeleteTagUseCase {
  private final TagRepository tags;
  private final ClassificationUsageService usage;

  public DeleteTagUseCase(TagRepository tags, ClassificationUsageService usage) {
    this.tags = tags;
    this.usage = usage;
  }

  public void execute(UUID id, boolean confirmed) {
    ClassificationUsage current = usage.tag(id);
    if (current.total() > 0 && !confirmed) throw new ClassificationInUseException(id, current);
    tags.delete(id);
  }
}
