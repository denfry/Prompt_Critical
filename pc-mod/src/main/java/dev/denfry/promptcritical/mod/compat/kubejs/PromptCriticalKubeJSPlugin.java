package dev.denfry.promptcritical.mod.compat.kubejs;

import dev.denfry.promptcritical.api.ApiEvent;
import dev.denfry.promptcritical.mod.api.ApiEvents;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/**
 * KubeJS plugin (16 §16.11.1), listed in {@code kubejs.plugins.txt}: only KubeJS reads that file, so without KubeJS
 * none of this package is loaded. Server scripts get the events {@code PromptCriticalEvents.alarm}, {@code trip},
 * {@code sisTrip}, {@code release}, {@code milestone}, {@code contractState}, {@code inspection} (the mirror of
 * {@link ApiEvent}) and the read-only binding {@code PromptCritical}. Scripts cannot command machines: a build script
 * would otherwise be a ready controller that bypasses P3.
 *
 * <pre>{@code
 * PromptCriticalEvents.trip(e => {
 *   if (e.tripped) e.server.tell(`Scram at ${e.device}: ${PromptCritical.read(e.stationId, e.device + '/power')}`)
 * })
 * }</pre>
 */
public final class PromptCriticalKubeJSPlugin implements KubeJSPlugin {
    static final EventGroup GROUP = EventGroup.of("PromptCriticalEvents");
    static final EventHandler ALARM = GROUP.server("alarm", () -> PromptCriticalKubeEvent.class);
    static final EventHandler TRIP = GROUP.server("trip", () -> PromptCriticalKubeEvent.class);
    static final EventHandler SIS_TRIP = GROUP.server("sisTrip", () -> PromptCriticalKubeEvent.class);
    static final EventHandler RELEASE = GROUP.server("release", () -> PromptCriticalKubeEvent.class);
    static final EventHandler MILESTONE = GROUP.server("milestone", () -> PromptCriticalKubeEvent.class);
    static final EventHandler CONTRACT_STATE = GROUP.server("contractState", () -> PromptCriticalKubeEvent.class);
    static final EventHandler INSPECTION = GROUP.server("inspection", () -> PromptCriticalKubeEvent.class);
    private static final EventHandler[] ALL = {ALARM, TRIP, SIS_TRIP, RELEASE, MILESTONE, CONTRACT_STATE, INSPECTION};

    @Override
    public void init() {
        ApiEvents.subscribe(PromptCriticalKubeJSPlugin::post, PromptCriticalKubeJSPlugin::anyListener);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type().isServer()) bindings.add("PromptCritical", new PromptCriticalBinding());
    }

    private static boolean anyListener() {
        for (EventHandler h : ALL) if (h.hasListeners()) return true;
        return false;
    }

    static EventHandler handler(ApiEvent e) {
        return switch (e) {
            case ApiEvent.Alarm a -> ALARM;
            case ApiEvent.Trip t -> TRIP;
            case ApiEvent.SisTrip s -> SIS_TRIP;
            case ApiEvent.Release r -> RELEASE;
            case ApiEvent.Milestone m -> MILESTONE;
            case ApiEvent.ContractState c -> CONTRACT_STATE;
            case ApiEvent.Inspection i -> INSPECTION;
        };
    }

    private static void post(ApiEvent e) {
        EventHandler h = handler(e);
        if (h.hasListeners()) h.post(ScriptType.SERVER, new PromptCriticalKubeEvent(e));
    }
}
