package com.minelittlepony.ponynpc.command;

import com.minelittlepony.ponynpc.party.Party;
import com.minelittlepony.ponynpc.party.PartyManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static net.minecraft.server.command.CommandManager.*;

/** /party create|invite|join|leave|kick|list — доступна всем игрокам. */
public final class PartyCommand {

    private PartyCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            register(dispatcher)
        );
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("party")
            // /party create
            .then(literal("create").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                PartyManager.getInstance().createParty(player);
                return 1;
            }))
            // /party invite <target>
            .then(literal("invite")
                .then(argument("target", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                        PartyManager.getInstance().invite(player, target);
                        return 1;
                    })))
            // /party join <leader>
            .then(literal("join")
                .then(argument("leader", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerPlayerEntity leader = EntityArgumentType.getPlayer(ctx, "leader");
                        PartyManager.getInstance().join(player, leader, ctx.getSource().getServer());
                        return 1;
                    })))
            // /party leave
            .then(literal("leave").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                PartyManager.getInstance().leave(player, ctx.getSource().getServer());
                return 1;
            }))
            // /party kick <target>
            .then(literal("kick")
                .then(argument("target", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
                        PartyManager.getInstance().kick(player, target, ctx.getSource().getServer());
                        return 1;
                    })))
            // /party list
            .then(literal("list").executes(ctx -> {
                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                Party party = PartyManager.getInstance().getParty(player.getUuid()).orElse(null);
                if (party == null) {
                    ctx.getSource().sendFeedback(
                        () -> Text.literal("[Party] You are not in a party."), false);
                    return 0;
                }
                StringBuilder sb = new StringBuilder("[Party] Members: ");
                for (UUID memberId : party.getMembers()) {
                    ServerPlayerEntity member = ctx.getSource().getServer()
                        .getPlayerManager().getPlayer(memberId);
                    sb.append(member != null ? member.getName().getString() : memberId.toString());
                    if (party.isLeader(memberId)) sb.append(" (leader)");
                    sb.append(", ");
                }
                String msg = sb.substring(0, sb.length() - 2);
                ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                return party.getMembers().size();
            }))
        );
    }
}
