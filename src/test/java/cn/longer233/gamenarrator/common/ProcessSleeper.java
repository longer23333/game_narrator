package cn.longer233.gamenarrator.common;

public final class ProcessSleeper {
    private ProcessSleeper() { }
    public static void main(String[] args) throws Exception {
        Thread.sleep(Long.parseLong(args[0]));
    }
}
