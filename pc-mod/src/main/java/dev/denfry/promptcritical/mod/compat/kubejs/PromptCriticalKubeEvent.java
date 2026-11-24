package dev.denfry.promptcritical.mod.compat.kubejs;

import dev.denfry.promptcritical.api.ApiEvent;
import dev.latvian.mods.kubejs.event.KubeEvent;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * One {@link ApiEvent} as a KubeJS event. Scripts read the fields as properties ({@code e.device}, {@code e.tripped});
 * a field the event kind does not have reads as {@code null}. {@code e.event} is the Java record itself.
 */
public final class PromptCriticalKubeEvent implements KubeEvent {
    private final ApiEvent event;

    PromptCriticalKubeEvent(ApiEvent event) {
        this.event = event;
    }

    public ApiEvent getEvent() {
        return event;
    }

    /** {@code alarm}, {@code trip}, {@code sisTrip}, {@code release}, {@code milestone}, {@code contractState}, {@code inspection}. */
    public String getKind() {
        return PromptCriticalKubeJSPlugin.handler(event).name;
    }

    @Nullable
    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public String getDimension() {
        return event.dimension();
    }

    /** Station id as a string, the form {@code PromptCritical.read} takes; {@code 00000000-...} for none. */
    public String getStationId() {
        return event.stationId().toString();
    }

    public long getGameTime() {
        return event.gameTime();
    }

    @Nullable
    public String getDevice() {
        return switch (event) {
            case ApiEvent.Alarm a -> a.device();
            case ApiEvent.Trip t -> t.device();
            case ApiEvent.SisTrip s -> s.device();
            default -> null;
        };
    }

    @Nullable
    public Boolean getTripped() {
        return switch (event) {
            case ApiEvent.Trip t -> t.tripped();
            case ApiEvent.SisTrip s -> s.tripped();
            default -> null;
        };
    }

    /** Trip source ({@code AUTOMATIC}, {@code LUA}). */
    @Nullable
    public String getSource() {
        return event instanceof ApiEvent.Trip t ? t.source() : null;
    }

    /** Alarm event type ({@code RAISE}, {@code CLEAR}, ...). */
    @Nullable
    public String getType() {
        return event instanceof ApiEvent.Alarm a ? a.type() : null;
    }

    @Nullable
    public Integer getAlarm() {
        return event instanceof ApiEvent.Alarm a ? a.alarm() : null;
    }

    @Nullable
    public String getState() {
        return event instanceof ApiEvent.Alarm a ? a.state() : null;
    }

    @Nullable
    public Integer getPriority() {
        return event instanceof ApiEvent.Alarm a ? a.priority() : null;
    }

    @Nullable
    public String getMessage() {
        return event instanceof ApiEvent.Alarm a ? a.message() : null;
    }

    @Nullable
    public String getTag() {
        return switch (event) {
            case ApiEvent.Alarm a -> a.tag();
            case ApiEvent.Milestone m -> m.tag();
            default -> null;
        };
    }

    @Nullable
    public Double getValue() {
        return switch (event) {
            case ApiEvent.Alarm a -> a.value();
            case ApiEvent.Milestone m -> m.value();
            default -> null;
        };
    }

    @Nullable
    public String getNuclide() {
        return event instanceof ApiEvent.Release r ? r.nuclide() : null;
    }

    @Nullable
    public Double getBqPerS() {
        return event instanceof ApiEvent.Release r ? r.bqPerS() : null;
    }

    @Nullable
    public Double getX() {
        return event instanceof ApiEvent.Release r ? r.x() : null;
    }

    @Nullable
    public Double getY() {
        return event instanceof ApiEvent.Release r ? r.y() : null;
    }

    @Nullable
    public Double getZ() {
        return event instanceof ApiEvent.Release r ? r.z() : null;
    }

    /** Milestone id ({@code promptcritical:engine_running}). */
    @Nullable
    public String getId() {
        return event instanceof ApiEvent.Milestone m ? m.event() : null;
    }

    @Nullable
    public Long getContract() {
        return event instanceof ApiEvent.ContractState c ? c.contract() : null;
    }

    @Nullable
    public String getSeller() {
        return event instanceof ApiEvent.ContractState c ? c.seller().toString() : null;
    }

    @Nullable
    public String getCommodity() {
        return event instanceof ApiEvent.ContractState c ? c.commodity() : null;
    }

    @Nullable
    public String getFrom() {
        return event instanceof ApiEvent.ContractState c ? c.from() : null;
    }

    @Nullable
    public String getTo() {
        return event instanceof ApiEvent.ContractState c ? c.to() : null;
    }

    /** Inspection kind. */
    @Nullable
    public String getInspectionKind() {
        return event instanceof ApiEvent.Inspection i ? i.kind() : null;
    }

    @Nullable
    public Double getRating() {
        return event instanceof ApiEvent.Inspection i ? i.rating() : null;
    }

    @Nullable
    public List<String> getFindings() {
        return event instanceof ApiEvent.Inspection i ? i.findings() : null;
    }
}
