/*
 * Dungeons Guide - The most intelligent Hypixel Skyblock Dungeons Mod
 * Copyright (C) 2021  cyoung06
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

package kr.syeyoung.dungeonsguide.launcher;

import com.mojang.authlib.exceptions.AuthenticationException;
import kr.syeyoung.dungeonsguide.launcher.authentication.Authenticator;
import kr.syeyoung.dungeonsguide.launcher.branch.ModDownloader;
import kr.syeyoung.dungeonsguide.launcher.exceptions.NoSuitableLoaderFoundException;
import kr.syeyoung.dungeonsguide.launcher.exceptions.PrivacyPolicyRequiredException;
import kr.syeyoung.dungeonsguide.launcher.exceptions.ReferenceLeakedException;
import kr.syeyoung.dungeonsguide.launcher.exceptions.TokenExpiredException;
import kr.syeyoung.dungeonsguide.launcher.loader.IDGLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.*;
import java.security.*;

@Mod(modid = Main.MOD_ID, version = Main.VERSION)
public class Main
{
    public static final String MOD_ID = "skyblock_dungeons_guide";
    public static final String VERSION = "1.0";
    public static final String DOMAIN = "http://testmachine:8080";

    private static Main main;

    private File configDir;

    private DGInterface dgInterface;
    private Authenticator authenticator = new Authenticator();
    private ModDownloader modDownloader = new ModDownloader(authenticator);


    private IDGLoader currentLoader;

    private Throwable lastError;
    private boolean isMcLoaded;




    @EventHandler
    public void initEvent(FMLInitializationEvent initializationEvent)
    {
        MinecraftForge.EVENT_BUS.register(this);
        if (dgInterface != null) {
            try {
                dgInterface.init(configDir);
            } catch (Exception e) {
            }
        }
    }

    public void unload() throws ReferenceLeakedException {
        if (currentLoader != null && !currentLoader.isUnloadable()) {
            throw new UnsupportedOperationException("Current version is not unloadable");
        }
        dgInterface = null;
        if (currentLoader != null) {
            currentLoader.unloadJar();
        }
        currentLoader = null;
    }
    public void load(IDGLoader newLoader) {
        if (dgInterface != null) throw new IllegalStateException("DG is loaded");
        newLoader.loadJar(authenticator);
        dgInterface = newLoader.getInstance();
        currentLoader = newLoader;

        dgInterface.init(configDir);
    }

    public void reload(IDGLoader newLoader) {
        try {
            unload();
            load(newLoader);
        } catch (Exception e) {
            lastError = e;
            dgInterface = null;
            currentLoader = null;
            tryOpenError();
        }
    }

    public void tryOpenError() {
    }

    public GuiScreen obtainErrorGUI() {
        // when gets called init and stuff remove thing
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent guiOpenEvent) {
        if (guiOpenEvent.gui instanceof GuiMainMenu) {
            isMcLoaded = true;
        }
        if (lastError != null && guiOpenEvent.gui instanceof GuiMainMenu) {
            guiOpenEvent.gui = obtainErrorGUI();
        }
    }

    public String getLoaderName(Configuration configuration) {
        String loader = System.getProperty("dg.loader");
        if (loader == null) {
            loader = configuration.get("loader", "modsource", "auto").getString();
        }
        if (loader == null) loader = "auto";
        return loader;
    }


    public IDGLoader obtainLoader(Configuration configuration) {
        String loader = getLoaderName(configuration);

        if ("local".equals(loader) ||
                (loader.equals("auto") && this.getClass().getResourceAsStream("/kr/syeyoung/dungeonsguide/DungeonsGuide.class") == null)) {

        } else if ("jar".equals("loader") ||
                (loader.equals("auto") &&  this.getClass().getResourceAsStream("/mod.jar") == null)) {

        } else if (loader.equals("auto") ){
                // remote load
        } else {
            throw new NoSuitableLoaderFoundException(System.getProperty("dg.loader"), configuration.get("loader", "modsource", "auto").getString());
        }
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent preInitializationEvent) {
        main = this;
        configDir = preInitializationEvent.getModConfigurationDirectory();
        ProgressManager.ProgressBar bar = null;
        try {
            bar = ProgressManager.push("DungeonsGuide",2);
            bar.step("Authenticating...");
            authenticator.repeatAuthenticate(5);

            File f = new File(preInitializationEvent.getModConfigurationDirectory(), "loader.cfg");
            Configuration configuration = new Configuration(f);
            bar.step("Instantiating...");

            load(obtainLoader(configuration));

            configuration.save();
        } catch (Throwable t) {
            lastError = t;
            dgInterface = null;
            currentLoader = null;
            tryOpenError();
        } finally {
            if (bar != null) ProgressManager.pop(bar);
        }

        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(a -> {
            if (dgInterface != null) dgInterface.onResourceReload(a);
        });
//        try {
//            token = authenticator.authenticateAndDownload(this.getClass().getResourceAsStream("/kr/syeyoung/dungeonsguide/DungeonsGuide.class") == null ? System.getProperty("dg.version") == null ? "nlatest" : System.getProperty("dg.version") : null);
//            if (token != null) {
//                main = this;
//                URL.setURLStreamHandlerFactory(new DGStreamHandlerFactory(authenticator));
//                LaunchClassLoader classLoader = (LaunchClassLoader) Main.class.getClassLoader();
//                classLoader.addURL(new URL("z:///"));
//
//                try {
//                    progressBar.step("Initializing");
//                    this.dgInterface = new DungeonsGuide(authenticator);
//                    this.dgInterface.pre(preInitializationEvent);
//                    while (progressBar.getStep() < progressBar.getSteps())
//                        progressBar.step("random-"+progressBar.getStep());
//                    ProgressManager.pop(progressBar);
//                    isLoaded = true;
//                } catch (Throwable e) {
//                    cause = e;
//                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//                    PrintStream printStream = new PrintStream(byteArrayOutputStream);
//                    e.printStackTrace(printStream);
//                    stacktrace = new String(byteArrayOutputStream.toByteArray());
//
//                    while (progressBar.getStep() < progressBar.getSteps())
//                        progressBar.step("random-"+progressBar.getStep());
//                    ProgressManager.pop(progressBar);
//
//                    e.printStackTrace();
//                }
//            }
//        } catch (IOException  | AuthenticationException | NoSuchAlgorithmException | CertificateException | KeyStoreException | KeyManagementException | InvalidKeySpecException | SignatureException e) {
//            cause = e;
//            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//            PrintStream printStream = new PrintStream(byteArrayOutputStream);
//            e.printStackTrace(printStream);
//            stacktrace = new String(byteArrayOutputStream.toByteArray());
//
//            while (progressBar.getStep() < progressBar.getSteps())
//                progressBar.step("random-"+progressBar.getStep());
//            ProgressManager.pop(progressBar);
//
//            e.printStackTrace();
//        }
    }

    public void setLastError(Throwable t) {
        lastError = t;
    }

    public static Main getMain() {
        return main;
    }
}
