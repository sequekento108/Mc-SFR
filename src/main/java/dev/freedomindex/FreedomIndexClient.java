package dev.freedomindex;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.freedomindex.LicenseClassifier.Category;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

@Mod(value = "freedomindex", dist = Dist.CLIENT)
public class FreedomIndexClient {
    private static final Logger LOG = LogUtils.getLogger();

    record Entry(String id, String name, String version, String license, Category category) {}

    public FreedomIndexClient() {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        // Mods are all discovered by the time the constructor runs
        List<Entry> entries = scan();
        LOG.info("=== Stallman Freedom Index ===");
        for (Entry e : entries) {
            LOG.info("[{}] {} {} - {}", e.category().label, e.name(), e.version(), e.license());
        }
        LOG.info("Freedom index: {}%", percent(entries));
    }

    private void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("freedom").executes(ctx -> {
            show(ctx.getSource(), scan());
            return 1;
        }));
    }

    private List<Entry> scan() {
        return ModList.get().getMods().stream().map(mod -> {
            String id = mod.getModId();
            String license;
            Category cat;
            if (id.equals("minecraft")) {
                license = "Minecraft EULA (proprietary)";
                cat = Category.NONFREE; // the elephant in the room
            } else {
                // NeoForge declares the license per mod *file*, so mods sharing a jar share a license
                IModFileInfo file = mod.getOwningFile();
                license = (file == null || file.getLicense() == null || file.getLicense().isBlank())
                        ? "no license declared" : file.getLicense();
                cat = LicenseClassifier.classify(license.equals("no license declared") ? List.of() : List.of(license));
            }
            return new Entry(id, mod.getDisplayName(), mod.getVersion().toString(), license, cat);
        }).sorted(Comparator.comparing((Entry e) -> e.category().ordinal()).reversed()
                .thenComparing(e -> e.name().toLowerCase())).toList();
    }

    private void show(CommandSourceStack src, List<Entry> entries) {
        send(src, Component.literal("=== Stallman Freedom Index ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (Entry e : entries) {
            ChatFormatting color = switch (e.category()) {
                case COPYLEFT -> ChatFormatting.GREEN;
                case PERMISSIVE -> ChatFormatting.DARK_GREEN;
                case UNKNOWN -> ChatFormatting.YELLOW;
                case NONFREE -> ChatFormatting.RED;
            };
            String mark = e.category().isFree() ? "\u2714 " : (e.category() == Category.UNKNOWN ? "? " : "\u2718 ");
            send(src, Component.literal(mark + e.name() + " " + e.version()).withStyle(color)
                    .append(Component.literal("  " + e.license() + " [" + e.category().label + "]")
                            .withStyle(ChatFormatting.GRAY)));
        }
        int pct = percent(entries);
        send(src, Component.literal("Freedom index: " + pct + "% (" + verdict(pct) + ")")
                .withStyle(pct >= 80 ? ChatFormatting.GREEN : pct >= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED,
                        ChatFormatting.BOLD));
        send(src, Component.literal("Note: Minecraft itself is proprietary. You are playing this on borrowed freedom.")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static void send(CommandSourceStack src, Component c) {
        src.sendSuccess(() -> c, false);
    }

    private static int percent(List<Entry> entries) {
        if (entries.isEmpty()) return 0;
        long free = entries.stream().filter(e -> e.category().isFree()).count();
        return (int) Math.round(100.0 * free / entries.size());
    }

    private static String verdict(int pct) {
        if (pct == 100) return "impossible, you must be lying";
        if (pct >= 90) return "almost GNU/Minecraft";
        if (pct >= 75) return "respectable, comrade";
        if (pct >= 50) return "compromised";
        if (pct >= 25) return "an open-source-ish disaster";
        return "you have been enslaved by proprietary software";
    }
}
