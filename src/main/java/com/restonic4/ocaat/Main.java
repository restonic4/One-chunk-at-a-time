package com.restonic4.ocaat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main implements ModInitializer {
    public boolean openWorld = false;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ResetChunksCommand.register(dispatcher);
        });

        ServerChunkEvents.CHUNK_LOAD.register(this::onChunkLoad);

        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStop);
    }

    private void onServerStart(MinecraftServer minecraftServer) {
        System.out.println("World opened");
        openWorld = true;
    }

    private void onServerStop(MinecraftServer minecraftServer) {
        System.out.println("World stopped");
        openWorld = false;
    }

    private void onChunkLoad(ServerLevel serverLevel, LevelChunk levelChunk) {
        ChunkPos chunkPos = levelChunk.getPos();

        if (serverLevel.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
            System.out.println("Removing " + chunkPos.x + ", " + chunkPos.z);

            startRemovingLayers(serverLevel, chunkPos);
        }
    }

    private void startRemovingLayers(ServerLevel world, ChunkPos chunkPos) {
        int maxHeight = world.getMaxBuildHeight();
        int minHeight = -64;

        Thread thread = new Thread(() -> {
            int currentHeight = maxHeight;

            world.getServer().execute(() -> {
                removeLiquids(world, chunkPos);
            });

            while (currentHeight >= minHeight) {
                if (!openWorld) {
                    currentHeight = minHeight - 1;
                }

                try {
                    int finalCurrentHeight = currentHeight;
                    world.getServer().execute(() -> {
                                removeLayer(world, chunkPos, finalCurrentHeight);
                    });
                    currentHeight--;

                    Thread.sleep(1000);
                } catch (Exception ignored) {
                    currentHeight = minHeight - 1;
                }
            }
        });
        thread.start();
    }

    private void removeLayer(ServerLevel world, ChunkPos chunkPos, int y) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void removeLiquids(ServerLevel world, ChunkPos chunkPos) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = world.getMaxBuildHeight(); y >= -64; y--) {
                    if (world.getBlockState(new BlockPos(x, y, z)) == Blocks.WATER.defaultBlockState() || world.getBlockState(new BlockPos(x, y, z)) == Blocks.LAVA.defaultBlockState()) {
                        BlockPos pos = new BlockPos(x, y, z);
                        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /*private void onChunkLoad(ServerLevel serverLevel, LevelChunk levelChunk) {
        ChunkPos chunkPos = levelChunk.getPos();

        if (serverLevel.getChunkSource().hasChunk(chunkPos.x, chunkPos.z)) {
            System.out.println("Removing " + chunkPos.x + ", " + chunkPos.z);
            startRemovingLayers(serverLevel, chunkPos);
        }
    }

    private void startRemovingLayers(ServerLevel world, ChunkPos chunkPos) {
        int maxHeight = world.getMaxBuildHeight();
        int minHeight = -64;

        scheduler.scheduleWithFixedDelay(new Runnable() {
            private int currentHeight = maxHeight;

            @Override
            public void run() {
                if (currentHeight < minHeight) {
                    System.out.println("Completed removing layers for chunk " + chunkPos);
                    return;
                }

                removeLayer(world, chunkPos, currentHeight);

                currentHeight--;
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void removeLayer(ServerLevel world, ChunkPos chunkPos, int y) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        System.out.println("Removed layer at Y=" + y + " for chunk " + chunkPos);
    }*/
}
