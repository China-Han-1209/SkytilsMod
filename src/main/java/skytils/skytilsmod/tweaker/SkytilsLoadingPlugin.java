/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2022 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package skytils.skytilsmod.tweaker;

import kotlin.KotlinVersion;
import kotlin.text.StringsKt;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

@IFMLLoadingPlugin.Name("Skytils On Top")
@IFMLLoadingPlugin.SortingIndex(69)
public class SkytilsLoadingPlugin implements IFMLLoadingPlugin {

    public static final String missingDependency =
            "<html><p>" +
            "Skytils has detected a possible missing dependency<br>" +
            "The most likely reason is Essential failed to load.<br>" +
            "Essential might also not work in your country.<br>" +
            "Check the Skytils Discord for any announcements, and<br>" +
            "if there are none, ask for support." +
            "</p></html>";

    public static final String kotlinErrorMessage =
        "<html><p>" +
        "Skytils has detected a mod with an older version of Kotlin.<br>" +
        "The most common culprit is the ChatTriggers mod.<br>" +
        "If you do have ChatTriggers, you can update to 1.3.2<br>" +
        "or later to fix the issue. https://www.chattriggers.com/<br>" +
        "In order to resolve this conflict you must<br>" +
        "deleted the outdated mods.<br>" +
        "If you have already done this and are still getting this error,<br>" +
        "or need assistance, ask for support in the Discord.";

    public static final String badMixinVersionMessage =
        "<html><p>" +
        "Skytils has detected an older version of Mixin.<br>" +
        "Many of my features require Mixin 0.7 or later!<br>" +
        "In order to resolve this conflict you must remove<br>" +
        "any mods with a Mixin version below 0.7.<br>" +
        "You can also try to rename Skytils be above other mods alphabetically<br>" +
        "by changing Skytils.jar to !Skytils.jar<br>" +
        "If you have already done this and are still getting this error,<br>" +
        "ask for support in the Discord.";

    public static final String liteloaderUserMessage =
        "<html><p>" +
        "Skytils has detected that you are using LiteLoader.<br>" +
        "LiteLoader bundles an older, incompatible version of Mixin.<br>" +
        "In order to resolve this conflict you must launch<br>" +
        "Minecraft without LiteLoader.<br>" +
        "If you have already done this and are still getting this error,<br>" +
        "ask for support in the Discord." +
        "</p></html>";

    private static final String voidChatMessage =
        "<html><p>" +
        "Skytils has detected that you are using VoidChat.<br>" +
        "VoidChat breaks many of my features!<br>" +
        "In order to resolve this conflict you must remove<br>" +
        "VoidChat from your Minecraft mods folder.<br>" +
        "A good alternative is Patcher at https://sk1er.club/mods/Patcher.<br>" +
        "If you have already done this and are still getting this error,<br>" +
        "ask for support in the Discord." +
        "</p></html>";

    private static final String betterFPSMessage =
        "<html><p>" +
        "Skytils has detected that you are using BetterFPS.<br>" +
        "BetterFPS breaks my core plugins, and also breaks the game!<br>" +
        "In order to resolve this conflict you must remove<br>" +
        "BetterFPS from your Minecraft mods folder.<br>" +
        "You probably will not notice a change in your FPS.<br>" +
        "Video showcasing breaking changes: https://streamable.com/q4ip5u.<br>" +
        "If you have already done this and are still getting this error,<br>" +
        "ask for support in the Discord." +
        "</p></html>";

    private final SkytilsLoadingPluginKt kotlinPlugin;

    public SkytilsLoadingPlugin() throws URISyntaxException {
        if (System.getProperty("skytils.skipStartChecks") == null) {
            if (!checkForClass("kotlin.KotlinVersion") || !checkForClass("gg.essential.api.EssentialAPI")) {
                showMessage(missingDependency);
                exit();
            }
            if (!KotlinVersion.CURRENT.isAtLeast(1, 5, 0)) {
                final File file = new File(KotlinVersion.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File realFile = file;
                for (int i = 0; i < 5; i++) {
                    if (realFile == null) {
                        realFile = file;
                        break;
                    }
                    if (!realFile.getName().endsWith(".jar!") && !realFile.getName().endsWith(".jar")) {
                        realFile = realFile.getParentFile();
                    } else break;
                }

                String name = realFile.getName().contains(".jar") ? realFile.getName() : StringsKt.substringAfterLast(StringsKt.substringBeforeLast(file.getAbsolutePath(), ".jar", "unknown"), "/", "Unknown");

                if (name.endsWith("!")) name = name.substring(0, name.length() - 1);

                showMessage(kotlinErrorMessage + "<br>The culprit seems to be " + name + "<br>It bundles version " + KotlinVersion.CURRENT + "</p></html>");
                exit();
            }
            if (checkForClass("com.sky.voidchat.EDFMLLoadingPlugin")) {
                showMessage(voidChatMessage);
                exit();
            }
            if (checkForClass("me.guichaguri.betterfps.BetterFpsHelper")) {
                showMessage(betterFPSMessage);
                exit();
            }
        }
        kotlinPlugin = new SkytilsLoadingPluginKt();
    }

    private boolean checkForClass(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private void showMessage(String errorMessage) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // This makes the JOptionPane show on taskbar and stay on top
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        Icon icon = null;
        try {
            URL url = SkytilsLoadingPlugin.class.getResource("/assets/skytils/sychicpet.gif");
            if (url != null) {
                icon = new ImageIcon(Toolkit.getDefaultToolkit().createImage(url).getScaledInstance(50, 50, Image.SCALE_DEFAULT));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        JButton discordLink = new JButton("Join the Discord");
        discordLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                try {
                    Desktop.getDesktop().browse(new URI("https://discord.gg/skytils"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        JButton close = new JButton("Close");
        close.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                exit();
            }
        });

        Object[] options = new Object[]{discordLink, close};
        JOptionPane.showOptionDialog(
            frame,
            errorMessage,
            "Skytils Error",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.ERROR_MESSAGE,
            icon,
            options,
            options[0]
        );
        exit();
    }

    /**
     * Bypasses forges security manager to exit the jvm
     */
    public static void exit() {
        try {
            Class<?> clazz = Class.forName("java.lang.Shutdown");
            Method m_exit = clazz.getDeclaredMethod("exit", int.class);
            m_exit.setAccessible(true);
            m_exit.invoke(null, 0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return kotlinPlugin.getASMTransformerClass();
    }

    @Override
    public String getModContainerClass() {
        return kotlinPlugin.getModContainerClass();
    }

    @Override
    public String getSetupClass() {
        return kotlinPlugin.getSetupClass();
    }

    @Override
    public void injectData(Map<String, Object> data) {
        kotlinPlugin.injectData(data);
    }

    @Override
    public String getAccessTransformerClass() {
        return kotlinPlugin.getAccessTransformerClass();
    }
}
