package io.github.ruok0214.comparatorinspector;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.config.ModMenuConfig;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import com.terraformersmc.modmenu.util.mod.fabric.FabricIconHandler;
import io.github.ruok0214.comparatorinspector.client.InspectorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

@SuppressWarnings("UnstableApiUsage")
final class ModMenuChecks {
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
    static void run(ClientGameTestContext c,String language) {
        var parent=c.computeOnClient(mc -> mc.gui.screen());
        boolean translated=ModMenuConfig.TRANSLATE_DESCRIPTIONS.getValue();
        try {
            c.runOnClient(mc -> {
                ModMenuConfig.TRANSLATE_DESCRIPTIONS.setValue(true);
                mc.gui.setScreen(ModMenuApi.createModsScreen(parent));
            });
            c.waitForScreen(ModsScreen.class);
            c.runOnClient(mc -> {
                var screen=(ModsScreen)mc.gui.screen();
                screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Comparator Inspector");
                var list=screen.children().stream().filter(ModListWidget.class::isInstance).map(ModListWidget.class::cast).findFirst().orElseThrow();
                list.select(list.children().stream().filter(e -> e.getMod().getId().equals("comparator_inspector")).findFirst().orElseThrow());
                var mod=screen.getSelectedEntry().getMod();
                check(mod.getVersion().equals("0.4.0"),"Mod Menu shows release version");
                check(mod.getTranslatedDescription().equals(Component.translatable("modmenu.descriptionTranslation.comparator_inspector").getString()),"Localized description resolves");
                check(mod.getTranslatedSummary().equals(Component.translatable("modmenu.summaryTranslation.comparator_inspector").getString()),"Localized summary resolves");
                var container=FabricLoader.getInstance().getModContainer("comparator_inspector").orElseThrow();
                check(container.getMetadata().getIconPath(64).orElseThrow().equals("assets/comparator_inspector/icon.png"),"Icon path is registered");
                try(var icons=new FabricIconHandler()) {
                    check(icons.createIcon(container,container.getMetadata().getIconPath(64).orElseThrow())!=null,"Mod Menu decodes packaged icon");
                }
            });
            c.waitTicks(3); c.takeScreenshot("modmenu-"+language);
            c.getInput().setCursorPos(800,500); c.getInput().scroll(-100); c.waitTicks(3); c.takeScreenshot("modmenu-details-"+language);
            c.clickScreenButton("modmenu.configure"); c.waitForScreen(InspectorScreen.class);
            c.runOnClient(mc -> mc.gui.screen().onClose()); c.waitForScreen(ModsScreen.class);
            c.runOnClient(mc -> {
                ModMenuConfig.TRANSLATE_DESCRIPTIONS.setValue(false);
                var mod=((ModsScreen)mc.gui.screen()).getSelectedEntry().getMod();
                check(mod.getTranslatedDescription().equals(mod.getDescription()) && mod.getDescription().contains("/ci"),"English fallback includes start instructions");
                mc.gui.screen().onClose(); check(mc.gui.screen()==parent,"Return to original screen");
            });
        } finally { c.runOnClient(mc -> ModMenuConfig.TRANSLATE_DESCRIPTIONS.setValue(translated)); }
    }
}
