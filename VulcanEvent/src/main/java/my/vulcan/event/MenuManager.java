package my.vulcan.event;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MenuManager implements Listener {
    private static final Map<UUID, String> openMenus = new HashMap<>();
    private static final Map<String, List<ItemStack>> lootStorage = new HashMap<>();
    private static File lootFile;
    private static YamlConfiguration lootConfig;

    public MenuManager(Main plugin) {
        lootFile = new File(plugin.getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            try { 
                plugin.getDataFolder().mkdirs();
                lootFile.createNewFile(); 
            } catch (IOException e) { 
                e.printStackTrace(); 
            }
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        loadAllLoot();
    }

    public static void openRarityMenu(Player player, String rarity) {
        Inventory inv = Bukkit.createInventory(null, 54, "Лут: " + rarity.toUpperCase());
        
        List<ItemStack> items = lootStorage.getOrDefault(rarity, new ArrayList<>());
        for (int i = 0; i < Math.min(items.size(), 54); i++) {
            inv.setItem(i, items.get(i));
        }
        
        openMenus.put(player.getUniqueId(), rarity);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (!openMenus.containsKey(player.getUniqueId())) return;

        String rarity = openMenus.remove(player.getUniqueId());
        List<ItemStack> items = new ArrayList<>();

        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                items.add(item);
            }
        }

        lootStorage.put(rarity, items);
        lootConfig.set("loot." + rarity, items);
        try {
            lootConfig.save(lootFile);
            player.sendMessage("§e[Вулкан] Лут для редкости " + rarity + " успешно сохранен!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<ItemStack> getRarityItems(String rarity) {
        return lootStorage.getOrDefault(rarity, new ArrayList<>());
    }

    private void loadAllLoot() {
        if (!lootConfig.contains("loot")) return;
        for (String rarity : lootConfig.getConfigurationSection("loot").getKeys(false)) {
            List<?> rawList = lootConfig.getList("loot." + rarity);
            if (rawList != null) {
                List<ItemStack> items = new ArrayList<>();
                for (Object obj : rawList) {
                    if (obj instanceof ItemStack item) items.add(item);
                }
                lootStorage.put(rarity, items);
            }
        }
    }
}