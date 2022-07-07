package anticope.serverinfo;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import joptsimple.internal.Strings;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class ServerInfo implements ModInitializer {
    public static final Handler handler = new Handler();
    private static final String completionStarts = "abcdefghijklmnopqrstuvwxyz0123456789/:";

    @Override
    public void onInitialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("server").executes(ctx -> {
                basicInfo();
                return SINGLE_SUCCESS;
            }));

            dispatcher.register(literal("server").then(literal("plugins").executes(ctx -> {
                printPlugins();
                return SINGLE_SUCCESS;
            })));
        });
    }

    private void printPlugins() {
        var plugins = MinecraftClient
                .getInstance()
                .getNetworkHandler()
                .getCommandDispatcher()
                .getRoot()
                .getChildren()
                .stream()
                .map(cmd -> cmd.getName())
                .filter(cmd -> cmd.contains(":"))
                .map(cmd -> cmd.split(":")[0])
                .collect(Collectors.toUnmodifiableSet());
        if (!plugins.isEmpty()) {
            Text pluginMsg = Text.of(Strings.join(plugins.toArray(new String[0]), ", "));
            print(createEntry(String.format("Plugins (%d)", plugins.size()), pluginMsg, false));
        } else {
            print(Text.of(Formatting.RED + "No plugins found."));
        }
    }

    private void basicInfo() {
        print(Text.of(Formatting.BOLD + Formatting.GOLD.toString() + "=== Server Info ==="));
        var mc = MinecraftClient.getInstance();

        if (mc.isIntegratedServerRunning() || mc.isInSingleplayer()) {
            print(Text.of("Singleplayer"));
            var server = mc.getServer();
            if (server != null) {
                if (server.getServerPort() > 0) {
                    var address = "";
                    try {
                        address = String.format("%s:%d", InetAddress.getLocalHost().getHostAddress(), server.getServerPort());
                    } catch (UnknownHostException e) {
                        address = String.format("localhost:%d", server.getServerPort());
                    }
                    print(createEntry("Address", address, true));
                }

                print(createEntry("Version", mc.getGameVersion() + " " + server.getVersion(), false));
                print(createEntry("Motd", server.getServerMotd(), true));
            }

        } else {
            var server = mc.getCurrentServerEntry();
            print(createEntry("Address", server.address, true));
            print(createEntry("Motd", server.label != null ? server.label : Text.of("unknown"), server.label != null));
            print(createEntry("Version", server.version, false));
            print(createEntry("Protocol version", String.format("%d", server.protocolVersion), true));
        }

        if (mc.world != null && mc.player != null) {
            print(createEntry("Difficulty", mc.world.getDifficulty().getTranslatableName(), false));
            print(createEntry("Local difficulty", String.format("%.2f", mc.world.getLocalDifficulty(mc.player.getBlockPos()).getLocalDifficulty()), false));
            print(createEntry("Day", String.format("%d", mc.world.getTimeOfDay() / 24000L), false));
            print(createEntry("Permissions", formatPerms(mc.player), false));
        }

        float tps = handler.getTickRate();
        if (!Float.isNaN(tps)) {
            Formatting color;
            if (tps > 17.0f) color = Formatting.GREEN;
            else if (tps > 12.0f) color = Formatting.YELLOW;
            else color = Formatting.RED;
            print(createEntry("TPS", color + String.format("%.2f", tps),false));
        }
    }

    private Text createEntry(String key, String value, boolean copy) {
        return createEntry(key, Text.of(String.format("%s%s%s", Formatting.AQUA + (copy ? Formatting.UNDERLINE : "").toString(), value, Formatting.RESET)), copy);
    }

    private Text createEntry(String key, Text value, boolean copy) {
        MutableText text = (MutableText) Text.of(String.format("%s: ", key));
        text.append(value);
        if (!copy) return text;
        return text.fillStyle(text
                .getStyle()
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Text.of("Click to copy.")
                ))
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.COPY_TO_CLIPBOARD,
                        value.getContent().toString()
                ))
        );
    }

    private void print(Text msg) {
        var mc = MinecraftClient.getInstance();
        mc.inGameHud.getChatHud().addMessage(msg);
    }

    public String formatPerms(PlayerEntity player) {
        int p = 5;
        while (!player.hasPermissionLevel(p) && p > 0) p--;

        return switch (p) {
            case 0 -> "0 (No Perms)";
            case 1 -> "1 (No Perms)";
            case 2 -> "2 (Player Command Access)";
            case 3 -> "3 (Server Command Access)";
            case 4 -> "4 (Operator)";
            default -> p + " (Unknown)";
        };
    }
}
