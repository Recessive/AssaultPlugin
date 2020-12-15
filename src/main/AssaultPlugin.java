package main;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.Plugin;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

import static mindustry.Vars.*;
import static mindustry.Vars.netServer;

public class AssaultPlugin extends Plugin {

    private Random rand = new Random(System.currentTimeMillis());

    private Preferences prefs;
    private int launchWave;
    private int techLevel;
    private int wave = 0;
    private boolean finalWave = false;
    private int buildingScore = 0;

    private int seconds;
    private float realTime = 0f;
    private static long startTime = System.currentTimeMillis();

    private final Rules rules = new Rules();
    private int currMap;
    private mindustry.maps.Map loadedMap;
    private String mapID;

    private RTInterval buildScoreInterval = new RTInterval(10000);

    private HashMap<String, CustomPlayer> uuidMapping = new HashMap<>();

    private final DBInterface mapDB = new DBInterface("map_data");
    private final DBInterface playerDB = new DBInterface("player_data");

    @Override
    public void init(){

        mapDB.connect("../network-files/assault_data.db");
        playerDB.connect(mapDB.conn);

        init_rules();

        Events.on(EventType.PlayerJoinSecondary.class, event ->{
            event.player.sendMessage(motd());
            if(!playerDB.hasRow(event.player.uuid())){
                playerDB.addRow(event.player.uuid());
            }
            playerDB.loadRow(event.player.uuid());

            if(!uuidMapping.containsKey(event.player.uuid())){
                int xp = (int) playerDB.safeGet(event.player.uuid(), "xp");
                uuidMapping.put(event.player.uuid(), new CustomPlayer(event.player, xp));
            }

            event.player.name = StringHandler.determineRank((int) playerDB.safeGet(event.player.uuid(), "xp")) + " " + event.player.name;

        });

        Events.on(EventType.Trigger.class, event ->{
           realTime = System.currentTimeMillis() - startTime;
           seconds = (int) (realTime / 1000);
        });

        Events.on(EventType.BlockDestroyEvent.class, event ->{
            if(event.tile.team() != Team.crux && event.tile.block() instanceof CoreBlock && event.tile.team().cores().size == 1){
                endgame(false);
            }

            if(event.tile.team() == Team.crux){


                if(!(event.tile.block() instanceof CoreBlock)){
                    buildingScore += calcValue(Arrays.stream(event.tile.block().requirements).toArray());
                    if(buildScoreInterval.get(buildingScore)){
                        for(Player player : Groups.player){
                            playerDB.safePut(player.uuid(), "xp", (int) playerDB.safeGet(player.uuid(), "xp") + 50*(player.donateLevel + 1));
                            player.sendMessage("[accent]+[scarlet]" + 50*(player.donateLevel + 1) + "[accent] xp for " +
                                    "destroying [scarlet]10,000[accent] points of building worth");
                        }
                    }
                }
                if(event.tile.block() instanceof CoreBlock){
                    for(Player player : Groups.player){
                        playerDB.safePut(player.uuid(), "xp", (int) playerDB.safeGet(player.uuid(), "xp") + 50*(player.donateLevel + 1));
                        player.sendMessage("[accent]+[scarlet]" + 50*(player.donateLevel + 1) + "[accent] xp for " +
                                "destroying a core");
                    }

                }

                if(event.tile.block() instanceof CoreBlock && Team.crux.cores().size == 1){
                    endgame(true);
                }

            }


        });

        Events.on(EventType.PlayerLeave.class, event -> {
            savePlayerData(event.player.uuid());
        });

        Events.on(EventType.TapEvent.class, event ->{
            if(event.tile.block() == Blocks.vault && event.tile.team() != Team.purple){
                if(event.tile.build.items.has(Items.thorium, 997)){
                    if(uuidMapping.get(event.player.uuid()).coresLeft < 1){
                        event.player.sendMessage("[accent]You can only place 1 core shard per game!");
                        return;
                    }
                    uuidMapping.get(event.player.uuid()).coresLeft -= 1;
                    event.tile.build.tile.setNet(Blocks.coreShard, event.tile.team(), 0);
                    event.player.sendMessage("[accent]You placed a core shard! " +
                            "(by filling a vault with thorium and tapping/clicking it)");
                }
            }
        });

        Events.on(EventType.CustomEvent.class, event ->{
            if(event.value instanceof String[] && ((String[]) event.value)[0].equals("newName")){
                String[] val = (String[]) event.value;
                Player ply = uuidMapping.get(val[1]).player;
                ply.name = StringHandler.determineRank((int) playerDB.safeGet(val[1],"xp")) + " " + ply.name;
            }
        });
    }


    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("assault", "[map]", "Begin hosting the Assault gamemode.", args -> {
            if (!Vars.state.is(GameState.State.menu)) {
                Log.err("Stop the server first.");
                return;
            }

            prefs = Preferences.userRoot().node(this.getClass().getName());
            currMap = prefs.getInt("mapchoice",0);
            int i = 0;
            for(mindustry.maps.Map map : maps.customMaps()){
                Log.info(i + ": " + map.name());
                i += 1;
            }

            if(args.length != 0){
                currMap = Integer.parseInt(args[0]);
            }


            mindustry.maps.Map map = maps.customMaps().get(currMap);
            Log.info("Loading map " + map.name());
            world.loadMap(map);
            loadedMap = map;
            rules.spawns = state.map.rules().spawns;
            rules.waveSpacing = state.map.rules().waveSpacing;
            /*rules.launchWaveMultiplier = 3;
            rules.bossWaveMultiplier = 3;*/

            Log.info("Map " + map.name() + " loaded");

            // Create cells objects

            state.rules = rules.copy();
            logic.play();

            netServer.openServer();

            prefs.putInt("mapchoice", currMap);
            mapID = map.file.name().split("_")[0];
            if(!mapDB.hasRow(mapID)){
                mapDB.addRow(mapID);
            }
            mapDB.loadRow(mapID);
        });

