#!/bin/sh
set -eu

adduser -D "$SSH_USER"
printf '%s:%s\\n' "$SSH_USER" "$SSH_PASSWORD" | chpasswd
exec /usr/sbin/sshd -D -e
