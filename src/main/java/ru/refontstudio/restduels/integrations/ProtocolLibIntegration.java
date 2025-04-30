package ru.refontstudio.restduels.integrations;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import ru.refontstudio.restduels.RestDuels;
import ru.refontstudio.restduels.listeners.CommandBlocker;
import ru.refontstudio.restduels.utils.ColorUtils;

public class ProtocolLibIntegration {
    private final RestDuels plugin;
    private final CommandBlocker commandBlocker;
    private final ProtocolManager protocolManager;

    public ProtocolLibIntegration(RestDuels plugin, CommandBlocker commandBlocker) {
        this.plugin = plugin;
        this.commandBlocker = commandBlocker;

        // Инициализируем ProtocolLib
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        // Регистрируем перехватчик пакетов
        registerPacketListener();
    }

    /**
     * Регистрирует перехватчик пакетов команд
     */
    private void registerPacketListener() {
        final RestDuels mainPlugin = this.plugin;

        // Блокируем команды от игрока
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.LOWEST,
                PacketType.Play.Client.CHAT) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();

                if (mainPlugin.getDuelManager().isPlayerInDuel(player.getUniqueId()) ||
                        mainPlugin.getDuelManager().isPlayerFrozen(player.getUniqueId())) {

                    String message = event.getPacket().getStrings().read(0);

                    if (message.startsWith("/")) {
                        String command = message.toLowerCase().split(" ")[0];

                        boolean allowed = commandBlocker.isCommandAllowed(command) ||
                                player.hasPermission("restduels.bypass.commandblock");

                        if (!allowed) {
                            event.setCancelled(true);

                            player.sendMessage(ColorUtils.colorize(
                                    mainPlugin.getConfig().getString("messages.prefix") +
                                            "&cКоманды заблокированы во время дуэли! " +
                                            "Разрешена только команда /hub."));

                            if (mainPlugin.getConfig().getBoolean("debug", false)) {
                                mainPlugin.getLogger().info("[ProtocolLib] Заблокирована команда " + command +
                                        " для игрока " + player.getName() + " во время дуэли");
                            }
                        }
                    }
                }
            }
        });

        // Блокируем исходящие сообщения, если игрок в дуэли
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGH,
                PacketType.Play.Server.CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (!mainPlugin.getDuelManager().isPlayerInDuel(player.getUniqueId())) return;
                try {
                    String msg = event.getPacket().getStrings().read(0);

                    // Добавляем проверку на сообщения о победе/поражении
                    if (msg != null && !msg.trim().isEmpty()) {
                        // Не блокируем сообщения о победе/поражении
                        if (msg.contains("победили в дуэли") || msg.contains("проиграли в дуэли") ||
                                msg.contains("Поздравляем") || msg.contains("Вы победили") ||
                                msg.contains("Время сбора ресурсов")) {
                            return; // Пропускаем эти сообщения
                        }

                        event.setCancelled(true);
                        if (mainPlugin.getConfig().getBoolean("debug", false)) {
                            mainPlugin.getLogger().info("[ProtocolLib] Заблокировано сообщение: " + msg +
                                    " для игрока " + player.getName());
                        }
                    }
                } catch (Exception ignored) {
                    // Игнорируем ошибки
                }
            }
        });
    }
}