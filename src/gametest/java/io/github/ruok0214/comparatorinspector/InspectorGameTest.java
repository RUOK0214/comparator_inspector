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
        try {
            c.waitFor(mc -> { var r = InspectorClient.validReading(pos); return r != null && r.uniform() && r.north() == expected; });
        } catch (AssertionError e) {
            throw new AssertionError("Expected output " + expected + " at " + pos + "; actual "
                    + c.computeOnClient(mc -> InspectorClient.validReading(pos) + "; state " + mc.level.getBlockState(pos)), e);
        }
    }
    private static void menu(ClientGameTestContext c) {
        c.getInput().pressKey(GLFW.GLFW_KEY_T); c.waitForScreen(ChatScreen.class);
        c.getInput().typeChars("/ci"); c.getInput().pressKey(GLFW.GLFW_KEY_ENTER); c.waitForScreen(InspectorScreen.class);
    }
    private static void color(ClientGameTestContext c,String button,String hex,String defaultHex) {
        c.clickScreenButton("comparator_inspector."+button); c.waitForScreen(ColorScreen.class);
        c.runOnClient(mc -> {
            var field=mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            field.setValue("#oops");
            check(mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                    .map(net.minecraft.client.gui.components.Button.class::cast).anyMatch(b -> b.getMessage().getString().equals(InspectorClient.tr("confirm").getString()) && !b.active),"Invalid HEX cannot be applied: "+mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).map(b -> b.getMessage().getString()+"="+b.active).toList());
        });
        c.clickScreenButton("comparator_inspector.reset");
        c.runOnClient(mc -> {
            var field=mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                    .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
            check(field.getValue().equals(defaultHex),"Color defaults restore this field's factory color");
            field.setValue(hex);
        });
        c.takeScreenshot("color-picker-ko");
        c.clickScreenButton("comparator_inspector.confirm"); c.waitForScreen(AppearanceScreen.class);
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
            check(!migrated.targeted() && migrated.nearby() && !migrated.comparators() && migrated.radius()==17 && migrated.world().equals(DisplayStyle.WORLD) && migrated.hud().equals(DisplayStyle.HUD),"Legacy settings retain detection and receive display defaults with comparator output off");
            var previous=InspectorConfig.fromJson("{\"world\":{\"enabled\":false,\"color\":444,\"secondaryColor\":555,\"opacity\":65,\"scalePercent\":125}}");
            check(previous.world().heightHundredths()==18 && !previous.world().enabled() && previous.world().color()==444 && previous.world().opacity()==65 && previous.world().scalePercent()==125,"0.2 appearance keeps its values and gets original label height");
            check(DisplayStyle.WORLD.height(-1).height()==0 && DisplayStyle.WORLD.height(400).height()==3,"Height clamps to supported range");
        });
        menu(c);
        c.clickScreenButton(InspectorClient.tr("nearby",net.minecraft.network.chat.Component.translatable("options.off")).getString());
        c.clickScreenButton("comparator_inspector.world.title"); c.waitForScreen(AppearanceScreen.class);
        var heightPoint=c.computeOnClient(mc -> {
            var slider=mc.gui.screen().children().stream().filter(AppearanceScreen.HeightSlider.class::isInstance)
                    .map(AppearanceScreen.HeightSlider.class::cast).findFirst().orElseThrow();
            return new double[]{(slider.getX()+slider.getWidth()/2.0)*mc.getWindow().getScreenWidth()/mc.gui.screen().width,
                    (slider.getY()+10.0)*mc.getWindow().getScreenHeight()/mc.gui.screen().height};
        });
        c.getInput().setCursorPos(heightPoint[0],heightPoint[1]); c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); c.waitTick();
        c.runOnClient(mc -> check(((AppearanceScreen)mc.gui.screen()).draft().heightHundredths()==150,"Height slider selects 1.50 blocks"));
        color(c,"color.primary","#43E7D4","#FFFFFF"); color(c,"color.target","#FF7061","#FFD166");
        styleSlider(c,true,2.0/3); styleSlider(c,false,.8);
        c.takeScreenshot("world-appearance-ko");
        c.getInput().resizeWindow(854,480); c.getInput().setCursorPos(20,20); c.waitTicks(3); c.takeScreenshot("world-height-small-ko");
        c.getInput().resizeWindow(1000,700); c.waitTicks(3);
        c.clickScreenButton("comparator_inspector.confirm");
        c.runOnClient(mc -> {
            var savedWorld=InspectorConfig.load();
            check(savedWorld.world().heightHundredths()==150 && savedWorld.world().color()==0x43E7D4,"World confirmation immediately persists its own display");
            check(!savedWorld.nearby() && ((InspectorScreen)mc.gui.screen()).draft().nearby(),"Appearance confirmation does not save pending detection changes");
            check(savedWorld.hud().equals(DisplayStyle.HUD),"World confirmation preserves the other display");
        });
        c.clickScreenButton("comparator_inspector.hud.title");
        c.runOnClient(mc -> check(mc.gui.screen().children().stream().noneMatch(AppearanceScreen.HeightSlider.class::isInstance),"Height control is only in world display settings"));
        color(c,"color.primary","#7DDF64","#FFD166"); color(c,"color.background","#202060","#141B24");
        styleSlider(c,true,1.0/3);
        c.takeScreenshot("hud-appearance-ko");
        c.getInput().resizeWindow(854,480); c.waitTicks(3); c.takeScreenshot("appearance-small-ko");
        c.clickScreenButton("comparator_inspector.confirm"); c.clickScreenButton("gui.cancel");
        value(c,CHEST,15);
        var saved=c.computeOnClient(mc -> InspectorClient.config());
        c.runOnClient(mc -> {
            check(InspectorConfig.load().equals(saved) && !saved.nearby(),"Both confirmed displays persist after parent Cancel, while pending detection is discarded");
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
            c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.WORLD,saved.world().height(height)))); value(c,CHEST,15);
            c.runOnClient(mc -> check(Math.abs(InspectorClient.extractLabels().getFirst().position().y-(CHEST.getY()+1+height/100.0))<.0001,"Height endpoints render at the requested position"));
        }
        c.runOnClient(mc -> InspectorClient.configure(saved)); value(c,CHEST,15);
        // Editing detection must preserve the two independently saved styles.
        menu(c); c.clickScreenButton(InspectorClient.tr("nearby",net.minecraft.network.chat.Component.translatable("options.off")).getString());
        c.clickScreenButton("comparator_inspector.confirm");
        value(c,BARREL,0);
        c.runOnClient(mc -> check(InspectorClient.extractLabels().stream().allMatch(label -> Math.abs(label.position().y-83.5)<.0001),"Nearby and targeted labels share the selected height"));
        c.runOnClient(mc -> check(InspectorClient.config().world().equals(saved.world()) && InspectorClient.config().hud().equals(saved.hud()),"Changing detection preserves colors"));
        menu(c); c.clickScreenButton("comparator_inspector.world.title");
        c.clickScreenButton("comparator_inspector.reset"); c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        c.runOnClient(mc -> check(((InspectorScreen)mc.gui.screen()).draft().world().equals(saved.world()),"Escape discards appearance changes"));
        c.clickScreenButton("comparator_inspector.hud.title"); c.clickScreenButton("comparator_inspector.reset"); c.clickScreenButton("comparator_inspector.confirm");
        c.clickScreenButton("gui.cancel");
        c.runOnClient(mc -> {
            check(InspectorClient.config().hud().equals(DisplayStyle.HUD) && InspectorConfig.load().hud().equals(DisplayStyle.HUD),"Confirmed default display survives parent Cancel");
            check(InspectorClient.config().world().equals(saved.world()),"Resetting HUD keeps saved world appearance");
        });
        menu(c); c.clickScreenButton("comparator_inspector.world.title");
        c.clickScreenButton(InspectorClient.tr("appearance.enabled",net.minecraft.network.chat.Component.translatable("options.on")).getString());
        c.clickScreenButton("comparator_inspector.confirm"); c.clickScreenButton("comparator_inspector.confirm");
        value(c,CHEST,15);
        c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()!=null,"World disabled does not hide HUD"));
        c.takeScreenshot("hud-only-ko");
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.HUD,saved.hud().enabled(false)))); value(c,CHEST,15);
        c.runOnClient(mc -> check(!InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"HUD disabled does not hide world labels"));
        c.takeScreenshot("world-only-ko");
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.WORLD,saved.world().enabled(false)).appearance(DisplayTarget.HUD,saved.hud().enabled(false))));
        c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty() && InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"Both displays disabled stops reads"));
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.WORLD,saved.world().opacity(0)).appearance(DisplayTarget.HUD,saved.hud().opacity(0))));
        c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()==null,"Zero opacity hides both displays"));
        c.runOnClient(mc -> { InspectorConfig.DEFAULT.save(); InspectorClient.configure(InspectorConfig.DEFAULT); }); value(c,CHEST,15);
    }
    private static InspectorClient.Label label(BlockPos pos) {
        return InspectorClient.extractLabels().stream().filter(l -> l.position().x==pos.getX()+.5 && l.position().z==pos.getZ()+.5).findFirst().orElseThrow();
    }
    private static void checkLabel(BlockPos pos, DisplayStyle style, boolean aimed) {
        var label=label(pos);
        check(label.color()==style.argb(aimed) && Math.abs(label.scale()-.32f*style.scale())<.001
                && Math.abs(label.position().y-(pos.getY()+1+style.height()))<.0001,"Independent color, opacity, size and height at "+pos);
    }
    private static void comparatorAppearances(ClientGameTestContext c) {
        var pos=CHEST.south();
        c.runOnClient(mc -> {
            var legacy=InspectorConfig.fromJson("{\"comparators\":true,\"world\":{\"enabled\":false,\"color\":123456,\"secondaryColor\":654321,\"opacity\":70,\"scalePercent\":150,\"heightHundredths\":125}}");
            check(legacy.comparators() && legacy.comparatorStyle().equals(legacy.world()),"0.5 settings migrate the entire shared style without changing appearance");
            check(legacy.save() && InspectorConfig.load().equals(legacy),"Migrated comparator style survives saving and reloading");
            var explicit=InspectorConfig.fromJson("{\"world\":{\"color\":123},\"comparatorStyle\":{\"color\":456,\"heightHundredths\":250}}");
            check(explicit.comparatorStyle().color()==456 && explicit.comparatorStyle().heightHundredths()==250,"Explicit comparator style takes precedence over legacy appearance");
        });
        var initial=InspectorConfig.DEFAULT.comparatorOutputs(true).detection(true,true,8)
                .appearance(DisplayTarget.WORLD,DisplayStyle.WORLD.colors(0x43E7D4,0x7DDF64).height(40).size(125).opacity(90))
                .appearance(DisplayTarget.HUD,DisplayStyle.HUD.colors(0xFFD166,0x202060));
        c.runOnClient(mc -> { check(initial.save(),"Save initial independent displays"); InspectorClient.configure(initial); });
        aim(c,CHEST); value(c,CHEST,15); value(c,pos,15);
        menu(c);
        c.clickScreenButton(InspectorClient.tr("targeted",net.minecraft.network.chat.Component.translatable("options.on")).getString());
        c.getInput().resizeWindow(854,480); c.getInput().setCursorPos(20,20); c.waitTicks(3); c.takeScreenshot("three-display-settings-small-ko");
        c.clickScreenButton("comparator_inspector.comparator.title"); c.waitForScreen(AppearanceScreen.class);
        color(c,"color.primary","#BC86FF","#FFFFFF"); color(c,"color.target","#FF7061","#FFD166");
        styleSlider(c,true,2.0/3); styleSlider(c,false,.7);
        var point=c.computeOnClient(mc -> {
            var slider=mc.gui.screen().children().stream().filter(AppearanceScreen.HeightSlider.class::isInstance)
                    .map(AppearanceScreen.HeightSlider.class::cast).findFirst().orElseThrow();
            return new double[]{(slider.getX()+slider.getWidth()/2.0)*mc.getWindow().getScreenWidth()/mc.gui.screen().width,
                    (slider.getY()+10.0)*mc.getWindow().getScreenHeight()/mc.gui.screen().height};
        });
        c.getInput().setCursorPos(point[0],point[1]); c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT); c.waitTick();
        c.getInput().setCursorPos(20,20); c.takeScreenshot("comparator-appearance-small-ko");
        c.clickScreenButton("comparator_inspector.confirm");
        c.runOnClient(mc -> {
            var saved=InspectorConfig.load();
            check(saved.comparatorStyle().equals(new DisplayStyle(true,0xBC86FF,0xFF7061,70,150,150)),"Comparator controls save immediately");
            check(saved.world().equals(initial.world()) && saved.hud().equals(initial.hud()),"Comparator confirmation preserves general and HUD styles");
            check(saved.targeted() && !((InspectorScreen)mc.gui.screen()).draft().targeted(),"Comparator confirmation leaves parent detection changes unconfirmed");
        });
        c.clickScreenButton("gui.cancel");
        c.getInput().resizeWindow(1000,700); c.waitTicks(3); value(c,CHEST,15); value(c,pos,15);
        var saved=c.computeOnClient(mc -> InspectorClient.config());
        c.runOnClient(mc -> {
            check(InspectorConfig.load().equals(saved),"Comparator confirmation survives parent Cancel");
            checkLabel(CHEST,saved.world(),true); checkLabel(pos,saved.comparatorStyle(),false);
            check(InspectorClient.hudDisplay().color()==saved.hud().argb(false),"HUD retains its own style");
        });
        c.takeScreenshot("separate-comparator-style-ko");
        // Unconfirmed resets are reversible; confirmed reset affects only its display.
        menu(c); c.clickScreenButton("comparator_inspector.comparator.title");
        c.clickScreenButton("comparator_inspector.reset"); c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); c.clickScreenButton("gui.cancel");
        c.runOnClient(mc -> check(InspectorConfig.load().equals(saved),"Cancel discards comparator defaults"));
        menu(c); c.clickScreenButton("comparator_inspector.world.title");
        c.clickScreenButton("comparator_inspector.reset"); c.clickScreenButton("comparator_inspector.confirm"); c.clickScreenButton("gui.cancel");
        c.runOnClient(mc -> check(InspectorConfig.load().world().equals(DisplayStyle.WORLD) && InspectorConfig.load().comparatorStyle().equals(saved.comparatorStyle()),"General display reset does not change comparator style"));
        menu(c); c.clickScreenButton("comparator_inspector.comparator.title");
        c.clickScreenButton("comparator_inspector.reset"); c.clickScreenButton("comparator_inspector.confirm"); c.clickScreenButton("gui.cancel");
        c.runOnClient(mc -> {
            var reset=InspectorConfig.load();
            check(reset.comparatorStyle().equals(DisplayStyle.COMPARATOR) && reset.world().equals(DisplayStyle.WORLD) && reset.hud().equals(saved.hud()),"Comparator reset preserves the other displays");
        });
        for (int height : new int[]{0,300}) {
            c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.COMPARATOR,saved.comparatorStyle().height(height))));
            value(c,CHEST,15); value(c,pos,15);
            c.runOnClient(mc -> { checkLabel(CHEST,saved.world(),true); checkLabel(pos,saved.comparatorStyle().height(height),false); });
        }
        // With normal labels and the info panel hidden, only comparator queries and labels remain.
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.WORLD,saved.world().enabled(false)).appearance(DisplayTarget.HUD,saved.hud().enabled(false))));
        value(c,pos,15);
        c.runOnClient(mc -> check(InspectorClient.validReading(CHEST)==null && InspectorClient.extractLabels().size()==1 && InspectorClient.hudDisplay()==null,"General display can be hidden without hiding comparator output"));
        // Hiding comparator labels does not hide general labels, nor fetch invisible comparators.
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.COMPARATOR,saved.comparatorStyle().enabled(false)).appearance(DisplayTarget.HUD,saved.hud().enabled(false))));
        value(c,CHEST,15);
        c.runOnClient(mc -> check(InspectorClient.validReading(pos)==null && !InspectorClient.extractLabels().isEmpty(),"Comparator display can be hidden independently"));
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.COMPARATOR,saved.comparatorStyle().opacity(0))));
        value(c,CHEST,15);
        c.runOnClient(mc -> check(InspectorClient.validReading(pos)==null,"Zero comparator opacity stops nearby comparator reads"));
        c.runOnClient(mc -> InspectorClient.configure(saved.appearance(DisplayTarget.WORLD,saved.world().enabled(false)).appearance(DisplayTarget.COMPARATOR,saved.comparatorStyle().enabled(false)).appearance(DisplayTarget.HUD,saved.hud().enabled(false))));
        c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty() && InspectorClient.extractLabels().isEmpty(),"All three hidden stops all reads"));
        // Targeted comparator text uses its own highlight, and the info panel can remain visible on its own.
        c.runOnClient(mc -> {
            InspectorClient.configure(saved.detection(true,false,8));
            var delta=net.minecraft.world.phys.Vec3.atLowerCornerWithOffset(pos,.5,.0625,.5).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        });
        c.waitFor(mc -> pos.equals(InspectorClient.target(mc))); value(c,pos,15);
        c.runOnClient(mc -> {
            checkLabel(pos,saved.comparatorStyle(),true);
            InspectorClient.configure(saved.detection(true,false,8).appearance(DisplayTarget.COMPARATOR,saved.comparatorStyle().enabled(false)));
        });
        value(c,pos,15);
        c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.hudDisplay()!=null,"Comparator info panel works with its above-block display hidden"));
        c.runOnClient(mc -> { InspectorConfig.DEFAULT.save(); InspectorClient.configure(InspectorConfig.DEFAULT); });
        aim(c,CHEST); value(c,CHEST,15);
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
            // Comparator output is independently opt-in, even when both other detection modes are off.
            var comparator=CHEST.south();
            c.runOnClient(mc -> check(InspectorClient.validReading(comparator)==null,"Comparator output is disabled by default"));
            menu(c);
            c.clickScreenButton(InspectorClient.tr("targeted",net.minecraft.network.chat.Component.translatable("options.on")).getString());
            c.clickScreenButton(InspectorClient.tr("comparators",net.minecraft.network.chat.Component.translatable("options.off")).getString());
            c.getInput().setCursorPos(20,20); c.takeScreenshot("comparator-output-settings-ko");
            c.getInput().resizeWindow(854,480); c.waitTicks(3); c.takeScreenshot("comparator-settings-small-ko");
            c.clickScreenButton("comparator_inspector.confirm");
            c.getInput().resizeWindow(1000,700);
            value(c,comparator,15);
            c.runOnClient(mc -> {
                var cfg=InspectorClient.config();
                check(cfg.comparators() && !cfg.targeted() && !cfg.nearby() && InspectorConfig.load().equals(cfg),"Comparator setting saves independently");
                check(InspectorClient.validReading(CHEST)==null && InspectorClient.extractLabels().size()==1,"Only comparator is shown when the other modes are off");
                check(InspectorClient.extractLabels().getFirst().text().equals("15 / 15"),"Comparator label displays actual output");
            });
            c.takeScreenshot("comparator-output-15-ko");
            world.getServer().runCommand("setblock -1 81 1 minecraft:redstone_block");
            c.waitTicks(10); value(c,comparator,15);
            world.getServer().runCommand("setblock 0 81 1 minecraft:comparator[facing=north,mode=subtract]");
            // A command changes the mode without the interaction's refresh; run its normal scheduled update.
            world.getServer().runOnServer(s -> s.overworld().scheduleTick(comparator,Blocks.COMPARATOR,2)); c.waitTicks(6);
            value(c,comparator,0);
            c.takeScreenshot("comparator-subtract-zero-ko");
            // A second comparator provides a stable side input of 1, so subtraction must show 14.
            world.getServer().runCommand("setblock -2 81 1 minecraft:barrel");
            world.getServer().runCommand("setblock -1 81 1 minecraft:comparator[facing=west]");
            world.getServer().runOnServer(s -> {
                var pos=new BlockPos(-2,81,1);
                ((Container)s.overworld().getBlockEntity(pos)).setItem(0,new ItemStack(Items.STONE,64));
                s.overworld().updateNeighbourForOutputSignal(pos,Blocks.BARREL);
            });
            value(c,new BlockPos(-1,81,1),1); value(c,comparator,14);
            c.takeScreenshot("comparator-subtract-14-ko");
            world.getServer().runCommand("setblock 0 81 1 minecraft:comparator[facing=north,mode=compare]");
            world.getServer().runOnServer(s -> s.overworld().scheduleTick(comparator,Blocks.COMPARATOR,2)); c.waitTicks(6);
            value(c,comparator,15);
            // Side input stronger than the main input suppresses compare-mode output.
            world.getServer().runCommand("setblock -1 81 1 minecraft:redstone_block");
            world.getServer().runOnServer(s -> {
                var chest=(Container)s.overworld().getBlockEntity(CHEST); chest.clearContent();
                chest.setItem(0,new ItemStack(Items.STONE,64));
                s.overworld().updateNeighbourForOutputSignal(CHEST,Blocks.CHEST);
            });
            value(c,comparator,0);
            world.getServer().runCommand("setblock -1 81 1 minecraft:air");
            world.getServer().runCommand("setblock -2 81 1 minecraft:air");
            value(c,comparator,1);
            c.runOnClient(mc -> InspectorClient.configure(InspectorClient.config().detection(false,false,1)));
            c.waitTicks(25); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty(),"Comparator obeys nearby radius"));
            c.runOnClient(mc -> InspectorClient.configure(InspectorClient.config().detection(false,false,8)));
            value(c,comparator,1);
            menu(c); c.clickScreenButton("comparator_inspector.world.title");
            c.clickScreenButton("comparator_inspector.confirm"); c.clickScreenButton("gui.cancel");
            c.runOnClient(mc -> check(InspectorClient.config().comparators() && InspectorConfig.load().comparators(),"Immediate appearance save preserves comparator option"));
            c.runOnClient(mc -> InspectorClient.configure(InspectorClient.config().appearance(DisplayTarget.COMPARATOR,DisplayStyle.COMPARATOR.height(150).colors(0x43E7D4,0xFF7061))));
            value(c,comparator,1);
            c.runOnClient(mc -> {
                var label=InspectorClient.extractLabels().getFirst();
                check(Math.abs(label.position().y-83.5)<.0001 && label.color()==0xFF43E7D4,"Comparator obeys its own height and color");
                InspectorClient.configure(InspectorClient.config().appearance(DisplayTarget.COMPARATOR,DisplayStyle.COMPARATOR.enabled(false)));
            });
            c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.extractLabels().isEmpty() && InspectorClient.readings().isEmpty(),"Comparator display toggle stops standalone comparator reads"));
            c.runOnClient(mc -> InspectorClient.configure(InspectorClient.config().appearance(DisplayTarget.COMPARATOR,DisplayStyle.COMPARATOR)));
            value(c,comparator,1);
            world.getServer().runCommand("setblock 0 81 1 minecraft:stone");
            c.waitFor(mc -> InspectorClient.validReading(comparator)==null && InspectorClient.extractLabels().isEmpty());
            world.getServer().runCommand("setblock 0 81 1 minecraft:comparator[facing=north]");
            world.getServer().runOnServer(s -> s.overworld().scheduleTick(comparator,Blocks.COMPARATOR,2));
            world.getConnection().waitForClientboundPackets(); c.waitTicks(6);
            value(c,comparator,1);
            c.runOnClient(mc -> {
                InspectorClient.configure(InspectorClient.config().detection(true,false,8));
                var delta=net.minecraft.world.phys.Vec3.atLowerCornerWithOffset(comparator,.5,.0625,.5).subtract(mc.player.getEyePosition());
                mc.player.setYRot((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            });
            c.waitFor(mc -> comparator.equals(InspectorClient.target(mc))); value(c,comparator,1);
            c.runOnClient(mc -> {
                check(InspectorClient.hudDisplay()!=null && InspectorClient.hudDisplay().text().getString().contains("1 / 15"),"Targeted comparator reports actual output in the info panel");
                check(InspectorClient.extractLabels().getFirst().color()==DisplayStyle.WORLD.argb(true),"Targeted comparator uses highlight color");
                InspectorClient.configure(InspectorClient.config().detection(false,false,8));
            });
            menu(c);
            c.clickScreenButton(InspectorClient.tr("comparators",net.minecraft.network.chat.Component.translatable("options.on")).getString());
            c.clickScreenButton("comparator_inspector.confirm");
            c.waitTicks(10); c.runOnClient(mc -> check(InspectorClient.readings().isEmpty() && InspectorClient.extractLabels().isEmpty() && !InspectorConfig.load().comparators(),"Disabling comparator output clears and saves"));
            world.getServer().runOnServer(s -> {
                var chest=(Container)s.overworld().getBlockEntity(CHEST);
                for(int i=0;i<chest.getContainerSize();i++) chest.setItem(i,new ItemStack(Items.STONE,64));
                s.overworld().updateNeighbourForOutputSignal(CHEST,Blocks.CHEST);
            });
            c.runOnClient(mc -> { InspectorConfig.DEFAULT.save(); InspectorClient.configure(InspectorConfig.DEFAULT); });
            aim(c,CHEST); value(c,CHEST,15);
            appearances(c);
            comparatorAppearances(c);
            // Covering the chest changes the actual vanilla comparator value.
            world.getServer().runCommand("setblock 0 82 0 minecraft:stone"); value(c,CHEST,0);
            world.getServer().runCommand("setblock 0 82 0 minecraft:air"); value(c,CHEST,15);
            menu(c);
            c.clickScreenButton(InspectorClient.tr("nearby",net.minecraft.network.chat.Component.translatable("options.off")).getString());
            c.takeScreenshot("settings-ko");
            c.clickScreenButton("comparator_inspector.confirm");
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
