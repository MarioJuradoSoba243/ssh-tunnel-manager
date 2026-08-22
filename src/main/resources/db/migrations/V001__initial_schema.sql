CREATE TABLE IF NOT EXISTS schema_version (
  version INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  applied_at TEXT NOT NULL
);

CREATE TABLE credential (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  password TEXT NOT NULL
);

CREATE TABLE ssh_host (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  hostname TEXT NOT NULL,
  ssh_port INTEGER NOT NULL DEFAULT 22 CHECK (ssh_port BETWEEN 1 AND 65535),
  credential_id TEXT NOT NULL REFERENCES credential(id) ON DELETE RESTRICT
);

CREATE TABLE project (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE environment (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE tag (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE tunnel (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  bind_address TEXT NOT NULL DEFAULT '127.0.0.1',
  local_port INTEGER NOT NULL CHECK (local_port BETWEEN 1 AND 65535),
  destination_host TEXT NOT NULL,
  destination_port INTEGER NOT NULL CHECK (destination_port BETWEEN 1 AND 65535),
  final_host_id TEXT NOT NULL REFERENCES ssh_host(id) ON DELETE RESTRICT,
  jump1_host_id TEXT REFERENCES ssh_host(id) ON DELETE RESTRICT,
  jump2_host_id TEXT REFERENCES ssh_host(id) ON DELETE RESTRICT,
  keep_alive_override_seconds INTEGER,
  connection_timeout_override_seconds INTEGER,
  retry_count_override INTEGER,
  project_id TEXT REFERENCES project(id) ON DELETE SET NULL,
  environment_id TEXT REFERENCES environment(id) ON DELETE SET NULL,
  is_favorite INTEGER NOT NULL DEFAULT 0 CHECK (is_favorite IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE tunnel_ssh_option (
  tunnel_id TEXT NOT NULL REFERENCES tunnel(id) ON DELETE CASCADE,
  option_key TEXT NOT NULL,
  option_value TEXT NOT NULL,
  PRIMARY KEY (tunnel_id, option_key)
);

CREATE TABLE tunnel_group (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  project_id TEXT REFERENCES project(id) ON DELETE SET NULL,
  environment_id TEXT REFERENCES environment(id) ON DELETE SET NULL,
  is_favorite INTEGER NOT NULL DEFAULT 0 CHECK (is_favorite IN (0, 1))
);

CREATE TABLE tunnel_group_member (
  group_id TEXT NOT NULL REFERENCES tunnel_group(id) ON DELETE CASCADE,
  tunnel_id TEXT NOT NULL REFERENCES tunnel(id) ON DELETE CASCADE,
  PRIMARY KEY (group_id, tunnel_id)
);

CREATE TABLE tunnel_tag (
  tunnel_id TEXT NOT NULL REFERENCES tunnel(id) ON DELETE CASCADE,
  tag_id TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
  PRIMARY KEY (tunnel_id, tag_id)
);

CREATE TABLE group_tag (
  group_id TEXT NOT NULL REFERENCES tunnel_group(id) ON DELETE CASCADE,
  tag_id TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
  PRIMARY KEY (group_id, tag_id)
);

CREATE TABLE trusted_host_key (
  id TEXT PRIMARY KEY,
  hostname TEXT NOT NULL,
  ssh_port INTEGER NOT NULL CHECK (ssh_port BETWEEN 1 AND 65535),
  key_algorithm TEXT NOT NULL,
  public_key TEXT NOT NULL,
  fingerprint_sha256 TEXT NOT NULL,
  approved_at TEXT NOT NULL,
  UNIQUE (hostname, ssh_port)
);

CREATE TABLE recent_item (
  item_type TEXT NOT NULL CHECK (item_type IN ('TUNNEL', 'GROUP')),
  item_id TEXT NOT NULL,
  last_started_at TEXT NOT NULL,
  PRIMARY KEY (item_type, item_id)
);

CREATE TABLE application_setting (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

CREATE TABLE keyboard_shortcut (
  action TEXT PRIMARY KEY,
  shortcut TEXT NOT NULL
);
