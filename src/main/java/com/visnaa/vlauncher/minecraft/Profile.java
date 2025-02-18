package com.visnaa.vlauncher.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.visnaa.vlauncher.file.FileHelper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record Profile(String name, String version, Path minecraftPath, File java, List<String> jvmArgs)
{
    public Profile(JsonObject profile)
    {
        this(profile.get("name").getAsString(), profile.get("version").getAsString(), Path.of(profile.get("minecraftPath").getAsString()), new File(profile.get("java").getAsString()), profile.getAsJsonArray("userJvmArgs").asList().stream().map(JsonElement::getAsString).toList());
    }

    public JsonObject toJson()
    {
        JsonObject profile = new JsonObject();
        profile.addProperty("name", name);
        profile.addProperty("version", version);
        profile.addProperty("minecraftPath", minecraftPath.toAbsolutePath().toString());
        profile.addProperty("java", java != null ? java.getAbsolutePath() : "bundled");

        JsonArray userJvmArgs = new JsonArray();
        jvmArgs.forEach(userJvmArgs::add);
        profile.add("userJvmArgs", userJvmArgs);

        return profile;
    }

    @Override
    public String toString()
    {
        return name + " (" + version + ")";
    }
}
