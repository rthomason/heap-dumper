# heap-dumper
a command line tool that will dump the heap of any java process

Steps to use:
1. build the jar: mvn clean package
2. copy both target/heapdumper.jar and heapdumper.sh to the system you wish to use the tool on.
3. in heapdumper.sh make sure the path to your tools.jar is correct
4. run it, for example: ./heapdumper.sh -pid=xxxxx -dir=/tmp
