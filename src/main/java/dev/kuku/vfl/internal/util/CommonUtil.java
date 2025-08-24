package dev.kuku.vfl.internal.util;

public class CommonUtil {
    public static String FormatMessage(String message, Object... args) {
        for (Object arg : args) {
            message = message.replaceFirst("\\{}", arg == null ? "null" : arg.toString());
        }
        return message;
    }
}
