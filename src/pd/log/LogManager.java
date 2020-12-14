package pd.log;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.PrimitiveIterator.OfInt;
import java.util.Properties;

import pd.time.TimeUtil;

/**
 * works as a holder of global logger
 */
public class LogManager {

    private static class Config {

        private static final String KEY_PREFIX = LogManager.class.getCanonicalName();
        private static final String KEY_LOGGERCLASS = KEY_PREFIX + ".loggerClass";
        private static final String KEY_LOGLEVEL = KEY_PREFIX + ".logLevel";
        private static final String KEY_FILELOGGER_FILEPARENT = KEY_PREFIX + ".fileLogger.fileParent";
        private static final String KEY_FILELOGGER_FILEPREFIX = KEY_PREFIX + ".fileLogger.filePrefix";
        private static final String KEY_FILELOGGER_FILEINTERVAL = KEY_PREFIX + ".fileLogger.fileInterval";

        public static final String CONFIG_FILE = KEY_PREFIX + ".properties";

        /**
         * return null if any unacceptable values
         */
        public static ILogger createDefaultLogger() {
            Properties props = Util.loadProperties(CONFIG_FILE);
            try {
                return createLogger(props);
            } catch (IllegalArgumentException e) {
                ConsoleLogger.defaultInstance.logError("E: unacceptable property: %s", e.getMessage());
                return null;
            } catch (Exception e) {
                ConsoleLogger.defaultInstance.logError("E: %s", e.getMessage());
                return null;
            }
        }

