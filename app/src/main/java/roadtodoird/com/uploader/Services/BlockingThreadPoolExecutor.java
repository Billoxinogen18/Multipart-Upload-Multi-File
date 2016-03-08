package roadtodoird.com.uploader.Services;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by misterj on 19/1/16.
 */
public class BlockingThreadPoolExecutor extends ThreadPoolExecutor {
    private final Semaphore mSemaphore;

    public BlockingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        mSemaphore = new Semaphore(corePoolSize + 50);

    }

    @Override
    protected void terminated() {
        super.terminated();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
    }

    @Override
    public void execute(Runnable command) {
        boolean acquired = false;
        do {
            try {
                mSemaphore.acquire();
                acquired = true;
            } catch (final InterruptedException e) {
                Log.e("Interr. Error: ", e.getMessage());
            }
        } while (!acquired);

        try {
            super.execute(command);
        } catch (final RejectedExecutionException e) {
            System.out.println("Task Rejected");
            mSemaphore.release();
            throw e;
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        if(t!=null){
            t.printStackTrace();
        }

        mSemaphore.release();

        System.out.println("afterExecute");

    }

}

