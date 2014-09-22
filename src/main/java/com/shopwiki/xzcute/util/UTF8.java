package com.shopwiki.xzcute.util;

import java.io.PrintStream;

/**
 * Print String to STDOUT / STDERR in UTF-8.
 */
public class UTF8 {

    public static final PrintStream out;
    public static final PrintStream err;

    static {
        try {
            out = new PrintStream(System.out, true, "utf-8");
            err = new PrintStream(System.err, true, "utf-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
