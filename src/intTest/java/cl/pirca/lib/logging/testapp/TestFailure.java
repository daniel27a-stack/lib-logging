package cl.pirca.lib.logging.testapp;

/** An exception that the test advice turns into a response, the way lib-errors does. */
public class TestFailure extends RuntimeException {

    private final int status;

    public TestFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
