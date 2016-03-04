package com.rusty;

import com.sun.tools.attach.VirtualMachine;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

/**
 * This program connects to a local Java process and dumps its heap. It uses RMI to call a native linux function and
 * therefore is only supported on linux. It works in a manner similar to VisualVM.
 * <p/>
 * To build the project you need to add a dependency on $JAVA_HOME/lib/tools.jar and make sure it is included when
 * building your artifacts. After building an executable jar (for example heapdumper.jar) you can run it:
 * java -jar heapdumper.jar -pid=12345 -dir=/some/place
 */
public class HeapDumper {
    /**
     * There are two required command line arguments, the location to write the heap dump, and the java pid.
     *
     * @param args command line arguments, specified by -pid=<pid> and -dir=<path>
     */
    public static void main(String[] args) {
        String dir = "", pid = "";
        for (String s : args) {
            if (s.startsWith("-dir=")) {
                dir = s.substring(5).trim();
            } else if (s.startsWith("-pid=")) {
                pid = s.substring(5).trim();
            } else {
                exitWithError("unrecognized command line parameter [" + s + "]");
            }
        }

        // if no dir was supplied exit, otherwise test if it's a valid path
        if ("".equals(dir)) {
            exitWithError("must supply command line parameter to set a write directory: -dir=<path>");
        } else {
            dir = dir.endsWith(File.separator) ? dir : dir + File.separator;
            if (!canWriteToDir(dir)) {
                exitWithError("unable to write to directory [" + dir + "]");
            }
        }

        // if no pid was supplied exit, otherwise make sure it is parseable
        if ("".equals(pid)) {
            exitWithError("must supply command line parameter to set a process id: -pid=<path>");
        } else {
            try {
                Integer.parseInt(pid);
            } catch (NumberFormatException nfe) {
                exitWithError("could not determine process id");
            }
        }

        String date = runSysCommand("date +\"%m%d%y-%H%M%S-%Z\"").replace("\"", "");
        String file = dir + "heapdump_pid-" + pid + "_date-" + date + ".hprof";
        String addr = getJMXConnection(pid);

        if (addr != null) {
            Long start = System.currentTimeMillis();
            dumpHeap(addr, file);
            Long duration = System.currentTimeMillis() - start;
            System.out.println("successfully wrote heap dump to " + file + " in " + (duration / 1000) + " seconds");
            runSysCommand("chmod 644 " + file); // default is 600
        } else {
            exitWithError("could not establish JMX connection");
        }
    }

    /**
     * Make sure the path is a directory and can be written to.
     *
     * @param dir the directory to write the heap dump into
     * @return boolean indicating whether the directory is valid
     */
    private static boolean canWriteToDir(String dir) {
        File test = new File(dir);
        return test != null && test.isDirectory() && test.canWrite();
    }

    /**
     * For serious errors that requires exiting the application.
     *
     * @param msg a description of the error encountered
     */
    private static void exitWithError(String msg) {
        System.err.println("exiting: " + msg);
        System.exit(1);
    }

    /**
     * Runs a system command. It consumes both the stderr and stdout buffers to avoid deadlock. Without fully consuming
     * both buffers it is possible for the Process object to hang on the call to waitFor().
     *
     * @param command the system command to run
     * @return string containing the standard output of the system command
     */
    private static String runSysCommand(String command) {
        String output = "";
        try {
            Process p = Runtime.getRuntime().exec(command);

            StreamEater stderr = new StreamEater(p.getErrorStream());
            stderr.start();
            StreamEater stdout = new StreamEater(p.getInputStream());
            stdout.start();

            int exitValue = p.waitFor();
            stderr.join();
            stdout.join();

            if (exitValue == 0) {
                output = stdout.getOutput();
            } else {
                exitWithError("command [" + command + "] returned status [" + exitValue + "] with error msg [" + stderr.getOutput() + "]");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    /**
     * Dynamically load a management agent into the target JVM and gets its connector address to establish a JMX
     * connection with.
     * <p/>
     * For an example see:
     * http://barecode.org/blog.php/establishing-jmx-connections-to-a
     *
     * @param pid the process id of tomcat
     * @return string containing the jmx connector address to connect to
     */
    private static String getJMXConnection(String pid) {
        String lcaProp = "com.sun.management.jmxremote.localConnectorAddress";
        String localConnectorAddress = null;
        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            String javaHome = vm.getSystemProperties().getProperty("java.home");
            String agentJar = javaHome + File.separator + "lib" + File.separator + "management-agent.jar";
            vm.loadAgent(agentJar, "com.sun.management.jmxremote");
            localConnectorAddress = vm.getAgentProperties().getProperty(lcaProp);
            if (localConnectorAddress == null) {
                // check system properties (IBM compatibility)
                localConnectorAddress = vm.getSystemProperties().getProperty(lcaProp);
            }
            vm.detach();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return localConnectorAddress;
    }

    /**
     * Uses reflection to call the dumpHeap method from HotSpotDiagnosticMXBean to avoid a dependency on rt.jar.
     * <p/>
     * For more information see:
     * http://docs.oracle.com/javase/6/docs/jre/api/management/extension/com/sun/management/HotSpotDiagnosticMXBean.html
     *
     * @param localConnectorAddress the jmx connector address to connect to
     */
    private static void dumpHeap(String localConnectorAddress, String file) {
        try {
            JMXServiceURL url = new JMXServiceURL(localConnectorAddress);
            JMXConnector connector = JMXConnectorFactory.connect(url);
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            Class c = Class.forName("com.sun.management.HotSpotDiagnosticMXBean");
            Method m = c.getMethod("dumpHeap", String.class, boolean.class);
            Object b = ManagementFactory.newPlatformMXBeanProxy(connection, "com.sun.management:type=HotSpotDiagnostic", c);
            m.invoke(b, file, false); // false includes non-live objects too
            connector.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A helper class meant to be used when performing system calls to fully consume an input stream.
     * <p/>
     * For more information see:
     * http://www.javaworld.com/jw-12-2000/jw-1229-traps.html
     */
    private static class StreamEater extends Thread {
        final InputStream is;
        String output;

        public StreamEater(InputStream is) {
            this.is = is;
            this.output = "";
        }

        public String getOutput() {
            return output;
        }

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n"); // keep the new lines
                }
                output = sb.toString().trim(); // but not the last new line, useful when output is a single line
                br.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
