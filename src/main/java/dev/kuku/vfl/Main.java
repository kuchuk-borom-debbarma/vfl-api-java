package dev.kuku.vfl;

public class Main {
    public static void main(String[] args) {
        //TODO config for flush handler. Either to throw exception or fail silently with logging
        //TODO null safeties
        //TODO a way to disable certain actions easily, such as NoOp buffer, flushHandler or skipping if byteBuddy is not initialized
        //TODO null pointer safety checks that should not break the code
        //TODO class level annotation for sub block
        //TODO root and remote block both in single annotation
        //TODO introduce groups
        //TODO extension method for logging so that users can chain that to slf4j and stuff. Should execute even if vfl is not initialized
        //TODO exception, debug and trace log type for logger
    }
}