        public static ILogger createLogger(Properties props) {
            if (props == null) {
                return null;
            }

            String logLevelString = props.getProperty(KEY_LOGLEVEL);
            if (logLevelString == null) {
                throw new IllegalArgumentException(KEY_LOGLEVEL + " not found");
            } else if (logLevelString.equals("OFF") || logLevelString.equals("MUTE")) {
                return new ConsoleLogger(null);
            }

            // if it throws exception, let it down
            LogLevel maxAcceptableLogLevel;
            try {
                maxAcceptableLogLevel = LogLevel.valueOf(logLevelString);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(KEY_LOGLEVEL + "=" + logLevelString);
            }

            String loggerClass = props.getProperty(KEY_LOGGERCLASS);
            if (loggerClass == null) {
                throw new IllegalArgumentException(KEY_LOGGERCLASS + " not found");
            }

            boolean hasWrapper = false;
            if (loggerClass.startsWith("Queued")) {
                hasWrapper = true;
                loggerClass = loggerClass.substring("Queued".length());
            }

            if (loggerClass.equals(ConsoleLogger.class.getSimpleName())) {
                ILogger logger = maxAcceptableLogLevel == ConsoleLogger.defaultInstance.getMaxAcceptableLogLevel()
                        ? ConsoleLogger.defaultInstance
                        : new ConsoleLogger(maxAcceptableLogLevel);
                return hasWrapper ? new QueuedLogger(logger) : logger;
            } else if (loggerClass.equals(FileLogger.class.getSimpleName())) {
                String fileParent = props.getProperty(KEY_FILELOGGER_FILEPARENT);
                if (fileParent == null) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEPARENT + " not found");
                } else if (fileParent.isEmpty()) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEPARENT + "=" + fileParent);
                }

                String filePrefix = props.getProperty(KEY_FILELOGGER_FILEPREFIX);
                if (filePrefix == null) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEPREFIX + " not found");
                } else if (filePrefix.isEmpty()) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEPREFIX + "=" + filePrefix);
                }

                String fileInterval = props.getProperty(KEY_FILELOGGER_FILEINTERVAL);
                if (fileInterval == null) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEINTERVAL + " not found");
                } else if (fileInterval.isEmpty()) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEINTERVAL + "=" + fileInterval);
                }
                // if it throws exception, let it down
                int numIntervalMilliseconds;
                try {
                    numIntervalMilliseconds = Integer.parseInt(fileInterval);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(KEY_FILELOGGER_FILEINTERVAL + "=" + fileInterval);
                }

                ILogger logger = new FileLogger(fileParent, filePrefix, numIntervalMilliseconds, maxAcceptableLogLevel);
                return hasWrapper ? new QueuedLogger(logger) : logger;
            }
            throw new IllegalArgumentException(KEY_LOGGERCLASS + "=" + loggerClass);
        }
    }

    public static class Util {

        /**
         * use '{}' as formatting anchor
         */
        public static String evaluateMessage(String message, Object... messageArguments) {
            if (messageArguments == null || messageArguments.length == 0) {
                return message;
            }

            // state machine
            StringBuilder sb = new StringBuilder();
            OfInt it = message.codePoints().iterator();
            int nextArgumentIndex = 0;
            int state = 0;
            while (state != 3) {
                switch (state) {
                    case 0: {
                        int ch = it.hasNext() ? it.nextInt() : -1;
                        switch (ch) {
                            case '\\':
                                state = 1;
                                break;
                            case '{':
                                state = 2;
                                break;
                            case -1:
                                state = 3;
                                break;
                            default:
                                state = 0;
                                sb.appendCodePoint(ch);
                                break;
                        }
                        break;
                    }
                    case 1: {
                        int ch = it.hasNext() ? it.nextInt() : -1;
                        switch (ch) {
                            case '\\':
                                state = 0;
                                sb.append('\\');
                                break;
                            case '{':
                                state = 0;
                                sb.append('{');
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "E: unrecognized \"\\" + new String(Character.toChars(ch)) + "\"");
                        }
                        break;
                    }
                    case 2: {
                        int ch = it.hasNext() ? it.nextInt() : -1;
                        switch (ch) {
                            case '}':
                                state = 0;
                                if (nextArgumentIndex < messageArguments.length) {
                                    sb.append(messageArguments[nextArgumentIndex++]);
                                } else {
                                    sb.append("{}");
                                }
                                break;
                            default:
                                state = 0;
                                sb.append('{');
                                sb.appendCodePoint(ch);
                                break;
                        }
                        break;
                    }
                }
            }
            return sb.toString();
        }

        public static String getHostname() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return "UnknownHostExceptionHostname";
            } catch (Exception e) {
                return "ExceptionHostname";
            }
        }

        private static InputStream getInputStream(String file) {
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
            return currentClassLoader != null
                    ? currentClassLoader.getResourceAsStream(file)
                    : ClassLoader.getSystemResourceAsStream(file);
        }

        public static boolean isAcceptable(LogLevel level, LogLevel maxAcceptableLogLevel) {
            return level != null && maxAcceptableLogLevel != null && !maxAcceptableLogLevel.isPriorTo(level);
        }

        public static Properties loadProperties(String file) {
            try (InputStream stream = getInputStream(file)) {
                if (stream != null) {
                    Properties props = new Properties();
                    props.load(stream);
                    return props;
                }
            } catch (IOException e) {
                // dummy
            }
            return null;
        }

        /**
         * actual logger would call this to write
         */
        public static void writeLine(Writer w, String fieldSeparator, long timestamp, String hostname, LogLevel level,
                String message) throws IOException {
            // TODO csv
            StringBuilder sb = new StringBuilder();
            sb.append(TimeUtil.toUtcString(timestamp)).append(fieldSeparator)
                    .append(hostname).append(fieldSeparator)
                    .append(level.toString()).append(fieldSeparator)
                    .append(message).append('\n');
            w.write(sb.toString());
        }
    }

    private static final Object lock = new Object();

    private static ILogger logger;

    /**
     * will try to create a logger from properties file if logger is null
     */
    public static ILogger getLogger() {
        if (logger == null) {
            synchronized (lock) {
                if (logger == null) {
                    logger = Config.createDefaultLogger();
                }
            }
        }
        if (logger == null) {
            throw new IllegalArgumentException("E: LogManager.logger is not set");
        }
        return logger;
    }

    /**
     * initialization should happen in main thread at very beginning of main()
     */
    public static void setLogger(ILogger logger) {
        LogManager.logger = logger;
    }

    public static void useConsoleLogger() {
        setLogger(ConsoleLogger.defaultInstance);
    }

    public static void useFileLogger(String fileParent, String filePrefix, long numIntervalMilliseconds) {
        setLogger(new FileLogger(fileParent, filePrefix, numIntervalMilliseconds));
    }

    public static void useQueuedFileLogger(String fileParent, String filePrefix, long numIntervalMilliseconds) {
        setLogger(new QueuedLogger(new FileLogger(fileParent, filePrefix, numIntervalMilliseconds)));
    }

    private LogManager() {
        // private dummy
    }
}