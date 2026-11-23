package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Свой FakePlayer на каждый тест. {@code FakePlayerFactory.getMinecraft(level)} отдаёт одного игрока на весь уровень,
 * а тесты пакета идут одновременно: один надевает аппарат и привязывает страховочный трос, другой в тот же тик
 * раздевает или переставляет того же игрока, и исход зависит от того, какие тесты оказались соседями по пакету.
 * В пределах теста — один и тот же игрок, как раньше.
 */
public final class TestPlayers {
    private static final Map<GameTestHelper, FakePlayer> BY_TEST = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger NEXT = new AtomicInteger();

    private TestPlayers() {}

    public static FakePlayer of(GameTestHelper h) {
        return BY_TEST.computeIfAbsent(h, k -> {
            int n = NEXT.incrementAndGet();
            return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("pc_gametest_" + n).getBytes()), "pc_test_" + n));
        });
    }
}
