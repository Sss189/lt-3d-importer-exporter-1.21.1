package net.timardo.lt3dimporter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.timardo.lt3dimporter.client.ClientJobs;
import net.timardo.lt3dimporter.client.ModelToolScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client tooling; imports use LittleTiles' existing server-side import screen. */
@Mod(value=LT3DImporter.MOD_ID,dist=Dist.CLIENT)
public final class LT3DImporter {
    public static final String MOD_ID="lt3dimporter";
    public static final Logger LOG=LoggerFactory.getLogger("LT 3D Importer & Exporter");
    private final KeyMapping open=new KeyMapping("lt3d.key.open",GLFW.GLFW_KEY_F8,"lt3d.key.category");
    private boolean pendingOpen;
    public LT3DImporter(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent event) -> event.register(open));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
            Commands.literal("lt3d").executes(context -> { pendingOpen=true; return 1; })));
        NeoForge.EVENT_BUS.addListener(this::tick);
        if (Boolean.getBoolean("lt3d.smoke")) net.timardo.lt3dimporter.tools.ClientSmokeChecks.install();
    }
    private void tick(ClientTickEvent.Post event) {
        ClientJobs.tick();
        var minecraft=Minecraft.getInstance();
        if (minecraft.player!=null && (open.consumeClick() || pendingOpen)) {
            pendingOpen=false; minecraft.setScreen(new ModelToolScreen());
        }
    }
}
