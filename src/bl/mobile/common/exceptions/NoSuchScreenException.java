package bl.mobile.common.exceptions;

import org.openqa.selenium.NoSuchElementException;

public class NoSuchScreenException extends NoSuchElementException {
    public NoSuchScreenException(String message) {
        super(message);
    }
}
