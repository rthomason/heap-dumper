#!/bin/bash

java -cp "./target/heapdumper.jar:$JAVA_HOME/lib/tools.jar" com.rusty.HeapDumper "$@"
