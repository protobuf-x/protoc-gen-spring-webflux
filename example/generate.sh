#!/usr/bin/env bash

export JAVA_HOME=$(/System/Library/Frameworks/JavaVM.framework/Versions/A/Commands/java_home -v "1.8")
export PROTOC_GEN=/Users/ar-mac-005/src/github.com/disc99/protoc-gen-spring-webflux/plugin/build/libs

cd ..
./gradlew clean plugin:shadowJar
./gradlew example:generateProto
