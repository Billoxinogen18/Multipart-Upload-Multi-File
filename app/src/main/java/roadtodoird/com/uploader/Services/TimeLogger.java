package roadtodoird.com.uploader.Services;

/**
 * Created by misterj on 19/1/16.
 */
public class TimeLogger {
    private long startTime;

    public TimeLogger(long startTime) {
        this.startTime = startTime;
    }

    public double getElapsedTime() {
        long endTime = System.currentTimeMillis();
        return (double) (endTime - startTime) / (1000);
    }
}
