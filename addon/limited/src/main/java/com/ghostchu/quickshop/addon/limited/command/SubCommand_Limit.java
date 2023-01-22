package com.ghostchu.quickshop.addon.limited.command;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.addon.limited.Main;
import com.ghostchu.quickshop.api.command.CommandHandler;
import com.ghostchu.quickshop.api.event.CalendarEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SubCommand_Limit implements CommandHandler<Player> {
    private final QuickShop quickshop;

    public SubCommand_Limit(QuickShop quickshop) {
        this.quickshop = quickshop;
    }

    @Override
    public void onCommand(Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        if (cmdArg.length < 1) {
            quickshop.text().of(sender, "command.wrong-args").send();
            return;
        }
        Shop shop = getLookingShop(sender);
        if (shop == null) {
            quickshop.text().of(sender, "not-looking-at-shop").send();
            return;
        }
        ConfigurationSection manager = shop.getExtra(Main.instance);
        switch (cmdArg[0]) {
            case "set" -> {
                try {
                    int limitAmount = Integer.parseInt(cmdArg[1]);
                    if (limitAmount > 0) {
                        manager.set("limit", limitAmount);
                        quickshop.text().of(sender, "addon.limited.success-setup").send();
                    } else {
                        manager.set("limit", null);
                        manager.set("data", null);
                        quickshop.text().of(sender, "addon.limited.success-remove").send();
                    }
                    shop.setExtra(Main.instance, manager);
                } catch (NumberFormatException e) {
                    quickshop.text().of(sender, "not-a-integer", cmdArg[1]).send();
                }
            }
            case "unset" -> {
                manager.set("limit", null);
                manager.set("data", null);
                quickshop.text().of(sender, "addon.limited.success-remove").send();
                shop.setExtra(Main.instance, manager);
            }
            case "reset" -> {
                manager.set("data", null);
                shop.setExtra(Main.instance, manager);
                quickshop.text().of(sender, "addon.limited.success-reset").send();
            }
            case "period" -> {
                try {
                    CalendarEvent.CalendarTriggerType type = CalendarEvent.CalendarTriggerType.valueOf(cmdArg[1].toUpperCase(Locale.ROOT));
                    manager.set("period", type.name());
                    quickshop.text().of(sender, "addon.limited.success-setup").send();
                    shop.setExtra(Main.instance, manager);
                } catch (IllegalArgumentException ignored) {
                    quickshop.text().of(sender, "command.wrong-args", cmdArg[1]).send();
                }
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player sender, @NotNull String commandLabel, @NotNull String[] cmdArg) {
        if (cmdArg.length < 1) {
            return List.of("set", "unset", "reset", "period");
        }
        if (cmdArg.length < 2) {
            switch (cmdArg[0]) {
                case "set" -> {
                    return List.of("<max>");
                }
                case "period" -> {
                    return Arrays.stream(CalendarEvent.CalendarTriggerType.values()).map(Enum::name).toList();
                }
            }
        }
        return Collections.emptyList();
    }
}
