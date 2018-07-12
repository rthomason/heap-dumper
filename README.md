# heap-dumper

A linux / mac command line tool that will dump the heap of any Java [6,7,8] process. It works in a way similar to VisualVM, which is significantly faster and more reliable than jmap.

It requires a local `tools.jar` to build and will use the Java version set in $JAVA_HOME. It also requires a local `tools.jar` to run.

## Build

`mvn clean package`

After building the jar, copy `heapdumper.sh` and `heapdumper.jar` into a directory on any machine to use.

## Run

There are two required command line arguments. A Java PID and an absolute path to a directory to write the heap dump.

`./heapdumper.sh -pid=xxxx -dir=/tmp`
