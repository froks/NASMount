package de.debugco.nasmount;

/**
 * Created by IntelliJ IDEA.
 * User: flo
 * Date: 13.05.2010
 * Time: 17:57:13
 * To change this template use File | Settings | File Templates.
 */
public class FailureException extends Exception {
    public FailureException(String message) {
        super(message);
    }

    public FailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public FailureException(Throwable cause) {
        super(cause);
    }
}
