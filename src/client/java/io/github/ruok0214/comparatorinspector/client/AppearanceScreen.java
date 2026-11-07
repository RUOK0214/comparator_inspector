package io.github.ruok0214.comparatorinspector.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static io.github.ruok0214.comparatorinspector.client.InspectorClient.tr;

public final class AppearanceScreen extends Screen {
    private final InspectorScreen parent;
    private final DisplayTarget target;
    private final boolean world;
    private DisplayStyle draft;
    private boolean failed;
    public AppearanceScreen(InspectorScreen parent, DisplayTarget target) {
        super(tr(target.titleKey())); this.parent=parent; this.target=target; this.world=target.inWorld();
        draft=parent.draft().style(target);
    }
    public DisplayStyle draft() { return draft; }
    private int top() { return world ? Math.max(28,height/2-98) : Math.max(32,height/2-90); }
    private int rowStep() { return 22; }
    @Override protected void init() {
        int w=Math.min(300,width-24), x=(width-w)/2, y=top(), step=rowStep();
        addRenderableWidget(Button.builder(enabledLabel(),b -> { draft=draft.enabled(!draft.enabled()); b.setMessage(enabledLabel()); }).bounds(x,y,w,20).build());
        addRenderableWidget(Button.builder(tr("color.primary"),b -> color(false)).bounds(x,y+step,w,20).build());
        addRenderableWidget(Button.builder(tr(world ? "color.target" : "color.background"),b -> color(true)).bounds(x,y+step*2,w,20).build());
        addRenderableWidget(new StyleSlider(x,y+step*3,w,false));
        addRenderableWidget(new StyleSlider(x,y+step*4,w,true));
        if(world) addRenderableWidget(new HeightSlider(x,y+step*5,w));
        addRenderableWidget(Button.builder(valueOnlyLabel(),b -> { draft=draft.valueOnly(!draft.valueOnly()); b.setMessage(valueOnlyLabel()); })
                .bounds(x,y+step*(world ? 6 : 5),w,20).tooltip(Tooltip.create(tr("appearance.value_only_hint"))).build());
        int bw=(w-8)/3;
        addRenderableWidget(Button.builder(tr("reset"),b -> { draft=target.defaults(); rebuildWidgets(); }).bounds(x,height-28,bw,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b -> onClose()).bounds(x+bw+4,height-28,bw,20).build());
        addRenderableWidget(Button.builder(tr("confirm"),b -> {
            if (parent.saveAppearance(target,draft)) onClose(); else failed=true;
        }).bounds(x+2*(bw+4),height-28,bw,20).build());
    }
    public final class HeightSlider extends AbstractSliderButton {
        HeightSlider(int x,int y,int w) {
            super(x,y,w,20,Component.empty(),(draft.heightHundredths()-target.minHeight())/(double)(DisplayStyle.MAX_HEIGHT-target.minHeight()));
            setTooltip(Tooltip.create(tr(target == DisplayTarget.COMPARATOR ? "appearance.comparator_height_hint" : "appearance.height_hint"))); updateMessage();
        }
        private int selectedHeight() { return target.minHeight()+(int)Math.round(value*(DisplayStyle.MAX_HEIGHT-target.minHeight())); }
        @Override protected void updateMessage() {
            setMessage(tr("appearance.height",String.format(java.util.Locale.ROOT,"%.2f",selectedHeight()/100.0)));
        }
        @Override protected void applyValue() { draft=draft.height(selectedHeight()); }
    }
    private Component enabledLabel() { return tr("appearance.enabled", Component.translatable(draft.enabled() ? "options.on" : "options.off")); }
    private Component valueOnlyLabel() { return tr("appearance.value_only", Component.translatable(draft.valueOnly() ? "options.on" : "options.off")); }
    private void color(boolean secondary) {
        var defaults=target.defaults();
        minecraft.gui.setScreen(new ColorScreen(this,secondary ? draft.secondaryColor() : draft.color(),
                secondary ? defaults.secondaryColor() : defaults.color(),draft.outputText(7),value ->
                draft=secondary ? draft.colors(draft.color(),value) : draft.colors(value,draft.secondaryColor())));
    }
    public final class StyleSlider extends AbstractSliderButton {
        private final boolean size;
        StyleSlider(int x,int y,int w,boolean size) {
            super(x,y,w,20,Component.empty(),size ? (draft.scalePercent()-50)/150.0 : draft.opacity()/100.0); this.size=size; updateMessage();
        }
        public boolean sizeControl() { return size; }
        @Override protected void updateMessage() { setMessage(tr(size ? "appearance.size" : "appearance.opacity",size ? 50+(int)Math.round(value*150) : (int)Math.round(value*100))); }
        @Override protected void applyValue() { draft=size ? draft.size(50+(int)Math.round(value*150)) : draft.opacity((int)Math.round(value*100)); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.centeredText(font,title,width/2,15,0xFFFFD166);
        int w=Math.min(300,width-24), x=(width-w)/2, y=top(), step=rowStep(), previewY=y+(world ? 160 : 144);
        g.fill(x+w-22,y+step+5,x+w-8,y+step+15,0xFF000000|draft.color());
        g.fill(x+w-22,y+step*2+5,x+w-8,y+step*2+15,0xFF000000|draft.secondaryColor());
        if (failed) g.centeredText(font,tr("save_failed"),width/2,previewY,0xFFFF8070);
        else if (!draft.visible()) g.centeredText(font,tr("appearance.hidden"),width/2,previewY,0xFFCED8E4);
        else {
            g.pose().pushMatrix(); g.pose().translate(width/2f,previewY); g.pose().scale(draft.scale(),draft.scale());
            if(world) {
                g.centeredText(font,Component.literal(draft.outputText(7)),-45,0,draft.argb(false));
                g.centeredText(font,Component.literal(draft.outputText(15)),45,0,draft.argb(true));
            } else {
                var text=draft.valueOnly() ? Component.literal("7") : tr("signal",7); int tw=font.width(text);
                g.fill(-tw/2-6,-4,tw/2+6,13,draft.backgroundArgb());
                g.centeredText(font,text,0,0,draft.argb(false));
            }
            g.pose().popMatrix();
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
