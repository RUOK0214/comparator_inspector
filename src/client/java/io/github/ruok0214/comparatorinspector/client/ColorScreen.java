package io.github.ruok0214.comparatorinspector.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
import static io.github.ruok0214.comparatorinspector.client.InspectorClient.tr;

public final class ColorScreen extends Screen {
    private static final int[] PALETTE={0xFFFFFF,0xFFD166,0x43E7D4,0x478CFF,0xFF7061,0x7DDF64,0xBC86FF,0x141B24};
    private final Screen parent;
    private final IntConsumer accept;
    private final int defaultColor;
    private final String sampleText;
    private int rgb;
    private EditBox hex;
    private Button done;
    public ColorScreen(Screen parent,int rgb,int defaultColor,String sampleText,IntConsumer accept) {
        super(tr("color.title")); this.parent=parent; this.rgb=rgb; this.defaultColor=defaultColor; this.sampleText=sampleText; this.accept=accept;
    }
    @Override protected void init() {
        int w=Math.min(300,width-24),x=(width-w)/2,y=Math.max(38,height/2-80);
        hex=new EditBox(font,x,y,w,20,tr("color.hex")); hex.setMaxLength(7); hex.setValue(String.format("#%06X",rgb));
        hex.setTooltip(Tooltip.create(tr("color.hex"))); addRenderableWidget(hex);
        for(int i=0;i<3;i++) addRenderableWidget(new ChannelSlider(x,y+25+i*25,w,i));
        for(int i=0;i<PALETTE.length;i++) {
            final int color=PALETTE[i];
            addRenderableWidget(new Button(x+i*(w/8),y+104,w/8-2,18,Component.literal(String.format("#%06X",color)),b -> { rgb=color; rebuildWidgets(); },supplier -> supplier.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float dt) {
                    g.fill(getX()+3,getY()+3,getX()+getWidth()-3,getY()+getHeight()-3,0xFF000000|color);
                    g.outline(getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF687580);
                }
            });
        }
        int bw=(w-8)/3;
        addRenderableWidget(Button.builder(tr("reset"),b -> hex.setValue(String.format("#%06X",defaultColor))).bounds(x,height-28,bw,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b -> onClose()).bounds(x+bw+4,height-28,bw,20).build());
        done=addRenderableWidget(Button.builder(tr("confirm"),b -> { accept.accept(rgb); onClose(); }).bounds(x+2*(bw+4),height-28,bw,20).build());
        hex.setResponder(text -> {
            boolean valid=text.matches("#?[0-9a-fA-F]{6}"); done.active=valid;
            if(valid) {
                rgb=Integer.parseInt(text.replace("#",""),16);
                for(var child:children()) if(child instanceof ChannelSlider slider) slider.sync();
            }
        });
    }
    private final class ChannelSlider extends AbstractSliderButton {
        private final int channel;
        ChannelSlider(int x,int y,int w,int channel) {
            super(x,y,w,20,Component.empty(),((rgb >> (16-channel*8)) & 255)/255.0); this.channel=channel; updateMessage();
        }
        void sync() { value=((rgb >> (16-channel*8)) & 255)/255.0; updateMessage(); }
        @Override protected void updateMessage() { setMessage(tr("color."+new String[]{"red","green","blue"}[channel],Math.round(value*255))); }
        @Override protected void applyValue() {
            int shift=16-channel*8; rgb=(rgb & ~(255<<shift)) | ((int)Math.round(value*255)<<shift);
            hex.setValue(String.format("#%06X",rgb));
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt) {
        super.extractRenderState(g,mx,my,dt); g.centeredText(font,title,width/2,15,0xFFFFD166);
        int y=Math.max(38,height/2-80)+140;
        g.centeredText(font,Component.literal(String.format("#%06X   %s",rgb,sampleText)),width/2,y,0xFF000000|rgb);
        if(!done.active) g.centeredText(font,tr("color.invalid"),width/2,y+14,0xFFFF8070);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
