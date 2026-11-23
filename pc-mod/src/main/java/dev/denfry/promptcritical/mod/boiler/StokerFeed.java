package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Подача угля забрасывателем в топку котла (07 §7.9.4): бункер на один слот топлива, подача
 * {@code nominalKgS}·n/{@code nominalRpm}, порции уходят командой {@link SimCommand.ChargeFuel} раз в секунду. Пока
 * слой больше {@link FireTubeBoilerBlockEntity#MAX_BED_KG} (котёл погас), подачи нет. Общая для цепной решётки
 * {@code grate_stoker} и механических забрасывателей ланкаширского котла {@code lancashire_stoker}.
 */
public final class StokerFeed {
    private static final int FLUSH_PERIOD = 20;

    private final double nominalKgS, nominalRpm;
    private final String source;
    private final ItemStackHandler hopper;
    @Nullable
    private String itemKey, pendingKey;
    private double itemLeftKg, pendingKg, feedKgS;
    private int ticks;

    public StokerFeed(double nominalKgS, double nominalRpm, String source, Runnable onChanged) {
        this.nominalKgS = nominalKgS;
        this.nominalRpm = nominalRpm;
        this.source = source;
        this.hopper = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return BoilerFuels.key(stack) != null;
            }

            @Override
            protected void onContentsChanged(int slot) {
                onChanged.run();
            }
        };
    }

    public ItemStackHandler hopper() {
        return hopper;
    }

    /** Подача последнего тика, кг/с. */
    public double feedKgS() {
        return feedKgS;
    }

    /** Тик сервера: вал {@code rpm}, котёл перед забрасывателем (или {@code null}). */
    public void tick(SimLevelBinding b, double rpm, @Nullable FireTubeBoilerBlockEntity boiler) {
        ticks++;
        var u = boiler == null ? null : boiler.unit();
        feedKgS = 0;
        if (rpm > 0 && u != null && u.boiler().bedKg() + pendingKg < FireTubeBoilerBlockEntity.MAX_BED_KG) {
            double want = nominalKgS * rpm / nominalRpm * 0.05;
            while (want > 1e-9) {
                if (itemLeftKg <= 1e-9) {
                    ItemStack next = hopper.getStackInSlot(0);
                    String key = BoilerFuels.key(next);
                    if (key == null) break;
                    hopper.extractItem(0, 1, false);
                    itemKey = key;
                    itemLeftKg = BoilerFuels.itemMassKg();
                }
                if (pendingKey != null && !pendingKey.equals(itemKey)) flush(b, boiler);
                double d = Math.min(want, itemLeftKg);
                pendingKey = itemKey;
                pendingKg += d;
                itemLeftKg -= d;
                want -= d;
                feedKgS += d / 0.05;
            }
        }
        if (boiler != null && (ticks % FLUSH_PERIOD == 0 || feedKgS == 0)) flush(b, boiler);
    }

    private void flush(SimLevelBinding b, FireTubeBoilerBlockEntity boiler) {
        if (pendingKg <= 0 || pendingKey == null || boiler.simId().isNone()) return;
        b.submit(new SimCommand.ChargeFuel(boiler.simId(), pendingKey, pendingKg), new CommandSource.Automatic(source));
        pendingKg = 0;
        pendingKey = null;
    }

    /** Игрок кладёт топливо из руки в бункер. */
    public ItemStack insert(ItemStack stack) {
        return hopper.insertItem(0, stack, false);
    }

    /** Состояние на строке действия: котла нет, или бункер и подача. */
    public void tell(Player player, boolean hasBoiler) {
        if (!hasBoiler) {
            player.displayClientMessage(Component.translatable(BoilerText.STOKER_NO_BOILER), true);
            return;
        }
        ItemStack s = hopper.getStackInSlot(0);
        player.displayClientMessage(Component.translatable(BoilerText.STOKER_STATUS,
                s.isEmpty() ? "—" : s.getCount() + " × " + s.getHoverName().getString(),
                String.format(Locale.ROOT, "%.2f", feedKgS)), true);
    }

    /** Каналы щита {@link BoilerPanels#STOKER}. */
    public void readPanel(int[] out, double rpm, @Nullable FireTubeBoilerBlockEntity boiler) {
        var u = boiler == null ? null : boiler.unit();
        out[BoilerPanels.S_FEED_KGH] = (int) Math.round(feedKgS * 3600);
        out[BoilerPanels.S_RPM10] = (int) Math.round(rpm * 10);
        out[BoilerPanels.S_BED_KG10] = u == null ? 0 : (int) Math.round(u.boiler().bedKg() * 10);
        out[BoilerPanels.S_FLAGS] = (feedKgS > 0 ? 1 << BoilerPanels.SF_RUNNING : 0) | (boiler != null ? 1 << BoilerPanels.SF_BOILER : 0);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("hopper", hopper.serializeNBT(registries));
        tag.putDouble("item_left_kg", itemLeftKg);
        if (itemKey != null) tag.putString("item_key", itemKey);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("hopper")) hopper.deserializeNBT(registries, tag.getCompound("hopper"));
        itemLeftKg = tag.getDouble("item_left_kg");
        itemKey = tag.contains("item_key") ? tag.getString("item_key") : null;
    }
}
