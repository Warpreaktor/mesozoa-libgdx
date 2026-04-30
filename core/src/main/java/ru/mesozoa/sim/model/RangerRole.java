package ru.mesozoa.sim.model;

public enum RangerRole {
    SCOUT("scout"),
    DRIVER("driver"),
    ENGINEER("engineer"),
    HUNTER("hunter");

    public final String assetPrefix;

    RangerRole(String assetPrefix) {
        this.assetPrefix = assetPrefix;
    }

    public String imagePath(RangerColor color) {
        return "rangers/" + assetPrefix + "_" + color.assetSuffix + ".png";
    }
}
