package data;

public class SerializationInfo {

    public long serializationTime;
    public long writeTime;
    public long readTime;
    public long deserializationTime;
    public long cgSize;

    public SerializationInfo(final long serializationTime, final long writeTime,
                             final long readTime,
                             final long deserializationTime, long cgSize) {
        this.serializationTime = serializationTime;
        this.writeTime = writeTime;
        this.readTime = readTime;
        this.deserializationTime = deserializationTime;
        this.cgSize = cgSize;
    }

    public SerializationInfo() {
        this.serializationTime = 0;
        this.writeTime = 0;
        this.readTime = 0;
        this.deserializationTime = 0;
        this.cgSize = 0;
    }
}
