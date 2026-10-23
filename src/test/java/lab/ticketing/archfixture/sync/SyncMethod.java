package lab.ticketing.archfixture.sync;

/** Deliberately violating fixture: a synchronized method (ACC_SYNCHRONIZED). */
public class SyncMethod {

    private int count;

    public synchronized int increment() {
        return ++count;
    }
}
