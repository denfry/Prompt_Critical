package dev.denfry.promptcritical.mod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Компонент {@code map_target} (11 §11.11): центр рудного тела, на которое указывает {@code geological_map}. */
public record MapTargetData(BlockPos pos, String vein) {
    public static final Codec<MapTargetData> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(MapTargetData::pos),
            Codec.STRING.fieldOf("vein").forGetter(MapTargetData::vein)).apply(i, MapTargetData::new));
    public static final StreamCodec<ByteBuf, MapTargetData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MapTargetData::pos, ByteBufCodecs.STRING_UTF8, MapTargetData::vein, MapTargetData::new);
}
