package io.github.tiraaamisuuu.mineogenesis.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Mineogenesis implements ModInitializer {
    public static final String ID = "mineogenesis";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Mineogenesis foundations loaded");
    }
}
