package data;

public class MergeResultStats {
    public long cgPoolTotalTime;
    public long mergeTime;
    public long appPCGTime;
    public double cgSize;
    public double mergerSize;

    public MergeResultStats(final long cgPoolTotalTime, final long mergeTotalTime,
                            final long appPCGTime, final double cgSize, final double mergerSize) {
        this.cgPoolTotalTime = cgPoolTotalTime;
        this.mergeTime = mergeTotalTime;
        this.appPCGTime = appPCGTime;
        this.cgSize = cgSize;
        this.mergerSize = mergerSize;
    }

}
