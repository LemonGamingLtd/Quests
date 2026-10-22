package com.leonardobishop.quests.bukkit.hook.actionbar;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Action bars for Paper servers without the old String overload (26.x).
 */
public class ActionBar_Adventure implements QuestsActionBar {

    // titles are already legacy coloured, including the §x§r§r§g§g§b§b hex format
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    @Override
    public void sendActionBar(Player player, String title) {
        player.sendActionBar(SERIALIZER.deserialize(title));
    }
}
