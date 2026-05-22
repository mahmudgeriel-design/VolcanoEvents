package my.vulcan.event;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VulcanCommand implements CommandExecutor {
    private final Main plugin;

    public VulcanCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vulcan.admin")) {
            sender.sendMessage(VulcanManager.color("&cУ вас нет прав!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(VulcanManager.color("&6Помощь: /vulcan [start/stop/reload/add]"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(VulcanManager.color("&aКонфиг успешно перезагружен!"));
                break;
            case "start":
                plugin.getVulcanManager().startEvent();
                sender.sendMessage(VulcanManager.color("&aИвент принудительно запущен!"));
                break;
            case "stop":
                plugin.getVulcanManager().stopEvent();
                sender.sendMessage(VulcanManager.color("&cИвент остановлен!"));
                break;
            case "add":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Только для игроков!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(VulcanManager.color("&cУкажите редкость: common, rare, epic, mythic, legendary"));
                    return true;
                }
                String rarity = args[1].toLowerCase();
                if (!List.of("common", "rare", "epic", "mythic", "legendary").contains(rarity)) {
                    player.sendMessage(VulcanManager.color("&cНеверная редкость! Выберите из: common, rare, epic, mythic, legendary"));
                    return true;
                }
                MenuManager.openRarityMenu(player, rarity);
                break;
            default:
                sender.sendMessage(VulcanManager.color("&cНеизвестная подкоманда."));
                break;
        }
        return true;
    }
}