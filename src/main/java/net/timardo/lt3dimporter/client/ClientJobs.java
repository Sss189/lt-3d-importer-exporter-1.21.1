package net.timardo.lt3dimporter.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.timardo.lt3dimporter.LT3DImporter;
import net.timardo.lt3dimporter.exporter.ExportCapture;
import net.timardo.lt3dimporter.exporter.ObjWriter;
import net.timardo.lt3dimporter.importer.*;
import team.creative.littletiles.common.block.little.element.LittleElement;
import team.creative.littletiles.common.grid.LittleGrid;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import team.creative.littletiles.common.math.box.LittleBox;

public final class ClientJobs {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r -> {
        var thread=new Thread(r,"LT3D file conversion"); thread.setDaemon(true); return thread;
    });
    public static JobControl control=new JobControl();
    public static boolean busy;
    public static Component status=Component.translatable("lt3d.status.ready");
    public static String lastBlueprint;
    public static Path lastOutput;
    private static ExportCapture capture;
    private static String exportName;
    private ClientJobs() {}
    public static Path outputRoot() { return Minecraft.getInstance().gameDirectory.toPath().resolve("lt3d"); }
    private static void begin() {
        if (busy) throw new IllegalStateException("Another conversion is running.");
        control=new JobControl(); busy=true; lastOutput=null; lastBlueprint=null;
        status=Component.translatable("lt3d.status.working");
    }
    public static void importModel(Path file, Path texture, double sizeBlocks, int gridCount, float precision, String baseId) {
        var grid=LittleGrid.get(gridCount);
        var id=ResourceLocation.parse(baseId);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown base block: " + baseId);
        var base=BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        if (base.isAir()) throw new IllegalArgumentException("Base block cannot be air.");
        var parameters=new ModelImportService.Parameters(sizeBlocks*gridCount,precision,250_000,texture);
        var element=new LittleElement(base,-1);
        String blockName=element.getBlockName();
        boolean baseTranslucent=element.getBlock().isTranslucent();
        Path root=outputRoot().resolve("imports");
        begin();
        var job=control;
        CompletableFuture.supplyAsync(() -> {
            try {
                var result=ModelImportService.convert(file,parameters,job);
                var boxes=VoxelBoxMerger.merge(result.voxels,job);
                String snbt=ImportBlueprintEncoder.encode(boxes,grid.count,blockName,baseTranslucent,job).toString();
                job.check(); job.phase="writing"; job.percent=99;
                Files.createDirectories(root);
                Path path=Files.createTempDirectory(root,"model-").resolve("model.struct");
                Files.writeString(path,snbt,StandardCharsets.UTF_8);
                job.check();
                LT3DImporter.LOG.info("LT3D_IMPORT_RESULT {} voxels -> {} boxes; {}",result.voxels.size(),boxes.size(),path);
                return new ImportOutput(path,snbt);
            } catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        },WORKER).whenComplete((result,error) -> Minecraft.getInstance().execute(() -> {
            if (control!=job) return;
            if (error!=null) fail(error);
            else if (job.cancelled.get()) fail(new CancellationException());
            else { lastBlueprint=result.snbt(); finish(result.path(),null); }
        }));
    }
    public static void exportHeld(String name) {
        var player=Minecraft.getInstance().player;
        if (player==null) throw new IllegalStateException("Enter a world first.");
        var stack=player.getMainHandItem();
        if (!(stack.getItem() instanceof ItemLittleBlueprint blueprint) || !blueprint.hasTiles(stack))
            throw new IllegalArgumentException("Hold a filled LittleTiles blueprint in your main hand.");
        var group=blueprint.getTiles(stack).copy();
        begin();
        try { exportName=name; capture=new ExportCapture(group,control); }
        catch (RuntimeException e) { fail(e); }
    }
    public static void tick() {
        try {
            if (capture!=null && capture.step()) {
                var mesh=capture.snapshot(); capture=null;
                var job=control; Path root=outputRoot().resolve("exports"); String name=exportName;
                CompletableFuture.supplyAsync(() -> {
                    try { return ObjWriter.write(mesh,root,name,job); }
                    catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
                },WORKER).whenComplete((path,error) -> Minecraft.getInstance().execute(() -> finish(path,error)));
            }
        } catch (Throwable e) { capture=null; fail(e); }
    }
    public static void cancel() { control.cancelled.set(true); }
    public static void fail(Throwable error) {
        while (error.getCause()!=null && (error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException)) error=error.getCause();
        busy=false;
        if (error instanceof CancellationException) { lastBlueprint=null; status=Component.translatable("lt3d.status.cancelled"); }
        else {
            status=Component.translatable("lt3d.status.error",String.valueOf(error.getMessage()));
            LT3DImporter.LOG.error("LT3D conversion failed",error);
        }
    }
    private static void finish(Path path,Throwable error) {
        if (error!=null) { fail(error); return; }
        if (control.cancelled.get()) { fail(new CancellationException()); return; }
        busy=false; control.percent=100; lastOutput=path;
        status=Component.translatable("lt3d.status.done",path.getFileName().toString());
        if (Minecraft.getInstance().player!=null) Minecraft.getInstance().player.sendSystemMessage(
            Component.translatable("lt3d.output",path.toString()));
    }
    public static void openNativeImport() {
        var minecraft=Minecraft.getInstance();
        if (busy || lastBlueprint==null || minecraft.player==null) return;
        minecraft.keyboardHandler.setClipboard(lastBlueprint);
        minecraft.setScreen(null);
        minecraft.player.connection.sendCommand("lt-import");
        minecraft.player.sendSystemMessage(Component.translatable("lt3d.native_import_help"));
    }
    private record ImportOutput(Path path,String snbt) {}
}
