#!/bin/bash
PID_FILE="application.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "PID file not found. Is the application running?"
    # Fallback to finding by name if PID file is missing, but careful not to kill other things
    PIDS=$(pgrep -f "file-encryption-1.0.0.jar")
    if [ -n "$PIDS" ]; then
        echo "Found running process(es) without PID file: $PIDS"
        kill $PIDS
        echo "Stopped process(es)."
    else
        echo "No running application found."
    fi
    exit 0
fi

PID=$(cat "$PID_FILE")
if ps -p $PID > /dev/null; then
    echo "Stopping application (PID: $PID)..."
    kill $PID
    rm "$PID_FILE"
    echo "Application stopped."
else
    echo "Process $PID not found. Cleaning up PID file."
    rm "$PID_FILE"
fi
