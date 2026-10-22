package com.leonardobishop.quests.bukkit.util;

import com.leonardobishop.quests.bukkit.BukkitQuestsPlugin;
import com.leonardobishop.quests.bukkit.util.chat.Chat;
import com.leonardobishop.quests.common.config.QuestsConfig;
import com.leonardobishop.quests.common.player.questprogressfile.QuestProgress;
import com.leonardobishop.quests.common.player.questprogressfile.TaskProgress;
import com.leonardobishop.quests.common.quest.Quest;
import com.leonardobishop.quests.common.quest.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Goal, percentage and progress bar formatting shared by menus, boss bars and feedback.
 */
public final class ProgressFormat {

    private static final BukkitQuestsPlugin plugin;

    static {
        plugin = BukkitQuestsPlugin.getPlugin(BukkitQuestsPlugin.class);
    }

    private ProgressFormat() {
    }

    // nearly all task types use "amount", walking uses "distance" and playtime uses "minutes"
    private static final String[] GOAL_KEYS = {"amount", "distance", "minutes"};

    /**
     * Get the goal of a task from its configured amount.
     *
     * @return the goal, or null if the task has no numeric amount
     */
    public static @Nullable Number getGoal(final @Nullable Task task) {
        if (task == null) {
            return null;
        }

        for (final String key : GOAL_KEYS) {
            final Object amount = task.getConfigValue(key);
            if (amount instanceof Number number) {
                return number;
            }

            if (amount instanceof String string) {
                try {
                    return Double.parseDouble(string);
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        return null;
    }

    public static @Nullable Task getTask(final @NotNull QuestProgress questProgress, final @NotNull TaskProgress taskProgress) {
        final Quest quest = plugin.getQuestManager().getQuestById(questProgress.getQuestId());
        return quest != null ? quest.getTaskById(taskProgress.getTaskId()) : null;
    }

    public static double getProgress(final @NotNull TaskProgress taskProgress) {
        return taskProgress.getProgress() instanceof Number number ? number.doubleValue() : 0.0d;
    }

    /**
     * @return progress as a fraction between 0 and 1, where a completed task is always 1
     */
    public static double getFraction(final @NotNull TaskProgress taskProgress, final @Nullable Number goal) {
        if (taskProgress.isCompleted()) {
            return 1.0d;
        }

        if (goal == null || goal.doubleValue() <= 0.0d) {
            return 0.0d;
        }

        return Math.max(0.0d, Math.min(1.0d, getProgress(taskProgress) / goal.doubleValue()));
    }

    public static int getPercent(final double fraction) {
        // never show 100% for a task which is not finished yet, the epsilon stops 29/50 showing as 57%
        final int percent = (int) Math.floor(fraction * 100.0d + 1.0e-9d);
        return fraction < 1.0d ? Math.min(percent, 99) : 100;
    }

    public static @NotNull String formatNumber(final @NotNull Number number) {
        final double value = number.doubleValue();
        return value == Math.rint(value) ? FormatUtils.integral(number.longValue()) : FormatUtils.floating(number);
    }

    /**
     * Build a progress bar using the characters and colours from options.progress-bar.
     */
    public static @NotNull String getBar(final double fraction) {
        final QuestsConfig config = plugin.getQuestsConfig();
        final int length = Math.max(1, config.getInt("options.progress-bar.length", 20));
        final String symbol = config.getString("options.progress-bar.symbol", "|");
        final String filledColor = config.getString("options.progress-bar.filled-color", "&a");
        final String emptyColor = config.getString("options.progress-bar.empty-color", "&8");
        final String completeColor = config.getString("options.progress-bar.complete-color", filledColor);

        final int filled = fraction >= 1.0d ? length : (int) Math.floor(fraction * length);
        final StringBuilder bar = new StringBuilder();
        if (filled > 0) {
            bar.append(fraction >= 1.0d ? completeColor : filledColor).append(symbol.repeat(filled));
        }
        if (filled < length) {
            bar.append(emptyColor).append(symbol.repeat(length - filled));
        }
        return Chat.legacyColor(bar.toString());
    }
}
