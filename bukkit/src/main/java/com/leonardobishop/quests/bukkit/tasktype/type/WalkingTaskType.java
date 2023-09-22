package com.leonardobishop.quests.bukkit.tasktype.type;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.tasktype.BukkitTaskType;
import com.leonardobishop.quests.bukkit.util.TaskUtils;
import com.leonardobishop.quests.bukkit.util.constraint.TaskConstraintSet;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.Arrays;
import java.util.List;

public final class WalkingTaskType extends BukkitTaskType {

    private final BukkitQuestsPlugin plugin;

    public WalkingTaskType(BukkitQuestsPlugin plugin) {
        super("walking", TaskUtils.TASK_ATTRIBUTION_STRING, "Walk a set distance.");
        this.plugin = plugin;

        super.addConfigValidator(TaskUtils.useRequiredConfigValidator(this, "distance"));
        super.addConfigValidator(TaskUtils.useIntegerConfigValidator(this, "distance"));
        super.addConfigValidator(TaskUtils.useAcceptedValuesConfigValidator(this, Arrays.asList(
                "boat",
                "horse",
                "pig",
                "minecart",
                "strider",
                "sneaking",
                "walking",
                "running",
                "swimming",
                "flying",
                "elytra"
        ), "mode"));
        super.addConfigValidator(TaskUtils.useBooleanConfigValidator(this, "force-ground-walking"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isInsideVehicle()) {
            return;
        }

        handle(player, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        List<Entity> entities = event.getVehicle().getPassengers();
        for (Entity entity : entities) {
            if (entity instanceof Player player) {
                handle(player, true);
            }
        }
    }

    private void handle(Player player, boolean passenger) {
        if (player.hasMetadata("NPC")) {
            return;
        }

        QPlayer qPlayer = plugin.getPlayerManager().getPlayer(player.getUniqueId());
        if (qPlayer == null) {
            return;
        }

        for (TaskUtils.PendingTask pendingTask : TaskUtils.getApplicableTasks(player.getPlayer(), qPlayer, this, TaskConstraintSet.ALL)) {
            Quest quest = pendingTask.quest();
            Task task = pendingTask.task();
            TaskProgress taskProgress = pendingTask.taskProgress();

            super.debug("Player moved", quest.getId(), task.getId(), player.getUniqueId());

            final String mode = (String) task.getConfigValue("mode");
            if (mode != null && !validateMode(player, mode)) {
                super.debug("Player's mode does not match required mode, continuing...", quest.getId(), task.getId(), player.getUniqueId());
                continue;
            }

            if (!passenger && (boolean) task.getConfigValue("force-ground-walking", false)) {
                // this is not that ideal for a ground check, but in lieu of not using more resources this will do
                if (!player.isOnGround()) {
                    continue;
                }
            }

            int progress = TaskUtils.incrementIntegerTaskProgress(taskProgress);
            super.debug("Incrementing task progress (now " + progress + ")", quest.getId(), task.getId(), player.getUniqueId());

            int distanceNeeded = (int) task.getConfigValue("distance");

            if (progress >= distanceNeeded) {
                super.debug("Marking task as complete", quest.getId(), task.getId(), player.getUniqueId());
                taskProgress.setCompleted(true);
            }
            TaskUtils.sendTrackAdvancement(player, quest, taskProgress);
        }
    }

    private boolean validateMode(Player player, String mode) {
        return switch (mode) {
            case "boat" -> player.getVehicle() instanceof Boat;
            case "horse" -> plugin.getVersionSpecificHandler().isPlayerOnHorse(player);
            case "pig" -> player.getVehicle() instanceof Pig;
            case "minecart" -> player.getVehicle() instanceof Minecart;
            case "strider" -> plugin.getVersionSpecificHandler().isPlayerOnStrider(player);
            case "sneaking" -> // sprinting does not matter
                    player.isSneaking() && !player.isSwimming() && !player.isFlying()
                            && !plugin.getVersionSpecificHandler().isPlayerGliding(player);
            case "walking" ->
                    !player.isSneaking() && !player.isSwimming() && !player.isSprinting() && !player.isFlying()
                            && !plugin.getVersionSpecificHandler().isPlayerGliding(player);
            case "running" -> !player.isSneaking() && !player.isSwimming() && player.isSprinting() && !player.isFlying()
                    && !plugin.getVersionSpecificHandler().isPlayerGliding(player);
            case "swimming" -> // sprinting and sneaking do not matter, flying is not possible
                    player.isSwimming() && !plugin.getVersionSpecificHandler().isPlayerGliding(player);
            case "flying" -> // if the player is flying then the player is flying
                    player.isFlying();
            case "elytra" -> // if the player is gliding then the player is gliding
                    plugin.getVersionSpecificHandler().isPlayerGliding(player);
            default -> false;
        };
    }

}
