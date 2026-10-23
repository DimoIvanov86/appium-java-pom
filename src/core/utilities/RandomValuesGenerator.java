package core.utilities;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

/**
 * Unique test data (names, numbers, passwords) and timestamps.
 */
public class RandomValuesGenerator {

    public static String getDateTime(String dateTimeFormat) {
        Date dateTime = new Date();
        SimpleDateFormat simpleFormat = new SimpleDateFormat(dateTimeFormat);

        return simpleFormat.format(dateTime);
    }

    public static String getDateTime() {
        return getDateTime("yyyy-MM-dd-HH-mm");
    }

    private static String getRandomNumber(int size) {
        StringBuilder number = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            number.append(random.nextInt(10));
        }

        return number.toString();
    }

    public static String getRandomNumber() {
        return getRandomNumber(10);
    }

    public static @Nonnull String getRandomString(int num) {
        int lowerLimit = 97;
        int upperLimit = 122;
        Random random = new Random();
        StringBuilder word = new StringBuilder(num);
        for (int i = 0; i < num; i++) {
            int nextRandomChar = lowerLimit + (int) (random.nextFloat() * (upperLimit - lowerLimit + 1));
            word.append((char) nextRandomChar);
        }

        return word.toString();
    }

    public static int getRandomNumberBetween(int start, int end) {
        Random random = new Random();
        return random.nextInt((end - start) + 1) + start;
    }
}
