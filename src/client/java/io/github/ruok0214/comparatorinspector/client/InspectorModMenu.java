package io.github.ruok0214.comparatorinspector.client;
import com.terraformersmc.modmenu.api.*;
import net.minecraft.client.gui.screens.Screen;
public final class InspectorModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<Screen> getModConfigScreenFactory() { return InspectorScreen::new; }
}
