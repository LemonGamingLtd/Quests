package com.leonardobishop.quests.bukkit.tasktype.type.dependent;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import ltd.lemongaming.lgshop.api.TransactionResult;
import ltd.lemongaming.lgshop.api.TransactionType;

public final class LGShopBuyTaskType extends LGShopInteractionTaskType {

    public LGShopBuyTaskType(BukkitQuestsPlugin plugin) {
        super(plugin, "lgshop_buy", TaskUtils.TASK_ATTRIBUTION_STRING, "Purchase a given item from an LGShop shop",
                "lgshop_buycertain", "shopguiplus_buy", "shopguiplus_buycertain");
    }

    @Override
    public boolean isCorrectInteraction(TransactionResult result) {
        return result.getType() == TransactionType.BUY;
    }
}
