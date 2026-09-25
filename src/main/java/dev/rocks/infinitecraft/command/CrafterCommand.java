package dev.rocks.infinitecraft.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rocks.infinitecraft.InfiniteCraftMod;
import dev.rocks.infinitecraft.fusion.FusionCrafterBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

final class CrafterCommand {
    private CrafterCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        var command = literal("crafter").executes(context -> execute(context, "menu", null));
        for (String action : List.of("once", "repeat", "start", "stop")) command.then(action(literal(action), action));
        // Changing the mode alone never starts the crafter.
        command.then(literal("mode")
                .then(action(literal("once"), "mode once"))
                .then(action(literal("repeat"), "mode repeat")));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(
            LiteralArgumentBuilder<CommandSourceStack> literal, String action) {
        return literal.executes(context -> execute(context, action, null))
                .then(argument("position", BlockPosArgument.blockPos()).executes(context ->
                        execute(context, action, BlockPosArgument.getLoadedBlockPos(context, "position"))));
    }

    private static int execute(CommandContext<CommandSourceStack> context, String mode, BlockPos position) throws CommandSyntaxException {
        var source = context.getSource();
        var player = source.getPlayerOrException();
        if (position == null && player.containerMenu instanceof CrafterMenu menu
                && menu.getContainer() instanceof CrafterBlockEntity block) position = block.getBlockPos();
        if (position == null && player.pick(6, 0, false) instanceof BlockHitResult hit) position = hit.getBlockPos();
        if (position == null || player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64
                || !(player.level().getBlockEntity(position) instanceof CrafterBlockEntity block) || !FusionCrafterBlock.marked(block)) {
            source.sendFailure(Component.literal("Open or look at a Fusion Crafter."));
            return 0;
        }
        if (!FusionCommands.canControlFusion(source) && !player.getUUID().equals(FusionCrafterBlock.owner(block))) {
            source.sendFailure(Component.literal("Only the owner or host can control this crafter."));
            return 0;
        }
        boolean repeat = FusionCrafterBlock.repeats(block);
        boolean paused = FusionCrafterBlock.paused(block);
        switch (mode) {
            case "menu" -> openMenu(player, position, repeat, paused);
            case "mode once", "mode repeat" -> {
                boolean next = mode.equals("mode repeat");
                FusionCrafterBlock.setMode(block, next, paused);
                feedback(source, player, Component.literal(next ? "Mode: Repeat." : "Mode: Once."));
            }
            case "stop" -> {
                FusionCrafterBlock.setMode(block, repeat, true);
                feedback(source, player, Component.literal("Crafter stopped."));
            }
            default -> {
                boolean next = mode.equals("repeat") || mode.equals("start") && repeat;
                FusionCrafterBlock.setMode(block, next, false);
                InfiniteCraftMod.triggerCrafter(block);
                feedback(source, player, Component.literal(next ? "Crafter repeating." : "Crafter will fuse once."));
            }
        }
        return 1;
    }

    private static void feedback(CommandSourceStack source, net.minecraft.server.level.ServerPlayer player, Component message) {
        // The crafter screen already shows the new state.
        if (!(player.containerMenu instanceof CrafterMenu)) source.sendSuccess(() -> message, false);
    }

    private static void openMenu(net.minecraft.server.level.ServerPlayer player, BlockPos position, boolean repeat, boolean paused) {
        String location = position.getX() + " " + position.getY() + " " + position.getZ();
        var actions = List.of(
                button(paused ? "Start" : "Stop", paused ? "start" : "stop", location,
                        paused ? "Fuse the two input items." : "Stop after the current fusion."),
                button(repeat ? "Switch to Once" : "Switch to Repeat", repeat ? "mode once" : "mode repeat", location,
                        repeat ? "Fuse one pair, then stop." : "Keep fusing while both inputs have items."));
        var state = Component.literal("Mode: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(repeat ? "Repeat" : "Once").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("   Status: ").withStyle(ChatFormatting.GRAY))
                .append(paused ? Component.literal("Stopped").withStyle(ChatFormatting.RED)
                        : Component.literal("Running").withStyle(ChatFormatting.GREEN));
        var help = Component.literal("Once fuses a single pair and stops. Repeat keeps fusing while both inputs have items. "
                + "A redstone pulse starts a stopped crafter and stops a running one.").withStyle(ChatFormatting.GRAY);
        var common = new CommonDialogData(Component.literal("Fusion Crafter"), Optional.empty(), true, false,
                DialogAction.CLOSE, List.of(new PlainMessage(state, 240), new PlainMessage(help, 240)), List.of());
        player.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.empty(), 2)));
    }

    private static ActionButton button(String label, String mode, String location, String tooltip) {
        return new ActionButton(new CommonButtonData(Component.literal(label), Optional.of(Component.literal(tooltip)), 120),
                Optional.of(new StaticAction(new ClickEvent.RunCommand("/fusion crafter " + mode + " " + location))));
    }
}
