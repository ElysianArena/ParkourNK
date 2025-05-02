package cn.daoge.parkour.command;

import cn.daoge.parkour.Parkour;
import cn.daoge.parkour.instance.IParkourInstance;
import cn.daoge.parkour.instance.ParkourInstance;
import cn.daoge.parkour.storage.JSONParkourStorage;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;

import java.nio.file.Path;

public class ParkourCommand extends Command {
    public ParkourCommand(String name) {
        super(name, "Parkour Plugin Main Command", "", new String[]{"pk"});
    }
    private void sendHelp(CommandSender sender) {
        StringBuilder helpMsg = new StringBuilder();
        helpMsg.append("§b===== Parkour Command Help =====");
        helpMsg.append("\n§a/pk help §7- Show this help message");
        helpMsg.append("\n§a/pk see info <name> §7- View parkour info");
        helpMsg.append("\n§a/pk send list §7- Show parkour list");
         if (sender.isOp()) {
            helpMsg.append("\n§6Admin Commands:");
            helpMsg.append("\n§6/pk create <name> §7- Create new parkour");
            helpMsg.append("\n§6/pk set start <name> [x y z] §7- Set start point");
            helpMsg.append("\n§6/pk set end <name> [x y z] §7- Set end point");
            helpMsg.append("\n§6/pk add point <name> [x y z] §7- Add checkpoint");
            helpMsg.append("\n§6/pk add rank <name> [x y z] §7- Add leaderboard position");
            helpMsg.append("\n§6/pk add tppos <name> [x y z] §7- Set teleport position");
        }
        helpMsg.append("\n§b================================");
        sender.sendMessage(helpMsg.toString());
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!sender.isPlayer()) {
            return false;
        }
        if (args.length == 0) {
            return false;
        }

        Parkour plugin = Parkour.getInstance();
        String name;
        IParkourInstance instance;
        switch (args[0].toLowerCase()) {
            case "see":
                if (args.length < 2 || !args[1].equals("info")) {
                    return false;
                }
                if (args.length < 3) {
                    return false;
                }
                name = args[2];
                instance = plugin.getParkourInstanceMap().get(name);
                if (instance == null) {
                    sender.sendMessage("[§bParkour§r] §cNo Parkour instance called §f" + name);
                    return false;
                }
                Parkour.getInstance().sendParkourInfo(sender.asPlayer(), instance);
                break;
            case "send":
                if (args.length < 2 || !args[1].equals("list")) {
                    return false;
                }
                Parkour.getInstance().sendParkourListForm(sender.asPlayer());
                break;
            case "create":
                if (!sender.isOp()) {
                    return false;
                }
                if (args.length < 2) {
                    return false;
                }
                name = args[1];
                Path dataPath = plugin.getDataPath().resolve(name + ".json");
                instance = new ParkourInstance(new JSONParkourStorage(dataPath));
                instance.getData().name = name;
                instance.getData().levelName = sender.getPosition().level.getName();
                plugin.addParkourInstance(instance);
                instance.save();
                sender.sendMessage("[§bParkour§r] Successfully add parkour §a" + name);
                break;
            case "set":
            case "help":
                sendHelp(sender);
                break;
            case "add":
                if (!sender.isOp()) {
                    sender.sendMessage("[§bParkour§r] You must be an operator to perform this command.");
                    return false;
                }

                if (args.length < 3) {
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "\n" + this.getCommandFormatTips()));
                    return false;
                }

                name = args[2];
                instance = plugin.getParkourInstanceMap().get(name);
                if (instance == null) {
                    sender.sendMessage("[§bParkour§r] §cNo Parkour instance called §f" + name);
                    return false;
                }

                Vector3 pos;
                if (args.length > 4) {
                    pos = new Vector3(Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
                } else {
                    Vector3 playerPos = sender.getPosition().floor();
                    pos = new Vector3(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
                }

                switch (args[1].toLowerCase()) {
                    case "start":
                        instance.getData().start = pos;
                        instance.save();
                        sender.sendMessage("[§bParkour§r] Successfully set start of parkour §a" + name);
                        break;
                    case "end":
                        instance.getData().end = pos;
                        instance.save();
                        sender.sendMessage("[§bParkour§r] Successfully set end of parkour §a" + name);
                        break;
                    case "point":
                        instance.getData().routePoints.add(pos);
                        instance.save();
                        sender.sendMessage("[§bParkour§r] Successfully add point to parkour §a" + name);
                        break;
                    case "rank":
                        // Assuming you have a method to add ranking text at a position
                        instance.addRankingText(Position.fromObject(pos, sender.getPosition().level));
                        sender.sendMessage("[§bParkour§r] Successfully add ranking text to parkour §a" + name);
                        break;
                    case "tppos":
                        instance.getData().tpPos = pos;
                        instance.save();
                        sender.sendMessage("[§bParkour§r] Successfully set tp pos of parkour §a" + name);
                        break;
                    default:
                        sender.sendMessage(new TranslationContainer("commands.generic.usage", "\n" + this.getCommandFormatTips()));
                        break;
                }
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }
}
