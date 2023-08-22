package io.kroxylicious;

public class Condition {

    private static boolean testInProgress = false;

    static boolean testInProgress() {
        return testInProgress;
    }

    public static void setTestInProgress(boolean testInProgress) {
        Condition.testInProgress = testInProgress;
    }
}