#!/bin/bash
./stop.sh
sleep 1
export LD_LIBRARY_PATH=/opt/safenet/protecttoolkit7/ptk/lib:$LD_LIBRARY_PATH
nohup java -jar target/file-encryption-1.0.0.jar > app.log 2>&1 &
