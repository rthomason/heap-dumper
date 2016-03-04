# heap-dumper
A linux / mac command line tool that will dump the heap of any java process. It works in a way similar to how VisualVM performs heap dumps.

There are two ways you can build this program, depending on how portable / easy to distrubte you want the files to be.

1. You can force your IDE to include your JDK's version of tools.jar when building the final artifact. This will let you use a single jar file.
  * To run it: java -jar heapdumper.jar -pid=xxxxx -dir=/tmp
2. Or you can build it with maven and use the supplied bash script to run it. The bash script will find your target machine's version of tools.jar which is more portable and should lead to a smaller jar file.
  * To run it: ./heapdumper.sh -pid=xxxxx -dir=/tmp
