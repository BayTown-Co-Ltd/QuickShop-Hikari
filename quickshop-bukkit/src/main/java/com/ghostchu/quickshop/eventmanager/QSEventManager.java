package com.ghostchu.quickshop.eventmanager;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.eventmanager.QuickEventManager;
import com.ghostchu.quickshop.util.logger.Log;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.AuthorNagException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class QSEventManager implements QuickEventManager, Listener, Reloadable {
    private final QuickShop plugin;
    private final List<ListenerContainer> ignoredListener = new ArrayList<>();

    public QSEventManager(QuickShop plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin.getJavaPlugin());
        this.rescan();
    }

    private synchronized void rescan() {
        this.ignoredListener.clear();
        plugin
                .getConfig()
                .getStringList("shop.protection-checking-listener-blacklist")
                .forEach(
                        input -> {
                            if (StringUtils.isEmpty(input)) {
                                return;
                            }
                            try {
                                Class<?> clazz = Class.forName(input);
                                this.ignoredListener.add(new ListenerContainer(clazz, input));
                                Log.debug("Successfully added blacklist: [BINDING] " + clazz.getName());
                            } catch (Exception ignored) {
                                this.ignoredListener.add(new ListenerContainer(null, input));
                                Log.debug("Successfully added blacklist: [DYNAMIC] " + input);
                            }
                        });
    }

    @Override
    public void callEvent(@NotNull Event event) {
        if (event.isAsynchronous()) {
            if (Thread.holdsLock(Bukkit.getPluginManager())) {
                throw new IllegalStateException(
                        event.getEventName()
                                + " cannot be triggered asynchronously from inside synchronized code.");
            }
            if (Bukkit.getServer().isPrimaryThread()) {
                throw new IllegalStateException(
                        event.getEventName()
                                + " cannot be triggered asynchronously from primary server thread.");
            }
        } else {
            if (!Bukkit.getServer().isPrimaryThread()) {
                throw new IllegalStateException(
                        event.getEventName() + " cannot be triggered asynchronously from another thread.");
            }
        }

        fireEvent(event);
    }

    private void fireEvent(Event event) {
        HandlerList handlers = event.getHandlers();
        RegisteredListener[] listeners = handlers.getRegisteredListeners();
        for (RegisteredListener registration : listeners) {
            if (!registration.getPlugin().isEnabled()) {
                continue;
            }
            Class<?> regClass = registration.getListener().getClass();
            boolean skip = false;
            for (ListenerContainer container : this.ignoredListener) {
                if (container.matches(regClass, registration.getPlugin())) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }
            try {
                registration.callEvent(event);
            } catch (AuthorNagException ex) {
                Plugin regPlugin = registration.getPlugin();
                if (regPlugin.isNaggable()) {
                    regPlugin.setNaggable(false);
                    regPlugin
                            .getLogger()
                            .log(
                                    Level.SEVERE,
                                    String.format(
                                            "Nag author(s): '%s' of '%s' about the following: %s",
                                            regPlugin.getDescription().getAuthors(),
                                            regPlugin.getDescription().getFullName(),
                                            ex.getMessage()));
                }
            } catch (Throwable ex) {
                Bukkit
                        .getLogger()
                        .log(
                                Level.SEVERE,
                                "Could not pass event "
                                        + event.getEventName()
                                        + " to "
                                        + registration.getPlugin().getDescription().getFullName(),
                                ex);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pluginDisable(PluginDisableEvent event) {
        this.rescan();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void pluginEnable(PluginEnableEvent event) {
        this.rescan();
    }

    @Override
    public ReloadResult reloadModule() {
        rescan();
        return new ReloadResult(ReloadStatus.SUCCESS, null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void serverReloaded(ServerLoadEvent event) {
        this.rescan();
    }
}

class ListenerContainer {
    @Nullable
    private final Class<?> clazz;
    @NotNull
    private final String clazzName;

    public ListenerContainer(@Nullable Class<?> clazz, @NotNull String clazzName) {
        this.clazz = clazz;
        this.clazzName = clazzName;
    }

    public boolean matches(@NotNull Class<?> matching, @NotNull Plugin plugin) {
        if (clazz != null) {
            return matching.equals(clazz);
        }
        if (clazzName.startsWith("@")) {
            return clazzName.equalsIgnoreCase("@" + plugin.getName());
        }
        String name = matching.getName();
        if (name.equalsIgnoreCase(clazzName)) {
            return true;
        }
        if (name.startsWith(clazzName)) {
            return true;
        }
        return name.matches(clazzName);
    }
}
