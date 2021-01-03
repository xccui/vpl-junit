import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Tests a complete JUnit file.
 *
 * @author hg
 */
public class VplJUnitTester extends RunListener {

    private final Pattern POINT_REGEX = Pattern.compile(".*_(\\d+)P.*");

    private final Map<String, Throwable> points = new LinkedHashMap<>();
    private final Map<String, List<StyleViolation>> deductions = new LinkedHashMap<>();

    /**
     * Runs All JUnit Testcases with the annotation {@see VplTestcase} of all given classes.
     *
     * @param args Classes to run the tests against
     */
    public static void main(String[] args) throws ClassNotFoundException, IOException, InterruptedException {

        VplJUnitTester testSuite = new VplJUnitTester();
        List<String> classesToRun = new ArrayList<>();

        // STEP 1: Check for files that should be tested.

        // No args -> Check all Classes in the current directory
        if (args.length == 0) {
            // Look for classes in the current directory
            classesToRun.addAll(findTestClasses());

            // Sanity check
            if (classesToRun.isEmpty()) {
                System.out.println("There are no classes in the directory " + Paths.get(".").toAbsolutePath() + " which could be tested !");
                System.out.println(" Option A: Hand over the classes which should be tested as arguments to the jar");
                System.out.println(" Option B: Ensure that your test classes names are ended with  'Tests'  (eg. SimpleTests)");
                System.out.println(" Or have a look into the documentation: https://github.com/bytebang/vpl-junit");
                System.exit(-1);
            }
        } else {
            classesToRun.addAll(Arrays.asList(args));
        }

        // STEP 2: Run tests for all classes in the arguments.
        System.out.println("Running JUnit tests");
        JUnitCore core = new JUnitCore();
        core.addListener(testSuite);

        for (String classname : classesToRun) {
            System.out.println("\t" + classname);
            core.run(Class.forName(classname));
        }


        // STEP 3: Run style checks against the source files.
        System.out.println("Running checkstyle");
        List<String> styleChecks = findStyleChecks();
        List<File> sourceFiles = findSourceFiles();
        if (!styleChecks.isEmpty() && !sourceFiles.isEmpty()) {
            if (Objects.requireNonNull(CheckstyleRunner.getCheckstyleExecutable()).exists()) {
                for (String check : styleChecks) {
                    System.out.println("\tCheck " + (new File(check)).getName() + " against " + sourceFiles.toString());
                    List<StyleViolation> violations = CheckstyleRunner.run(check, sourceFiles);
                    testSuite.deductions.put(check, violations);
                }
            } else {
                System.out.println("Comment :=>> Cannot check for style violations because checkstyle was not found.");
            }
        }

        // STEP 4: Summary for JUnit.
        int totalPoints = 0;
        for (String testName : testSuite.points.keySet()) {
            Throwable t = testSuite.points.get(testName);
            int points = testSuite.extractPointsFromTestName(testName);

            // Testcase without points
            if (points <= 0) {
                continue;
            }

            // No Exception -> Test has succeeded
            if (null == t) {
                totalPoints += points;
                System.out.println("Comment :=>>\uD83D\uDE04 " + testName + " SUCCESS -> You get *" + points + "* points!");
            } else {
                String message = t.getMessage() != null ? t.getMessage() : "No message";
                String[] lines = message.split(System.lineSeparator());
                System.out.println("Comment :=>>\uD83D\uDE2D " + testName + " FAILED!!!");
                System.out.println("<|--");
                for (String line : lines) {
                    System.out.println(">" + line);
                }
                System.out.println("--|>");
            }
        }

        // STEP 5: Summary for checkstyle
        for (String check : testSuite.deductions.keySet()) {
            Integer max_deduction = testSuite.getDeductionsForCheckName(check);
            List<StyleViolation> violations = testSuite.deductions.get(check)
                    .stream()
                    .filter(sv -> sv.getSeverity().equalsIgnoreCase("WARN"))
                    .collect(Collectors.toList());

            int drain = Math.min(max_deduction, violations.size());
            String checkName = (new File(check)).getName();
            if (drain == 0) {
                System.out.println("Comment :=>> " + checkName + " ... no violations");

                Map<String, List<StyleViolation>> otherMessages = testSuite.deductions.get(check)
                        .stream()
                        .filter(sv -> !sv.getSeverity().equalsIgnoreCase("WARN"))
                        .collect(Collectors.groupingBy(StyleViolation::getSeverity));

                if (!otherMessages.isEmpty()) {
                    System.out.println("<|--");
                    for (String severity : otherMessages.keySet()) {
                        List<StyleViolation> sv = otherMessages.get(severity);
                        System.out.println("Messages of type '" + severity + "' (not counted as violations)");
                        for (StyleViolation v : sv) {
                            System.out.println("        o " + v.getFile().getName() + ":" + v.getLine() + " -> " + v.getMessage());
                        }
                    }
                    System.out.println("--|>");
                }
                continue;
            }

            System.out.println("Comment :=>> " + checkName + " ... -" + drain + " points because of " + violations.size() + " "
                    + (violations.size() == 1 ? "violation" : "violations"));

            // Reduce the points
            totalPoints = totalPoints - drain;

            // Inform the user
            Map<String, List<StyleViolation>> violationsPerType = violations.stream()
                    .collect(Collectors.groupingBy(StyleViolation::getType));

            // Give the user a hint of what went wrong
            System.out.println("<|--");
            for (String violationType : violationsPerType.keySet()) {
                List<StyleViolation> sv = violationsPerType.get(violationType);
                System.out.println(" *** " + violationType + " (" + sv.size() + " " + (violations.size() == 1 ? "violation" : "violations") + ") ***");

                for (StyleViolation v : sv) {
                    System.out.println("        o " + v.getFile().getName() + ":" + v.getLine() + " -> " + v.getMessage());
                }
            }
            System.out.println("--|>");

        }
        System.out.println("\nGrade :=>> " + Math.max(totalPoints, 0));
    }

