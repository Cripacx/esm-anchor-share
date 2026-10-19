package com.cripacx.esmanchorshare.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /esmanchor share|publish|copy}. Generic over the command source because Fabric and NeoForge
 * use different source types for client commands; nothing here needs the source.
 */
public final class ClientCommands {

    private ClientCommands() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        SuggestionProvider<S> ownAnchors = (context, builder) -> SharedSuggestionProvider.suggest(EsmBridge.anchorNames(), builder);
        dispatcher.register(LiteralArgumentBuilder.<S>literal("esmanchor")
                .then(LiteralArgumentBuilder.<S>literal("share")
                        .then(RequiredArgumentBuilder.<S, String>argument("anchor", StringArgumentType.greedyString())
                                .suggests(ownAnchors)
                                .executes(context -> {
                                    // the shared anchor shows up in chat, so no extra confirmation
                                    AnchorActions.share(EsmBridge.export(StringArgumentType.getString(context, "anchor")));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("publish")
                        .then(RequiredArgumentBuilder.<S, String>argument("anchor", StringArgumentType.greedyString())
                                .suggests(ownAnchors)
                                .executes(context -> {
                                    // the server answers in chat
                                    AnchorActions.publish(EsmBridge.export(StringArgumentType.getString(context, "anchor")));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<S>literal("copy")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("id", IntegerArgumentType.integer(0))
                                .executes(context -> copy(IntegerArgumentType.getInteger(context, "id"))))));
    }

    private static int copy(int id) throws CommandSyntaxException {
        String savedAs = EsmBridge.importAnchor(ChatShares.get(id));
        ChatShares.print(Component.translatable("esmanchorshare.copy.done", savedAs));
        return 1;
    }
}
