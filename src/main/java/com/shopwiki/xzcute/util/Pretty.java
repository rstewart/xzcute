package com.shopwiki.xzcute.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Static methods for pretty-printing (i.e. human-readable)
 *
 * @owner rstewart
 */
public class Pretty {

    public static String bytes(double n) {
        return metric(n, 1024);
    }

    public static String metric(double n) {
        return metric(n, 1000);
    }

    public static String metric(double n, int multiple) {
        if (n < 0)
            return "-" + bytes(Math.abs(n));

        if (n < multiple)
            return round(n);

        n /= multiple;
        if (n < multiple)
            return round(n) + "K";

        n /= multiple;
        if (n < multiple)
            return round(n) + "M";

        n /= multiple;
        return round(n) + "G";
    }

    public static String time(long millis) {
        if (millis < 0) 
            return "-" + time(Math.abs(millis));

        if (millis < 1000)
            return millis + " millis";

        double seconds = (double)millis / 1000;
        if (seconds < 60)
            return round(seconds) + " seconds";

        double minutes = seconds / 60;
        if (minutes < 60)
            return round(minutes) + " minutes";

        double hours = minutes / 60;
        if (hours < 24)
            return round(hours) + " hours";

        double days = hours / 24;
        return round(days) + " days";
    }

    public static String round(double d) {
        if (d < 1) {
            return round(d, 3);
        }

        if (d < 10) {
            return round(d, 2);
        }

        if (d < 100) {
            return round(d, 1);
        }

        return round(d, 0);
    }

    public static String round(double d, int decimalPlaces) {
        double tens = Math.pow(10, decimalPlaces);
        //return FastMath.round(d * tens) / tens;
        d = Math.round(d * tens) / tens; // TODO: Use FastMath.round ???
        return String.valueOf(d);
    }

    private static final ThreadLocal<NumberFormat> numberFormat = new ThreadLocal<NumberFormat>() {
        @Override
        protected NumberFormat initialValue() {
            return new DecimalFormat();
        }
    };

    public static final String comma(long n) {
        return numberFormat.get().format(n);
    }

    public static final String comma(double n) {
        return numberFormat.get().format(n);
    }
}
