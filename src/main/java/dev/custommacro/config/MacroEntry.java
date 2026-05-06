package dev.custommacro.config;

/**
 * Represents a single macro: a key binding + an action (command/text).
 */
public class MacroEntry {
    private String name;
    private int keyCode;      // GLFW key code
    private String action;    // command or chat text to send
    private boolean isCommand; // true = send as command (prefix /), false = raw chat

    public MacroEntry() {}

    public MacroEntry(String name, int keyCode, String action) {
        this.name = name;
        this.keyCode = keyCode;
        this.action = action;
        this.isCommand = action.startsWith("/");
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }

    public String getAction() { return action; }
    public void setAction(String action) {
        this.action = action;
        this.isCommand = action.startsWith("/");
    }

    public boolean isCommand() { return isCommand; }
    public void setCommand(boolean command) { isCommand = command; }
}
