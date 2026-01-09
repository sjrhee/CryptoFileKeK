#!/bin/bash
APP_NAME="file-encryption-1.0.0.jar"
JAR_PATH="target/$APP_NAME"
PID_FILE="application.pid"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Jar file not found at $JAR_PATH. Please run ./build.sh first."
    exit 1
fi

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null; then
        echo "Application is already running (PID: $PID)."
        exit 1
    else
        rm "$PID_FILE"
    fi
fi

echo "Starting application..."
nohup java -jar "$JAR_PATH" > app.log 2>&1 &
PID=$!
echo $PID > "$PID_FILE"
echo "Application started with PID: $PID"
