FROM alpine:3.22

RUN apk add --no-cache openssh-server netcat-openbsd \
    && mkdir -p /run/sshd \
    && ssh-keygen -A \
    && printf '%s\\n' \
       'PasswordAuthentication yes' \
       'PermitRootLogin no' \
       'AllowTcpForwarding yes' \
       'GatewayPorts no' \
       > /etc/ssh/sshd_config.d/ssh-spike.conf

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 22
ENTRYPOINT ["/entrypoint.sh"]
