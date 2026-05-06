package dev.custommacro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles saving/loading macro entries to/from JSON on disk.
 */
public class MacroConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("custommacro");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "custommacro.json";

    private static List<MacroEntry> macros = new ArrayList<>();

    public static List<MacroEntry> getMacros() {
        return macros;
    }

    public static void setMacros(List<MacroEntry> list) {
        macros = list;
    }

    public static void load() {
        Path configPath = getConfigPath();
        if (!Files.exists(configPath)) {
            macros = new ArrayList<>();
            return;
        }
        try (Reader reader = new InputStreamReader(
                new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<MacroEntry>>(){}.getType();
            List<MacroEntry> loaded = GSON.fromJson(reader, listType);
            macros = loaded != null ? loaded : new ArrayList<>();
            LOGGER.info("[CustomMacro] Loaded {} macros.", macros.size());
        } catch (Exception e) {
            LOGGER.error("[CustomMacro] Failed to load config: {}", e.getMessage());
            macros = new ArrayList<>();
        }
    }

    public static void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(macros, writer);
                LOGGER.info("[CustomMacro] Saved {} macros.", macros.size());
            }
        } catch (Exception e) {
            LOGGER.error("[CustomMacro] Failed to save config: {}", e.getMessage());
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }
}
