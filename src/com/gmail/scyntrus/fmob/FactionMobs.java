package com.gmail.scyntrus.fmob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_8_R3.Entity;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;
import org.mcstats.Metrics.Graph;

import com.gmail.scyntrus.fmob.mobs.Archer;
import com.gmail.scyntrus.fmob.mobs.Mage;
import com.gmail.scyntrus.fmob.mobs.Swordsman;
import com.gmail.scyntrus.fmob.mobs.Titan;
import com.gmail.scyntrus.ifactions.Faction;
import com.gmail.scyntrus.ifactions.FactionsManager;
import com.gmail.scyntrus.ifactions.Rank;

public class FactionMobs extends JavaPlugin {

    public PluginManager pm = null;
    public static List<FactionMob> mobList = new ArrayList<FactionMob>();
    public static Map<String,Integer> factionColors = new HashMap<String,Integer>();
    public Map<String,Boolean> mobLeader = new HashMap<String,Boolean>();
    public Map<String,List<FactionMob>> playerSelections = new HashMap<String,List<FactionMob>>();
    public static long mobCount = 0;
    public static String sndBreath = null;
    public static String sndHurt = null;
    public static String sndDeath = null;
    public static String sndStep = null;
    public static int spawnLimit = 50;
    public static int mobsPerFaction = 0;
    public static boolean attackMobs = true;
    public static boolean noFriendlyFire = false;
    public static boolean noPlayerFriendlyFire = false;
    public static boolean displayMobFaction = true;
    public static boolean alertAllies = true;
    private long saveInterval = 6000;
    public Economy econ = null;
    public Boolean vaultEnabled = false;
    public static double mobSpeed = .3;
    public static double mobPatrolSpeed = .175;
    public static double mobNavRange = 64;
    public static FactionMobs instance;
    public static boolean scheduleChunkMobLoad = false;
    public static int chunkMobLoadTask = -1;
    public static boolean feedEnabled = true;
    public static int feedItem = 260;
    public static float feedAmount = 5;
    public static boolean silentErrors = true;
    private static String minRankToSpawnStr = "MEMBER";
    public static Rank minRankToSpawn;

