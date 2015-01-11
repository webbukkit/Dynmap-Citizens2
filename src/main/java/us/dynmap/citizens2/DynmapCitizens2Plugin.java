package us.dynmap.citizens2;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.event.NPCCreateEvent;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class DynmapCitizens2Plugin extends JavaPlugin {
    private static Logger log;

    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    CitizensPlugin citizens;
    private MarkerIcon deficon;
    private MarkerSet npcset;
    boolean reload = false;
    
    FileConfiguration cfg;
    
    private Set<String> existingnpcs = new HashSet<String>();

    private void processNPC(MarkerSet set, NPC npc) {
        processNPC(set, npc, null);
    }
    
    private void processNPC(MarkerSet set, NPC npc, Set<String> toremove) {
        UUID uuid = npc.getUniqueId();
        String id = "npc_" + Long.toHexString(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        Entity ent = null;
        if (npc.isSpawned()) {
            ent = npc.getEntity();
        }
        if (ent == null) {  // If null, see if we need to remove it
            if (existingnpcs.contains(id)) {    // Found?
                Marker m = set.findMarker(id);
                if (m != null) {
                    m.deleteMarker();
                }
                existingnpcs.remove(id);
            }
        }
        else {
            Location loc = ent.getLocation();
            Marker m = set.findMarker(id);
            if (m == null) {
                m = set.createMarker(id, npc.getName(), false, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), deficon, false);
                existingnpcs.add(id);
            }
            else {
                m.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
                m.setLabel(npc.getName());
            }
            if (toremove != null) {
                toremove.remove(id);
            }
        }
    }
    
    private void updateAllNPCs(MarkerSet npcset) {
        Iterable<NPCRegistry> reg = CitizensAPI.getNPCRegistries();
        HashSet<String> toremove = new HashSet<String>(existingnpcs);
        if (reg != null) {
            for (NPCRegistry r : reg) {
                for (NPC npc : r) {
                    processNPC(npcset, npc, toremove);
                }
            }
        }
        for (String s : toremove) {
            Marker m = npcset.findMarker(s);
            if (m != null) {
                m.deleteMarker();
            }
            existingnpcs.remove(s);
        }
    }

    @Override
    public void onLoad() {
        log = this.getLogger();
    }

    long updperiod;
    long playerupdperiod;
    boolean stop;
    
    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }
    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }


    private class OurNPCListener implements Listener {
        @EventHandler
        public void onNPCCreate(NPCCreateEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCRemove(NPCRemoveEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCSpawn(NPCSpawnEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCDespawn(NPCDespawnEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
        @EventHandler
        public void onNPCDeath(NPCDeathEvent evt) {
            processNPC(npcset, evt.getNPC());
        }
    }
    
    private class OurServerListener implements Listener {
        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin p = event.getPlugin();
            String name = p.getDescription().getName();
            if(name.equals("dynmap") || name.equals("Essentials")) {
                if(dynmap.isEnabled() && citizens.isEnabled())
                    activate();
            }
        }
    }
    
    public void onEnable() {
        info("initializing");
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if(dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI) dynmap; /* Get API */
        /* Get Citizens */
        Plugin p = pm.getPlugin("Citizens");
        if(p == null) {
            severe("Cannot find Citizens!");
            return;
        }
        citizens = (CitizensPlugin) p;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If both enabled, activate */
        if(dynmap.isEnabled() && citizens.isEnabled()) {
            activate();
            try { 
                MetricsLite ml = new MetricsLite(this);
                ml.start();
            } catch (IOException iox) {
            }
        }
    }

    private class MarkerUpdate implements Runnable {
        public void run() {
            if (!stop) {
                updateAllNPCs(npcset);
                getServer().getScheduler().scheduleSyncDelayedTask(DynmapCitizens2Plugin.this, this, updperiod);
            }
        }
    }
    
    private void activate() {
        /* Now, get markers API */
        markerapi = api.getMarkerAPI();
        if(markerapi == null) {
            severe("Error loading Dynmap marker API!");
            return;
        }
            
        /* Load configuration */
        if(reload) {
            reloadConfig();
        }
        else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */
        deficon = markerapi.getMarkerIcon("offlineuser");
        npcset = markerapi.getMarkerSet("Citizens2");
        if (npcset == null) {
            npcset = markerapi.createMarkerSet("Citizens2", "NPCs", null, false);
        }
        getServer().getPluginManager().registerEvents(new OurNPCListener(), this);        
        
        /* Set up update job - based on period */
        double per = cfg.getDouble("update.period", 5.0);
        if(per < 2.0) per = 2.0;
        updperiod = (long)(per*20.0);
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5*20);

        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        stop = true;
    }

}