        handler.register("nextmap", "<map>", "End the game and force start the specified map.", args -> {
            endgame(false);
            prefs.putInt("mapchoice", Integer.parseInt(args[0]));
            Log.info("Game ended with next map " + args[0]);

        });

        handler.register("setxp", "<uuid> <xp>", "Set the xp of a player", args -> {
            int newXp;
            try{
                newXp = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid xp input '" + args[1] + "'");
                return;
            }

            if(!playerDB.entries.containsKey(args[0])){
                playerDB.loadRow(args[0]);
                playerDB.safePut(args[0],"xp", newXp);
                playerDB.saveRow(args[0]);
            }else{
                playerDB.safePut(args[0],"xp", newXp);
            }
            Log.info("Set uuid " + args[0] + " to have xp of " + args[1]);

        });

        handler.register("manual_reset", "Perform a manual reset of monthly map stats and xp", args -> {
            rankReset();
            mapStatsReset();
            Log.info("monthly map stats and xp have been reset");
        });
    }

    public void registerClientCommands(CommandHandler handler) {

        handler.<Player>register("info", "Display info about the gamemode", (args, player) -> {
            player.sendMessage("[#4d004d]{[purple]AA[#4d004d]}[cyan]Assault [accent] is essentially Attack but" +
                    " a race to see how fast you can beat the map. Set records and destroy buildings to get xp!");
        });


        handler.<Player>register("xp", "[sky]Show your xp", (args, player) -> {
            int xp = (int) playerDB.safeGet(player.uuid(), "xp");

            String s = "[scarlet]" + xp + "[accent] xp\n";/*nGet [scarlet]" + leftover + "[accent] more xp for 1 additional ";
            if(leftover < 10000){
                s += "[lime]Level 1";
            }else if (leftover < 20000){
                s += "[acid]Level 2";
            } else{
                s += "[green]Level 3";
            }
            s += "[accent] boost!\n";*/

            String nextRank = StringHandler.determineRank(xp+5000);
            player.sendMessage(s + "Reach [scarlet]" + (xp/5000+1)*5000 + "[accent] xp to reach " + nextRank + "[accent] rank.");

        });

        handler.<Player>register("stats", "Display the stats for this map", (args, player) -> {
            player.sendMessage(motd());
        });

        handler.<Player>register("time", "Display the current time", (args, player) -> {
            player.sendMessage(formatSeconds(seconds));
        });

    }


    void init_rules(){
        rules.waitEnemies = false;
        // rules.enemyCheat = true;
        rules.waves = true;
        rules.buildSpeedMultiplier = 1.5f;
        rules.canGameOver = false;
        rules.buildSpeedMultiplier = 2;
    }


    void endgame(boolean win){
        Log.info("Ending the game...");
        if(win){

            for(Player player : Groups.player){
                playerDB.safePut(player.uuid(), "xp", (int) playerDB.safeGet(player.uuid(), "xp") + 500*(player.donateLevel + 1));
                player.sendMessage("[accent]+[scarlet]" + 500*(player.donateLevel + 1) + "[accent] xp for destroying the crux");
            }

            int nextMap = currMap;
            while(nextMap == currMap) {
                nextMap = rand.nextInt(maps.customMaps().size - 1);
            }

            prefs.putInt("mapchoice", nextMap);
            String s = "";

            if(seconds < (int) mapDB.safeGet(mapID, "allRecord") || (int) mapDB.safeGet(mapID, "allRecord") == 0){
                mapDB.safePut(mapID, "allRecord", seconds);
                mapDB.safePut(mapID, "monthRecord", seconds);
                s += "[gold]New all time record!\n\n";
                for(Player player : Groups.player){
                    playerDB.safePut(player.uuid(), "xp", (int) playerDB.safeGet(player.uuid(), "xp") + 1000*(player.donateLevel + 1));
                    player.sendMessage("[gold]+[scarlet]" + 1000*(player.donateLevel + 1) + "[gold] xp for setting an all time record!");
                }
            }else if(seconds < (int) mapDB.safeGet(mapID, "monthRecord") || (int) mapDB.safeGet(mapID, "monthRecord") == 0){
                mapDB.safePut(mapID, "monthRecord", seconds);
                s += "[acid]New monthly record!\n\n";
                for(Player player : Groups.player){
                    playerDB.safePut(player.uuid(), "xp", (int) playerDB.safeGet(player.uuid(), "xp") + 250*(player.donateLevel + 1));
                    player.sendMessage("[accent]+[scarlet]" + 250*(player.donateLevel + 1) + "[accent] xp for setting a monthly record!");
                }
            }
            s += "[green]Congratulations! You destroyed the crux.\n[accent]All time score record: [pink]" +
                    formatSeconds((int) mapDB.safeGet(mapID, "allRecord")) +
                    "\n[accent]Month score record: [scarlet]" + formatSeconds((int) mapDB.safeGet(mapID, "monthRecord"));
            s += "\n[accent]Time: [scarlet]" + formatSeconds(seconds);


            Call.infoMessage(s);
        }else{
            int nextMap = currMap;
            while(nextMap == currMap) {
                nextMap = rand.nextInt(maps.customMaps().size - 1);
            }

            prefs.putInt("mapchoice", nextMap);
            Call.infoMessage("[scarlet]Bad luck! You died.");
        }



        Time.runTask(60f * 20f, () -> {
            mapDB.saveRow(mapID);

            for(Player player : Groups.player) {
                Call.connect(player.con, "aamindustry.play.ai", 6567);
            }


            // I shouldn't need this, all players should be gone since I connected them to hub
            // netServer.kickAll(KickReason.serverRestarting);
            Log.info("Game ended successfully.");
            Time.runTask(60f*2, () -> System.exit(2));
        });
    }

    void savePlayerData(String uuid){
        Log.info("Saving " + uuid + " data...");
        if(playerDB.entries.containsKey(uuid)){
            playerDB.saveRow(uuid);
        }

    }

    void mapStatsReset(){
        // Reset monthly records
        mapDB.setColumn("monthRecord", 0);

        for(Object uuid: mapDB.entries.keySet().toArray()){
            mapDB.safePut((String) uuid,"monthRecord", 0);
        }
    }

    void rankReset(){
        // Reset ranks
        playerDB.setColumn("xp", 0);

        for(Object uuid: playerDB.entries.keySet().toArray()){
            playerDB.safePut((String) uuid,"xp", 0);
        }
    }

    int calcValue(Object[] stacks){
        int val = 0;
        for(Object o : stacks){
            ItemStack stack = (ItemStack) o;
            val += (int) (AssaultData.itemValues.getOrDefault(stack.item.name, 1f) * stack.amount);
        }
        return val;
    }

    String formatSeconds(int seconds){
        return "[scarlet]" + (seconds / 60) + " [accent]minutes and [scarlet]" + (seconds - (seconds/60)) + "[accent] seconds";
    }

    String motd(){
        String ret = "[accent]Welcome to [#4d004d]{[purple]AA[#4d004d]} [cyan]Assault[accent]!\n[accent]Map name: [white]"
                + loadedMap.name() + "\n[accent]Author: [white]" + loadedMap.author()  + "\n\n[gold]All time score record: [scarlet]"
                + formatSeconds((int) mapDB.safeGet(mapID, "allRecord")) + "\n[accent]Monthly score record: [scarlet]"
                + formatSeconds((int) mapDB.safeGet(mapID, "monthRecord"));
        return ret;

    }
}
