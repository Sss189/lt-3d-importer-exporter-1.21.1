package net.timardo.lt3dimporter.importer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JobControl {
    public final AtomicBoolean cancelled = new AtomicBoolean();
    public volatile int percent;
    public volatile String phase="working";
    private long work;
    public void check() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    public void step() throws ImportException {
        if ((++work & 1023) == 0) check();
        if (work > 50_000_000L) throw new ImportException("Sampling limit reached. Reduce size or increase sampling interval.");
    }
}
