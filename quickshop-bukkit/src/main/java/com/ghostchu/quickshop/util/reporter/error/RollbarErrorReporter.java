package com.ghostchu.quickshop.util.reporter.error;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.GameVersion;
import com.ghostchu.quickshop.common.util.QuickExecutor;
import com.ghostchu.quickshop.util.Util;
import com.ghostchu.quickshop.util.logger.Log;
import com.google.common.collect.Lists;
import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import com.rollbar.notifier.config.ConfigBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidPluginException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ProtocolException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Filter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class RollbarErrorReporter {
    private static final String QUICKSHOP_ROOT_PACKAGE_NAME = "com.ghostchu.quickshop";
    private final Rollbar rollbar;
    private final List<String> reported = new ArrayList<>(5);
    private final List<Class<?>> ignoredException = Lists.newArrayList(IOException.class
            , OutOfMemoryError.class
            , ProtocolException.class
            , InvalidPluginException.class
            , UnsupportedClassVersionError.class
            , LinkageError.class
            , SQLException.class
    );
    private final QuickShop plugin;
    private final QuickShopExceptionFilter quickShopExceptionFilter;
    private final GlobalExceptionFilter serverExceptionFilter;
    private boolean disable;
    private boolean tempDisable;
    @Getter
    private volatile boolean enabled;


    public RollbarErrorReporter(@NotNull QuickShop plugin) {
        this.plugin = plugin;
        Config config = ConfigBuilder.withAccessToken("aeace9eab9e042dfb43d97d39728e19c")
                .environment(Util.isDevEdition() ? "development" : "production")
                .platform(Bukkit.getVersion())
                .codeVersion(QuickShop.getInstance().getVersion())
                .handleUncaughtErrors(false)
                .build();
        this.rollbar = Rollbar.init(config);

        quickShopExceptionFilter = new QuickShopExceptionFilter(plugin.getJavaPlugin().getLogger().getFilter());
        plugin.getJavaPlugin().getLogger().setFilter(quickShopExceptionFilter); // Redirect log request passthrough our error catcher.

        serverExceptionFilter = new GlobalExceptionFilter(plugin.getJavaPlugin().getLogger().getFilter());
        Bukkit.getLogger().setFilter(serverExceptionFilter);

        Log.debug("Rollbar error reporter success loaded.");
        enabled = true;
    }

    private void sendError0(@NotNull Throwable throwable, @NotNull String... context) {
        if (Bukkit.isPrimaryThread()) {
            plugin.logger().warn("Cannot send error on primary thread (I/O blocking). This error has been discard.");
            return;
        }
        try {
            if (plugin.getBootError() != null) {
                return; // Don't report any errors if boot failed.
            }
            if (tempDisable) {
                this.tempDisable = false;
                return;
            }
            if (disable) {
                return;
            }
            if (!enabled) {
                return;
            }

            if (!canReport(throwable)) {
                return;
            }
            if (isDisallowedClazz(throwable.getClass())) {
                return;
            }
            if (throwable.getCause() != null) {
                if (isDisallowedClazz(throwable.getCause().getClass())) {
                    return;
                }
            }
            this.rollbar.error(throwable, this.makeMapping(), throwable.getMessage());
            plugin
                    .logger()
                    .warn(
                            "A exception was thrown, QuickShop already caught this exception and reported it. This error will only shown once before next restart.");
            plugin.logger().warn("====QuickShop Error Report BEGIN===");
            plugin.logger().warn("Description: {}", throwable.getMessage());
            plugin.logger().warn("Server   ID: {}", plugin.getServerUniqueID());
            plugin.logger().warn("Exception  : ");
            ignoreThrows();
            throwable.printStackTrace();
            resetIgnores();
            plugin.logger().warn("====QuickShop Error Report E N D===");
            plugin
                    .logger()
                    .warn(
                            "If this error affects any function, you can join our Discord server to report it and track the feedback: https://discord.gg/Bu3dVtmsD3");
            Log.debug(throwable.getMessage());
            Arrays.stream(throwable.getStackTrace()).forEach(a -> Log.debug(a.getClassName() + "." + a.getMethodName() + ":" + a.getLineNumber()));
            if (Util.isDevMode()) {
                throwable.printStackTrace();
            }
        } catch (Exception th) {
            ignoreThrow();
            plugin.logger().warn("Something going wrong when automatic report errors, please submit this error on Issue Tracker", th);
        }
    }

    /**
     * Check a throw is cause by QS
     *
     * @param throwable Throws
     * @return Cause or not
     */
    public PossiblyLevel checkWasCauseByQS(@Nullable Throwable throwable) {
        if (throwable == null) {
            return PossiblyLevel.IMPOSSIBLE;
        }
        if (throwable.getMessage() == null) {
            return PossiblyLevel.IMPOSSIBLE;
        }
        if (throwable.getMessage().contains("Could not pass event")) {
            if (throwable.getMessage().contains("QuickShop")) {
                return PossiblyLevel.CONFIRM;
            } else {
                return PossiblyLevel.IMPOSSIBLE;
            }
        }
        while (throwable.getCause() != null) {
            throwable = throwable.getCause();
        }

        StackTraceElement[] stackTraceElements = throwable.getStackTrace();

        if (stackTraceElements.length == 0) {
            return PossiblyLevel.IMPOSSIBLE;
        }

        if (stackTraceElements[0].getClassName().contains(QUICKSHOP_ROOT_PACKAGE_NAME) && stackTraceElements[1].getClassName().contains(QUICKSHOP_ROOT_PACKAGE_NAME)) {
            return PossiblyLevel.CONFIRM;
        }

        long errorCount = Arrays.stream(stackTraceElements)
                .limit(3)
                .filter(stackTraceElement -> stackTraceElement.getClassName().contains(QUICKSHOP_ROOT_PACKAGE_NAME))
                .count();

        if (errorCount > 0) {
            return PossiblyLevel.MAYBE;
        } else if (throwable.getCause() != null) {
            return checkWasCauseByQS(throwable.getCause());
        }
        return PossiblyLevel.IMPOSSIBLE;
    }

    /**
     * Set ignore throw. It will unlocked after accept a throw
     */
    public void ignoreThrow() {
        tempDisable = true;
    }

    /**
     * Set ignore throws. It will unlocked after called method resetIgnores.
     */
    public void ignoreThrows() {
        disable = true;
    }

    private Map<String, Object> makeMapping() {
        Map<String, Object> dataMapping = new LinkedHashMap<>();
        dataMapping.put("system_os", System.getProperty("os.name"));
        dataMapping.put("system_arch", System.getProperty("os.arch"));
        dataMapping.put("system_version", System.getProperty("os.version"));
        dataMapping.put("system_cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        dataMapping.put("server_build", Bukkit.getVersion());
        dataMapping.put("server_java", String.valueOf(System.getProperty("java.version")));
        dataMapping.put("server_players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers());
        dataMapping.put("server_onlinemode", String.valueOf(Bukkit.getOnlineMode()));
        dataMapping.put("server_bukkitversion", Bukkit.getVersion());
        dataMapping.put("user", QuickShop.getInstance().getServerUniqueID().toString());
        return dataMapping;
    }

    /**
     * Reset ignore throw(s).
     */
    public void resetIgnores() {
        tempDisable = false;
        disable = false;
    }

    /**
     * Send a error to Sentry
     *
     * @param throwable Throws
     * @param context   BreadCrumb
     */
    public void sendError(@NotNull Throwable throwable, @NotNull String... context) {
        QuickExecutor.getCommonExecutor().submit(() -> sendError0(throwable, context));
    }

    /**
     * Dupe report check
     *
     * @param throwable Throws
     * @return dupecated
     */
    public boolean canReport(@NotNull Throwable throwable) {
        if (!enabled) {
            return false;
        }
        if (plugin.getUpdateWatcher() == null) {
            return false;
        }
        try {
            if (!plugin.getNexusManager().isLatest()) { // We only receive latest reports.
                return false;
            }
        } catch (Exception exception) {
            Log.debug("Cannot to check reportable: " + exception.getMessage());
            return false;
        }
        if (!GameVersion.get(plugin.getPlatform().getMinecraftVersion()).isCoreSupports()) { // Ignore errors if user install quickshop on unsupported
            // version.
            return false;
        }

        PossiblyLevel possiblyLevel = checkWasCauseByQS(throwable);
        if (possiblyLevel != PossiblyLevel.CONFIRM) {
            return false;
        }
        if (throwable.getMessage().startsWith("#")) {
            return false;
        }
        StackTraceElement stackTraceElement;
        if (throwable.getStackTrace().length < 3) {
            stackTraceElement = throwable.getStackTrace()[1];
        } else {
            stackTraceElement = throwable.getStackTrace()[2];
        }
        if (stackTraceElement.getClassName().contains("com.ghostchu.quickshop.util.reporter.error")) {
            ignoreThrows();
            plugin.logger().warn("Uncaught exception in ErrorRollbar", throwable);
            resetIgnores();
            return false;
        }
        String text =
                stackTraceElement.getClassName()
                        + "#"
                        + stackTraceElement.getMethodName()
                        + "#"
                        + stackTraceElement.getLineNumber();
        if (!reported.contains(text)) {
            reported.add(text);
            return true;
        } else {
            return false;
        }
    }

    private boolean isDisallowedClazz(Class<?> clazz) {
        for (Class<?> ignoredClazz : this.ignoredException) {
            if (ignoredClazz.isAssignableFrom(clazz) || ignoredClazz.equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    public void unregister() {
        enabled = false;
        plugin.getJavaPlugin().getLogger().setFilter(quickShopExceptionFilter.preFilter);
        Bukkit.getLogger().setFilter(serverExceptionFilter.preFilter);
        try {
            rollbar.close(false);
        } catch (Exception ignored) {
        }
    }

    enum PossiblyLevel {
        CONFIRM,
        MAYBE,
        IMPOSSIBLE
    }

    class GlobalExceptionFilter implements Filter {

        @Nullable
        @Getter
        private final Filter preFilter;

        GlobalExceptionFilter(@Nullable Filter preFilter) {
            this.preFilter = preFilter;
        }

        /**
         * Check if a given log record should be published.
         *
         * @param rec a LogRecord
         * @return true if the log record should be published.
         */
        @Override
        public boolean isLoggable(@NotNull LogRecord rec) {
            if (!enabled) {
                return defaultValue(rec);
            }
            Level level = rec.getLevel();
            if (level != Level.WARNING && level != Level.SEVERE) {
                return defaultValue(rec);
            }
            if (rec.getThrown() == null) {
                return defaultValue(rec);
            }
            if (Util.isDevMode()) {
                sendError(rec.getThrown(), rec.getMessage());
                return defaultValue(rec);
            } else {
                sendError(rec.getThrown(), rec.getMessage());
                PossiblyLevel possiblyLevel = checkWasCauseByQS(rec.getThrown());
                if (possiblyLevel == PossiblyLevel.IMPOSSIBLE) {
                    return true;
                }
                if (possiblyLevel == PossiblyLevel.MAYBE) {
                    plugin.logger().warn("This seems not a QuickShop error. If you have any question, you should ask QuickShop developer.");
                    return true;
                }
                return false;
            }
        }

        private boolean defaultValue(LogRecord rec) {
            return preFilter == null || preFilter.isLoggable(rec);
        }

    }

    class QuickShopExceptionFilter implements Filter {
        @Nullable
        @Getter
        private final Filter preFilter;

        QuickShopExceptionFilter(@Nullable Filter preFilter) {
            this.preFilter = preFilter;
        }

        /**
         * Check if a given log record should be published.
         *
         * @param rec a LogRecord
         * @return true if the log record should be published.
         */
        @Override
        public boolean isLoggable(@NotNull LogRecord rec) {
            if (!enabled) {
                return defaultValue(rec);
            }
            Level level = rec.getLevel();
            if (level != Level.WARNING && level != Level.SEVERE) {
                return defaultValue(rec);
            }
            if (rec.getThrown() == null) {
                return defaultValue(rec);
            }
            if (Util.isDevMode()) {
                sendError(rec.getThrown(), rec.getMessage());
                return defaultValue(rec);
            } else {
                sendError(rec.getThrown(), rec.getMessage());
                PossiblyLevel possiblyLevel = checkWasCauseByQS(rec.getThrown());
                if (possiblyLevel == PossiblyLevel.IMPOSSIBLE) {
                    return true;
                }
                if (possiblyLevel == PossiblyLevel.MAYBE) {
                    plugin.logger().warn("This seems not a QuickShop error. If you have any question, you may can ask QuickShop developer but don't except any solution.");
                    return true;
                }
                return false;
            }
        }

        private boolean defaultValue(LogRecord rec) {
            return preFilter == null || preFilter.isLoggable(rec);
        }

    }
}
