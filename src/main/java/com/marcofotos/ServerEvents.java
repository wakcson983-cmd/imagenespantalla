package com.marcofotos;

import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = MarcoFotos.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        ServerMedia.init(e.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        ServerMedia.clearPlayer(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("marcofotos")
                .then(Commands.literal("ocultar")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> {
                            Net.CHANNEL.send(PacketDistributor.ALL.noArg(), new Net.HideS2C());
                            return 1;
                        })));
    }
}
