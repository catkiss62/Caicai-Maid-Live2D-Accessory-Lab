package com.catkiss.senlive2dcompanion;

enum CompositeOutfit {
    MAID_WITH_SEN_ACCESSORIES("maid_with_sen_accessories", "菜菜女仆 + Sen 三配件");

    final String id;
    final String displayName;

    CompositeOutfit(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    static CompositeOutfit fromId(String id) {
        for (CompositeOutfit value : values()) if (value.id.equals(id)) return value;
        // Old private-package identifiers map to the one supported maid-plus-accessories layout.
        return MAID_WITH_SEN_ACCESSORIES;
    }
}
