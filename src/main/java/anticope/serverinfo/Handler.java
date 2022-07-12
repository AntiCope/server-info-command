package anticope.serverinfo;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.util.*;

public class Handler {
    private static final Gson GSON_NON_PRETTY = new GsonBuilder().enableComplexMapKeySerialization().disableHtmlEscaping().create();
    private static final Type BADLION_MODS_TYPE = new TypeToken<Map<String, BadlionMod>>() {}.getType();
    

    private final float[] tickRates = new float[20];
    private int nextIndex = 0;
    private long timeLastTimeUpdate = -1;
    private long timeGameJoined;
    public MutableText badlionMods = null;

    public void onWorldTimeUpdate(WorldTimeUpdateS2CPacket packet) {
        long now = System.currentTimeMillis();
        float timeElapsed = (float) (now - timeLastTimeUpdate) / 1000.0F;
        tickRates[nextIndex] = MathHelper.clamp(20.0f / timeElapsed, 0.0f, 20.0f);
        nextIndex = (nextIndex + 1) % tickRates.length;
        timeLastTimeUpdate = now;
    }

    public void onGameJoined(GameJoinS2CPacket packet) {
        Arrays.fill(tickRates, 0);
        nextIndex = 0;
        timeGameJoined = timeLastTimeUpdate = System.currentTimeMillis();
        badlionMods = null;
    }

    public float getTickRate() {
        if (System.currentTimeMillis() - timeGameJoined < 4000) return 20;

        int numTicks = 0;
        float sumTickRates = 0.0f;
        for (float tickRate : tickRates) {
            if (tickRate > 0) {
                sumTickRates += tickRate;
                numTicks++;
            }
        }
        return sumTickRates / numTicks;
    }

    private String readString(PacketByteBuf data) {
        return data.readCharSequence(
                data.readableBytes(),
                StandardCharsets.UTF_8
        ).toString();
    }

    public void onBadLionMods(PacketByteBuf data) {
        badlionMods = Text.literal("").copy();
        String json = readString(data);
        Map<String, BadlionMod> mods = GSON_NON_PRETTY.fromJson(json, BADLION_MODS_TYPE);
        mods.forEach((name, mod) -> {
            MutableText modLine = Text.literal(String.format("%s%s%s ", Formatting.YELLOW, name, Formatting.GRAY));
            modLine.append(mod.disabled ? "disabled" : "enabled");
            modLine.append(", ");
            if (mod.extra_data != null) {
                modLine.setStyle(modLine.getStyle()
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Text.literal(mod.extra_data.toString())
                        )));
            }
            badlionMods.append(modLine);
        });
    }

    private static class BadlionMod {
        private boolean disabled;
        private JsonObject extra_data;
        private JsonObject settings;
    }
}
