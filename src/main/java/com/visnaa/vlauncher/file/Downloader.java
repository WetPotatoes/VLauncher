package com.visnaa.vlauncher.file;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.visnaa.vlauncher.Main;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Downloader
{
    private final Path rootDirectory;
    private JsonObject versionManifest;
    private JsonObject versionData;
    private JsonObject assetIndex;
    private String os;
    private List<File> libraries = new ArrayList<>();
    private List<File> natives = new ArrayList<>();
    private String versionId;
    private String versionType;
    private String assetsVersion;
    private String mainClass;
    private String gameArguments;
    private String jvmArguments;
    private Path runPath;
    private File java;

    private final HashMap<Integer, String> javaVersions = new HashMap<>();

    public Downloader(String path)
    {
        javaVersions.put(8, "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u442-b06/OpenJDK8U-jdk_x64_windows_hotspot_8u442b06.zip");
        javaVersions.put(16, "https://github.com/adoptium/temurin16-binaries/releases/download/jdk-16.0.2%2B7/OpenJDK16U-jdk_x64_windows_hotspot_16.0.2_7.zip");
        javaVersions.put(17, "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.14%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.14_7.zip");
        javaVersions.put(21, "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_windows_hotspot_21.0.6_7.zip");

        rootDirectory = Path.of(path);
        loadVersionManifest();
    }

    public void loadVersionManifest()
    {
        versionManifest = JsonParser.parseString(FileHelper.getHttpTextResource("https://piston-meta.mojang.com/mc/game/version_manifest.json")).getAsJsonObject();
    }

    public String getLatestRelease()
    {
        return versionManifest.getAsJsonObject("latest").get("release").getAsString();
    }

    public List<String> getVersions()
    {
        List<String> versions = new ArrayList<>();
        versionManifest.getAsJsonArray("versions").forEach(version -> versions.addLast(version.getAsJsonObject().get("id").getAsString()));
        return versions;
    }

    public void setVersion(String version)
    {
        String url = null;
        for (JsonElement v : versionManifest.getAsJsonArray("versions"))
        {
            if (v.getAsJsonObject().get("id").getAsString().equals(version))
                url = v.getAsJsonObject().get("url").getAsString();
        }

        if (url == null || url.isEmpty())
            JOptionPane.showMessageDialog(Main.getInstance().getGuiManager().getFrame(), "Could not find version " + version, "Error", JOptionPane.ERROR_MESSAGE);
        else
        {
            versionData = JsonParser.parseString(FileHelper.getHttpTextResource(url)).getAsJsonObject();
            FileHelper.downloadToFile(rootDirectory.resolve("versions").resolve(versionData.get("id").getAsString()).resolve(versionData.get("id").getAsString() + ".json"), url);
        }
    }

    public void loadVersionData()
    {
        if (versionData == null)
            return;

        versionId = versionData.get("id").getAsString();
        versionType = versionData.get("type").getAsString();
        assetsVersion = versionData.get("assets").getAsString();
        mainClass = versionData.get("mainClass").getAsString();

        String osName = System.getProperty("os.name");
        if (osName.contains("Windows"))
            os = "windows";
        else if (osName.contains("Mac"))
            os = "macos";
        else
            os = "linux";
    }

    public void loadAssetIndex()
    {
        if (versionData == null)
            return;

        String assetsPath = versionData.getAsJsonObject("assetIndex").get("url").getAsString();
        int assetsSize = versionData.getAsJsonObject("assetIndex").get("size").getAsInt();
        String assetsSha1 = versionData.getAsJsonObject("assetIndex").get("sha1").getAsString();
        String assetsIndex = versionData.getAsJsonObject("assetIndex").get("id").getAsString();

        assetIndex = JsonParser.parseString(FileHelper.getHttpTextResource(assetsPath)).getAsJsonObject().getAsJsonObject("objects");
        FileHelper.downloadToFile(rootDirectory.resolve("assets").resolve("indexes").resolve(assetsIndex + ".json"), assetsPath, assetsSize, assetsSha1);
    }

    public long getEstimatedSize()
    {
        if (versionData == null)
            return Integer.MAX_VALUE;

        long totalSize = 0;

        // Java
        if (versionData.has("javaVersion"))
            totalSize += FileHelper.getHttpFileSize(javaVersions.get(versionData.getAsJsonObject("javaVersion").get("majorVersion").getAsInt()));
        else
            totalSize += FileHelper.getHttpFileSize(javaVersions.get(8));

        // Version jar
        totalSize += versionData.getAsJsonObject("downloads").getAsJsonObject("client").get("size").getAsInt();

        // Libraries
        AtomicLong librariesSize = new AtomicLong(0);
        versionData.getAsJsonArray("libraries").asList().stream().map(JsonElement::getAsJsonObject).forEach(library -> {
            if (library.getAsJsonObject("downloads").has("artifact"))
                librariesSize.addAndGet(library.getAsJsonObject("downloads").getAsJsonObject("artifact").get("size").getAsInt());
            if (library.getAsJsonObject("downloads").has("classifiers") && library.getAsJsonObject("downloads").getAsJsonObject("classifiers").has("natives-" + (os.equals("macos") ? "osx" : os)))
                librariesSize.addAndGet(library.getAsJsonObject("downloads").getAsJsonObject("classifiers").getAsJsonObject("natives-" + (os.equals("macos") ? "osx" : os)).get("size").getAsInt());
        });
        totalSize += librariesSize.get();

        // Assets
        AtomicLong assetsSize = new AtomicLong(0);
        assetIndex.asMap().forEach((_, asset) -> assetsSize.addAndGet(asset.getAsJsonObject().get("size").getAsInt()));
        totalSize += assetsSize.get();

        return totalSize;
    }

    public void downloadJava()
    {
        if (versionData == null)
            return;

        int javaVersion;
        if (versionData.has("javaVersion"))
            javaVersion = versionData.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        else
            javaVersion = 8;
        String url = javaVersions.get(javaVersion);

        Main.getInstance().getGuiManager().setLoadingText("Downloading Java " + javaVersion);
        System.out.println("Downloading Java " + javaVersion);

        Path destination = rootDirectory.resolve("runtime").resolve("jdk-" + javaVersion + ".zip");
        if (!rootDirectory.resolve("runtime").resolve("jdk-" + javaVersion + ".zip").toFile().exists())
            FileHelper.downloadToFile(destination, url);
        if (!FileHelper.extractZip(rootDirectory.resolve("runtime").resolve("jdk-" + javaVersion + ".zip").toFile(), rootDirectory.resolve("runtime").resolve("jdk-" + javaVersion)))
            return;
        Main.getInstance().getGuiManager().progressLoading(FileHelper.getHttpFileSize(url));

        for (File javaDir : rootDirectory.resolve("runtime").resolve("jdk-" + javaVersion).toFile().listFiles(File::isDirectory))
        {
            for (File bin : javaDir.listFiles(File::isDirectory))
            {
                if (bin.isDirectory() && bin.getName().equals("bin"))
                    java = bin.toPath().resolve("java.exe").toFile();
            }
        }
    }

    public void downloadVersion()
    {
        if (versionData == null)
            return;

        String version = versionData.get("id").getAsString();
        String versionUrl = versionData.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();
        int versionSize = versionData.getAsJsonObject("downloads").getAsJsonObject("client").get("size").getAsInt();
        String versionSha1 = versionData.getAsJsonObject("downloads").getAsJsonObject("client").get("sha1").getAsString();

        Main.getInstance().getGuiManager().setLoadingText("Downloading Minecraft version " + version);
        System.out.println("Downloading Minecraft version " + version);
        FileHelper.downloadToFile(rootDirectory.resolve("versions").resolve(version).resolve(version + ".jar"), versionUrl, versionSize, versionSha1);
        Main.getInstance().getGuiManager().progressLoading(versionSize);
    }

    public void downloadLibraries()
    {
        if (versionData == null)
            return;

        JsonArray libraries = versionData.getAsJsonArray("libraries");
        libraries.asList().stream().map(JsonElement::getAsJsonObject).forEach(library -> {
            String osName = os.equals("macos") ? "osx" : os;

            JsonObject artifact = library.getAsJsonObject("downloads").getAsJsonObject("artifact");
            if (artifact != null)
            {
                System.out.println("Downloading library: " + library.get("name").getAsString());
                Main.getInstance().getGuiManager().setLoadingText("Downloading library: " + library.get("name").getAsString());

                Path path = rootDirectory.resolve("libraries").resolve(artifact.get("path").getAsString());
                File file = FileHelper.downloadToFile(path, artifact.get("url").getAsString(), artifact.get("size").getAsInt(), artifact.get("sha1").getAsString());
                Main.getInstance().getGuiManager().progressLoading(artifact.get("size").getAsInt());
                if (os.equals("windows"))
                    this.libraries.add(file);

                if (file.exists() && (file.getName().endsWith("natives-" + os + ".jar") || file.getName().endsWith("natives-" + os + (System.getProperty("os.arch").contains("64") ? "-64" : "-32") + ".jar")))
                    natives.add(file);
            }

            if (library.getAsJsonObject("downloads").has("classifiers"))
            {
                if (!library.getAsJsonObject("downloads").getAsJsonObject("classifiers").has("natives-" + osName))
                    return;

                String natives = library.getAsJsonObject("natives").get(osName).getAsString().replace("${os_arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
                JsonObject classifier = library.getAsJsonObject("downloads").getAsJsonObject("classifiers").getAsJsonObject(natives);
                if (classifier == null)
                    return;

                System.out.println("Downloading library: " + library.get("name").getAsString() + " (" + natives + ")");
                Main.getInstance().getGuiManager().setLoadingText("Downloading library: " + library.get("name").getAsString() + " (" + natives + ")");

                Path path = rootDirectory.resolve("libraries").resolve(classifier.get("path").getAsString());
                File file = FileHelper.downloadToFile(path, classifier.get("url").getAsString(), classifier.get("size").getAsInt(), classifier.get("sha1").getAsString());
                Main.getInstance().getGuiManager().progressLoading(classifier.get("size").getAsInt());
                if (os.equals("windows"))
                    this.libraries.add(file);

                if (file.exists())
                    this.natives.add(file);
            }
        });
    }

    public void downloadObjects()
    {
        if (versionData == null)
            return;

        assetIndex.asMap().forEach((name, asset) -> {
            String sha1 = asset.getAsJsonObject().get("hash").getAsString();
            int size = asset.getAsJsonObject().get("size").getAsInt();
            String location = sha1.substring(0, 2) + "/" + sha1;
            Main.getInstance().getGuiManager().setLoadingText("Downloading asset: " + name + ", hash: " + sha1);
            System.out.println("Downloading asset: " + name + ", hash: " + sha1);
            FileHelper.downloadToFile(rootDirectory.resolve("assets").resolve("objects").resolve(location), "https://resources.download.minecraft.net/" + location, size, sha1);
            Main.getInstance().getGuiManager().progressLoading(size);
        });
    }

    public void createArgs(boolean premium, String playerName, List<String> userJvmArgs)
    {
        if (versionData == null)
            return;

        boolean hasArguments = versionData.has("arguments");
        if (hasArguments)
        {
            JsonArray gameArgs = versionData.getAsJsonObject("arguments").getAsJsonArray("game");
            StringBuilder gameArgsBuilder = new StringBuilder();
            for (JsonElement arg : gameArgs.asList())
            {
                if (arg.isJsonPrimitive() && arg.getAsJsonPrimitive().isString())
                    gameArgsBuilder.append(" ").append(arg.getAsJsonPrimitive().getAsString());
            }
            gameArguments = gameArgsBuilder.toString();
        }
        else
            gameArguments = " " + versionData.get("minecraftArguments").getAsString();

        String name;
        String uuid;
        String authToken;
        if (premium)
        {
            JsonObject playerData = logIn();
            name = playerData.getAsJsonObject("selectedProfile").get("name").getAsString();
            uuid = playerData.getAsJsonObject("selectedProfile").get("id").getAsString();
            authToken = playerData.get("accessToken").getAsString();
            gameArguments = gameArguments.replace("${auth_uuid}", uuid);
            gameArguments = gameArguments.replace("--clientId ", "");
            gameArguments = gameArguments.replace("${clientid} ", "");
            gameArguments = gameArguments.replace("${auth_xuid} ", playerData.get("xuid").getAsString());
            gameArguments = gameArguments.replace("${user_type}", "msa");
        }
        else
        {
            name = !playerName.isEmpty() ? playerName : "Player";
            authToken = "0";
            gameArguments = gameArguments.replace("--uuid ", "");
            gameArguments = gameArguments.replace("${auth_uuid} ", "");
            gameArguments = gameArguments.replace("--clientId ", "");
            gameArguments = gameArguments.replace("${clientid} ", "");
            gameArguments = gameArguments.replace("--xuid ", "");
            gameArguments = gameArguments.replace("${auth_xuid} ", "");
            gameArguments = gameArguments.replace("${user_type}", "mojang");
        }

        gameArguments = gameArguments.replace("${auth_player_name}", name);
        gameArguments = gameArguments.replace("${version_name}", versionId);
        gameArguments = gameArguments.replace("${game_directory}", "\"" + rootDirectory.toAbsolutePath() + "\"");
        gameArguments = gameArguments.replace("${assets_root}", "\"" + rootDirectory.resolve("assets").toAbsolutePath() + "\"");
        gameArguments = gameArguments.replace("${assets_index_name}", assetsVersion);
        gameArguments = gameArguments.replace("${auth_access_token}", authToken);
        gameArguments = gameArguments.replace("${version_type}", versionType);
        gameArguments = gameArguments.replace("${user_properties}", "0");

        StringBuilder classpath = new StringBuilder("\"");
        if (os.equals("windows"))
        {
            for (File library : libraries)
                classpath.append(library.toPath().toAbsolutePath()).append(";");
        }
        else
        {
            classpath.append(rootDirectory.resolve("libraries").toAbsolutePath()).append("/*:");
        }
        classpath.append(rootDirectory.resolve("versions").resolve(versionId).resolve(versionId + ".jar").toAbsolutePath()).append("\"");

        StringBuilder jvmArgsBuilder = new StringBuilder();
        for (String userJvm : userJvmArgs)
        {
            if (userJvm != null && !userJvm.isEmpty())
                jvmArgsBuilder.append(userJvm).append(" ");
        }

        if (hasArguments)
        {
            JsonArray jvmArgs = versionData.getAsJsonObject("arguments").getAsJsonArray("jvm");
            for (JsonElement arg : jvmArgs.asList())
            {
                if (arg.isJsonPrimitive() && arg.getAsJsonPrimitive().isString())
                    jvmArgsBuilder.append(arg.getAsJsonPrimitive().getAsString()).append(" ");
            }
            jvmArguments = jvmArgsBuilder.toString();
            jvmArguments = jvmArguments.replace("${natives_directory}", "\"" + FileHelper.extractNatives(natives).toAbsolutePath() + "\"");
            jvmArguments = jvmArguments.replace("${launcher_name}", "VLauncher");
            jvmArguments = jvmArguments.replace("${launcher_version}", "1.0");

            jvmArguments = jvmArguments.replace("${classpath}", classpath);
        }
        else
        {
            jvmArgsBuilder.append("-Djava.library.path=").append("\"" + FileHelper.extractNatives(natives).toAbsolutePath() + "\" ");
            jvmArgsBuilder.append("-cp ").append(classpath).append(" ");
            jvmArguments = jvmArgsBuilder.toString();
        }

        StringBuilder command = new StringBuilder("@echo off\n");
        command.append("echo Staring Minecraft ").append(versionId).append(" using VLauncher!\n");
        command.append(java == null ? "java " : "\"" + java.getAbsolutePath() + "\" ");
        command.append(jvmArguments);
        command.append(mainClass);
        command.append(gameArguments);

        Main.getInstance().getGuiManager().setLoadingText("Starting Minecraft " + versionId);
        runPath = rootDirectory.resolve("vlauncher-run.bat");
        FileHelper.createFile(command.toString().getBytes(), runPath);
    }

    private JsonObject logIn()
    {
        try
        {
            HttpURLConnection mojangConnection = (HttpURLConnection) URI.create("https://authserver.mojang.com/authenticate").toURL().openConnection();
            mojangConnection.setDoOutput(true);
            mojangConnection.setRequestMethod("POST");
            mojangConnection.addRequestProperty("Content-Type", "application/json");

            JsonObject payload = new JsonObject();

            JsonObject agent = new JsonObject();
            agent.addProperty("name", "Minecraft");
            agent.addProperty("version", 1);

            payload.add("agent", agent);
            payload.addProperty("username", "Visnaa");
            payload.addProperty("password", "hawkTuah");

            mojangConnection.getOutputStream().write(payload.toString().getBytes());

            int responseCode = mojangConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_BAD_REQUEST)
                throw new IllegalStateException("Invalid username or password: HTTP " + responseCode);
            else if (responseCode != HttpURLConnection.HTTP_OK)
                throw new IllegalStateException("Could not log into Microsoft account: HTTP " + responseCode);

            JsonObject data = JsonParser.parseString(mojangConnection.getResponseMessage()).getAsJsonObject();

            HttpURLConnection xboxConnection = (HttpURLConnection) URI.create("https://api.minecraftservices.com/minecraft/profile").toURL().openConnection();
            xboxConnection.setDoOutput(true);
            xboxConnection.setRequestMethod("GET");
            xboxConnection.addRequestProperty("Authorization", "Bearer " + data.get("accessToken").getAsString());

            responseCode = xboxConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_BAD_REQUEST)
                throw new IllegalStateException("Invalid username or password: HTTP " + responseCode);
            else if (responseCode != HttpURLConnection.HTTP_OK)
                throw new IllegalStateException("Could not log into Microsoft account: HTTP " + responseCode);

            data.addProperty("xuid", JsonParser.parseString(xboxConnection.getResponseMessage()).getAsJsonObject().get("xuid").getAsString());

            return data;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public Path getRootDirectory()
    {
        return rootDirectory;
    }

    public Path getRunPath()
    {
        return runPath;
    }
}
