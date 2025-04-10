package ru.refontstudio.restduels.utils;

import org.bukkit.ChatColor;
import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("§x(§[0-9A-F]){6}");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:([0-9A-Fa-f]{6}):([0-9A-Fa-f]{6})>(.*?)</gradient>");

    public static String colorize(String message) {
        if (message == null) return "";

        // Сначала обрабатываем градиенты
        Matcher matcher = GRADIENT_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String startHex = matcher.group(1);
            String endHex = matcher.group(2);
            String content = matcher.group(3);

            matcher.appendReplacement(sb, applyGradient(content, startHex, endHex));
        }
        matcher.appendTail(sb);
        message = sb.toString();

        // Затем обрабатываем обычные цвета
        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    private static String applyGradient(String text, String startHex, String endHex) {
        Color startColor = Color.decode("#" + startHex);
        Color endColor = Color.decode("#" + endHex);

        StringBuilder result = new StringBuilder();

        int length = text.length();
        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (length - 1);

            int red = (int) (startColor.getRed() * (1 - ratio) + endColor.getRed() * ratio);
            int green = (int) (startColor.getGreen() * (1 - ratio) + endColor.getGreen() * ratio);
            int blue = (int) (startColor.getBlue() * (1 - ratio) + endColor.getBlue() * ratio);

            Color color = new Color(red, green, blue);
            String hex = String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

            result.append("§x");
            for (char c : hex.toCharArray()) {
                result.append("§").append(c);
            }
            result.append(text.charAt(i));
        }

        return result.toString();
    }
}