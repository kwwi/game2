#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

LOG_DIR="${ROOT_DIR}/logs"
PID_DIR="${ROOT_DIR}/.pids"
mkdir -p "${LOG_DIR}" "${PID_DIR}"

is_listening() {
  local port="$1"
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
}

start_backend() {
  if is_listening "${BACKEND_PORT}"; then
    echo "[backend] already running on port ${BACKEND_PORT}"
    return 0
  fi

  echo "[backend] starting on port ${BACKEND_PORT}..."

  local jar="${ROOT_DIR}/target/game2-1.0.0-SNAPSHOT.jar"
  if [[ ! -f "${jar}" ]]; then
    echo "[backend] jar not found, building..."
    (cd "${ROOT_DIR}" && mvn -DskipTests clean package)
  fi

  # Prefer current machine JDK; if JAVA_HOME is unset but JDK 17 exists, use it.
  if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
      export JAVA_HOME="$("/usr/libexec/java_home" -v 17)"
      export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
  fi

  nohup java -jar "${jar}" \
    >"${LOG_DIR}/backend.log" 2>&1 &
  echo $! >"${PID_DIR}/backend.pid"

  echo "[backend] started (pid=$(cat "${PID_DIR}/backend.pid")), logs: ${LOG_DIR}/backend.log"
}

start_frontend() {
  if is_listening "${FRONTEND_PORT}"; then
    echo "[frontend] already running on port ${FRONTEND_PORT}"
    return 0
  fi

  echo "[frontend] starting on port ${FRONTEND_PORT}..."

  if [[ ! -d "${ROOT_DIR}/frontend/node_modules" ]]; then
    echo "[frontend] node_modules not found, installing..."
    (cd "${ROOT_DIR}/frontend" && npm install)
  fi

  nohup npm run dev -- --host 127.0.0.1 --port "${FRONTEND_PORT}" \
    >"${LOG_DIR}/frontend.log" 2>&1 &
  echo $! >"${PID_DIR}/frontend.pid"

  echo "[frontend] started (pid=$(cat "${PID_DIR}/frontend.pid")), logs: ${LOG_DIR}/frontend.log"
}

start_backend
start_frontend

echo
echo "Frontend: http://127.0.0.1:${FRONTEND_PORT}/"
echo "Backend : http://127.0.0.1:${BACKEND_PORT}/"

