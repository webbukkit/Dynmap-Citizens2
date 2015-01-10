package us.dynmap.citizens2;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.citizensnpcs.api.CitizensPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;

public class DynmapCitizens2Plugin extends JavaPlugin {
    private static Logger log;

    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;
    CitizensPlugin citizens;
    boolean reload = false;
    
    FileConfiguration cfg;
    
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
        api = (DynmapAPI)dynmap; /* Get API */
        /* Get Citizens */
        Plugin p = pm.getPlugin("Citizens");
        if(p == null) {
            severe("Cannot find Citizens!");
            return;
        }
        citizens = (CitizensPlugin) p;

        getServer().getPluginManager().registerEvents(new OurServerListener(), this);        

        /* If both enabled, activate */
        if(dynmap.isEnabled() && citizens.isEnabled())
            activate();
        
        try { 
            MetricsLite ml = new MetricsLite(this);
            ml.start();
        } catch (IOException iox) {
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
        
        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        stop = true;
    }

}
