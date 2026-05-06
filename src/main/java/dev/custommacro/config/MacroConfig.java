package dev.custommacro.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves/loads macro list to .minecraft/config/custommacro.json
 * Format JSON manual (tanpa library eksternal).
 */
public class MacroConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("custommacro");
    private static final String FILE_NAME = "custommacro.json";
    private static List<MacroEntry> macros = new ArrayList<>();

    public static List<MacroEntry> getMacros() { return macros; }
    public static void setMacros(List<MacroEntry> list) { macros = new ArrayList<>(list); }

    // ── SAVE ─────────────────────────────────────────────────────────────────
    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < macros.size(); i++) {
                MacroEntry e = macros.get(i);
                sb.append("  {\n");
                sb.append("    \"name\": ").append(jsonStr(e.getName())).append(",\n");
                sb.append("    \"keyCode\": ").append(e.getKeyCode()).append(",\n");
                sb.append("    \"action\": ").append(jsonStr(e.getAction())).append("\n");
                sb.append("  }");
                if (i < macros.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("[CustomMacro] Saved {} macro(s) to {}", macros.size(), path);
        } catch (Exception e) {
            LOGGER.error("[CustomMacro] Save failed: {}", e.getMessage(), e);
        }
    }

    // ── LOAD ─────────────────────────────────────────────────────────────────
    public static void load() {
        Path path = getPath();
        macros = new ArrayList<>();
        if (!Files.exists(path)) {
            LOGGER.info("[CustomMacro] No config file found, starting fresh.");
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8).trim();
            macros = parseJsonArray(json);
            LOGGER.info("[CustomMacro] Loaded {} macro(s) from {}", macros.size(), path);
        } catch (Exception e) {
            LOGGER.error("[CustomMacro] Load failed: {}", e.getMessage(), e);
            macros = new ArrayList<>();
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────
    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Minimal JSON array parser: baca list of {name, keyCode, action} */
    private static List<MacroEntry> parseJsonArray(String json) {
        List<MacroEntry> result = new ArrayList<>();
        // Cari setiap object {...}
        int i = 0;
        while (i < json.length()) {
            int objStart = json.indexOf('{', i);
            if (objStart < 0) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart + 1, objEnd);
            MacroEntry entry = parseObject(obj);
            if (entry != null) result.add(entry);
            i = objEnd + 1;
        }
        return result;
    }

    private static MacroEntry parseObject(String obj) {
        try {
            String name    = readStr(obj, "name");
            String action  = readStr(obj, "action");
            int    keyCode = readInt(obj, "keyCode");
            if (name == null || action == null) return null;
            return new MacroEntry(name, keyCode, action);
        } catch (Exception e) {
            LOGGER.warn("[CustomMacro] Skipping malformed entry: {}", e.getMessage());
            return null;
        }
    }

    private static String readStr(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int ki = obj.indexOf(pattern);
        if (ki < 0) return null;
        int colon = obj.indexOf(':', ki + pattern.length());
        if (colon < 0) return null;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = q1 + 1;
        StringBuilder sb = new StringBuilder();
        while (q2 < obj.length()) {
            char c = obj.charAt(q2);
            if (c == '\\' && q2 + 1 < obj.length()) {
                q2++;
                char esc = obj.charAt(q2);
                if (esc == '"') sb.append('"');
                else if (esc == '\\') sb.append('\\');
                else sb.append(esc);
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
            q2++;
        }
        return sb.toString();
    }

    private static int readInt(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int ki = obj.indexOf(pattern);
        if (ki < 0) return -1;
        int colon = obj.indexOf(':', ki + pattern.length());
        if (colon < 0) return -1;
        int start = colon + 1;
        while (start < obj.length() && (obj.charAt(start) == ' ' || obj.charAt(start) == '\n'
                || obj.charAt(start) == '\r' || obj.charAt(start) == '\t')) start++;
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
        return Integer.parseInt(obj.substring(start, end));
    }
}
