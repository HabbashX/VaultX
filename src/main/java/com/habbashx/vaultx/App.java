package com.habbashx.vaultx;

import com.habbashx.vaultx.core.Fonts;
import com.habbashx.vaultx.ui.AppSettings;
import com.habbashx.vaultx.ui.Themes;
import org.jetbrains.annotations.Contract;

import javax.swing.SwingUtilities;

public final class App {

    @Contract(pure = true)
    private App() {}

    public static void main(String[] args) {
        main();
    }

    static void main() {
        Fonts.registerBundledFonts();
        Themes.apply(AppSettings.theme(), AppSettings.appFontFamily(), AppSettings.appFontSize());
        SwingUtilities.invokeLater(() -> new com.habbashx.vaultx.ui.LoginDialog().setVisible(true));
    }
}