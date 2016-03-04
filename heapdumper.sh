#!/bin/bash

java -cp "heapdumper.jar:$JAVA_HOME/lib/tools.jar" com.rusty.HeapDumper "$@"
