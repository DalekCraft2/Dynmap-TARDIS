/*
 * Copyright (C) 2021 eccentric_nz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package me.eccentric_nz.dynmaptardis;

import me.eccentric_nz.tardis.TardisPlugin;
import me.eccentric_nz.tardis.api.TardisApi;
import me.eccentric_nz.tardis.api.TardisData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public class DynmapTardisPlugin extends JavaPlugin {

    private static final String INFO = "<div class=\"regioninfo\"><div class=\"infowindow\"><span style=\"font-weight:bold;\">Time Lord:</span> %owner%<br/><span style=\"font-weight:bold;\">Console type:</span> %console%<br/><span style=\"font-weight:bold;\">Chameleon circuit:</span> %chameleon%<br/><span style=\"font-weight:bold;\">Location:</span> %location%<br/><span style=\"font-weight:bold;\">Door:</span> %door%<br/><span style=\"font-weight:bold;\">Powered on:</span> %powered%<br/><span style=\"font-weight:bold;\">Siege mode:</span> %siege%<br/><span style=\"font-weight:bold;\">Occupants:</span> %occupants%</div></div>";
    private Plugin dynmap;
    private DynmapAPI dynmapApi;
    private MarkerAPI markerApi;
    private TardisApi tardisApi;
    private TardisPlugin tardis;
    private boolean reload = false;
    private boolean stop;
    private Layer tardisLayer;

    @Override
    public void onDisable() {
        if (tardisLayer != null) {
            tardisLayer.cleanup();
            tardisLayer = null;
        }
        stop = true;
    }

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        /*
         * Get dynmap
         */
        dynmap = pluginManager.getPlugin("dynmap");
        if (dynmap == null) {
            Bukkit.getLogger().log(Level.WARNING, "Cannot find dynmap!");
            return;
        }
        /*
         * Get dynmap API
         */
        dynmapApi = (DynmapAPI) dynmap;
        /*
         * Get tardis
         */
        Plugin tardis = pluginManager.getPlugin("TARDIS");
        if (tardis == null) {
            Bukkit.getLogger().log(Level.WARNING, "Cannot find TARDIS!");
            return;
        }
        this.tardis = (TardisPlugin) tardis;
        getServer().getPluginManager().registerEvents(new TardisServerListener(), this);
        /*
         * If both enabled, activate
         */
        if (dynmap.isEnabled() && this.tardis.isEnabled()) {
            activate();
        }
    }

    private void activate() {
        /*
         * Now, get markers API
         */
        markerApi = dynmapApi.getMarkerAPI();
        if (markerApi == null) {
            Bukkit.getLogger().log(Level.WARNING, "Error loading Dynmap marker API!");
            return;
        }
        /*
         * Now, get TARDIS API
         */
        tardisApi = tardis.getTardisApi();

        /*
         * If not found, signal disabled
         */
        if (tardisApi == null) {
            Bukkit.getLogger().log(Level.WARNING, "TARDIS not found - support disabled");
        }
        /*
         * Load configuration
         */
        if (reload) {
            if (tardisLayer != null) {
                if (tardisLayer.set != null) {
                    tardisLayer.set.deleteMarkerSet();
                    tardisLayer.set = null;
                }
                tardisLayer = null;
            }
        } else {
            reload = true;
        }
        /*
         * Now, add marker set for TardisApi
         */
        if (tardisApi != null) {
            tardisLayer = new TardisLayer();
        }
        /*
         * Set up update job - based on period
         */
        stop = false;
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 5 * 20);
    }

    private void updateMarkers() {
        if (tardisApi != null) {
            tardisLayer.updateMarkerSet();
        }
        getServer().getScheduler().scheduleSyncDelayedTask(this, new MarkerUpdate(), 100L);
    }

    private String formatInfoWindow(String who, TardisData tardisData) {
        String window = INFO;
        window = window.replace("%owner%", who);
        window = window.replace("%console%", tardisData.getConsole());
        window = window.replace("%chameleon%", tardisData.getChameleon());
        String location = "x: " + tardisData.getLocation().getBlockX() + ", y: " + tardisData.getLocation().getBlockY() + ", z: " + tardisData.getLocation().getBlockZ();
        window = window.replace("%location%", location);
        window = window.replace("%powered%", tardisData.getPowered());
        window = window.replace("%door%", tardisData.getDoor());
        window = window.replace("%siege%", tardisData.getSiege());
        StringBuilder travellers = new StringBuilder();
        if (tardisData.getOccupants().size() > 0) {
            for (String occupant : tardisData.getOccupants()) {
                travellers.append(occupant).append("<br />");
            }
        } else {
            travellers = new StringBuilder("Empty");
        }
        window = window.replace("%occupants%", travellers.toString());
        return window;
    }

    private abstract class Layer {

        MarkerSet set;
        MarkerIcon defaultIcon;
        String labelFormat;
        Map<String, Marker> markers = new HashMap<>();

        public Layer() {
            set = markerApi.getMarkerSet("tardis");
            if (set == null) {
                set = markerApi.createMarkerSet("tardis", "TARDISes", null, false);
            } else {
                set.setMarkerSetLabel("TARDISes");
            }
            if (set == null) {
                Bukkit.getLogger().log(Level.WARNING, "Error creating TARDIS marker set");
                return;
            }
            set.setLayerPriority(0);
            set.setHideByDefault(false);
            defaultIcon = markerApi.getMarkerIcon("tardis");
            labelFormat = "%name% (TARDIS)";
        }

        void cleanup() {
            if (set != null) {
                set.deleteMarkerSet();
                set = null;
            }
            markers.clear();
        }

        void updateMarkerSet() {
            Map<String, Marker> newMap = new HashMap<>();
            /*
             * Build new map
             */
            Map<String, TardisData> markers = getMarkers();
            markers.keySet().forEach((name) -> {
                Location location = markers.get(name).getLocation();
                String worldName = Objects.requireNonNull(location.getWorld()).getName();
                /*
                 * Get location
                 */
                String id = worldName + "/" + name;
                String label = labelFormat.replace("%name%", name);
                /*
                 * See if we already have marker
                 */
                Marker marker = this.markers.remove(id);
                if (marker == null) {
                    /*
                     * Not found? Need new one
                     */
                    marker = set.createMarker(id, label, worldName, location.getX(), location.getY(), location.getZ(), defaultIcon, false);
                } else {
                    /*
                     * Else, update position if needed
                     */
                    marker.setLocation(worldName, location.getX(), location.getY(), location.getZ());
                    marker.setLabel(label);
                    marker.setMarkerIcon(defaultIcon);
                }
                /*
                 * Build popup
                 */
                String description = formatInfoWindow(name, markers.get(name));
                /*
                 * Set popup
                 */
                marker.setDescription(description);
                /*
                 * Add to new map
                 */
                newMap.put(id, marker);
            });
            /*
             * Now, review old map - anything left is gone
             */
            this.markers.values().forEach(GenericMarker::deleteMarker);
            /*
             * And replace with new map
             */
            this.markers.clear();
            this.markers = newMap;
        }

        /*
         * Get current markers, by ID with location
         */
        public abstract Map<String, TardisData> getMarkers();
    }

    private class TardisServerListener implements Listener {

        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            Plugin plugin = event.getPlugin();
            String name = plugin.getDescription().getName();
            if (name.equals("dynmap") || name.equals("TARDIS")) {
                if (dynmap.isEnabled() && tardis.isEnabled()) {
                    activate();
                }
            }
        }
    }

    private class TardisLayer extends Layer {

        public TardisLayer() {
            super();
        }

        /*
         * Get current markers, by Time Lord with location
         */
        @Override
        public Map<String, TardisData> getMarkers() {
            HashMap<String, TardisData> map = new HashMap<>();
            if (tardisApi != null) {
                HashMap<String, Integer> timeLordMap = tardisApi.getTimeLordMap();
                timeLordMap.forEach((key, value) -> {
                    TardisData tardisData;
                    try {
                        tardisData = tardisApi.getTardisMapData(value);
                        if (tardisData.getLocation() != null) {
                            map.put(key, tardisData);
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
            return map;
        }
    }

    private class MarkerUpdate implements Runnable {

        @Override
        public void run() {
            if (!stop) {
                updateMarkers();
            }
        }
    }
}
