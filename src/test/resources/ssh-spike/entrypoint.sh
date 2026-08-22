#!/bin/sh
set -eu

if ! id "$SSH_USER" >/dev/null 2>&1; then
  adduser -D "$SSH_USER"
fi
printf '%s:%s\n' "$SSH_USER" "$SSH_PASSWORD" | chpasswd
rm -f /etc/ssh/ssh_host_*_key /etc/ssh/ssh_host_*_key.pub
ssh-keygen -A
exec /usr/sbin/sshd -D -e
