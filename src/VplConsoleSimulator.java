import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Simulates console input and output
 *
 * @author gue
 */
public class VplConsoleSimulator {
    private Process process;
    private BufferedReader inReader;
    private BufferedWriter outWriter;
    private BufferedReader errReader;
    private Boolean immideateLogToConsole = false;
    private List<String> consolewindow = new ArrayList<>();

    /**
     * Starts a JVM with the given class. The input and output are redirected, so we can use them
     * with the oher methods of this class.
     *
     * @param classname Class which should be started using java
     * @param args      Optional arguments which are handed over to the started jvm. These are then
     *                  available within the main agruments of the class under test
     */
    public VplConsoleSimulator(String classname, String... args) {

        String jvmLocation;
        if (System.getProperty("os.name").startsWith("Win")) {
            jvmLocation = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe";
        } else {
            jvmLocation = System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        }

        String currentClasspath = System.getProperty("java.class.path").toString();


        try {

            String[] cmdline = (String[]) Stream.concat(Stream.of(jvmLocation,    // start another java vm
                    "-cp", currentClasspath, // set the classpath to the current one
                    classname),            // Class which should be started
                    Stream.of(args))                        // Optional Parameters
                    .toArray(String[]::new);

            this.process = new ProcessBuilder(cmdline).start(); // Go for it !
        } catch (IOException e) {
            throw new IllegalArgumentException("The class " + classname + "could not be found. " + e.getMessage());
        }

        // Get the input and output streams
        this.inReader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()));
        this.outWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), Charset.defaultCharset()));
        this.errReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()));

    }

    /**
     * Internal logging of each input the user enters to the class under test
     */
    private void log_programInput(String s) {
        if (immideateLogToConsole) {
            System.out.println("> out " + s);
        }
        consolewindow.add("> in:  " + s);
    }

    /**
     * Internal logging of each output of the class under test
     *
     * @param s
     */
    private void log_programOutput(String s) {
        if (immideateLogToConsole) {
            System.out.println("> out " + s);
        }
        consolewindow.add("> out: " + s);
    }

    /**
     * Internal logging of each errorof the class under test
     *
     * @param s
     */
    private void log_programmError(String s) {
        if (immideateLogToConsole) {
            System.out.println("> ! " + s);
        }
        consolewindow.add("> ! " + s);
    }

    /**
     * Prints the whole console dialog
     *
     * @return
     */
    public String getFullConsoleIO() {
        //return consolewindow.stream().collect(Collectors.joining("\r\n")).toString();
        return consolewindow.stream().collect(Collectors.joining(System.lineSeparator())).toString();
    }

    /**
     * Returns the full output of the program since the last call
     */
    public List<String> getOutput() throws IOException {
        String line = null;
        List<String> ret = new ArrayList<>();
        while ((line = inReader.readLine()) != null) {
            log_programOutput(line);
            ret.add(line);
        }
        return ret;
    }

    /**
     * Returns the full errorstream of the program since the last call
     */
    public List<String> getError() throws IOException {
        String line = null;
        List<String> ret = new ArrayList<>();
        while ((line = errReader.readLine()) != null) {
            log_programOutput(line);
            ret.add(line);
        }
        return ret;
    }

    /**
     * Reads the given stream till the next line is not empty
     *
     * @param br
     * @return
     * @throws IOException
     */
    private String getNextNonEmptyLine(BufferedReader br) throws IOException {
        String line = null;
        while ((line = br.readLine()) != null) {
            if (br == this.inReader) {
                log_programOutput(line);
            } else {
                log_programmError(line);
            }

            if (line.length() > 0) {
                return (line);
            }
        }

        return null;
    }

    /**
     * Convinience function: Gives the next Line of the programs stdout which is not an empty line
     *
     * @return
     * @throws IOException
     */
    public String getNextNonEmptyOutputLine() throws IOException {
        return getNextNonEmptyLine(this.inReader);
    }


    /**
     * Skips input till the String is found. This string is consumed - so anything after this string can
     * be consumed with getOutput. If the String is not found then it reads up all remaining lines.
     */
    public void skipOutputTill(Predicate<String> condition) throws IOException {
        String line = null;
        while ((line = inReader.readLine()) != null) {
            log_programOutput(line);
            if (condition.test(line)) {
                return;
            }
        }
    }

    /**
     * Fetches the full content and asserts the Strings to be containing in the output.
     * Auto generates an error message containing the fullConsoleIO and the expected content
     */
    public void assertOutputContains(String... content) throws IOException {
        assertOutputContains(true, content);
    }

    /**
     * Fetches the full content and asserts the Strings to be containing in the output.
     *
     * @param content              all string have to in the output
     * @param generateErrorMessage if true auto generates an error message
     *                             containing the fullConsoleIO and the expected content
     * @throws IOException
     */
    public void assertOutputContains(boolean generateErrorMessage, String... content) throws IOException {
        boolean expectOutputOk = this.expectOutputContains(content);
        if (generateErrorMessage) {
            String errorMessage = System.lineSeparator() + "> ***Error producing console log***" + System.lineSeparator() +
                    this.getFullConsoleIO() + System.lineSeparator() + ">  ***Last output line should contain: ";
            for (String string : content) {
                errorMessage += "\"" + string + "\"";
            }

            errorMessage += "***" + System.lineSeparator() + " >";

            assertTrue(errorMessage, expectOutputOk);
        } else {
            assertTrue(expectOutputOk);
        }
    }

    /**
     * Fetches the full content and asserts the Strings to be containing in the output.
     * Auto generates an error message containing the fullConsoleIO and the expected content
     */
    public void assertOutput(String expectedValue) throws IOException {
        assertOutput(true, expectedValue);
    }

    /**
     * Fetches the full output and asserts the String to be at the end of the output.
     */
    public void assertOutput(boolean generateErrorMessage, String expectedValue) throws IOException {
        boolean result = expectOutput(expectedValue);

        if (generateErrorMessage) {
            String errorMessage = System.lineSeparator() + "> ***Error producing console log***" + System.lineSeparator() +
                    getFullConsoleIO() + System.lineSeparator() + "> Last output line should end with: " +
                    "\"" + expectedValue + "\"" + System.lineSeparator() + " >";

            assertTrue(errorMessage, result);
        } else {
            assertTrue(result);
        }
    }

    /**
     * Fetches the full output and test the String with the condition with automatic assertion.
     */
    public void assertOutput(Predicate<String> condition) throws IOException {
        boolean result = expectOutput(condition);
        assertTrue(result);
    }

    /**
     * Fetches the full output and test the String with the condition with automatic assertion.
     * Generates an error message from getFullConsoleIO with errorMessage lambda expression.
     */
    public void assertOutput(Predicate<String> condition, Function<String, String> errorMessage) throws IOException {
        boolean result = expectOutput(condition);
        String consoleIo = getFullConsoleIO();
        assertTrue(errorMessage.apply(consoleIo), result);
    }

    /**
     * Fetches the full output and test the String with the condition.
     */
    public boolean expectOutput(Predicate<String> condition) throws IOException {
        String line = this.getNextNonEmptyOutputLine();
        if (line == null) {
            String errorMessage = System.lineSeparator() + "> ***Error producing console log***" + System.lineSeparator() +
                    getFullConsoleIO() + System.lineSeparator() + "***> Last output line is empty ***";
            fail(errorMessage);
        }
        boolean result = condition.test(line);
        return result;
    }


    /**
     * Convenience function: Expects the last line to be the exact the following value
     */
    public boolean expectOutput(String expectedValue) throws IOException {
        return this.expectOutput(line -> line.equals(expectedValue));
    }

    /**
     * Convenience function: Expects the Strings to be containing in the output.
     */
    public boolean expectOutputContains(String... content) throws IOException {
        return this.expectOutput(line ->
        {
            for (String string : content) {
                if (!line.contains(string)) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Fetches the full output and expects the String to be at the end of the output.
     */
    public void expectError(Predicate<String> condition) throws IOException {
        String line = this.getNextNonEmptyLine(this.errReader);
        if (line == null) {
            fail("Line is null");
        }

        if (!condition.test(line)) {
            fail("Condition not met: " + condition);
        }
    }

    /**
     * Enters a single line into the virtual console. An system dependent line separator
     * is added automatically.
     */
    public void enterLine(String string) throws IOException {
        this.enter(string + System.lineSeparator());
    }

    /**
     * Enters the given text into the virtual console. Use with caution.
     * Attention: If you are using this within a dialog, then this function may block your execution.
     */
    public void enter(String string) throws IOException {
        log_programInput(string.trim());
        outWriter.write(string);
        outWriter.flush();
    }

    /**
     * Waits for the process to exit and returns the exit value
     */
    public Integer getExitValue() throws InterruptedException {
        if (process.isAlive()) {
            process.waitFor();
        }
        return process.exitValue();
    }

    /**
     * Kills the application forcibly
     */
    public void kill() {
        process.destroyForcibly();
    }
}
 