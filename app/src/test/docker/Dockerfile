# SSH test target: sshd + tmux. User "test" logs in with the "none" method, like Tailscale SSH.
# SshServer (unit tests) starts and removes it. By hand, from the repo root:
#   docker build -t muxmote-sshd app/src/test/docker
#   docker run -d --rm --name muxmote-sshd -p 127.0.0.1:22022:22 muxmote-sshd
#   docker rm -f muxmote-sshd
# App host: 127.0.0.1:22022 (10.0.2.2:22022 from the emulator), user test.
FROM alpine:3.22
RUN apk add --no-cache openssh-server tmux dash-binsh \
  && ssh-keygen -A \
  && adduser -D test && passwd -d test \
  && printf 'PasswordAuthentication yes\nPermitEmptyPasswords yes\n' >> /etc/ssh/sshd_config
EXPOSE 22
CMD ["/usr/sbin/sshd", "-D", "-e"]
