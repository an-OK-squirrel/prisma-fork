package com.puzzletimer.util;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

import com.puzzletimer.models.Solution;

public class SolutionUtils {
    public static String formatSeconds(long time) {
        if (time == Long.MAX_VALUE) {
            return "DNF";
        }

        String sign = "";
        if (time < 0) {
            sign = "-";
            time = -time;
        }

        time = (time + 5) / 10;

        long seconds = time / 100;
        long centiseconds = time % 100;

        return sign +
               seconds + "." +
               (centiseconds < 10 ? "0" + centiseconds : centiseconds);
    }

    public static String formatMinutes(long time) {
        if (time == Long.MAX_VALUE) {
            return "DNF";
        }

        String sign = "";
        if (time < 0) {
            sign = "-";
            time = -time;
        }

        time = (time + 5) / 10;

        long minutes = time / 6000;
        long seconds = (time / 100) % 60;
        long centiseconds = time % 100;

        return sign +
               (minutes < 10 ? "0" + minutes : minutes) + ":" +
               (seconds < 10 ? "0" + seconds : seconds) + "." +
               (centiseconds < 10 ? "0" + centiseconds : centiseconds);
    }

    public static String format(long time) {
        if (-60000 < time && time < 60000) {
            return formatSeconds(time);
        }

        if (time == Long.MAX_VALUE) {
            return "DNF";
        }

        String sign = "";
        if (time < 0) {
            sign = "-";
            time = -time;
        }

        time = (time + 5) / 10;

        long minutes = time / 6000;
        long seconds = (time / 100) % 60;
        long centiseconds = time % 100;

        return sign +
               minutes + ":" +
               (seconds < 10 ? "0" + seconds : seconds) + "." +
               (centiseconds < 10 ? "0" + centiseconds : centiseconds);
    }

    public static long parseTime(String input) {
        Scanner scanner = new Scanner(input.trim());
        scanner.useLocale(Locale.ENGLISH);

        long time;

        // 00:00.00
        if (input.contains(":")) {
            scanner.useDelimiter(":");

            if (!scanner.hasNextLong()) {
                return 0;
            }

            long minutes = scanner.nextLong();
            if (minutes < 0) {
                return 0;
            }

            if (!scanner.hasNextDouble()) {
                return 0;
            }

            double seconds = scanner.nextDouble();
            if (seconds < 0.0 || seconds >= 60.0) {
                return 0;
            }

            time = (long) (60000 * minutes + 1000 * seconds);
        }

        // 00.00
        else {
            if (!scanner.hasNextDouble()) {
                return 0;
            }

            double seconds = scanner.nextDouble();
            if (seconds < 0.0) {
                return 0;
            }

            time = (long) (1000 * seconds);
        }

        return 10 * ((time + 5) / 10);
    }

    public static long realTime(Solution solution) {
        if (solution.getPenalty().equals("DNF")) {
            return Long.MAX_VALUE;
        }

        if (solution.getPenalty().equals("+2")) {
            return solution.getTiming().getElapsedTime() + 2000;
        }

        return solution.getTiming().getElapsedTime();
    }

    public static long[] realTimes(Solution[] solutions, boolean filterDNF) {
        ArrayList<Long> realTimes = new ArrayList<Long>();
        for (int i = 0; i < solutions.length; i++) {
            long actualTime = realTime(solutions[i]);
            if (!filterDNF || actualTime != Long.MAX_VALUE) {
                realTimes.add(actualTime);
            }
        }

        long[] realTimesArray = new long[realTimes.size()];
        for (int i = 0; i < realTimesArray.length; i++) {
            realTimesArray[i] = realTimes.get(i);
        }

        return realTimesArray;
    }
}
