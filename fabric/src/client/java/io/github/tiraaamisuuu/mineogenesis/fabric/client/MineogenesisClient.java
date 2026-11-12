package io.github.tiraaamisuuu.mineogenesis.fabric.client;

import net.fabricmc.api.ClientModInitializer;

public final class MineogenesisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Rendering registrations are kept out of the dedicated server entrypoint.
    }
}
