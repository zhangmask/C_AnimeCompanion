#!/bin/bash
export JAVA_HOME="/d/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/CompanionChat"
./gradlew --no-daemon assembleDebug 2>&1
