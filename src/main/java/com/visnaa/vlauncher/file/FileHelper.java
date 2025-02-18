package com.visnaa.vlauncher.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileHelper
{
    private static Path tempFolder = createTempFolder();

    public static URL getResource(String path)
    {
        try
        {
            return Thread.currentThread().getContextClassLoader().getResource(path);
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static String getHttpTextResource(String url)
    {
        try
        {
            return new String(getHttpResource(url));
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static JsonObject loadJsonConfigFile(String path)
    {
        try
        {
            File file = new File(path);
            if (!file.exists())
                return new JsonObject();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder contents = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                contents.append(line);
            reader.close();
            return JsonParser.parseString(contents.toString()).getAsJsonObject();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void saveJsonConfigFile(JsonObject config, String path)
    {
        try
        {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(config);
            File file = new File(path);
            if (file.exists())
                file.delete();
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(json);
            writer.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static byte[] getHttpResource(String url)
    {
        try
        {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new TimeoutException("HTTP error: " + connection.getResponseCode());
            InputStream stream = connection.getInputStream();

            byte[] bytes = stream.readAllBytes();
            stream.close();
            connection.disconnect();
            return bytes;
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    public static File downloadToFile(Path path, String url)
    {
        return downloadToFile(path, url, -1, null);
    }

    public static File downloadToFile(Path path, String url, int size, String sha1)
    {
        try
        {
            File file = path.toFile();
            if (file.exists())
            {
                FileInputStream stream = new FileInputStream(file);
                byte[] bytes = stream.readAllBytes();
                if (bytes.length == size && validateSha1(bytes, sha1))
                    return file;
            }

            byte[] bytes = getHttpResource(url);
            if (size != -1 && bytes.length != size)
                throw new IllegalStateException("File size does not match");

            if (sha1 != null && !validateSha1(bytes, sha1))
                throw new IllegalStateException("File's SHA-1 did not match");

            createFile(bytes, path);
            return file;
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Path createTempFolder()
    {
        try
        {
            Path temp = Files.createTempDirectory("vlauncher-" + UUID.randomUUID());
            temp.toFile().deleteOnExit();
            return temp;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void createFile(byte[] bytes, Path path)
    {
        try
        {
            File file = path.toFile();
            file.mkdirs();
            if (file.exists())
                file.delete();
            file.createNewFile();

            FileOutputStream stream = new FileOutputStream(file);
            stream.write(bytes);
            stream.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static Path extractNatives(List<File> natives)
    {
        try
        {
            tempFolder.resolve("natives").toFile().mkdir();
            for (File file : natives)
            {
                Path nativePath = Files.copy(file.toPath(), tempFolder.resolve("natives").resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
                extractZip(nativePath.toFile(), tempFolder.resolve("natives"), "dll");
            }
            return tempFolder.resolve("natives");
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean extractZip(File source, Path destination, String... extensions)
    {
        if (!source.exists())
            return false;

        if (destination.toFile().exists())
            destination.toFile().delete();
        destination.toFile().mkdirs();

        try (ZipInputStream stream = new ZipInputStream(new FileInputStream(source)))
        {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                    destination.resolve(entry.getName()).toFile().mkdir();

                String[] name = entry.getName().split("\\.");
                String extension = name[name.length - 1];
                if (!entry.isDirectory() && (extensions == null || extensions.length == 0 || Arrays.stream(extensions).toList().contains(extension)))
                    Files.copy(stream, destination.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
                stream.closeEntry();
            }
            return true;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static void delete(Path path)
    {
        try
        {
            File file = path.toFile();
            file.delete();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static int getHttpFileSize(String url)
    {
        try
        {
            HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
            int size = connection.getContentLength();
            connection.disconnect();
            return size;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public static boolean validateSha1(byte[] bytes, String sha1)
    {
        try
        {
            MessageDigest algorithm = MessageDigest.getInstance("SHA-1");
            algorithm.update(bytes.clone());
            byte[] digest = algorithm.digest();

            StringBuilder hex = new StringBuilder();
            for (byte b : digest)
                hex.append(String.format("%02x", b));

            return sha1.contentEquals(hex);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
