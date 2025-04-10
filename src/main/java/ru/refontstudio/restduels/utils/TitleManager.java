package ru.refontstudio.restduels.utils;

import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;

public class TitleManager {
    private final RestDuels plugin;

    public TitleManager(RestDuels plugin) {
        this.plugin = plugin;
    }

    public void sendTitle(Player player, String titleType, String... replacements) {
        if (player == null || !player.isOnline()) return;

        String titlePath = "titles." + titleType + ".title";
        String subtitlePath = "titles." + titleType + ".subtitle";
        int fadeIn = plugin.getConfig().getInt("titles." + titleType + ".fadeIn", 10);
        int stay = plugin.getConfig().getInt("titles." + titleType + ".stay", 60);
        int fadeOut = plugin.getConfig().getInt("titles." + titleType + ".fadeOut", 20);

        String title = plugin.getConfig().getString(titlePath, "");
        String subtitle = plugin.getConfig().getString(subtitlePath, "");

        // Применяем замены
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    title = title.replace(replacements[i], replacements[i + 1]);
                    subtitle = subtitle.replace(replacements[i], replacements[i + 1]);
                }
            }
        }

        // Применяем цвета и градиенты
        title = ColorUtils.colorize(title);
        subtitle = ColorUtils.colorize(subtitle);

        // Отправляем заголовок
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }
}