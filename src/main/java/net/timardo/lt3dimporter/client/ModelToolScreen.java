package net.timardo.lt3dimporter.client;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import team.creative.littletiles.LittleTilesRegistry;

public final class ModelToolScreen extends Screen {
    private String modelValue="", textureValue="", sizeValue="4", gridValue="16", intervalValue="0.25", baseValue;
    private EditBox model,texture,size,grid,interval,base;
    private Button importButton,exportButton,nativeButton;
    private int left,top,panelWidth;
    public ModelToolScreen() {
        super(Component.translatable("lt3d.title"));
        baseValue=BuiltInRegistries.BLOCK.getKey(LittleTilesRegistry.CLEAN.value()).toString();
        gridValue=Integer.toString(team.creative.littletiles.common.grid.LittleGrid.overallDefault().count);
    }
    private EditBox field(int x,int y,int width,String value,String key) {
        var field=new EditBox(font,x,y,width,20,Component.translatable(key));
        field.setMaxLength(32768); field.setValue(value); addRenderableWidget(field); return field;
    }
    private Button button(int x,int y,int width,String key,Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.translatable(key),action).bounds(x,y,width,20).build());
    }
    @Override protected void init() {
        panelWidth=Math.min(360,width-24); left=(width-panelWidth)/2; top=Math.max(4,(height-232)/2);
        int third=(panelWidth-12)/3, half=(panelWidth-12)/2;
        model=field(left,top+29,panelWidth,modelValue,"lt3d.model"); model.setResponder(v -> modelValue=v);
        texture=field(left,top+62,panelWidth,textureValue,"lt3d.texture"); texture.setResponder(v -> textureValue=v);
        size=field(left,top+95,third,sizeValue,"lt3d.size"); size.setResponder(v -> sizeValue=v);
        grid=field(left+third+6,top+95,third,gridValue,"lt3d.grid"); grid.setResponder(v -> gridValue=v);
        interval=field(left+2*(third+6),top+95,panelWidth-2*(third+6),intervalValue,"lt3d.interval"); interval.setResponder(v -> intervalValue=v);
        base=field(left,top+128,panelWidth,baseValue,"lt3d.base"); base.setResponder(v -> baseValue=v);
        importButton=button(left,top+164,half,"lt3d.import",b -> startImport());
        exportButton=button(left+half+12,top+164,half,"lt3d.export",b -> {
            try { ClientJobs.exportHeld("blueprint"); } catch (Exception e) { ClientJobs.fail(e); }
        });
        nativeButton=button(left,top+188,half,"lt3d.native_import",b -> ClientJobs.openNativeImport());
        button(left+half+12,top+188,half,"lt3d.open_output",b -> Util.getPlatform().openPath(ClientJobs.outputRoot()));
        button(left,top+212,half,"lt3d.cancel",b -> ClientJobs.cancel());
        button(left+half+12,top+212,half,"gui.done",b -> onClose());
    }
    private static String clean(String input) {
        String value=input.trim();
        if (value.length()>=2 && value.startsWith("\"") && value.endsWith("\"")) value=value.substring(1,value.length()-1);
        return value;
    }
    private void startImport() {
        try {
            Path file=Path.of(clean(model.getValue()));
            String texturePath=clean(texture.getValue());
            ClientJobs.importModel(file,texturePath.isEmpty()?null:Path.of(texturePath),Double.parseDouble(size.getValue()),
                Integer.parseInt(grid.getValue()),Float.parseFloat(interval.getValue()),base.getValue().trim());
        } catch (Exception e) { ClientJobs.fail(e); }
    }
    @Override public void onFilesDrop(List<Path> files) {
        for (Path path : files) {
            String name=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".obj")) model.setValue(path.toString());
            else if (name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".bmp")) texture.setValue(path.toString());
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        renderTransparentBackground(graphics);
        graphics.fill(left-8,top-4,left+panelWidth+8,top+236,0xE0202020);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawString(font,title,left,top+3,0xFFFFFF);
        int third=(panelWidth-12)/3;
        label(graphics,"lt3d.model",left,top+18); label(graphics,"lt3d.texture",left,top+51);
        label(graphics,"lt3d.size",left,top+84); label(graphics,"lt3d.grid",left+third+6,top+84);
        label(graphics,"lt3d.interval",left+2*(third+6),top+84); label(graphics,"lt3d.base",left,top+117);
        var status=ClientJobs.busy?Component.translatable("lt3d.phase."+ClientJobs.control.phase):ClientJobs.status;
        graphics.drawString(font,font.plainSubstrByWidth(status.getString(),panelWidth),left,top+153,0xFFFFAA,false);
        if (ClientJobs.busy) graphics.drawString(font,ClientJobs.control.percent+"%",left+panelWidth-35,top+3,0xAADDFF);
        importButton.active=!ClientJobs.busy; exportButton.active=!ClientJobs.busy;
        nativeButton.active=!ClientJobs.busy && ClientJobs.lastBlueprint!=null;
    }
    private void label(GuiGraphics graphics,String key,int x,int y) { graphics.drawString(font,Component.translatable(key),x,y,0xDDDDDD); }
    @Override public boolean isPauseScreen() { return false; }
}
