#!/bin/bash
export JAVA_HOME="/d/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
cd /c/Users/72952/OneDrive/Desktop/ui/CompanionChat
./gradlew --no-daemon assembleDebug 2>&1 | tail -30
