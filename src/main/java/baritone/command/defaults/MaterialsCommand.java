/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.process.BuilderProcess;
import baritone.process.builder.MaterialList;
import baritone.utils.schematic.SchematicSystem;
import baritone.utils.schematic.format.UnknownBlocks;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class MaterialsCommand extends Command {

    private final File schematicsDir;

    public MaterialsCommand(IBaritone baritone) {
        super(baritone, "materials", "mats");
        this.schematicsDir = new File(baritone.getPlayerContext().minecraft().gameDirectory, "schematics");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String mode = args.hasAny() && !args.is(Integer.class) ? args.getString().toLowerCase(Locale.US) : "status";
        MaterialList list = new MaterialList();
        String title;
        String pagePrefix;
        if (mode.equals("status")) {
            BuilderProcess builder = ((Baritone) baritone).getBuilderProcess();
            if (!builder.collectMissing(list)) {
                throw new CommandInvalidStateException("Not building anything. Use 'materials file <name>' for a schematic file");
            }
            title = "Still needed for the current build:";
            pagePrefix = String.format("%s%s status", FORCE_COMMAND_PREFIX, label);
        } else if (mode.equals("file")) {
            File file = args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
            String consumed = args.consumedString();
            if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
                file = new File(file.getAbsolutePath() + "." + Baritone.settings().schematicFallbackExtension.value);
            }
            if (!file.exists()) {
                throw new CommandInvalidStateException("Cannot find " + file);
            }
            Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(file);
            if (!format.isPresent()) {
                throw new CommandInvalidStateException("Unsupported schematic format");
            }
            IStaticSchematic schematic;
            try (FileInputStream in = new FileInputStream(file)) {
                schematic = format.get().parse(in);
            } catch (Exception e) {
                e.printStackTrace();
                throw new CommandInvalidStateException("Couldn't load the schematic: " + e);
            }
            if (schematic instanceof UnknownBlocks.Source && !((UnknownBlocks.Source) schematic).getUnknownBlocks().isEmpty()) {
                logDirect(((UnknownBlocks.Source) schematic).getUnknownBlocks().summary(5));
            }
            list.addAll(schematic);
            title = String.format("Materials for %s (%dx%dx%d):", file.getName(), schematic.widthX(), schematic.heightY(), schematic.lengthZ());
            pagePrefix = String.format("%s%s %s", FORCE_COMMAND_PREFIX, label, consumed);
        } else {
            throw new CommandInvalidStateException("Unknown mode '" + mode + "', use 'status' or 'file <name>'");
        }

        Object2LongOpenHashMap<Item> inventory = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Item> inShulkers = new Object2LongOpenHashMap<>();
        Inventory inv = ctx.player().getInventory();
        List<ItemStack> stacks = new ArrayList<>(inv.getNonEquipmentItems());
        stacks.add(inv.getItem(Inventory.SLOT_OFFHAND));
        MaterialList.countItems(stacks, inventory, inShulkers);
        List<MaterialList.Entry> entries = list.compare(inventory, inShulkers);

        long missingTotal = entries.stream().mapToLong(MaterialList.Entry::missing).sum();
        if (entries.isEmpty() && list.getWithoutItem().isEmpty()) {
            logDirect(title + " nothing");
            return;
        }
        Function<MaterialList.Entry, Component> transform = entry -> {
            MutableComponent line = Component.literal("");
            line.append(Component.translatable(entry.item.getDescriptionId()).withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(String.format(": %d needed, %d carried", entry.needed, entry.inInventory)).withStyle(ChatFormatting.GRAY));
            if (entry.inShulkers > 0) {
                line.append(Component.literal(String.format(" + %d in shulkers", entry.inShulkers)).withStyle(ChatFormatting.GRAY));
            }
            line.append(entry.missing() > 0
                    ? Component.literal(String.format(", %d missing", entry.missing())).withStyle(ChatFormatting.RED)
                    : Component.literal(", ok").withStyle(ChatFormatting.GREEN));
            return line;
        };
        Paginator.paginate(args, new Paginator<>(entries), () -> {
            logDirect(title);
            logDirect(String.format("%d items of %d types, %d missing", list.totalNeeded(), entries.size(), missingTotal));
            if (!list.getWithoutItem().isEmpty()) {
                logDirect("Can't be placed (no item): " + list.getWithoutItem().object2LongEntrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                        .limit(8)
                        .map(e -> BuiltInRegistries.BLOCK.getKey(e.getKey()).getPath() + " x" + e.getLongValue())
                        .collect(Collectors.joining(", ")));
            }
        }, transform, pagePrefix);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper().append("status", "file").filterPrefix(args.getString()).stream();
        }
        if (args.has(2) && args.getString().equalsIgnoreCase("file")) {
            if (args.hasExactlyOne()) {
                return RelativeFile.tabComplete(args, schematicsDir);
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Lists the materials a build needs";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Shows needed items, what you carry (including shulker boxes in your inventory) and what is missing.",
                "Orientations and other block states are merged into the item.",
                "",
                "Usage:",
                "> materials [page] - What the current build still needs",
                "> materials status [page] - Same",
                "> materials file <filename> [page] - What a schematic file needs in total"
        );
    }
}
