package com.derp.itemexport;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "itemExport")
public class ItemExportConfig implements ConfigData {

    private int scaleMultiplier = 16;

    public int getScaleMultiplier() {
        return this.scaleMultiplier;
    }
}
