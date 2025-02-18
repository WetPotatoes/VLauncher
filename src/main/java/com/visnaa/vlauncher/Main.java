package com.visnaa.vlauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.visnaa.vlauncher.file.Downloader;
import com.visnaa.vlauncher.gui.GuiManager;
import com.visnaa.vlauncher.file.FileHelper;
import com.visnaa.vlauncher.minecraft.Profile;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main
{
    private static Main instance;
    private GuiManager guiManager;
    private Downloader downloader;
    private Process minecraft;
    private Profile currentProfile;
    private List<Profile> profiles = new ArrayList<>();
    private String playerName;
    private String lastProfileName;

    public static void main(String[] args)
    {
        instance = new Main();

        instance.run();
    }

    public void run()
    {
        downloader = new Downloader(".minecraft");

        loadLauncherData();
        loadProfiles();
        initGui();
    }

    public void initGui()
    {
        guiManager = new GuiManager(300, 300, "VLauncher", FileHelper.getResource("icon.png") != null ? new ImageIcon(FileHelper.getResource("icon.png")).getImage() : null);
        guiManager.createMainWindow();
    }

    public void loadLauncherData()
    {
        JsonObject data = FileHelper.loadJsonConfigFile("vlauncher.json");
        playerName = data.has("playerName") ? data.get("playerName").getAsString() : "VPlayer";
        lastProfileName = data.has("lastProfile") ? data.get("lastProfile").getAsString() : null;
    }

    public void saveLauncherData()
    {
        JsonObject data = new JsonObject();
        data.addProperty("playerName", playerName);
        data.addProperty("lastProfile", currentProfile == null ? this.profiles.getFirst().name() : currentProfile.name());
        FileHelper.saveJsonConfigFile(data, "vlauncher.json");
    }

    public void loadProfiles()
    {
        profiles = new ArrayList<>();
        JsonObject profiles = FileHelper.loadJsonConfigFile("profiles.json");
        if (!profiles.isEmpty())
        {
            profiles.get("profiles").getAsJsonArray().forEach(element -> this.profiles.add(new Profile(element.getAsJsonObject())));
            for (Profile profile : this.profiles)
            {
                if (lastProfileName == null || profile.name().equals(lastProfileName))
                {
                    currentProfile = profile;
                    lastProfileName = profile.name();
                    break;
                }
            }
            if (guiManager != null)
                guiManager.setCurrentProfile(currentProfile, this.profiles);
            return;
        }
        this.profiles.add(null);
        currentProfile = null;
    }

    public void saveProfiles()
    {
        JsonObject profiles = new JsonObject();
        JsonArray profileArray = new JsonArray();
        this.profiles = this.profiles.stream().filter(Objects::nonNull).toList();
        this.profiles.forEach(profile -> profileArray.add(profile.toJson()));
        profiles.add("profiles", profileArray);
        FileHelper.saveJsonConfigFile(profiles, "profiles.json");
        loadProfiles();
    }

    public void createProfile(JDialog profileCreator, Profile profile)
    {
        if (profiles.stream().filter(p -> p != null && p.name().equals(profile.name())).count() != 0)
        {
            if (JOptionPane.showConfirmDialog(profileCreator, "Profile with name " + profile.name() + " already exists, do you wish to override?", "Profile already exists", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.NO_OPTION)
                return;
        }
        lastProfileName = profile.name();
        profiles.add(profile);
        saveProfiles();
        profileCreator.dispose();
    }

    public void startGame()
    {
        if (playerName.isEmpty())
        {
            JOptionPane.showMessageDialog(guiManager.getFrame(), "You have to specify player name!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        saveLauncherData();
        saveProfiles();

        download(currentProfile.version(), playerName, currentProfile.jvmArgs());

        try
        {
            guiManager.disposeLoadingPopup();
            guiManager.minimize();
            ProcessBuilder builder = new ProcessBuilder(downloader.getRunPath().toAbsolutePath().toString());
            builder.directory(downloader.getRootDirectory().toAbsolutePath().toFile());
            builder.inheritIO();
            Process minecraft = builder.start();
            minecraft.onExit().thenAccept(_ -> {
                this.minecraft = null;
                guiManager.maximise();
            });
            this.minecraft = minecraft;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void download(String version, String playerName, List<String> userJvmArgs)
    {
        downloader.setVersion(version);
        downloader.loadVersionData();
        downloader.loadAssetIndex();

        guiManager.createLoadingPopup(downloader.getEstimatedSize());

        downloader.downloadJava();
        downloader.downloadVersion();
        downloader.downloadLibraries();
        downloader.downloadObjects();
        downloader.createArgs(false, playerName, userJvmArgs);
    }

    public static Main getInstance()
    {
        return instance;
    }

    public Downloader getDownloader()
    {
        return downloader;
    }

    public GuiManager getGuiManager()
    {
        return guiManager;
    }

    public Profile getCurrentProfile()
    {
        return currentProfile;
    }

    public void setCurrentProfile(Profile currentProfile)
    {
        this.currentProfile = currentProfile;
        this.lastProfileName = currentProfile.name();
    }

    public List<Profile> getProfiles()
    {
        return profiles;
    }

    public String getPlayerName()
    {
        return playerName;
    }

    public void setPlayerName(String playerName)
    {
        this.playerName = playerName;
    }

    public void exit()
    {
        saveLauncherData();
        saveProfiles();
        if (minecraft != null)
            destroyProcess(minecraft.toHandle());
    }

    public Process getMinecraft()
    {
        return minecraft;
    }

    private void destroyProcess(ProcessHandle processHandle)
    {
        processHandle.descendants().forEach(this::destroyProcess);
        processHandle.destroyForcibly();
    }
}