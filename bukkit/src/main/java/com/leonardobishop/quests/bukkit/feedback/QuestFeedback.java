package com.leonardobishop.quests.bukkit.feedback;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.menu.itemstack.QItemStack;
import com.leonardobishop.quests.bukkit.util.ProgressFormat;
import com.leonardobishop.quests.bukkit.util.SoundUtils;
import com.leonardobishop.quests.bukkit.util.chat.Chat;
import com.leonardobishop.quests.common.config.QuestsConfig;
import com.leonardobishop.quests.common.enums.QuestStartResult;
import com.leonardobishop.quests.common.player.QPlayer;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Extra player feedback for quest progress and completion, configured under options.feedback.
 * Everything here is disabled unless options.feedback.enabled is true.
 */
public final class QuestFeedback {

    private static final String PATH = "options.feedback.";

    private final BukkitQuestsPlugin plugin;

    // weak keys so the state goes away with the player object after they quit
    private final Cache<Player, PlayerState> states = CacheBuilder.newBuilder().weakKeys().build();
    private final Set<UUID> takenNoticeShown = ConcurrentHashMap.newKeySet();

    public QuestFeedback(final @NotNull BukkitQuestsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class PlayerState {
        // last known progress keyed by quest id + task id, used for deltas and milestones
        private final Map<String, Double> lastProgress = new ConcurrentHashMap<>();
        private volatile long lastProgressSound;
    }

    private boolean isEnabled() {
        return config().getBoolean(PATH + "enabled", false);
    }

    private QuestsConfig config() {
        return plugin.getQuestsConfig();
    }

    private @Nullable PlayerState getState(final @NotNull Player player) {
        try {
            return states.get(player, PlayerState::new);
        } catch (final ExecutionException e) {
            return null;
        }
    }

    private static String key(final @NotNull Quest quest, final @NotNull Task task) {
        return quest.getId() + ":" + task.getId();
    }

    /**
     * Called whenever a task makes progress, from TaskUtils#sendTrackAdvancement.
     *
     * @param itemsTaken amount of items an inventory task removed from the player, or 0
     */
    public void onProgress(final @NotNull Player player, final @NotNull Quest quest, final @NotNull Task task,
                           final @NotNull TaskProgress taskProgress, final @Nullable Number goal, final int itemsTaken) {
        if (!isEnabled()) {
            return;
        }

        final PlayerState state = getState(player);
        if (state == null) {
            return;
        }

        final double progress = ProgressFormat.getProgress(taskProgress);
        final double fraction = ProgressFormat.getFraction(taskProgress, goal);
        final Double previous = state.lastProgress.put(key(quest, task), progress);
        final double delta = itemsTaken > 0 ? itemsTaken : (previous != null ? progress - previous : 0.0d);

        sendActionBar(player, quest, taskProgress, progress, goal, fraction, delta, itemsTaken > 0);

        // explain why items vanished, even when the first hand-in finishes the quest
        if (itemsTaken > 0 && takenNoticeShown.add(player.getUniqueId())) {
            for (final String line : config().getStringList(PATH + "items-taken-notice")) {
                Chat.send(player, line, true);
            }
        }

        if (taskProgress.isCompleted()) {
            return;
        }

        final boolean milestone = previous != null && checkMilestone(player, quest, previous, progress, goal);
        if (!milestone && delta > 0.0d) {
            playProgressSound(player, state, fraction);
        }
    }

    private void sendActionBar(final Player player, final Quest quest, final TaskProgress taskProgress, final double progress,
                               final Number goal, final double fraction, final double delta, final boolean taken) {
        final String format;
        if (taskProgress.isCompleted()) {
            format = config().getString(PATH + "actionbar.complete-format", "");
        } else if (taken) {
            format = config().getString(PATH + "actionbar.taken-format", "");
        } else {
            format = config().getString(PATH + "actionbar.format", "");
        }

        if (format == null || format.isEmpty()) {
            return;
        }

        final String deltaString = delta > 0.0d
                ? config().getString(PATH + "actionbar.delta-format", "&a+{delta} ").replace("{delta}", ProgressFormat.formatNumber(delta))
                : "";

        final String message = applyQuestPlaceholders(format, quest)
                .replace("{delta}", deltaString)
                .replace("{amount}", ProgressFormat.formatNumber(delta))
                .replace("{progress}", ProgressFormat.formatNumber(progress))
                .replace("{goal}", goal != null ? ProgressFormat.formatNumber(goal) : "?")
                .replace("{remaining}", goal != null ? ProgressFormat.formatNumber(Math.max(0.0d, goal.doubleValue() - progress)) : "?")
                .replace("{percent}", String.valueOf(ProgressFormat.getPercent(fraction)))
                .replace("{bar}", ProgressFormat.getBar(fraction));

        plugin.getActionBarHandle().sendActionBar(player, Chat.legacyColor(message));
    }

    private void playProgressSound(final Player player, final PlayerState state, final double fraction) {
        final String sound = config().getString(PATH + "progress-sound.sound", "");
        if (sound == null || sound.isEmpty()) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (now - state.lastProgressSound < config().getInt(PATH + "progress-sound.min-interval-ms", 400)) {
            return;
        }
        state.lastProgressSound = now;

        // pitch climbs as the task gets closer to completion
        final double pitchMin = parseDouble(config().getString(PATH + "progress-sound.pitch-min", "0.8"), 0.8d);
        final double pitchMax = parseDouble(config().getString(PATH + "progress-sound.pitch-max", "1.6"), 1.6d);
        final double volume = parseDouble(config().getString(PATH + "progress-sound.volume", "0.3"), 0.3d);
        final double pitch = pitchMin + (pitchMax - pitchMin) * fraction;
        SoundUtils.playSoundForPlayer(player, sound + ":" + (float) pitch + ":" + (float) volume);
    }

    private boolean checkMilestone(final Player player, final Quest quest, final double previous, final double progress, final Number goal) {
        if (goal == null || goal.doubleValue() <= 0.0d) {
            return false;
        }

        final int previousPercent = (int) Math.floor(previous / goal.doubleValue() * 100.0d + 1.0e-9d);
        final int percent = (int) Math.floor(progress / goal.doubleValue() * 100.0d + 1.0e-9d);

        int reached = -1;
        for (final String milestoneString : config().getStringList(PATH + "milestones.percentages")) {
            final int milestone = (int) parseDouble(milestoneString, -1.0d);
            if (milestone > 0 && milestone < 100 && previousPercent < milestone && percent >= milestone) {
                reached = Math.max(reached, milestone);
            }
        }

        if (reached < 0) {
            return false;
        }

        final String message = config().getString(PATH + "milestones.message", "");
        if (message != null && !message.isEmpty()) {
            Chat.send(player, applyQuestPlaceholders(message, quest).replace("{percent}", String.valueOf(reached)), true);
        }
        SoundUtils.playSoundForPlayer(player, config().getString(PATH + "milestones.sound", ""));
        return true;
    }

    public void onStart(final @NotNull Player player, final @NotNull Quest quest) {
        forget(player, quest);

        if (!isEnabled()) {
            return;
        }

        // start counting from zero so the first piece of progress shows a delta
        final PlayerState state = getState(player);
        if (state != null) {
            for (final Task task : quest.getTasks()) {
                state.lastProgress.put(key(quest, task), 0.0d);
            }
        }
    }

    public void forget(final @NotNull Player player, final @NotNull Quest quest) {
        final PlayerState state = states.getIfPresent(player);
        if (state != null) {
            for (final Task task : quest.getTasks()) {
                state.lastProgress.remove(key(quest, task));
            }
        }
    }

    public void onComplete(final @NotNull Player player, final @NotNull QPlayer qPlayer, final @NotNull Quest quest) {
        forget(player, quest);

        if (!isEnabled()) {
            return;
        }

        for (final String sound : config().getStringList(PATH + "complete.sounds")) {
            SoundUtils.playSoundForPlayer(player, sound);
        }

        spawnCompleteParticles(player);

        final String nextFormat = config().getString(PATH + "complete.next-quest-format", "");
        final int maxNext = config().getInt(PATH + "complete.max-next-quests", 2);
        if (nextFormat == null || nextFormat.isEmpty() || maxNext <= 0) {
            return;
        }

        int shown = 0;
        for (final Quest next : plugin.getQuestManager().getQuests().values()) {
            if (shown >= maxNext) {
                break;
            }

            if (!next.getRequirements().contains(quest.getId())) {
                continue;
            }

            final QuestStartResult result = qPlayer.canStartQuest(next);
            if (result != QuestStartResult.QUEST_SUCCESS && result != QuestStartResult.QUEST_LIMIT_REACHED) {
                continue;
            }

            Chat.send(player, applyQuestPlaceholders(nextFormat, next).replace("{id}", next.getId()), true);
            shown++;
        }
    }

    private void spawnCompleteParticles(final Player player) {
        final String particleName = config().getString(PATH + "complete.particle", "");
        if (particleName == null || particleName.isEmpty()) {
            return;
        }

        final Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(particleName.toLowerCase(Locale.ROOT)));
        if (particle == null || particle.getDataType() != Void.class) {
            return;
        }

        final int count = config().getInt(PATH + "complete.particle-count", 40);
        plugin.getScheduler().runTaskAtEntity(player, () -> {
            final Location location = player.getLocation().add(0.0d, 1.0d, 0.0d);
            player.getWorld().spawnParticle(particle, location, count, 0.4d, 0.6d, 0.4d, 0.25d);
        });
    }

    private String applyQuestPlaceholders(final String message, final Quest quest) {
        final QItemStack qItemStack = plugin.getQItemStackRegistry().getQuestItemStack(quest);
        final String displayName = qItemStack != null ? qItemStack.getName() : quest.getId();
        return message
                .replace("{questcolored}", displayName)
                .replace("{quest}", Chat.legacyStrip(displayName));
    }

    private static double parseDouble(final @Nullable String string, final double def) {
        if (string == null) {
            return def;
        }

        try {
            return Double.parseDouble(string);
        } catch (final NumberFormatException e) {
            return def;
        }
    }
}
