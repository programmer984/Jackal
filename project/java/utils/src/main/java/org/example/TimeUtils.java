package org.example;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TimeUtils {
    public static long nowMs() {
        return System.currentTimeMillis();
    }


    public static boolean elapsedSeconds(int amountSeconds, long fromTsMs) {
        return elapsed(amountSeconds * 1000, fromTsMs);
    }

    public static boolean elapsed(int ms, long from) {
        return nowMs() - ms > from;
    }

    public static long getElapsedSeconds(long start) {
        return getElapsedMs(start) / 1000;
    }

    public static long getElapsedMs(long start) {
        return nowMs() - start;
    }



}