    @Override
    public void onEnable() {
        FactionMobs.instance = this;
        this.saveDefaultConfig();
        FileConfiguration config = this.getConfig();
        config.options().copyDefaults(true);
        this.saveConfig();
        FactionMobs.silentErrors = config.getBoolean("silentErrors", FactionMobs.silentErrors);
        ErrorManager.initErrorStream();

        try {
            VersionManager.checkVersion();
        } catch (VersionManager.VersionException e) {
            ErrorManager.handleError(e.getMessage(), e);
            this.getCommand("fm").setExecutor(new ErrorCommand(this));
            this.getCommand("fmc").setExecutor(new ErrorCommand(this));
            return;
        }

        Utils.copyDefaultConfig();

        if (!FactionsManager.init(this.getName())) {
            ErrorManager.handleError("You are running an unsupported version of Factions. Please contact the plugin author for more info.", null);
            this.getCommand("fm").setExecutor(new ErrorCommand(this));
            this.getCommand("fmc").setExecutor(new ErrorCommand(this));
            return;
        }

        int modelNum = 51;
        switch (config.getInt("model")) {
            case 0: // skeleton
                modelNum = 51;
                //FactionMobs.sndBreath = "mob.skeleton.say";
                FactionMobs.sndHurt = "mob.skeleton.hurt";
                FactionMobs.sndDeath = "mob.skeleton.death";
                FactionMobs.sndStep = "mob.skeleton.step";
                break;
            case 1: // zombie
                modelNum = 54;
                //FactionMobs.sndBreath = "mob.zombie.say";
                FactionMobs.sndHurt = "mob.zombie.hurt";
                FactionMobs.sndDeath = "mob.zombie.death";
                FactionMobs.sndStep = "mob.zombie.step";
                break;
            case 2: // pigzombie
                modelNum = 57;
                //FactionMobs.sndBreath = "mob.zombiepig.zpig";
                FactionMobs.sndHurt = "mob.zombiepig.zpighurt";
                FactionMobs.sndDeath = "mob.zombiepig.zpigdeath";
                FactionMobs.sndStep = "mob.zombie.step";
                break;
        }

        FactionMobs.spawnLimit = config.getInt("spawnLimit", FactionMobs.spawnLimit);
        FactionMobs.mobsPerFaction = config.getInt("mobsPerFaction", FactionMobs.mobsPerFaction);
        FactionMobs.noFriendlyFire = config.getBoolean("noFriendlyFire", FactionMobs.noFriendlyFire);
        FactionMobs.noPlayerFriendlyFire = config.getBoolean("noPlayerFriendlyFire", FactionMobs.noPlayerFriendlyFire);
        FactionMobs.alertAllies = config.getBoolean("alertAllies", FactionMobs.alertAllies);
        FactionMobs.displayMobFaction = config.getBoolean("displayMobFaction", FactionMobs.displayMobFaction);
        FactionMobs.attackMobs = config.getBoolean("attackMobs", FactionMobs.attackMobs);
        FactionMobs.mobSpeed = (float) config.getDouble("mobSpeed", FactionMobs.mobSpeed);
        FactionMobs.mobPatrolSpeed = (float) config.getDouble("mobPatrolSpeed", FactionMobs.mobPatrolSpeed);
        FactionMobs.mobPatrolSpeed = FactionMobs.mobPatrolSpeed / FactionMobs.mobSpeed;
        FactionMobs.mobNavRange = (float) config.getDouble("mobNavRange", FactionMobs.mobNavRange);
        FactionMobs.feedEnabled = config.getBoolean("feedEnabled", FactionMobs.feedEnabled);
        FactionMobs.feedItem = config.getInt("feedItem", FactionMobs.feedItem);
        FactionMobs.feedAmount = (float) config.getDouble("feedAmount", FactionMobs.feedAmount);
        FactionMobs.minRankToSpawnStr = config.getString("mustBeAtleast", FactionMobs.minRankToSpawnStr);
        FactionMobs.minRankToSpawn = Rank.getByName(FactionMobs.minRankToSpawnStr);

        Archer.maxHp = (float) config.getDouble("Archer.maxHp", Archer.maxHp);
        if (Archer.maxHp<1) Archer.maxHp = 1;
        Mage.maxHp = (float) config.getDouble("Mage.hp", Mage.maxHp);
        if (Mage.maxHp<1) Mage.maxHp = 1;
        Swordsman.maxHp = (float) config.getDouble("Swordsman.maxHp", Swordsman.maxHp);
        if (Swordsman.maxHp<1) Swordsman.maxHp = 1;
        Titan.maxHp = (float) config.getDouble("Titan.maxHp", Titan.maxHp);
        if (Titan.maxHp<1) Titan.maxHp = 1;

        Archer.damage = config.getDouble("Archer.damage", Archer.damage);
        if (Archer.damage<0) Archer.damage = 0;
        Swordsman.damage = config.getDouble("Swordsman.damage", Swordsman.damage);
        if (Swordsman.damage<0) Swordsman.damage = 0;
        Titan.damage = config.getDouble("Titan.damage", Titan.damage);
        if (Titan.damage<0) Titan.damage = 0;

        Archer.enabled = config.getBoolean("Archer.enabled", Archer.enabled);
        Mage.enabled = config.getBoolean("Mage.enabled", Mage.enabled);
        Swordsman.enabled = config.getBoolean("Swordsman.enabled", Swordsman.enabled);
        Titan.enabled = config.getBoolean("Titan.enabled", Titan.enabled);
        Archer.powerCost = config.getDouble("Archer.powerCost", Archer.powerCost);
        Archer.moneyCost = config.getDouble("Archer.moneyCost", Archer.moneyCost);
        Mage.powerCost = config.getDouble("Mage.powerCost", Mage.powerCost);
        Mage.moneyCost = config.getDouble("Mage.moneyCost", Mage.moneyCost);
        Swordsman.powerCost = config.getDouble("Swordsman.powerCost", Swordsman.powerCost);
        Swordsman.moneyCost = config.getDouble("Swordsman.moneyCost", Swordsman.moneyCost);
        Titan.powerCost = config.getDouble("Titan.powerCost", Titan.powerCost);
        Titan.moneyCost = config.getDouble("Titan.moneyCost", Titan.moneyCost);
        Archer.drops = config.getInt("Archer.drops", 0);
        Mage.drops = config.getInt("Mage.drops", 0);
        Swordsman.drops = config.getInt("Swordsman.drops", 0);
        Titan.drops = config.getInt("Titan.drops", 0);

        this.pm = this.getServer().getPluginManager();
        if (!ReflectionManager.init()) {
            this.getLogger().severe("[Fatal Error] Unable to register mobs");
            this.getCommand("fm").setExecutor(new ErrorCommand(this));
            this.getCommand("fmc").setExecutor(new ErrorCommand(this));
            return;
        }
        try {
            addEntityType(Archer.class, Archer.typeName, modelNum);
            addEntityType(Swordsman.class, Swordsman.typeName, modelNum);
            addEntityType(Mage.class, Mage.typeName, modelNum);
            addEntityType(Titan.class, Titan.typeName, 99);
        } catch (Exception e) {
            this.getLogger().severe("[Fatal Error] Unable to register mobs");
            this.getCommand("fm").setExecutor(new ErrorCommand(this));
            this.getCommand("fmc").setExecutor(new ErrorCommand(this));
            return;
        }

        this.getCommand("fm").setExecutor(new FmCommand(this));
        if (config.getBoolean("fmcEnabled", false)) {
            this.getCommand("fmc").setExecutor(new FmcCommand(this));
        }

        this.pm.registerEvents(new EntityListener(this), this);
        this.pm.registerEvents(new CommandListener(this), this);

        switch (FactionsManager.getFactionsVersion()) {
            case F2:
                this.pm.registerEvents(new FactionListener2(this), this);
                break;
            case F16:
            case F16U:
            case F18:
                this.pm.registerEvents(new FactionListener68(this), this);
                break;
            case TOWNY:
                this.pm.registerEvents(new TownyListener(this), this);
                break;
            case INVALID:
                // Should never happen
                break;
        }

        File colorFile = new File(getDataFolder(), "colors.dat");
        if (colorFile.exists()){
            FileInputStream fileInputStream = null;
            try {
                fileInputStream = new FileInputStream(colorFile);
                ObjectInputStream oInputStream = new ObjectInputStream(fileInputStream);
                @SuppressWarnings("unchecked")
                HashMap<String, Integer> colorMap = (HashMap<String, Integer>) oInputStream.readObject();
                FactionMobs.factionColors = colorMap;
                oInputStream.close();
                fileInputStream.close();
            } catch (Exception e) {
                ErrorManager.handleError("Error reading faction colors file, colors.dat.", e);
            }
        }

        if (config.getBoolean("autoSave", false)) {
            this.saveInterval = config.getLong("saveInterval", this.saveInterval);
            if (this.saveInterval > 0) {
                this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new AutoSaver(this), this.saveInterval, this.saveInterval);
                System.out.println("[FactionMobs] Auto-Save enabled.");
            }
        }

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
                if (econ != null) {
                    vaultEnabled = true;
                }
            }
        }
        if (vaultEnabled) {
            System.out.println("[FactionMobs] Vault detected.");
        } else {
            System.out.println("[FactionMobs] Vault not detected.");
        }

        runMetrics(); // using mcstats.org metrics

        this.loadMobList();

        chunkMobLoadTask = this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new ChunkMobLoader(this), 4, 4);
    }

    private void addEntityType(Class<? extends Entity> paramClass, String paramString, int paramInt) {
        ReflectionManager.mapC.put(paramString, paramClass);
        ReflectionManager.mapD.put(paramClass, paramString);
        ReflectionManager.mapF.put(paramClass, Integer.valueOf(paramInt));
        ReflectionManager.mapG.put(paramString, Integer.valueOf(paramInt));
    }

    private void runMetrics() {
        try {
            Plugin plugin = this.getServer().getPluginManager().getPlugin("Factions");
            String factionsVersion = "";
            if (plugin == null) {
                plugin = this.getServer().getPluginManager().getPlugin("Towny");
                factionsVersion = "T";
            }
            factionsVersion = factionsVersion + plugin.getDescription().getVersion();
            String factionMobsVersion = this.getServer().getPluginManager().getPlugin("FactionMobs").getDescription().getVersion();
            Metrics metrics = new Metrics(this);

            Graph versionGraph = metrics.createGraph("Factions Version");

            versionGraph.addPlotter(new Metrics.Plotter(factionsVersion) {

                @Override
                public int getValue() {
                    return 1;
                }

            });

            Graph versionComboGraph = metrics.createGraph("Version Combination");

            versionComboGraph.addPlotter(new Metrics.Plotter(factionMobsVersion+":"+factionsVersion) {

                @Override
                public int getValue() {
                    return 1;
                }

            });

            metrics.start();
        } catch (Exception e) {
            ErrorManager.handleError("Metrics failed to start", e);
        }
    }

    @Override
    public void onDisable() {
        this.saveMobList();
        for (int i = mobList.size() - 1; i >= 0; --i) {
            mobList.get(i).forceDie();
        }
        ErrorManager.closeErrorStream();
    }

    public void loadMobList() {
        File file = new File(getDataFolder(), "data.dat");
        boolean backup = false;
        if (file.exists()) {
            YamlConfiguration conf = YamlConfiguration.loadConfiguration(file);
            @SuppressWarnings("unchecked")
            List<List<String>> save = (List<List<String>>) conf.getList("data", new ArrayList<List<String>>());
            for (List<String> mobData : save) {
                FactionMob newMob = null;
                if (mobData.size() < 10) {
                    System.out.println("Incomplete Faction Mob found and removed. Did you edit the data.dat file?");
                    backup = true;
                    continue;
                }
                org.bukkit.World world = this.getServer().getWorld(mobData.get(1));
                if (world == null) {
                    System.out.println("Worldless Faction Mob found and removed. Did you delete or rename a world?");
                    backup = true;
                    continue;
                }
                Faction faction = FactionsManager.getFactionByName(mobData.get(2));
                if (faction == null || faction.isNone()) {
                    System.out.println("Factionless Faction Mob found and removed. Did you delete a Faction?");
                    backup = true;
                    continue;
                }
                Location spawnLoc = new Location(
                        world,
                        Double.parseDouble(mobData.get(3)),
                        Double.parseDouble(mobData.get(4)),
                        Double.parseDouble(mobData.get(5)));
                if (mobData.get(0).equalsIgnoreCase("Archer") || mobData.get(0).equalsIgnoreCase("Ranger")) {
                    newMob = new Archer(spawnLoc, faction);
                } else if (mobData.get(0).equalsIgnoreCase("Mage")) {
                    newMob = new Mage(spawnLoc, faction);
                } else if (mobData.get(0).equalsIgnoreCase("Swordsman")) {
                    newMob = new Swordsman(spawnLoc, faction);
                } else if (mobData.get(0).equalsIgnoreCase("Titan")) {
                    newMob = new Titan(spawnLoc, faction);
                } else {
                    continue;
                }
                if (newMob.getFaction() == null || newMob.getFactionName() == null || newMob.getFaction().isNone()) {
                    System.out.println("Factionless Faction Mob found and removed. Did something happen to Factions?");
                    backup = true;
                    continue;
                }
                newMob.getEntity().setPosition(Double.parseDouble(mobData.get(6)),
                        Double.parseDouble(mobData.get(7)),
                        Double.parseDouble(mobData.get(8)));
                newMob.getEntity().setHealth(Float.parseFloat(mobData.get(9)));

                if (mobData.size() > 10) {
                    newMob.setPoi(
                            Double.parseDouble(mobData.get(10)),
                            Double.parseDouble(mobData.get(11)),
                            Double.parseDouble(mobData.get(12)));
                    newMob.setOrder(mobData.get(13));
                } else {
                    newMob.setPoi(
                            Double.parseDouble(mobData.get(6)),
                            Double.parseDouble(mobData.get(7)),
                            Double.parseDouble(mobData.get(8)));
                    newMob.setOrder("poi");
                }

                newMob.getEntity().world.addEntity((Entity) newMob, SpawnReason.CUSTOM);
                mobList.add(newMob);
                newMob.getEntity().dead = false;
            }
            if (backup) {
                try {
                    conf.save(new File(getDataFolder(), "data_backup.dat"));
                    System.out.println("Backup file saved as data_backup.dat");
                } catch (IOException e) {
                    System.out.println("Failed to save backup file");
                    if (!FactionMobs.silentErrors) e.printStackTrace();
                }
            }
        }
    }

    public void saveMobList() {
        YamlConfiguration conf = new YamlConfiguration();
        List<List<String>> save = new ArrayList<List<String>>();
        for (FactionMob fmob : mobList) {
            if (fmob.getFaction() == null || fmob.getFaction().isNone()) {
                continue;
            }
            List<String> mobData = new ArrayList<String>();
            mobData.add(fmob.getTypeName()); //0
            Location spawnLoc = fmob.getSpawn();
            mobData.add(spawnLoc.getWorld().getName()); //1
            mobData.add(fmob.getFactionName()); //2
            mobData.add(""+spawnLoc.getX()); //3
            mobData.add(""+spawnLoc.getY());
            mobData.add(""+spawnLoc.getZ());
            mobData.add(""+fmob.getlocX()); //6
            mobData.add(""+fmob.getlocY());
            mobData.add(""+fmob.getlocZ());
            mobData.add(""+fmob.getEntity().getHealth()); //9
            mobData.add(""+fmob.getPoiX()); //10
            mobData.add(""+fmob.getPoiY());
            mobData.add(""+fmob.getPoiZ());
            mobData.add(fmob.getOrder()); //13
            save.add(mobData);
        }
        conf.set("data", save);
        try {
            conf.save(new File(getDataFolder(), "data.dat"));
            System.out.println("FactionMobs data saved.");
        } catch (IOException e) {
            ErrorManager.handleError("Failed to save faction mob data, data.dat", e);
        }
        try {
            File colorFile = new File(getDataFolder(), "colors.dat");
            colorFile.createNewFile();
            FileOutputStream fileOut = new FileOutputStream(colorFile);
            ObjectOutputStream oOut = new ObjectOutputStream(fileOut);
            oOut.writeObject(FactionMobs.factionColors);
            oOut.close();
            fileOut.close();
            System.out.println("FactionMobs color data saved.");
        } catch (Exception e) {
            ErrorManager.handleError("Error writing faction colors file, colors.dat", e);
        }
    }

    public void updateList() {
        for (int i = mobList.size()-1; i >= 0; i--) {
            mobList.get(i).updateMob();
        }
    }

    public static final String signature_Author = "Scyntrus";
    public static final String signature_URL = "http://dev.bukkit.org/bukkit-plugins/faction-mobs/";
    public static final String signature_Source = "http://github.com/Scyntrus/FactionMobs";
}
