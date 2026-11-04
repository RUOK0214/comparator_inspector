package io.github.ruok0214.comparatorinspector;

import io.github.ruok0214.comparatorinspector.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.locale.Language;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("UnstableApiUsage")
public final class InspectorGameTest implements FabricClientGameTest {
    private static final BlockPos CHEST = new BlockPos(0,81,0), BARREL = new BlockPos(2,81,0);
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void aim(ClientGameTestContext c, BlockPos pos) {
        c.runOnClient(mc -> {
            var delta = net.minecraft.world.phys.Vec3.atCenterOf(pos).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(delta.z, delta.x))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        }); c.waitTicks(5);
    }
    private static void value(ClientGameTestContext c, BlockPos pos, int expected) {
        c.waitFor(mc -> { var r = InspectorClient.validReading(pos); return r != null && r.uniform() && r.north() == expected; });
    }
    private static void menu(ClientGameTestContext c) {
        c.getInput().pressKey(GLFW.GLFW_KEY_T); c.waitForScreen(ChatScreen.class);
        c.getInput().typeChars("/ci"); c.getInput().pressKey(GLFW.GLFW_KEY_ENTER); c.waitForScreen(InspectorScreen.class);
    }
    private static void color(ClientGameTestContext c,String button,String hex) {
        c.clickScreenButton("comparator_inspector."+button); c.waitForScreen(ColorScreen.class);
        c.runOnClient(mc -> {
            var field=mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            field.setValue("#oops");
            check(mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).anyMatch(b -> b.getMessage().getString().equals(InspectorClient.tr("apply").getString()) && !b.active),"Invalid HEX cannot be applied: "+mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).map(b -> b.getMessage().getString()+"="+b.active).toList());
            field.setValue(hex);
        });
        c.takeScreenshot("color-picker-ko");
        c.clickScreenButton("comparator_inspector.apply"); c.waitForScreen(AppearanceScreen.class);
    }
    private static void styleSlider(ClientGameTestContext c,boolean size,double value) {
        var point=c.computeOnClient(mc -> {
            var slider=mc.gui.screen().children().stream().filter(AppearanceScreen.StyleSlider.class::isInstance)
                    .map(AppearanceScreen.StyleSlider.class::cast).filter(s -> s.sizeControl()==size).findFirst().orElseThrow();
            return new double[]{(slider.getX()+4+value*(slider.getWidth()-8))*mc.getWindow().getScreenWidth()/mc.gui.screen().width,
                    (slider.getY()+10.0)*mc.getWindow().getScreenHeight()/mc.gui.screen().height};
        });
        c.getInput().setCursorPos(point[0],point[1]); c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); c.waitTick();
    }
    private static void appearances(ClientGameTestContext c) {
        c.runOnClient(mc -> {
            var migrated=InspectorConfig.fromJson("{\"targeted\":false,\"nearby\":true,\"radius\":17}");
            check(!migrated.targeted() && migrated.nearby() && migrated.radius()==17 && migrated.world().equals(DisplayStyle.WORLD) && migrated.hud().equals(DisplayStyle.HUD),"Legacy settings retain detection and receive display defaults");
            var previous=InspectorConfig.fromJson("{\"world\":{\"enabled\":false,\"color\":444,\"secondaryColor\":555,\"opacity\":65,\"scalePercent\":125}}");
            check(previous.world().heightHundredths()==18 && !previous.world().enabled() && previous.world().color()==444 && previous.world().opacity()==65 && previous.world().scalePercent()==125,"0.2 appearance keeps its values and gets original label height");
            check(DisplayStyle.WORLD.height(-1).height()==0 && DisplayStyle.WORLD.height(400).height()==3,"Height clamps to supported range");
        });
        menu(c); c.clickScreenButton("comparator_inspector.world.title"); c.waitForScreen(AppearanceScreen.class);
        var heightPoint=c.computeOnClient(mc -> {
            var slider=mc.gui.screen().children().stream().filter(AppearanceScreen.HeightSlider.class::isInstance)
                    .map(AppearanceScreen.HeightSlider.class::cast).findFirst().orElseThrow();
            return new double[]{(slider.getX()+slider.getWidth()/2.0)*mc.getWindow().getScreenWidth()/mc.gui.screen().width,
                    (slider.getY()+10.0)*mc.getWindow().getScreenHeight()/mc.gui.screen().height};
        });
        c.getInput().setCursorPos(heightPoint[0],heightPoint[1]); c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); c.waitTick();
        c.runOnClient(mc -> check(((AppearanceScreen)mc.gui.screen()).draft().heightHundredths()==150,"Height slider selects 1.50 blocks"));
        color(c,"color.primary","#43E7D4"); color(c,"color.target","#FF7061");
        styleSlider(c,true,2.0/3); styleSlider(c,false,.8);
        c.takeScreenshot("world-appearance-ko");
        c.getInput().resizeWindow(854,480); c.getInput().setCursorPos(20,20); c.waitTicks(3); c.takeScreenshot("world-height-small-ko");
        c.getInput().resizeWindow(1000,700); c.waitTicks(3);
        c.clickScreenButton("comparator_inspector.apply");
        c.clickScreenButton("comparator_inspector.hud.title");
        c.runOnClient(mc -> check(mc.gui.screen().children().stream().noneMatch(AppearanceScreen.HeightSlider.class::isInstance),"Height control is only in world display settings"));
        color(c,"color.primary","#7DDF64"); color(c,"color.background","#202060");
        styleSlider(c,true,1.0/3);
        c.takeScreenshot("hud-appearance-ko");
        c.getInput().resizeWindow(854,480); c.waitTicks(3); c.takeScreenshot("appearance-small-ko");
        c.clickScreenButton("comparator_inspector.apply"); c.clickScreenButton("comparator_inspector.save");
        value(c,CHEST,15);
        var saved=c.computeOnClient(mc -> InspectorClient.config());
        c.runOnClient(mc -> {
            check(InspectorConfig.load().equals(saved),"Both independent appearances persist");
            check(saved.world().color()==0x43E7D4 && saved.world().secondaryColor()==0xFF7061,"World colors saved");
            check(saved.world().scalePercent()==150 && saved.world().opacity()==80,"World sliders saved");
            check(saved.hud().color()==0x7DDF64 && saved.hud().secondaryColor()==0x202060,"HUD colors saved independently");
            var label=InspectorClient.extractLabels().getFirst();
            check(label.color()==saved.world().argb(true) && Math.abs(label.scale()-.48f)<.001,"World appearance reaches renderer");
            check(saved.world().heightHundredths()==150 && Math.abs(label.position().y-(CHEST.getY()+2.5))<.0001,"Height persists through color/size edits and moves the world label");
            var hud=InspectorClient.hudDisplay();
            check(hud!=null && hud.color()==saved.hud().argb(false) && hud.background()==saved.hud().backgroundArgb(),"HUD appearance reaches renderer");
        });
        c.getInput().resizeWindow(1000,700); c.waitTicks(3); c.takeScreenshot("independent-colors-ko");
        for(int height:new int[]{0,300}) {
            c.runOnClient(mc -> InspectorClient.configure(saved.appearance(true,saved.world().height(height)))); value(c,CHEST,15);
            c.runOnClient(mc -> check(Math.abs(InspectorClient.extractLabels().getFirst().position().y-(CHEST.getY()+1+height/100.0))<.0001,"Height endpoints render at the requested position"));
        }
        c.runOnClient(mc -> InspectorClient.configure(saved)); value(c,CHEST,15);
        // Editing detection must preserve the two independently saved styles.
        menu(c); c.clickScreenButton(InspectorClient.tr("nearby",net.minecraft.network.chat.Component.translatable("options.off")).getString());
        c.clickScreenButton("comparator_inspector.save");
        value(c,BARREL,0);
        c.runOnClient(mc -> check(InspectorClient.extractLabels().stream().allMatch(label -> Math.abs(label.position().y-83.5)<.0001),"Nearby and targeted labels share the selected height"));
        c.runOnClient(mc -> check(InspectorClient.config().world().equals(saved.world()) && InspectorClient.config().hud().equals(saved.hud()),"Changing detection preserves colors"));
        menu(c); c.clickScreenButton("comparator_inspector.world.title");
        c.clickScreenButton("comparator_inspector.reset"); c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        c.runOnClient(mc -> check(((InspectorScreen)mc.gui.screen()).draft().world().equals(saved.world()),"Escape discards appearance changes"));
        c.clickScreenButton("comparator_inspector.hud.title"); c.clickScreenButton("comparator_inspector.reset"); c.clickScreenButton("comparator_inspector.apply");
        c.clickScreenButton("gui.cancel");
        c.runOnClient(mc -> check(InspectorClient.config().hud().equals(saved.hud()),"Parent Cancel also discards applied sub-menu draft"));
        menu(c); c.clickScreenButton("comparator_inspector.world.title");
        c.clickScreenButton(InspectorClient.tr("appearance.enabled",net.minecraft.network.chat.Component.translatable("options.on")).getString());
        c.clickScreenButton("comparator_inspector.apply"); c.clickScreenButton("comparator_inspector.save");
        value(c,CHEST,15);
        c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()!=null,"World disabled does not hide HUD"));
        c.takeScreenshot("hud-only-ko");
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(false,saved.hud().enabled(false)))); value(c,CHEST,15);
        c.runOnClient(mc -> check(!InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"HUD disabled does not hide world labels"));
        c.takeScreenshot("world-only-ko");
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(true,saved.world().enabled(false)).appearance(false,saved.hud().enabled(false))));
        c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty() && InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"Both displays disabled stops reads"));
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(true,saved.world().opacity(0)).appearance(false,saved.hud().opacity(0))));
        c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"Zero opacity hides both displays"));
        c.runOnClient(mc -> { InspectorConfig.DEFAULT.save(); InspectorClient.configure(InspectorConfig.DEFAULT); }); value(c,CHEST,15);
    }
    @Override public void runTest(ClientGameTestContext c) {
        c.getInput().resizeWindow(1000,700);
        c.runOnClient(mc -> {
            InspectorClient.configure(InspectorConfig.DEFAULT);
            mc.options.languageCode="ko_kr"; mc.getLanguageManager().setSelected("ko_kr"); mc.reloadResourcePacks();
        });
        c.waitFor(mc -> mc.gui.overlay()==null && Language.getInstance().getOrDefault("comparator_inspector.title").equals("비교기 신호 확인"));
        ModMenuChecks.run(c,"ko");
        c.runOnClient(mc -> { mc.options.languageCode="en_us"; mc.getLanguageManager().setSelected("en_us"); mc.reloadResourcePacks(); });
        c.waitFor(mc -> mc.gui.overlay()==null && Language.getInstance().getOrDefault("comparator_inspector.title").equals("Comparator Inspector"));
        ModMenuChecks.run(c,"en");
        c.runOnClient(mc -> { mc.options.languageCode="ko_kr"; mc.getLanguageManager().setSelected("ko_kr"); mc.reloadResourcePacks(); });
        c.waitFor(mc -> mc.gui.overlay()==null && Language.getInstance().getOrDefault("comparator_inspector.title").equals("비교기 신호 확인"));
        try (var world = c.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("fill -8 80 -8 12 80 10 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:chest");
            world.getServer().runCommand("setblock 2 81 0 minecraft:barrel");
            world.getServer().runCommand("setblock 4 81 0 minecraft:composter[level=6]");
            world.getServer().runCommand("setblock 6 81 0 minecraft:water_cauldron[level=3]");
            world.getServer().runCommand("setblock 8 81 0 minecraft:cake[bites=0]");
            world.getServer().runCommand("setblock 0 81 1 minecraft:comparator[facing=north]");
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("tp @a 0.5 81 4.5 180 20");
            world.getConnection().waitForClientboundPackets();
            aim(c,CHEST); value(c,CHEST,0);
            c.runOnClient(mc -> check(InspectorClient.readings().size()==1,"Target-only mode reads just the pointed block"));
            c.takeScreenshot("target-empty-chest-ko");
            world.getServer().runOnServer(s -> ((Container)s.overworld().getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.STONE,1)));
            value(c,CHEST,1);
            world.getServer().runOnServer(s -> {
                var level=s.overworld(); var chest=(Container)level.getBlockEntity(CHEST);
                for(int i=0;i<chest.getContainerSize();i++) chest.setItem(i,new ItemStack(Items.STONE,64));
                level.updateNeighbourForOutputSignal(CHEST,Blocks.CHEST);
            });
            value(c,CHEST,15); c.waitTicks(8);
            world.getServer().runOnServer(s -> check(((ComparatorBlockEntity)s.overworld().getBlockEntity(CHEST.south())).getOutputSignal()==15,"Real adjacent comparator matches full chest reading"));
            c.takeScreenshot("target-full-chest-ko");
            appearances(c);
            // Covering the chest changes the actual vanilla comparator value.
            world.getServer().runCommand("setblock 0 82 0 minecraft:stone"); value(c,CHEST,0);
            world.getServer().runCommand("setblock 0 82 0 minecraft:air"); value(c,CHEST,15);
            menu(c);
            c.clickScreenButton(InspectorClient.tr("nearby",net.minecraft.network.chat.Component.translatable("options.off")).getString());
            c.takeScreenshot("settings-ko");
            c.clickScreenButton("comparator_inspector.save");
            c.runOnClient(mc -> check(InspectorConfig.load().nearby(),"Settings persist"));
            value(c,BARREL,0); value(c,new BlockPos(4,81,0),6); value(c,new BlockPos(6,81,0),3);
            c.runOnClient(mc -> check(!InspectorClient.readings().containsKey(new BlockPos(8,81,0)),"Outside radius omitted"));
            // Scan radius changes; monitor blocks even when not pointed at.
            c.runOnClient(mc -> InspectorClient.configure(new InspectorConfig(false,true,12)));
            value(c,new BlockPos(8,81,0),14);
            world.getServer().runOnServer(s -> ((Container)s.overworld().getBlockEntity(BARREL)).setItem(0,new ItemStack(Items.DIAMOND,64)));
            value(c,BARREL,1);
            world.getServer().runCommand("tp @a 4.5 83 8.5 180 28");
            world.getConnection().waitForClientboundPackets(); c.waitTicks(25); c.takeScreenshot("nearby-signals-ko");
            world.getServer().runCommand("setblock 2 81 0 minecraft:stone");
            c.waitFor(mc -> InspectorClient.validReading(BARREL)==null);
            // Double chest: one full half and one empty half = signal 8.
            world.getServer().runCommand("setblock 0 81 0 minecraft:chest[facing=north,type=left]");
            world.getServer().runCommand("setblock 1 81 0 minecraft:chest[facing=north,type=right]");
            world.getServer().runOnServer(s -> {
                var chest=(Container)s.overworld().getBlockEntity(CHEST);
                for(int i=0;i<chest.getContainerSize();i++) chest.setItem(i,new ItemStack(Items.STONE,64));
            });
            value(c,CHEST,8); value(c,new BlockPos(1,81,0),8);
            // Loot is not generated as a side effect of observation.
            world.getServer().runCommand("setblock -3 81 0 minecraft:chest{LootTable:\"minecraft:chests/simple_dungeon\"}");
            c.waitTicks(30);
            world.getServer().runOnServer(s -> {
                var pos=new BlockPos(-3,81,0);
                check(ComparatorInspector.read(s.overworld(),pos)==null,"Unopened loot reports unavailable");
                check(((net.minecraft.world.RandomizableContainer)s.overworld().getBlockEntity(pos)).getLootTable()!=null,"Inspection preserves unopened loot");
                check(ComparatorInspector.read(s.overworld(),new BlockPos(20000,81,20000))==null,"Unloaded chunks are not read");
            });
            menu(c); c.clickScreenButton("comparator_inspector.reset"); c.clickScreenButton("gui.cancel");
            c.runOnClient(mc -> check(InspectorClient.config().radius()==12,"Cancel leaves config unchanged"));
            c.runOnClient(mc -> InspectorClient.configure(new InspectorConfig(false,false,8)));
            c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty(),"Both disabled clears readings"));
            c.runOnClient(mc -> InspectorClient.configure(new InspectorConfig(false,true,1)));
            c.waitTicks(30); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty(),"Radius one excludes distant blocks"));
            // The top build-height section must be scanned without indexing beyond the chunk.
            world.getServer().runCommand("tp @a 0 319 0");
            world.getConnection().waitForClientboundPackets();
            c.runOnClient(mc -> InspectorClient.configure(new InspectorConfig(false,true,32)));
            c.waitTicks(25);
            c.runOnClient(mc -> { InspectorConfig.DEFAULT.save(); InspectorClient.configure(InspectorConfig.DEFAULT); });
        }
        c.runOnClient(mc -> check(InspectorClient.readings().isEmpty(),"Disconnect clears world data"));
    }
}
