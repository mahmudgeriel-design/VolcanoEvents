package my.vulcan.event;

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

import java.util.*;

public class VulcanManager {
    private final Main plugin;
    private BukkitTask eventTask;
    private Location vulcanLocation;
    private BossBar bossBar;
    private final Random random = new Random();
    
    private final Map<Location, Material> originalBlocks = new HashMap<>();
    private final Map<Item, String> activeLootItems = new HashMap<>();

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

        String configWorldName = plugin.getConfig().getString("settings.world-name", "rtp");
        World world = Bukkit.getWorld(configWorldName);
        
        if (world == null) {
            plugin.getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА: Мир '" + configWorldName + "' не найден!");
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

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        }

        broadcastSimpleMessage();
        buildVolcanoStructure();

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
                tickItemGlowEffects();
                elapsed += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }
    private void buildVolcanoStructure() {
        World world = vulcanLocation.getWorld();
        int baseRadius = 4;

        for (int xOffset = -baseRadius; xOffset <= baseRadius; xOffset++) {
            for (int zOffset = -baseRadius; zOffset <= baseRadius; zOffset++) {
                double distance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
                if (distance > baseRadius) continue;

                int height = (int) (baseRadius - distance) + 1;

                for (int yOffset = 0; yOffset <= height; yOffset++) {
                    Location blockLoc = vulcanLocation.clone().add(xOffset, yOffset, zOffset);
                    
                    if (!originalBlocks.containsKey(blockLoc)) {
                        originalBlocks.put(blockLoc, blockLoc.getBlock().getType());
                    }

                    if (xOffset == 0 && zOffset == 0 && yOffset == height) {
                        blockLoc.getBlock().setType(Material.LAVA);
                    } else if (distance <= 1.5) {
                        blockLoc.getBlock().setType(Material.MAGMA_BLOCK);
                    } else if (random.nextBoolean()) {
                        blockLoc.getBlock().setType(Material.OBSIDIAN);
                    } else {
                        blockLoc.getBlock().setType(Material.CRYING_OBSIDIAN);
                    }
                }
            }
        }
        Location topLava = vulcanLocation.clone().add(0, baseRadius + 1, 0);
        if (!originalBlocks.containsKey(topLava)) originalBlocks.put(topLava, topLava.getBlock().getType());
        topLava.getBlock().setType(Material.LAVA);
    }

    private void tickItemGlowEffects() {
        Iterator<Map.Entry<Item, String>> iterator = activeLootItems.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Item, String> entry = iterator.next();
            Item item = entry.getKey();
            String rarity = entry.getValue();

            if (item == null || !item.isValid() || item.isDead()) {
                iterator.remove();
                continue;
            }

            Location loc = item.getLocation().add(0, 0.2, 0);
            
            switch (rarity) {
                case "common":
                    loc.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, loc, 3, 0.1, 0.1, 0.1, 0.0);
                    break;
                case "rare":
                    loc.getWorld().spawnParticle(org.bukkit.Particle.FALLING_WATER, loc, 4, 0.1, 0.1, 0.1, 0.0);
                    break;
                case "epic":
                    loc.getWorld().spawnParticle(org.bukkit.Particle.DRAGON_BREATH, loc, 3, 0.1, 0.1, 0.1, 0.01);
                    break;
                case "mythic":
                    loc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, loc, 4, 0.1, 0.1, 0.1, 0.05);
                    break;
                case "legendary":
                    loc.getWorld().spawnParticle(org.bukkit.Particle.FLAME, loc, 5, 0.1, 0.1, 0.1, 0.02);
                    loc.getWorld().spawnParticle(org.bukkit.Particle.LAVA, loc, 1, 0.0, 0.0, 0.0, 0.0);
                    break;
            }
        }
    }

    private void broadcastSimpleMessage() {
        String msg = color(
            "\n&#ff1100&l🌋 [ИВЕНТ] ДРЕВНИЙ ВУЛКАН 🌋\n" +
            "&7Где-то в мире началось мощное извержение редких сокровищ!\n" +
            "&8» Успейте найти его и собрать падающий лут пяти редкостей!\n"
        );
        Bukkit.broadcastMessage(msg);
    }

    private void spawnVolcanoEffects() {
        if (vulcanLocation == null || vulcanLocation.getWorld() == null) return;
        Location topLoc = vulcanLocation.clone().add(0, 5, 0);
        topLoc.getWorld().spawnParticle(org.bukkit.Particle.LAVA, topLoc, 25, 0.6, 0.5, 0.6, 0.2);
        topLoc.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, topLoc, 15, 0.5, 1.5, 0.5, 0.05);
    }

    private void throwLootItem() {
        String rarity = getRandomRarityByChance();
        List<org.bukkit.inventory.ItemStack> items = MenuManager.getRarityItems(rarity);
        if (items.isEmpty()) return;

        org.bukkit.inventory.ItemStack itemToDrop = items.get(random.nextInt(items.size()));
        
        Location dropLoc = vulcanLocation.clone().add(0, 5, 0);
        Item droppedItem = vulcanLocation.getWorld().dropItem(dropLoc, itemToDrop);
        
        double offsetX = (random.nextDouble() - 0.5) * 2.5;
        double offsetZ = (random.nextDouble() - 0.5) * 2.5;
        double offsetY = 1.1 + random.nextDouble() * 0.7;
        droppedItem.setVelocity(new Vector(offsetX, offsetY, offsetZ));

        activeLootItems.put(droppedItem, rarity);
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

        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        originalBlocks.clear();
        activeLootItems.clear();

        String stopMsg = plugin.getConfig().getString("messages.stop", "&#ff4500&l[ВУЛКАН] &7Магма полностью остыла. Извержение вулкана завершено!");
        Bukkit.broadcastMessage(color(stopMsg));
    }
    
    public void stopCurrentEvent() {
        if (eventTask != null) eventTask.cancel();
        if (bossBar != null) bossBar.removeAll();
        
        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        originalBlocks.clear();
        activeLootItems.clear();
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
