package com.ssh.tunnelmanager.ui.shell;

import com.ssh.tunnelmanager.application.persistence.model.TunnelRecord;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/** Data state kept with the cached tunnel view while the shell changes section. */
public final class TunnelViewState {
  private final ObservableList<TunnelRecord> rows = FXCollections.observableArrayList();
  private final FilteredList<TunnelRecord> filteredRows = new FilteredList<>(rows, ignored -> true);

  public ObservableList<TunnelRecord> rows() {
    return rows;
  }

  public FilteredList<TunnelRecord> filteredRows() {
    return filteredRows;
  }

  public void replaceAll(List<TunnelRecord> values) {
    rows.setAll(values);
  }
}
