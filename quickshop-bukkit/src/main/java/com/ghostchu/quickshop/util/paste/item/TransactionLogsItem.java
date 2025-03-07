package com.ghostchu.quickshop.util.paste.item;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.common.util.CommonUtil;
import com.ghostchu.quickshop.util.MsgUtil;
import com.ghostchu.quickshop.util.logger.Log;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.StringJoiner;

public class TransactionLogsItem implements SubPasteItem {
    private SimpleDateFormat format;

    public TransactionLogsItem() {
        String timeFormat = LegacyComponentSerializer.legacySection().serialize(QuickShop.getInstance().text().of("timeunit.std-time-format").forLocale(MsgUtil.getDefaultGameLanguageCode()));
        try {
            format = new SimpleDateFormat(timeFormat);
        } catch (IllegalArgumentException e) {
            format = new SimpleDateFormat("HH:mm:ss");
        }
    }

    @Override
    public @NotNull String genBody() {
        return buildContent();
    }

    @Override
    public @NotNull String getTitle() {
        return "Transaction History";
    }

    @NotNull
    private String buildContent() {
        StringJoiner builder = new StringJoiner("\n");
        List<String> debugLogs = Log.fetchLogs(Log.Type.TRANSACTION).stream().map(recordEntry -> "[" + format.format(recordEntry.getTimestamp()) + "] " + recordEntry).toList();
        List<String> tail = CommonUtil.tail(debugLogs, 1000);
        tail.forEach(builder::add);
        return "<textarea name=\"debuglogs\" style=\"height: 1000px; width: 100%;\">" +
                StringEscapeUtils.escapeHtml4(builder.toString()) +
                "</textarea><br />";
    }
}
