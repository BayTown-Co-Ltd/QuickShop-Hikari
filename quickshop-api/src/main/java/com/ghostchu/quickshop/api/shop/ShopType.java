package com.ghostchu.quickshop.api.shop;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The shop trading type *SELLING* or *BUYING*
 */
public enum ShopType {
    // SELLING = SELLMODE BUYING = BUY MODE
    SELLING(0),
    BUYING(1);
    private final int id;

    ShopType(int id) {
        this.id = id;
    }

    public static @Nullable ShopType fromString(@NotNull String string) {
        for (ShopType type : ShopType.values()) {
            if (type.name().equalsIgnoreCase(string))
                return type;
        }
        return null;
    }


    public static @NotNull ShopType fromID(int id) {
        for (ShopType type : ShopType.values()) {
            if (type.id == id) {
                return type;
            }
        }
        return SELLING;
    }

    public static int toID(@NotNull ShopType shopType) {
        return shopType.id;
    }

    public int toID() {
        return id;
    }
}
