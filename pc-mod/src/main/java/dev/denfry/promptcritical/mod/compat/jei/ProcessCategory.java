package dev.denfry.promptcritical.mod.compat.jei;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.MachineProcesses;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Fluids_;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Items_;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Part;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Plant;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Process;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * Категория цеха: слева машина (катализатор), входы до 6 в два ряда, стрелка, выходы до 6, ниже — пояснение процесса.
 * Количество «по режиму» показывается условно (1 предмет, 1000 мБ) с подсказкой на слоте.
 */
final class ProcessCategory implements IRecipeCategory<Process> {
    static final int WIDTH = 178, HEIGHT = 92;
    private static final int COLS = 3, SLOT = 18, IN_X = 22, ARROW_X = 80, OUT_X = 108, NOTE_Y = 40;
    private static final long NOMINAL_MB = 1000;

    private final Plant plant;
    private final RecipeType<Process> type;
    private final IDrawable icon;

    ProcessCategory(Plant plant, IGuiHelper gui) {
        this.plant = plant;
        this.type = type(plant);
        List<ItemStack> machines = MachineProcesses.machines(plant);
        this.icon = gui.createDrawableItemStack(machines.isEmpty() ? new ItemStack(Items.FURNACE) : machines.get(0));
    }

    static RecipeType<Process> type(Plant plant) {
        return new RecipeType<>(PromptCritical.id("process/" + plant.id()), Process.class);
    }

    @Override
    public RecipeType<Process> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(plant.titleKey());
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public ResourceLocation getRegistryName(Process recipe) {
        return PromptCritical.id("process/" + recipe.id());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Process p, IFocusGroup focuses) {
        List<ItemStack> machines = p.machineStacks();
        if (!machines.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 1, 1).addItemStacks(machines).setStandardSlotBackground();
        }
        place(builder, RecipeIngredientRole.INPUT, p.inputs(), IN_X);
        place(builder, RecipeIngredientRole.OUTPUT, p.outputs(), OUT_X);
    }

    private static void place(IRecipeLayoutBuilder builder, RecipeIngredientRole role, List<Part> parts, int x0) {
        int n = Math.min(parts.size(), COLS * 2);
        for (int i = 0; i < n; i++) {
            Part part = parts.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(role, x0 + 1 + (i % COLS) * SLOT, 1 + (i / COLS) * SLOT);
            if (role == RecipeIngredientRole.OUTPUT) slot.setOutputSlotBackground();
            else slot.setStandardSlotBackground();
            switch (part) {
                case Items_ items -> slot.addItemStacks(items.resolve());
                case Fluids_ fluids -> {
                    long mb = fluids.amount() > 0 ? fluids.amount() : NOMINAL_MB;
                    for (Fluid f : fluids.fluids().get()) slot.addFluidStack(f, mb);
                    slot.setFluidRenderer(NOMINAL_MB, false, 16, 16);
                }
            }
            if (part.amount() == 0) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei." + PromptCritical.MOD_ID + ".amount_by_mode").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder extras, Process p, IFocusGroup focuses) {
        extras.addRecipeArrowWidget().setPosition(ARROW_X, 10);
        extras.addText(List.of(Component.translatable(p.noteKey())), WIDTH, HEIGHT - NOTE_Y)
                .setPosition(0, NOTE_Y)
                .setColor(0xFF404040);
    }
}