    /**
     * Searches checkstyle_files.
     */
    private static List<String> findStyleChecks() {
        List<String> foundChecks = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "checkstyle*.xml")) {
            for (Path entry : stream) {
                foundChecks.add(entry.toAbsolutePath().normalize().toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return foundChecks;
    }

    /**
     * Searches checkstyle_files.
     */
    private static List<File> findSourceFiles() {
        List<File> foundSources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.java")) {
            for (Path entry : stream) {
                // Ignore everything that looks like a JUnit-test
                if (!entry.getFileName().toString().matches(".*[Tt]est[s]?.java")) {
                    foundSources.add(entry.toFile());
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return foundSources;
    }

    /**
     * Searches Classes which look like test classes in the directory
     */
    public static List<String> findTestClasses() {
        List<String> foundClasses = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.class")) {
            for (Path entry : stream) {
                String classFilename = entry.getFileName().toString();
                if (classFilename.matches(".*[Tt]est[s]?.class")) {
                    foundClasses.add(classFilename.substring(0, classFilename.lastIndexOf(".class")));
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return foundClasses;
    }

    /**
     * Gives minus points for the check
     */
    private Integer getDeductionsForCheckName(String check) {
        Pattern p = Pattern.compile(".*-([0-9]+)P.xml");

        Matcher m = p.matcher(check);

        if (m.matches()) {
            String points = m.group(1);
            return Integer.parseInt(points);
        }

        return Integer.MAX_VALUE;
    }

    /**
     * Returns the Points for a given function name.
     * If the function contains no hint for points then -1 is returned.
     */
    public int extractPointsFromTestName(String functionName) {
        Matcher m = POINT_REGEX.matcher(functionName);
        if (m.matches()) {
            String points = m.group(1);
            return Integer.parseInt(points);
        }
        return -1;
    }

    /**
     * Adds the points of the current test to the total sum of points
     */
    @Override
    public void testFinished(Description description) {
        String methodName = description.getTestClass().getName() + "." + description.getMethodName();
        this.points.putIfAbsent(methodName, null);
    }

    /**
     * If the test fails, then we subtract the points from the total sum
     */
    @Override
    public void testFailure(Failure failure) {
        String methodName = failure.getDescription().getTestClass().getName() + "." + failure.getDescription().getMethodName();
        this.points.putIfAbsent(methodName, failure.getException());
    }
}
