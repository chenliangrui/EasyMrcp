package com.example.easymrcp.utils;

import java.util.Date;
import java.util.Random;

public class SipUtils {
    private static final Random random = new Random((new Date()).getTime());

    public static String getGUID() {
        // counter++;
        // return guidPrefix+counter;
        int r = random.nextInt();
        r = (r < 0) ? 0 - r : r; // generate a positive number
        return Integer.toString(r);
    }
}
