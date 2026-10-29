#!/bin/bash
# Only bash builtins are required in the JRE image. Docker bounds the whole probe.
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
IFS= read -r -t 3 status <&3
[[ "$status" == 'HTTP/1.1 200 '* ]]
