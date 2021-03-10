//  Logger.java
//
package getIdents;
import java.io.*;
import java.util.*;

//  Logger
//
public class Logger {

    public static PrintStream out = System.err;
    public static int LogLevel = 0;

    public static void info(Object ... a) {
        if (1 <= LogLevel) {
            println(a);
        }
    }

    public static void debug(Object ... a) {
        if (2 <= LogLevel) {
            println(a);
        }
    }

    public static void println(Object[] a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i != 0) {
                b.append(" ");
            }
            if (a[i] == null) {
                b.append("null");
            } else {
                b.append(a[i].toString());
            }
        }
        out.println(b.toString());
    }
}
