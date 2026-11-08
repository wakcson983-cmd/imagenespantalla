package com.marcofotos;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec COMMON;
    public static final ForgeConfigSpec CLIENT;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_OP;
    public static final ForgeConfigSpec.ConfigValue<String> FFMPEG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        REQUIRE_OP = b.comment("Si es true, solo los operadores pueden mostrar imagenes en la pantalla de todos.")
                .define("soloOperadoresMuestran", false);
        COMMON = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        FFMPEG = c.comment("Ruta del ejecutable ffmpeg (solo hace falta para importar videos).")
                .define("rutaFfmpeg", "ffmpeg");
        CLIENT = c.build();
    }
}
