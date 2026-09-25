package dev.rocks.infinitecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rocks.infinitecraft.item.ItemTraits;
import dev.rocks.infinitecraft.item.VanillaTraits;
import dev.rocks.infinitecraft.traits.TraitRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Creates an independent test item, without changing recipes or held equipment. */
final class TraitTestCommand {
    private TraitTestCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return literal("test").requires(FusionCommands::canControlFusion)
                .then(argument("item", IdentifierArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                        .then(argument("traits", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TraitRegistry.ids(), builder))
                                .executes(context -> {
                                    var source = context.getSource();
                                    var player = source.getPlayerOrException();
                                    var item = BuiltInRegistries.ITEM.getOptional(IdentifierArgument.getId(context, "item"));
                                    // Trait IDs are separated by one or more spaces or commas.
                                    var traits = Arrays.stream(StringArgumentType.getString(context, "traits").split("[\\s,]+"))
                                            .filter(id -> !id.isBlank()).toList();
                                    var result = item.isEmpty() ? ItemStack.EMPTY
                                            : VanillaTraits.apply(new ItemStack(item.get()), traits, "");
                                    if (result.isEmpty() || !ItemTraits.apply(result, ItemStack.EMPTY, ItemStack.EMPTY, traits, 8)) {
                                        source.sendFailure(Component.literal("Invalid item or incompatible traits."));
                                        return 0;
                                    }
                                    if (!player.getInventory().add(result)) player.drop(result, false);
                                    source.sendSuccess(() -> Component.literal("Test item given."), false);
                                    return 1;
                                })));
    }
}
