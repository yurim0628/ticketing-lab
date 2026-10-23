package lab.ticketing.archfixture.sync;

/** Deliberately violating fixture: a synchronized block (bytecode MONITORENTER). ArchUnit's modifier check cannot see it. */
public class SyncBlock {

    private final Object lock = new Object();
    private int count;

    public int increment() {
        synchronized (lock) {
            return ++count;
        }
    }
}
