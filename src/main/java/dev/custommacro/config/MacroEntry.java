package dev.custommacro.config;

public class MacroEntry {
    // Tipe aksi yang didukung
    public enum ActionType { CHAT, SWAP_ITEM }

    private String  name;
    private int     keyCode;
    private boolean modShift;
    private boolean modCtrl;
    private boolean modAlt;
    private String  action;
    private boolean isCommand;
    private ActionType actionType = ActionType.CHAT;

    public MacroEntry() {}

    public MacroEntry(String name, int keyCode, boolean modShift, boolean modCtrl, boolean modAlt, String action) {
        this.name      = name;
        this.keyCode   = keyCode;
        this.modShift  = modShift;
        this.modCtrl   = modCtrl;
        this.modAlt    = modAlt;
        this.action    = action;
        this.isCommand = action != null && action.startsWith("/");
    }

    public MacroEntry(String name, int keyCode, String action) {
        this(name, keyCode, false, false, false, action);
    }

    public String  getName()          { return name; }
    public void    setName(String n)  { this.name = n; }
    public int     getKeyCode()       { return keyCode; }
    public void    setKeyCode(int k)  { this.keyCode = k; }
    public boolean isModShift()       { return modShift; }
    public void    setModShift(boolean v) { modShift = v; }
    public boolean isModCtrl()        { return modCtrl; }
    public void    setModCtrl(boolean v)  { modCtrl = v; }
    public boolean isModAlt()         { return modAlt; }
    public void    setModAlt(boolean v)   { modAlt = v; }
    public String  getAction()        { return action; }
    public void    setAction(String a) { action = a; isCommand = a != null && a.startsWith("/"); }
    public boolean isCommand()        { return isCommand; }
    public void    setCommand(boolean c) { isCommand = c; }
    public ActionType getActionType() { return actionType == null ? ActionType.CHAT : actionType; }
    public void setActionType(ActionType t) { this.actionType = t; }
    public boolean isSwapAction()     { return getActionType() == ActionType.SWAP_ITEM; }

    public String getKeyComboDisplay(java.util.function.IntFunction<String> keyNameFn) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return "?";
        StringBuilder sb = new StringBuilder();
        if (modCtrl)  sb.append("CTRL+");
        if (modAlt)   sb.append("ALT+");
        if (modShift) sb.append("SHIFT+");
        sb.append(keyNameFn.apply(keyCode));
        return sb.toString();
    }
}
