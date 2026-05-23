package my.vulcan.event;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

public class VulcanManager implements Listener {
    private final Main plugin;
    private BukkitTask eventTask;
    private Location vulcanLocation;
    private BossBar bossBar;
    private final Random random = new Random();
    private final Map<Location, Material> originalBlocks = new HashMap<>();
    private final Map<Item, String> activeLootItems = new HashMap<>();

    public VulcanManager(Main plugin) { this.plugin = plugin; }

    public void startAutoEventTimer() {
        int delay = plugin.getConfig().getInt("settings.auto-event-delay-minutes", 60) * 1200;
        new BukkitRunnable() {
            @Override
            public void run() { if (eventTask == null) startEvent(); }
        }.runTaskTimer(plugin, delay, delay);
    }

    public void startEvent() {
        if (eventTask != null) return;
        String worldName = plugin.getConfig().getString("settings.world-name", "rtp");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        
        Bukkit.getPluginManager().registerEvents(this, plugin);

        int minX = plugin.getConfig().getInt("settings.rtp.min-x", 1500);
        int maxX = plugin.getConfig().getInt("settings.rtp.max-x", 5000);
        int minZ = plugin.getConfig().getInt("settings.rtp.min-z", 1500);
        int maxZ = plugin.getConfig().getInt("settings.rtp.max-z", 5000);

        int x = random.nextInt(maxX - minX) + minX; if (random.nextBoolean()) x = -x;
        int z = random.nextInt(maxZ - minZ) + minZ; if (random.nextBoolean()) z = -z;
        int y = Math.max(world.getHighestBlockYAt(x, z), 60);

        this.vulcanLocation = new Location(world, x, y, z);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);
        }
        
        String msg = color("\n&#ff1100&l🌋 [ИВЕНТ] ДРЕВНИЙ ВУЛКАН 🌋\n&7Началось мощное извержение редких сокровищ!\n&7Локация: &#ffcc00&lX: " + x + " Y: " + y + " Z: " + z + "\n&8» Скорее бегите по координатам, чтобы успеть собрать лут!\n");
        Bukkit.broadcastMessage(msg);
        
        buildVolcanoStructure();
        String barTitle = color(plugin.getConfig().getString("messages.bossbar-title", "&#cc0000&l🌋 Извержение Древнего Вулкана! 🌋"));
        bossBar = Bukkit.createBossBar(barTitle, BarColor.RED, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        
        long durationTicks = plugin.getConfig().getInt("settings.duration-seconds", 300) * 20L;
        long periodTicks = plugin.getConfig().getInt("settings.drop-period-ticks", 20);

        eventTask = new BukkitRunnable() {
            long elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks) { stopEvent(); cancel(); return; }
                double progress = 1.0 - ((double) elapsed / durationTicks);
                if (progress >= 0.0 && progress <= 1.0) bossBar.setProgress(progress);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
                }
                saveFlowingLava(); spawnVolcanoEffects(); throwLootItem();
                elapsed += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, periodTicks);
    }
    private void buildVolcanoStructure() {
        World w = vulcanLocation.getWorld();
        int maxHeight = 9; 
        int maxRadius = 8; 

        for (int y = 0; y <= maxHeight; y++) {
            double currentRadius = maxRadius * (1.0 - ((double) y / (maxHeight + 1)));
            for (int x = -maxRadius; x <= maxRadius; x++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance <= currentRadius) {
                        Location loc = vulcanLocation.clone().add(x, y, z);
                        if (!originalBlocks.containsKey(loc)) originalBlocks.put(loc, loc.getBlock().getType());

                        if (y >= maxHeight - 1) {
                            if (distance <= 1.8) {
                                loc.getBlock().setType(Material.LAVA); 
                            } else {
                                loc.getBlock().setType(Material.MAGMA_BLOCK); 
                            }
                        } else {
                            if (distance <= currentRadius - 1.5) {
                                loc.getBlock().setType(Material.MAGMA_BLOCK); 
                            } else {
                                loc.getBlock().setType(random.nextBoolean() ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN);
                            }
                        }
                    }
                }
            }
        }
    }

    private void saveFlowingLava() {
        int radius = 14;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 11; y++) {
                    Location loc = vulcanLocation.clone().add(x, y, z);
                    if (loc.getBlock().getType() == Material.LAVA && !originalBlocks.containsKey(loc)) {
                        originalBlocks.put(loc, Material.AIR);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && activeLootItems.containsKey(item)) {
            EntityDamageEvent.DamageCause c = event.getCause();
            if (c == EntityDamageEvent.DamageCause.LAVA || c == EntityDamageEvent.DamageCause.FIRE || c == EntityDamageEvent.DamageCause.FIRE_TICK) {
                event.setCancelled(true);
            }
        }
    }

    private void spawnVolcanoEffects() {
        if (vulcanLocation == null || vulcanLocation.getWorld() == null) return;
        Location top = vulcanLocation.clone().add(0, 9, 0);
        top.getWorld().spawnParticle(org.bukkit.Particle.LAVA, top, 25, 1.0, 0.5, 1.0, 0.3);
        top.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, top, 15, 0.8, 2.0, 0.8, 0.05);
        if (random.nextDouble() < 0.15) {
            top.getWorld().playSound(top, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
        }
    }

    private void throwLootItem() {
        String rarity = getRandomRarityByChance();
        List<org.bukkit.inventory.ItemStack> items = MenuManager.getRarityItems(rarity);
        if (items.isEmpty()) return;
        
        Location spawnLoc = vulcanLocation.clone().add(0, 9.5, 0);
        Item dropped = vulcanLocation.getWorld().dropItem(spawnLoc, items.get(random.nextInt(items.size())));
        dropped.setGlowing(true); 
        
        dropped.setVelocity(new Vector(
            (random.nextDouble() - 0.5) * 2.8, 
            1.4 + random.nextDouble() * 0.8, 
            (random.nextDouble() - 0.5) * 2.8
        ));
        activeLootItems.put(dropped, rarity);
    }

    private String getRandomRarityByChance() {
        double r = random.nextDouble() * 100;
        double common = plugin.getConfig().getDouble("chances.common", 50.0);
        double rare = plugin.getConfig().getDouble("chances.rare", 30.0);
        double epic = plugin.getConfig().getDouble("chances.epic", 13.0);
        double mythic = plugin.getConfig().getDouble("chances.mythic", 5.0);
        if (r < common) return "common";
        if (r < common + rare) return "rare";
        if (r < common + rare + epic) return "epic";
        if (r < common + rare + epic + mythic) return "mythic";
        return "legendary";
    }

    public void stopEvent() {
        if (eventTask != null) { eventTask.cancel(); eventTask = null; }
        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        HandlerList.unregisterAll(this);
        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        originalBlocks.clear(); activeLootItems.clear();
        Bukkit.broadcastMessage(color(plugin.getConfig().getString("messages.stop", "&#ff4500&l[ВУЛКАН] &7Магма полностью остыла. Извержение вулкана завершено!")));
    }
    
    public void stopCurrentEvent() {
        if (eventTask != null) eventTask.cancel();
        if (bossBar != null) bossBar.removeAll();
        HandlerList.unregisterAll(this);
        for (Map.Entry<Location, Material> entry : originalBlocks.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        originalBlocks.clear(); activeLootItems.clear();
    }

    public static String color(String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("&#[a-fA-F0-9]{6}");
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            String cp = text.substring(m.start(), m.end());
            text = text.replace(cp, net.md_5.bungee.api.ChatColor.of(cp.substring(1)).toString());
            m = p.matcher(text);
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', text);
    }
}
