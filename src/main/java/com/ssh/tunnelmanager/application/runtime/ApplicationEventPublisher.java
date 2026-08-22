package com.ssh.tunnelmanager.application.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApplicationEventPublisher {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationEventPublisher.class);
  private final List<Consumer<TunnelEvent>> listeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<GroupEvent>> groupListeners = new CopyOnWriteArrayList<>();

  public void subscribe(Consumer<TunnelEvent> listener) {
    listeners.add(listener);
  }

  public void publish(TunnelEvent event) {
    for (Consumer<TunnelEvent> listener : listeners) {
      try {
        listener.accept(event);
      } catch (RuntimeException exception) {
        LOGGER.warn(
            "Listener de evento falló eventType={}", event.getClass().getSimpleName(), exception);
      }
    }
  }

  public void subscribeGroup(Consumer<GroupEvent> listener) {
    groupListeners.add(listener);
  }

  public void publishGroup(GroupEvent event) {
    for (Consumer<GroupEvent> listener : groupListeners) {
      try {
        listener.accept(event);
      } catch (RuntimeException exception) {
        LOGGER.warn(
            "Listener de evento de grupo falló eventType={}",
            event.getClass().getSimpleName(),
            exception);
      }
    }
  }
}
