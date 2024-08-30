package org.charlie.cRGBArmor;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class CRGBArmor extends JavaPlugin implements TabExecutor {

    private Map<String, SpecialArmor> specialArmors;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();
        loadSpecialArmors();

        this.getCommand("crgbarmor").setExecutor(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack[] armor = player.getInventory().getArmorContents();
                    for (ItemStack item : armor) {
                        if (item != null && item.getItemMeta() != null) {
                            if (item.getItemMeta().getLore() != null) {
                                for (SpecialArmor specialArmor : specialArmors.values()) {
                                    if (item.getItemMeta().getLore().contains(specialArmor.getName())) {
                                        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
                                        Color color = specialArmor.updateColor();
                                        meta.setColor(color);

                                        String armorPieceName = getArmorPieceName(item.getType());
                                        if (specialArmor.shouldGlow(armorPieceName)) {
                                            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                                            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                                        } else {
                                            meta.removeEnchant(Enchantment.LUCK_OF_THE_SEA);
                                        }

                                        item.setItemMeta(meta);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("crgbarmor.admin")) {
            sender.sendMessage(formatMessage("no_permission"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSpecialArmors();
            loadMessages();
            sender.sendMessage(formatMessage("reload_success"));
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(formatMessage("invalid_usage"));
            return false;
        }

        String armorType = args[0].toLowerCase();
        String armorPiece = args[1].toLowerCase();
        String playerName = args[2];

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage(formatMessage("player_not_found"));
            return false;
        }

        SpecialArmor specialArmor = specialArmors.get(armorType);
        if (specialArmor == null) {
            sender.sendMessage(formatMessage("armor_type_not_found"));
            return false;
        }

        if (!specialArmor.getArmorPieces().contains(armorPiece)) {
            sender.sendMessage(formatMessage("invalid_armor_piece"));
            return false;
        }

        ItemStack armor = null;

        switch (armorPiece) {
            case "helmet":
                armor = new ItemStack(Material.LEATHER_HELMET);
                break;
            case "chestplate":
                armor = new ItemStack(Material.LEATHER_CHESTPLATE);
                break;
            case "leggings":
                armor = new ItemStack(Material.LEATHER_LEGGINGS);
                break;
            case "boots":
                armor = new ItemStack(Material.LEATHER_BOOTS);
                break;
            default:
                sender.sendMessage(formatMessage("invalid_armor_piece"));
                return false;
        }

        LeatherArmorMeta meta = (LeatherArmorMeta) armor.getItemMeta();
        if (meta != null) {
            meta.setColor(specialArmor.getCurrentColor());
            meta.setLore(Collections.singletonList(specialArmor.getName()));

            if (specialArmor.shouldGlow(armorPiece)) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            }

            if (specialArmor.isUnbreakable()) {
                meta.setUnbreakable(true);
            }

            armor.setItemMeta(meta);
        }

        targetPlayer.getInventory().addItem(armor);
        sender.sendMessage(formatMessage("armor_given")
                .replace("%armor_piece%", armorPiece)
                .replace("%armor_name%", specialArmor.getName())
                .replace("%player%", playerName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(specialArmors.keySet());
            options.add("reload");
            return options;
        }
        if (args.length == 2) {
            if (specialArmors.containsKey(args[0].toLowerCase())) {
                return specialArmors.get(args[0].toLowerCase()).getArmorPieces();
            }
        }
        if (args.length == 3) {
            return null;
        }
        return Collections.emptyList();
    }

    private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String formatMessage(String key) {
        String message = messages.getString(key, "&cMessage not found: " + key);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void loadSpecialArmors() {
        specialArmors = new HashMap<>();
        FileConfiguration config = getConfig();
        for (String key : config.getConfigurationSection("special_armor").getKeys(false)) {
            String name = config.getString("special_armor." + key + ".name");
            int speed = config.getInt("special_armor." + key + ".speed", 1);
            boolean unbreakable = config.getBoolean("special_armor." + key + ".unbreakable", false);
            List<String> colorStrings = config.getStringList("special_armor." + key + ".color_sequence");
            List<Color> colors = new ArrayList<>();
            for (String colorString : colorStrings) {
                String[] rgb = colorString.split(",");
                int r = Integer.parseInt(rgb[0]);
                int g = Integer.parseInt(rgb[1]);
                int b = Integer.parseInt(rgb[2]);
                colors.add(Color.fromRGB(r, g, b));
            }
            List<String> armorPieces = config.getStringList("special_armor." + key + ".armor_pieces");

            Map<String, Boolean> glowSettings = new HashMap<>();
            ConfigurationSection glowSection = config.getConfigurationSection("special_armor." + key + ".glow");
            if (glowSection != null) {
                for (String piece : armorPieces) {
                    glowSettings.put(piece, glowSection.getBoolean(piece, false));
                }
            }

            specialArmors.put(key, new SpecialArmor(name, colors, armorPieces, glowSettings, speed, unbreakable));
        }
    }

    private static String getArmorPieceName(Material material) {
        switch (material) {
            case LEATHER_HELMET:
                return "helmet";
            case LEATHER_CHESTPLATE:
                return "chestplate";
            case LEATHER_LEGGINGS:
                return "leggings";
            case LEATHER_BOOTS:
                return "boots";
            default:
                return "";
        }
    }

    private static class SpecialArmor {
        private final String name;
        private final List<Color> colors;
        private final List<String> armorPieces;
        private final Map<String, Boolean> glowSettings;
        private final int stepSize;
        private final boolean unbreakable;
        private int colorIndex = 0;
        private int currentRed;
        private int currentGreen;
        private int currentBlue;
        private Color currentTargetColor;

        public SpecialArmor(String name, List<Color> colors, List<String> armorPieces, Map<String, Boolean> glowSettings, int speed, boolean unbreakable) {
            this.name = name;
            this.colors = colors;
            this.armorPieces = armorPieces;
            this.glowSettings = glowSettings;
            this.stepSize = speed;
            this.unbreakable = unbreakable;

            Color initialColor = colors.get(0);
            this.currentRed = initialColor.getRed();
            this.currentGreen = initialColor.getGreen();
            this.currentBlue = initialColor.getBlue();
            this.currentTargetColor = colors.get(1);
        }

        public String getName() {
            return name;
        }

        public List<String> getArmorPieces() {
            return armorPieces;
        }

        public boolean shouldGlow(String armorPiece) {
            return glowSettings.getOrDefault(armorPiece, false);
        }

        public boolean isUnbreakable() {
            return unbreakable;
        }

        public Color getCurrentColor() {
            return Color.fromRGB(currentRed, currentGreen, currentBlue);
        }

        private void setNextTargetColor() {
            colorIndex = (colorIndex + 1) % colors.size();
            currentTargetColor = colors.get(colorIndex);
        }

        public Color updateColor() {
            if (currentRed != currentTargetColor.getRed()) {
                currentRed += Math.signum(currentTargetColor.getRed() - currentRed) * Math.min(stepSize, Math.abs(currentTargetColor.getRed() - currentRed));
            }
            if (currentGreen != currentTargetColor.getGreen()) {
                currentGreen += Math.signum(currentTargetColor.getGreen() - currentGreen) * Math.min(stepSize, Math.abs(currentTargetColor.getGreen() - currentGreen));
            }
            if (currentBlue != currentTargetColor.getBlue()) {
                currentBlue += Math.signum(currentTargetColor.getBlue() - currentBlue) * Math.min(stepSize, Math.abs(currentTargetColor.getBlue() - currentBlue));
            }

            if (currentRed == currentTargetColor.getRed() && currentGreen == currentTargetColor.getGreen() && currentBlue == currentTargetColor.getBlue()) {
                setNextTargetColor();
            }

            return getCurrentColor();
        }
    }
}
