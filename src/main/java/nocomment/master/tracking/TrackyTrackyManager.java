package nocomment.master.tracking;

import nocomment.master.Server;
import nocomment.master.db.TrackResume;
import nocomment.master.util.ChunkPos;
import nocomment.master.util.HighwayScanner;

import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class TrackyTrackyManager {
    public static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(16); // at most 16 threads because stupid

    private final Server server;
    private final WorldTrackyTracky overworld;
    private final WorldTrackyTracky nether;

    public TrackyTrackyManager(Server server) {
        this.server = server;
        this.overworld = new WorldTrackyTracky(server.getWorld(0), this, this::lostTrackingInOverworld);
        this.nether = new WorldTrackyTracky(server.getWorld(-1), this, this::lostTrackingInNether);
        highways();
        spiral();
        //this.overworld.ingestApprox(new ChunkPos(-38, -70), OptionalLong.empty());
    }

    private void highways() {
        new HighwayScanner(nether.world, 1000, 2_000_000 / 8, 400_000, hit -> nether.ingestGenericKnownHit(hit, OptionalLong.empty())).submitTasks();
        new HighwayScanner(nether.world, 100, 25_000, 40_000, hit -> nether.ingestGenericKnownHit(hit, OptionalLong.empty())).submitTasks();
        new HighwayScanner(overworld.world, 100, 25_000, 40_000, hit -> overworld.ingestGenericKnownHit(hit, OptionalLong.empty())).submitTasks();
    }

    private void spiral() {
        overworld.grid(10000, 9, 250, new ChunkPos(0, 0), hit -> overworld.ingestGenericKnownHit(hit, OptionalLong.empty()));
        nether.grid(10000, 9, 250, new ChunkPos(0, 0), hit -> nether.ingestGenericKnownHit(hit, OptionalLong.empty()));
    }

    private void lostTrackingInOverworld(Filter lost) {
        nether.ingestApprox(new ChunkPos(lost.getMostRecentHit().x / 8, lost.getMostRecentHit().z / 8), OptionalLong.of(lost.getTrackID()));
    }

    private void lostTrackingInNether(Filter lost) {
        overworld.ingestApprox(new ChunkPos(lost.getMostRecentHit().x * 8, lost.getMostRecentHit().z * 8), OptionalLong.of(lost.getTrackID()));
    }

    public boolean hasActiveFilter(long trackID) {
        return overworld.hasActiveFilter(trackID) || nether.hasActiveFilter(trackID);
    }

    public void attemptResume(TrackResume resumeData) {
        System.out.println("Attempting to resume tracking at " + resumeData.pos + " in dimension " + resumeData.dimension + " in server " + server.hostname + " from track id " + resumeData.prevTrackID);
        switch (resumeData.dimension) {
            case 0: {
                overworld.ingestApprox(resumeData.pos, OptionalLong.of(resumeData.prevTrackID));
                break;
            }
            case -1: {
                nether.ingestApprox(resumeData.pos, OptionalLong.of(resumeData.prevTrackID));
                break;
            }
            default: {
                System.out.println("We don't do that here " + resumeData.dimension);
            }
        }
    }
}
