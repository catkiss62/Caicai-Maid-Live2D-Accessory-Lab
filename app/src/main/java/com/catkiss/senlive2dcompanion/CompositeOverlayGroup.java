package com.catkiss.senlive2dcompanion;

enum CompositeOverlayGroup {
    GLOBAL("global", "整体"),
    AHOGE("ahoge", "呆毛"),
    EAR_FINS("ear_fins", "左右耳鳍"),
    TAIL("tail", "尾巴");

    final String id;
    final String displayName;

    CompositeOverlayGroup(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    static CompositeOverlayGroup fromId(String id) {
        for (CompositeOverlayGroup value : values()) if (value.id.equals(id)) return value;
        return GLOBAL;
    }
}
