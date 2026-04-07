package com.thaumicwards.pregen;

import com.thaumicwards.util.ChunkUtils;
import net.minecraft.util.math.ChunkPos;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class PregenTask {

    private final ChunkPos center;
    private final int radius;
    private final int totalChunks;
    private int completedChunks;
    private boolean running;
    private final Queue<ChunkPos> pending;

    public PregenTask(ChunkPos center, int radius) {
        this.center = center;
        this.radius = radius;
        this.totalChunks = (2 * radius + 1) * (2 * radius + 1);
        this.completedChunks = 0;
        this.running = true;

        // Build the queue from spiral iterator
        this.pending = new LinkedList<>();
        Iterator<ChunkPos> spiral = ChunkUtils.spiralIterator(center, radius);
        while (spiral.hasNext()) {
            pending.add(spiral.next());
        }
    }

    public ChunkPos pollNext() {
        return pending.poll();
    }

    public boolean hasNext() {
        return !pending.isEmpty();
    }

    public void incrementCompleted() {
        completedChunks++;
    }

    public int getCompletedChunks() {
        return completedChunks;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getPercentComplete() {
        if (totalChunks == 0) return 100;
        return (int) ((completedChunks * 100L) / totalChunks);
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
    }

    public ChunkPos getCenter() {
        return center;
    }

    public int getRadius() {
        return radius;
    }
}
