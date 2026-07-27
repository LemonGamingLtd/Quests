package com.leonardobishop.quests.bukkit.tasktype.type.dependent;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import ltd.lemongaming.lgshop.api.Shop;
import ltd.lemongaming.lgshop.api.ShopItem;
import ltd.lemongaming.lgshop.api.TransactionResult;
import ltd.lemongaming.lgshop.api.event.ShopPostTransactionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

public abstract class LGShopInteractionTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public LGShopInteractionTaskType(BukkitQuestsPlugin plugin, @NotNull String type, String author, String description, String... aliases) {
        super(type, author, description, aliases);
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "amount"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "amount"));
    }

    public abstract boolean isCorrectInteraction(TransactionResult result);

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopPostTransaction(ShopPostTransactionEvent event) {
        TransactionResult result = event.getResult();
        if (result.getResult() != TransactionResult.ResultType.SUCCESS) {
            return;
        }

        if (!isCorrectInteraction(result)) {
            return;
        }

        Player player = result.getPlayer();
        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        ShopItem shopItem = result.getShopItem();
        if (shopItem == null) {
            return;
        }

        Shop shop = shopItem.getShop();
        String shopId = shop != null ? shop.getId() : null;
        String itemId = shopItem.getId();
        int amountBought = result.getAmount();

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player, qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player traded item (shop = " + shopId + ", item id = " + itemId + ", action = " + result.getType() + ")", quest.getId(), task.getId(), player.getUniqueId());

            String taskShopId = (String) task.getConfigValue("shop-id");
            if (taskShopId == null || !taskShopId.equals(shopId)) {
                super.debug("Shop id does not match required id, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            String taskItemId = (String) task.getConfigValue("item-id");
            if (taskItemId == null || !taskItemId.equals(itemId)) {
                super.debug("Item id does not match required id, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            int amountNeeded = (int) task.getConfigValue("amount");

            int progress = TaskUtils.getIntegerTaskProgress(taskProgress);
            int newProgress = progress + amountBought;
            taskProgress.setProgress(newProgress);

            super.debug("Updating task progress (now " + newProgress + ")", quest.getId(), task.getId(), player.getUniqueId());

            if (newProgress >= amountNeeded) {
                super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setProgress(amountNeeded);
                taskProgress.setCompleted(true);
            }

            TaskUtils.sendTrackAdvancement(player, quest, task, pendingTask, amountNeeded);
        }
    }
}
