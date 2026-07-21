package com.leonardobishop.quests.bukkit.tasktype.type.dependent;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import ltd.lemongaming.lgshop.api.TransactionResult;
import ltd.lemongaming.lgshop.api.TransactionType;

public final class LGShopSellTaskType extends LGShopInteractionTaskType {

    public LGShopSellTaskType(BukkitQuestsPlugin plugin) {
        super(plugin, "lgshop_sell", TaskUtils.TASK_ATTRIBUTION_STRING, "Sell a given item to an LGShop shop",
                "lgshop_sellcertain", "shopguiplus_sell", "shopguiplus_sellcertain");
    }

    @Override
    public boolean isCorrectInteraction(TransactionResult result) {
        TransactionType type = result.getType();
        return type == TransactionType.SELL || type == TransactionType.SELL_ALL;
    }
}
