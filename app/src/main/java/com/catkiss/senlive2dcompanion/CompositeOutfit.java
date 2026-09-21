package com.catkiss.senlive2dcompanion;

enum CompositeOutfit {
    RUBY_ORIGINAL("ruby_original", "Ruby 原装"),
    SEN_MAID("sen_maid", "Sen 女仆装");

    final String id;
    final String displayName;

    CompositeOutfit(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    static CompositeOutfit fromId(String id) {
        for (CompositeOutfit value : values()) if (value.id.equals(id)) return value;
        return RUBY_ORIGINAL;
    }
}
