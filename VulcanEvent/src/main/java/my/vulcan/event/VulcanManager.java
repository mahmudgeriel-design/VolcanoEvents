package my.vulcan.event;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

public class VulcanManager {
    private final Main plugin;
    private BukkitTask eventTask;
    private Location vulcanLocation;
    private BossBar bossBar;
    private final Random random = new Random();

    public VulcanManager(Main plugin) {
        this.plugin = plugin;
    }

    public void startAutoEventTimer() {
        int delay = plugin.getConfig().getInt("settings.auto-event-delay-minutes", 60) * 1200;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (eventTask == null) startEvent();
            }
        }.runTaskTimer(plugin, delay, delay);
    }

    public void startEvent() {
        if (eventTask != null) return;

        // Берём точное имя мира из настроек
        String configWorldName = plugin.getConfig().getString("settings.world-name", "rtp");
        World world = Bukkit.getWorld(configWorldName);
        
        if (world == null) {
            plugin.getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА: Мир '" + configWorldName + "' не найден! Проверьте config.yml.");
            return;
        }

        int minX = plugin.getConfig().getInt("settings.rtp.min-x", 1500);
        int maxX = plugin.getConfig().getInt("settings.rtp.max-x", 5000);
        int minZ = plugin.getConfig().getInt("settings.rtp.min-z", 1500);
        int maxZ = plugin.getConfig().getInt("settings.rtp.max-z", 5000);

        int x = random.nextInt(maxX - minX) + minX;
        if (random.nextBoolean()) x = -x;
        int z = random.nextInt(maxZ - minZ) + minZ;
        if (random.nextBoolean()) z = -z;
        int y = world.getHighestBlockYAt(x, z);

        if (y < 60) y = 65; 

        this.vulcanLocation = new Location(world, x, y, z);

        String titleText = color("&#ff3300&l🌋 ВУЛКАН ПРОСНУЛСЯ 🌋");
        String subtitleText = color("&7Скорее открывай чат, чтобы узнать координаты!");
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(titleText, subtitleText, 10, 80, 10);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        }

        broadcastInteractiveMessage(x, y, z);

        vulcanLocation.getBlock().setType(Material.MAGMA_BLOCK);

        String barTitle = color(plugin.getConfig().getString("messages.bossbar-title", "&#cc0000&l🌋 Извержение Древнего Вулкана! 🌋"));
        bossBar = Bukkit.createBossBar(barTitle, BarColor.RED, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        long durationTicks = plugin.getConfig().getInt("settings.duration-seconds", 300) * 20L;
        long periodTicks = plugin.getConfig().getInt("settings.drop-period-ticks", 20);

        eventTask = new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= durationTicks) {
                    stopEvent();
                    cancel();
                    return;
                }

                double progress = 1.0 - ((double) elapsed / durationTicks);
                if (progress >= 0.0 && progress <= 1.0) {
                    bossBar.setProgress(progress);
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!bossBar.getPlayers().contains(player)) {
                        bossBar.addPlayer(player);
                    }
                }

                spawnVolcanoEffects();
                throwLootItem();
                elapsed += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }

    private void broadcastInteractiveMessage(int x, int y, int z) {
        TextComponent line1 = new TextComponent(color("\n&#ff1100&l🌋 [ИВЕНТ] ДРЕВНИЙ ВУЛКАН 🌋\n"));
        TextComponent line2 = new TextComponent(color("&7На сервере началось мощное извержение сокровищ!\n&7Локация вулкана: "));
        
        TextComponent clickable = new TextComponent(color("&#ffcc00&l[" + x + ", " + y + ", " + z + "]"));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(color("&eКликни, чтобы использовать случайный телепорт!"))));
        
        // Берём готовую команду прямо из конфига
        String clickCmd = plugin.getConfig().getString("settings.click-command", "/rtp");
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCmd));
        
        TextComponent line3 = new TextComponent(color("\n&8» Скорее беги собирать падающие сокровища пяти редкостей!\n"));

        line2.addExtra(clickable);
        line2.addExtra(line3);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(line1);
            player.spigot().sendMessage(line2);
        }
    }

    private void spawnVolcanoEffects() {
        if (vulcanLocation == null || vulcanLocation.getWorld() == null) return;
        vulcanLocation.getWorld().spawnParticle(org.bukkit.Particle.LAVA, vulcanLocation.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.15);
        vulcanLocation.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, vulcanLocation.clone().add(0, 2, 0), 8, 0.4, 1, 0.4, 0.03);
    }

    private void throwLootItem() {
        String rarity = getRandomRarityByChance();
        List<org.bukkit.inventory.ItemStack> items = MenuManager.getRarityItems(rarity);
        if (items.isEmpty()) return;

        org.bukkit.inventory.ItemStack itemToDrop = items.get(random.nextInt(items.size()));
        Item droppedItem = vulcanLocation.getWorld().dropItem(vulcanLocation.clone().add(0, 2, 0), itemToDrop);
        
        double offsetX = (random.nextDouble() - 0.5) * 2.0;
        double offsetZ = (random.nextDouble() - 0.5) * 2.0;
        double offsetY = 0.9 + random.nextDouble() * 0.6;
        droppedItem.setVelocity(new Vector(offsetX, offsetY, offsetZ));
    }

    private String getRandomRarityByChance() {
        FileConfiguration config = plugin.getConfig();
        double r = random.nextDouble() * 100;
        
        double common = config.getDouble("chances.common", 50.0);
        double rare = config.getDouble("chances.rare", 30.0);
        double epic = config.getDouble("chances.epic", 13.0);
        double mythic = config.getDouble("chances.mythic", 5.0);

        if (r < common) return "common";
        if (r < common + rare) return "rare";
        if (r < common + rare + epic) return "epic";
        if (r < common + rare + epic + mythic) return "mythic";
        return "legendary";
    }

    public void stopEvent() {
        if (eventTask != null) {
            eventTask.cancel();
            eventTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        String stopMsg = plugin.getConfig().getString("messages.stop", "&#ff4500&l[ВУЛКАН] &7Магма полностью остыла. Извержение вулкана завершено!");
        Bukkit.broadcastMessage(color(stopMsg));
    }
    
    public void stopCurrentEvent() {
        if (eventTask != null) eventTask.cancel();
        if (bossBar != null) bossBar.removeAll();
    }

    public static String color(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("&#[a-fA-F0-9]{6}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String cp = text.substring(matcher.start(), matcher.end());
            text = text.replace(cp, net.md_5.bungee.api.ChatColor.of(cp.substring(1)).toString());
            matcher = pattern.matcher(text);
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }
}
