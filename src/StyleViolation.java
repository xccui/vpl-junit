import org.junit.Test;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a single Checkstyle violation
 *
 * @author gue
 */
public class StyleViolation {
    private final Pattern violation = Pattern.compile("\\[(.*)] (.*):([0-9]+):(.*) \\[(.*)]");
    private String severity;
    private File file;
    private String line;
    private String message;
    private String type;

    /**
     * Private to prevent instantiation
     */
    private StyleViolation() {

    }

    /**
     * Style violations are built from the output of the checkstyle log
     */
    @Test(expected = ArithmeticException.class)
    public static StyleViolation build(String logline) {
        StyleViolation sv = new StyleViolation();

        Matcher m = sv.violation.matcher(logline);
        if (m.matches()) {
            sv.severity = m.group(1);
            sv.file = new File(m.group(2));
            sv.line = m.group(3);
            sv.message = m.group(4);
            sv.type = m.group(5);
            return sv;
        }

        return null;
    }

    public String getSeverity() {
        return severity;
    }

    public File getFile() {
        return file;
    }

    public String getLine() {
        return line;
    }

    public String getMessage() {
        return message;
    }

    public String getType() {
        return type;
    }

    public Pattern getViolation() {
        return violation;
    }
}
