package io.github.ruok0214.comparatorinspector.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static io.github.ruok0214.comparatorinspector.client.InspectorClient.tr;

public final class InspectorScreen extends Screen {
    private final Screen parent;
    private InspectorConfig draft;
    private boolean failed;
    public InspectorScreen(Screen parent) { super(tr("title")); this.parent = parent; draft = InspectorClient.config(); }
    public InspectorConfig draft() { return draft; }
    public void setAppearance(boolean world, DisplayStyle style) { draft = draft.appearance(world,style); }
    @Override protected void init() {
        int w = Math.min(300, width - 24), x = (width-w)/2, y = Math.max(38, height/2-80);
        addRenderableWidget(Button.builder(toggle("targeted", draft.targeted()), b -> {
            draft = draft.detection(!draft.targeted(), draft.nearby(), draft.radius()); b.setMessage(toggle("targeted", draft.targeted()));
        }).bounds(x,y,w,20).build());
        addRenderableWidget(Button.builder(toggle("nearby", draft.nearby()), b -> {
            draft = draft.detection(draft.targeted(), !draft.nearby(), draft.radius()); b.setMessage(toggle("nearby", draft.nearby()));
        }).bounds(x,y+25,w,20).build());
        addRenderableWidget(new AbstractSliderButton(x,y+50,w,20,tr("radius", draft.radius()), (draft.radius()-1)/31.0) {
            @Override protected void updateMessage() { setMessage(tr("radius", 1+(int)Math.round(value*31))); }
            @Override protected void applyValue() { draft = draft.detection(draft.targeted(), draft.nearby(), 1+(int)Math.round(value*31)); }
        });
        addRenderableWidget(Button.builder(tr("world.title"),b -> minecraft.gui.setScreen(new AppearanceScreen(this,true))).bounds(x,y+80,w,20).build());
        addRenderableWidget(Button.builder(tr("hud.title"),b -> minecraft.gui.setScreen(new AppearanceScreen(this,false))).bounds(x,y+105,w,20).build());
        int bw=(w-8)/3;
        addRenderableWidget(Button.builder(tr("reset"), b -> { draft=InspectorConfig.DEFAULT; rebuildWidgets(); }).bounds(x,height-28,bw,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(x+bw+4,height-28,bw,20).build());
        addRenderableWidget(Button.builder(tr("save"), b -> {
            if (draft.save()) { InspectorClient.configure(draft); onClose(); } else failed=true;
        }).bounds(x+2*(bw+4),height-28,bw,20).build());
    }
    private Component toggle(String key, boolean on) { return tr(key, Component.translatable(on ? "options.on" : "options.off")); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt);
        g.centeredText(font,title,width/2,15,0xFFFFD166);
        int w=Math.min(300,width-24), x=(width-w)/2, y=Math.max(38,height/2-80);
        g.textWithWordWrap(font,tr("hint"),x,y+135,w,0xFFCED8E4);
        if (failed) g.centeredText(font,tr("save_failed"),width/2,height-43,0xFFFF8070);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
