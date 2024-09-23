package com.restonic4.ocaat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.datafixers.util.Unit;
import com.mojang.logging.LogUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ResetChunksCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ResetChunksCommand() {
    }

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher) {
        commandDispatcher.register(
                Commands.literal("generate")
                        .requires(commandSourceStack -> commandSourceStack.hasPermission(2))
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(commandContext -> {
                                            int chunkX = IntegerArgumentType.getInteger(commandContext, "chunkX");
                                            int chunkZ = IntegerArgumentType.getInteger(commandContext, "chunkZ");
                                            return resetChunks((CommandSourceStack) commandContext.getSource(), chunkX, chunkZ, true);
                                        }))
                        )
        );
    }

    private static int resetChunks(CommandSourceStack commandSourceStack, int chunkX, int chunkZ, boolean skipOldChunks) {
        ServerLevel serverLevel = commandSourceStack.getLevel();
        ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
        serverChunkCache.chunkMap.debugReloadGenerator();
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        LevelChunk levelChunk = serverChunkCache.getChunk(chunkPos.x, chunkPos.z, skipOldChunks);
        if (levelChunk != null && (!skipOldChunks || !levelChunk.isOldNoiseGeneration())) {
            Iterator var15 = BlockPos.betweenClosed(chunkPos.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), serverLevel.getMaxBuildHeight() - 1, chunkPos.getMaxBlockZ()).iterator();

            while(var15.hasNext()) {
                BlockPos blockPos = (BlockPos)var15.next();
                serverLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 16);
            }
        }

        ProcessorMailbox<Runnable> processorMailbox = ProcessorMailbox.create(Util.backgroundExecutor(), "worldgen-resetchunks");
        long p = System.currentTimeMillis();

        UnmodifiableIterator var34 = ImmutableList.of(ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.FEATURES, ChunkStatus.INITIALIZE_LIGHT).iterator();

        long r;
        while(var34.hasNext()) {
            ChunkStatus chunkStatus = (ChunkStatus)var34.next();
            r = System.currentTimeMillis();
            Supplier var10000 = () -> {
                return Unit.INSTANCE;
            };
            Objects.requireNonNull(processorMailbox);
            CompletableFuture<Unit> completableFuture = CompletableFuture.supplyAsync(var10000, processorMailbox::tell);
            WorldGenContext worldGenContext = new WorldGenContext(serverLevel, serverChunkCache.getGenerator(), serverLevel.getStructureManager(), serverChunkCache.getLightEngine());

            LevelChunk levelChunk2 = serverChunkCache.getChunk(chunkPos.x, chunkPos.z, false);
            if (levelChunk2 != null && (!skipOldChunks || !levelChunk2.isOldNoiseGeneration())) {
                List<ChunkAccess> list = Lists.newArrayList();
                int u = Math.max(1, chunkStatus.getRange());

                for(int v = chunkPos.z - u; v <= chunkPos.z + u; ++v) {
                    for(int w = chunkPos.x - u; w <= chunkPos.x + u; ++w) {
                        ChunkAccess chunkAccess = serverChunkCache.getChunk(w, v, chunkStatus.getParent(), true);
                        Object chunkAccess2;
                        if (chunkAccess instanceof ImposterProtoChunk) {
                            chunkAccess2 = new ImposterProtoChunk(((ImposterProtoChunk)chunkAccess).getWrapped(), true);
                        } else if (chunkAccess instanceof LevelChunk) {
                            chunkAccess2 = new ImposterProtoChunk((LevelChunk)chunkAccess, true);
                        } else {
                            chunkAccess2 = chunkAccess;
                        }

                        list.add((ChunkAccess) chunkAccess2);
                    }
                }

                Function var10001 = (unit) -> {
                    Objects.requireNonNull(processorMailbox);
                    return chunkStatus.generate(worldGenContext, processorMailbox::tell, (chunkAccess) -> {
                        throw new UnsupportedOperationException("Not creating full chunks here");
                    }, list).thenApply((chunkAccess) -> {
                        if (chunkStatus == ChunkStatus.NOISE) {
                            Heightmap.primeHeightmaps(chunkAccess, ChunkStatus.POST_FEATURES);
                        }

                        return Unit.INSTANCE;
                    });
                };
                Objects.requireNonNull(processorMailbox);
                completableFuture = completableFuture.thenComposeAsync(var10001, processorMailbox::tell);
            }

            MinecraftServer var37 = commandSourceStack.getServer();
            Objects.requireNonNull(completableFuture);
            var37.managedBlock(completableFuture::isDone);
            Logger var38 = LOGGER;
            String var40 = String.valueOf(chunkStatus);
            var38.debug(var40 + " took " + (System.currentTimeMillis() - r) + " ms");
        }

        long x = System.currentTimeMillis();

        LevelChunk levelChunk3 = serverChunkCache.getChunk(chunkPos.x, chunkPos.z, false);
        if (levelChunk3 != null && (!skipOldChunks || !levelChunk3.isOldNoiseGeneration())) {
            Iterator var43 = BlockPos.betweenClosed(chunkPos.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX(), serverLevel.getMaxBuildHeight() - 1, chunkPos.getMaxBlockZ()).iterator();

            while(var43.hasNext()) {
                BlockPos blockPos2 = (BlockPos)var43.next();
                serverChunkCache.blockChanged(blockPos2);
            }
        }

        LOGGER.debug("blockChanged took " + (System.currentTimeMillis() - x) + " ms");
        r = System.currentTimeMillis() - p;
        long finalR = r;
        commandSourceStack.sendSuccess(() -> {
            return Component.literal(String.format(Locale.ROOT, "Chunk generated!. This took %d ms.", finalR));
        }, true);
        return 1;
    }

    /*private static int resetChunks(CommandSourceStack commandSourceStack, int i, boolean bl) {
        ServerLevel serverLevel = commandSourceStack.getLevel();
        ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
        serverChunkCache.chunkMap.debugReloadGenerator();
        Vec3 vec3 = commandSourceStack.getPosition();
        ChunkPos chunkPos = new ChunkPos(BlockPos.containing(vec3));
        int j = chunkPos.z - i;
        int k = chunkPos.z + i;
        int l = chunkPos.x - i;
        int m = chunkPos.x + i;

        for(int n = j; n <= k; ++n) {
            for(int o = l; o <= m; ++o) {
                ChunkPos chunkPos2 = new ChunkPos(o, n);
                LevelChunk levelChunk = serverChunkCache.getChunk(o, n, false);
                if (levelChunk != null && (!bl || !levelChunk.isOldNoiseGeneration())) {
                    Iterator var15 = BlockPos.betweenClosed(chunkPos2.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos2.getMinBlockZ(), chunkPos2.getMaxBlockX(), serverLevel.getMaxBuildHeight() - 1, chunkPos2.getMaxBlockZ()).iterator();

                    while(var15.hasNext()) {
                        BlockPos blockPos = (BlockPos)var15.next();
                        serverLevel.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 16);
                    }
                }
            }
        }

        ProcessorMailbox<Runnable> processorMailbox = ProcessorMailbox.create(Util.backgroundExecutor(), "worldgen-resetchunks");
        long p = System.currentTimeMillis();
        int q = (i * 2 + 1) * (i * 2 + 1);
        UnmodifiableIterator var34 = ImmutableList.of(ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.FEATURES, ChunkStatus.INITIALIZE_LIGHT).iterator();

        long r;
        while(var34.hasNext()) {
            ChunkStatus chunkStatus = (ChunkStatus)var34.next();
            r = System.currentTimeMillis();
            Supplier var10000 = () -> {
                return Unit.INSTANCE;
            };
            Objects.requireNonNull(processorMailbox);
            CompletableFuture<Unit> completableFuture = CompletableFuture.supplyAsync(var10000, processorMailbox::tell);
            WorldGenContext worldGenContext = new WorldGenContext(serverLevel, serverChunkCache.getGenerator(), serverLevel.getStructureManager(), serverChunkCache.getLightEngine());

            for(int s = chunkPos.z - i; s <= chunkPos.z + i; ++s) {
                for(int t = chunkPos.x - i; t <= chunkPos.x + i; ++t) {
                    ChunkPos chunkPos3 = new ChunkPos(t, s);
                    LevelChunk levelChunk2 = serverChunkCache.getChunk(t, s, false);
                    if (levelChunk2 != null && (!bl || !levelChunk2.isOldNoiseGeneration())) {
                        List<ChunkAccess> list = Lists.newArrayList();
                        int u = Math.max(1, chunkStatus.getRange());

                        for(int v = chunkPos3.z - u; v <= chunkPos3.z + u; ++v) {
                            for(int w = chunkPos3.x - u; w <= chunkPos3.x + u; ++w) {
                                ChunkAccess chunkAccess = serverChunkCache.getChunk(w, v, chunkStatus.getParent(), true);
                                Object chunkAccess2;
                                if (chunkAccess instanceof ImposterProtoChunk) {
                                    chunkAccess2 = new ImposterProtoChunk(((ImposterProtoChunk)chunkAccess).getWrapped(), true);
                                } else if (chunkAccess instanceof LevelChunk) {
                                    chunkAccess2 = new ImposterProtoChunk((LevelChunk)chunkAccess, true);
                                } else {
                                    chunkAccess2 = chunkAccess;
                                }

                                list.add((ChunkAccess) chunkAccess2);
                            }
                        }

                        Function var10001 = (unit) -> {
                            Objects.requireNonNull(processorMailbox);
                            return chunkStatus.generate(worldGenContext, processorMailbox::tell, (chunkAccess) -> {
                                throw new UnsupportedOperationException("Not creating full chunks here");
                            }, list).thenApply((chunkAccess) -> {
                                if (chunkStatus == ChunkStatus.NOISE) {
                                    Heightmap.primeHeightmaps(chunkAccess, ChunkStatus.POST_FEATURES);
                                }

                                return Unit.INSTANCE;
                            });
                        };
                        Objects.requireNonNull(processorMailbox);
                        completableFuture = completableFuture.thenComposeAsync(var10001, processorMailbox::tell);
                    }
                }
            }

            MinecraftServer var37 = commandSourceStack.getServer();
            Objects.requireNonNull(completableFuture);
            var37.managedBlock(completableFuture::isDone);
            Logger var38 = LOGGER;
            String var40 = String.valueOf(chunkStatus);
            var38.debug(var40 + " took " + (System.currentTimeMillis() - r) + " ms");
        }

        long x = System.currentTimeMillis();

        for(int y = chunkPos.z - i; y <= chunkPos.z + i; ++y) {
            for(int z = chunkPos.x - i; z <= chunkPos.x + i; ++z) {
                ChunkPos chunkPos4 = new ChunkPos(z, y);
                LevelChunk levelChunk3 = serverChunkCache.getChunk(z, y, false);
                if (levelChunk3 != null && (!bl || !levelChunk3.isOldNoiseGeneration())) {
                    Iterator var43 = BlockPos.betweenClosed(chunkPos4.getMinBlockX(), serverLevel.getMinBuildHeight(), chunkPos4.getMinBlockZ(), chunkPos4.getMaxBlockX(), serverLevel.getMaxBuildHeight() - 1, chunkPos4.getMaxBlockZ()).iterator();

                    while(var43.hasNext()) {
                        BlockPos blockPos2 = (BlockPos)var43.next();
                        serverChunkCache.blockChanged(blockPos2);
                    }
                }
            }
        }

        LOGGER.debug("blockChanged took " + (System.currentTimeMillis() - x) + " ms");
        r = System.currentTimeMillis() - p;
        long finalR = r;
        commandSourceStack.sendSuccess(() -> {
            return Component.literal(String.format(Locale.ROOT, "%d chunks have been reset. This took %d ms for %d chunks, or %02f ms per chunk", q, finalR, q, (float) finalR / (float)q));
        }, true);
        return 1;
    }*/
}