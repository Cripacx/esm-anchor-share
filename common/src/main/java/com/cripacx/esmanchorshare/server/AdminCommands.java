package com.cripacx.esmanchorshare.server;

import com.cripacx.esmanchorshare.anchor.SharedAnchor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import java.util.List;
import java.util.Optional;
import static com.cripacx.esmanchorshare.server.ServerAnchorManager.message;

/** {@code /esmadmin list|remove}. Creating server anchors happens client-side via {@code /esmanchor publish}. */
public final class AdminCommands {

    private AdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("esmadmin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list").executes(AdminCommands::list))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                ServerAnchorManager.list(context.getSource().getServer()).stream().map(SharedAnchor::name), builder))
                        .executes(AdminCommands::remove))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<SharedAnchor> anchors = ServerAnchorManager.list(source.getServer());
        if (anchors.isEmpty()) {
            source.sendSuccess(() -> message("esmanchorshare.admin.list.empty", "There are no server anchors. Create one with /esmanchor publish <anchor>."), false);
            return 0;
        }
        source.sendSuccess(() -> message("esmanchorshare.admin.list.header", "Server anchors (%s):", anchors.size()), false);
        for (SharedAnchor anchor : anchors) {
            source.sendSuccess(() -> message("esmanchorshare.admin.list.entry", "- %s: %s at %s %s %s, radius %s, %s sounds",
                    anchor.name(), anchor.dimension().toString(), anchor.pos().getX(), anchor.pos().getY(), anchor.pos().getZ(),
                    anchor.radius(), anchor.sounds().size()), false);
        }
        return anchors.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "name");
        Optional<SharedAnchor> removed = ServerAnchorManager.remove(source.getServer(), name);
        if (removed.isEmpty()) {
            source.sendFailure(message("esmanchorshare.admin.remove.unknown", "There is no server anchor named \"%s\".", name));
            return 0;
        }
        source.sendSuccess(() -> message("esmanchorshare.admin.remove.done", "Removed server anchor \"%s\" for all players.", removed.get().name()), true);
        return 1;
    }
}
