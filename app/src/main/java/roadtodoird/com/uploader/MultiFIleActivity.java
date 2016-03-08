package roadtodoird.com.uploader;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.Part;
import roadtodoird.com.uploader.Models.PartInfo;
import roadtodoird.com.uploader.Models.RecyclerViewAdapter;
import roadtodoird.com.uploader.Models.UploadTaskCallable;
import roadtodoird.com.uploader.Services.BlockingThreadPoolExecutor;
import roadtodoird.com.uploader.Services.DbHelper;
import roadtodoird.com.uploader.Services.DbManager;
import roadtodoird.com.uploader.Services.InitUploadTask;
import roadtodoird.com.uploader.Services.QueueControl;
import roadtodoird.com.uploader.Services.SanityCheck;
import roadtodoird.com.uploader.Services.SanityCheckerThreadOnly;
import roadtodoird.com.uploader.Services.TimeLogger;
import roadtodoird.com.uploader.Services.UploadInfo;

public class MultiFIleActivity extends AppCompatActivity implements RecyclerViewAdapter.OnItemLongClickListener, RecyclerViewAdapter.OnFabClickListener {

    ArrayMap<FIleInfo, List<Part>> mList = new ArrayMap<>();


    private RecyclerView mRecyclerView;
    private RecyclerViewAdapter mAdapter;
    private ArrayList<Uri> mFileUri = new ArrayList<>();
    private ArrayList<UploadInfo> mUploadInfo = new ArrayList<>();

    private ArrayList<ArrayMap<Integer, Long>> uploadedMap = new ArrayList<>();

    private Handler mHandler;

    private ArrayList<UploadManager> mUploadManager = new ArrayList<>();

    private ArrayList<Integer> mProgress;

    private ArrayList<Boolean> firstTime = new ArrayList<>();


    /*
    * TODO: CACHE
    * */
    //Cache containign FIle Info and partInfo for db updates and others....
    private ArrayList<ArrayMap<FIleInfo, PartInfo>> inMemCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_file);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        mFileUri = getIntent().getParcelableArrayListExtra("fileUri");
        mProgress = new ArrayList<>(mFileUri.size());

        for (int i = 0; i < mFileUri.size(); i++) {
            mProgress.add(0);
            mUploadManager.add(null);
            uploadedMap.add(i, new ArrayMap<Integer, Long>());
            firstTime.add(i, true);

            inMemCache.add(i, new ArrayMap<FIleInfo, PartInfo>());
        }
        /*
        for (int i = 0; i < mFileUri.size(); i++) {
            System.out.println(mFileUri.get(i).toString());
        }
        */

        mAdapter = new RecyclerViewAdapter(getApplicationContext(), mFileUri, mProgress);
        mAdapter.setOnItemClickListener(this);
        mAdapter.setOnFabClickListener(this);

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);


        // Make Upload Info List

        for (int i = 0; i < mFileUri.size(); i++) {
            mUploadInfo.add(new UploadInfo(0));
        }


        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {

                Bundle mBundle;
                mBundle = (Bundle) msg.obj;


                uploadedMap.get(mBundle.getInt("PosFromThread")).put(
                        mBundle.getInt("id"),
                        mBundle.getLong("count"));

                //uploadedMap.add(mBundle.getInt("PosFromThread"), (ArrayMap.Entry<Integer, Long>) entry);


                if (mBundle.getLong("done") == 100) {

                    Integer pos = mBundle.getInt("pos");

                    mProgress.set(pos, 100);
                    mAdapter.notifyDataSetChanged();

                    //mUploadInfo.get(pos).setState(UploadInfo.UploadState.FINISHED);
                    //showSnackBar("Upload Complete.");
                }


                if (mBundle.getString("sanitycheckreturn") != null) {
                    int pos = mBundle.getInt("pos");

                    if (mUploadInfo.get(pos).getState() == UploadInfo.UploadState.TOSTART) {
                        showSnackBar("Resuming upload.");
                        uploadedMap.get(pos).clear();
                        //fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
                        startUpload(mBundle.getInt("pos"));
                    } else if (mUploadInfo.get(pos).getState() == UploadInfo.UploadState.TOSTART) {
                        mUploadInfo.get(pos).setState(UploadInfo.UploadState.FINISHED);
                        showSnackBar("Upload Complete.");

                    }
                }

                if (mBundle.getString("finished") != null) {
                    showSnackBar("Upload Complete");
                    //fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_action_backup));
                }


                if (mBundle.getString("newRanges") != null) {
                    mList.clear();
                    FIleInfo fIleInfo = (FIleInfo) mBundle.getSerializable("mFileInfo");
                    ArrayList<Part> partList = (ArrayList<Part>) mBundle.getSerializable("mNewPartList");
                    System.out.println("File :-- " + fIleInfo.getFileName());

                    System.out.println("PartList :- " + partList.size());

                    mList.put(fIleInfo, partList);
                }

                if (mBundle.getString("updateUI") != null) {
                    mAdapter.notifyDataSetChanged();
                }

                if (mBundle.getBoolean("timeout") == true) {
                    int pos = mBundle.getInt("pos");
                    mUploadInfo.get(pos).setState(UploadInfo.UploadState.TOSTART);
                }

            }
        };


    }


    private void showSnackBar(String message) {
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator);
        Snackbar snackbar = Snackbar
                .make(coordinatorLayout, message, Snackbar.LENGTH_LONG).setAction("OK", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                    }
                });

        snackbar.show();
    }


    @Override
    public void onItemClick(View view, String mFileName) {
        //Toast.makeText(getApplicationContext(), "Long Pressed " + mFileName, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onFabClick(View view, String s, int position) {
        String msg = "" + position + " -- " + mUploadInfo.get(position).getState();

        if (mUploadManager.get(position) != null) {
            msg += " not null";
        }
        showSnackBar(msg);

        //Toast.makeText(getApplicationContext(), "Clicked FAB " + s, Toast.LENGTH_SHORT).show();
        FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);

        if (mUploadInfo.get(position).getState() == UploadInfo.UploadState.TOSTART) {
            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
        }


        if (firstTime.get(position) == true && mUploadManager.get(position) == null && mUploadInfo.get(position).getState() == UploadInfo.UploadState.TOSTART) {

            firstTime.set(position, false);
            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
            mUploadInfo.get(position).setState(UploadInfo.UploadState.WARMINGUP);
            startUpload(position);

        } else if (mUploadManager.get(position) != null && mUploadInfo.get(position).getState() == UploadInfo.UploadState.STARTED) {

            //mUploadManager.cancel(true);
            //System.gc();
            mUploadManager.set(position, null);
            System.gc();
            showSnackBar("Upload paused");
            mUploadInfo.get(position).setState(UploadInfo.UploadState.PAUSED);
            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_play_arrow));
        } else if (mUploadManager.get(position) != null && mUploadInfo.get(position).getState() == UploadInfo.UploadState.PAUSED) {
            showSnackBar("Resuming");
            //change this finally
            //mProgress.setProgress(0);
            //percentText.setText("0%");
            uploadedMap.get(position).clear();
            //*************************
            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
            startUpload(position);
        } else if (mUploadManager.get(position) != null && mUploadInfo.get(position).getState() == UploadInfo.UploadState.WARMINGUP) {
            showSnackBar("Starting Threads.");
        } else if (mUploadManager.get(position) == null && mUploadInfo.get(position).getState() == UploadInfo.UploadState.TOSTART) {


            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));

            //Send fileInfos to sanity checker
            ArrayList<FIleInfo> fileList = new ArrayList<>();


            Log.e("Wooo", String.valueOf(inMemCache.get(position).size()));
            if (inMemCache.get(position).size() != 0) {
                fileList.add(inMemCache.get(position).keyAt(0));
            }

            for (int i = 0; i < fileList.size(); i++) {
                System.out.println("index " + i + " - " + fileList.get(i).getServerFileID() + " - " + fileList.get(i).getFileName());
            }

            sanityCheckerFunc(fileList, position);


        } else if (mUploadInfo.get(position).getState() == UploadInfo.UploadState.FINISHED) {
            fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_action_backup));
        }

    }

    private synchronized void startUpload(final int position) {

        System.out.println("Staring upload for " + position);

        ArrayList<Uri> mTempUri = new ArrayList<Uri>();
        mTempUri.add(mFileUri.get(position));

        if (mUploadInfo.get(position).getState() == UploadInfo.UploadState.WARMINGUP) {
            final InitUploadTask newUploadTask = new InitUploadTask(getApplicationContext()
                    , mTempUri);
            //newUploadTask.execute("http://solulabdev.com/itransfer/dev/api/temp_v2/upload");
            final ProgressDialog ringProgressDialog = ProgressDialog.show(MultiFIleActivity.this, "", "Please Wait...", true);
            ringProgressDialog.setCancelable(false);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        try {
                            mList.clear();
                            mList = (newUploadTask
                                    .execute("http://103.233.25.47:5252/upload")
                                    .get());

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }


                    } catch (Exception e) {

                    }


                    //System.out.println("mList Size = " + mList.size());
                    if (mList.size() != 0) {
                        mUploadManager.set(position, new UploadManager(5, 5, 15, 30000, position));
                        //mUploadManager.get(position).execute(mList);

                        mUploadManager.get(position).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mList);

                        ringProgressDialog.dismiss();
                        showSnackBar("Upload Started.");
                    } else {
                        ringProgressDialog.dismiss();
                        showSnackBar("Something went wrong.");
                    }
                }
            }).start();
            /*
            for (Map.Entry<FIleInfo, List<Part>> entry : mList.entrySet()) {
                for (int i = 0; i < entry.getValue().size(); i++) {
                    System.out.println(entry.getKey().getFileName() + entry.getValue().get(i).getStart());
                }
            }
            */

            mUploadInfo.get(position).setState(UploadInfo.UploadState.STARTED);

        } else if (mUploadInfo.get(position).getState() == UploadInfo.UploadState.TOSTART) {

            mUploadManager.set(position, null);
            System.gc();

            mUploadManager.set(position, new UploadManager(5, 5, 15, 30000, position));
            mUploadManager.get(position).execute(mList);

            mUploadInfo.get(position).setState(UploadInfo.UploadState.STARTED);
        }


    }


    public class UploadManager extends AsyncTask<ArrayMap<FIleInfo, List<Part>>, String, Void> {
        private Integer NumberOfThreads = 5;
        private Integer threadCounter = 0;

        private Integer BlockingQueueSize;
        private Integer CorePoolSize;
        private Integer MaxPoolSize;
        private long KeepAliveTime;

        private BlockingQueue<Runnable> mBlockingQueue;
        private BlockingThreadPoolExecutor executor;

        private TimeLogger mTimeLogger;


        private DbManager dbManager = new DbManager();

        //private static HashMap<FIleInfo, PartInfo> inMemCache;

        private ArrayMap<FIleInfo, List<Part>> mMap = new ArrayMap<>();

        List<FutureTask<Integer>> taskList = new ArrayList<FutureTask<Integer>>();

        private OkHttpClient client;

        private QueueControl mQueueControl;

        private int curPosInList;


        public UploadManager(Integer QueueSize, Integer corePoolSize, Integer maxPoolSize, long keepAliveTime, int position) {
            this.BlockingQueueSize = QueueSize;
            this.CorePoolSize = corePoolSize;
            this.MaxPoolSize = maxPoolSize;
            this.KeepAliveTime = keepAliveTime;
            this.mTimeLogger = new TimeLogger(System.currentTimeMillis());

            this.dbManager.initInstance(new DbHelper(getApplicationContext()));

            //init okHttp client
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.MINUTES)
                    .writeTimeout(20, TimeUnit.MINUTES)
                    .readTimeout(20, TimeUnit.MINUTES)
                    .build();

            this.curPosInList = position;
        }

        private void kill() {
            for (int i = 0; i < taskList.size(); i++) {
                taskList.get(i).cancel(true);
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mBlockingQueue = new ArrayBlockingQueue<Runnable>(BlockingQueueSize);
            executor = new BlockingThreadPoolExecutor(CorePoolSize, MaxPoolSize, KeepAliveTime, TimeUnit.MILLISECONDS, mBlockingQueue);

            executor.prestartAllCoreThreads();


        }

        @Override
        protected Void doInBackground(ArrayMap<FIleInfo, List<Part>>... params) {

            long testFileSize = 0;

            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
            mMap.putAll((SimpleArrayMap<? extends FIleInfo, ? extends List<Part>>) params[0]);
            //mMap = params[0];

            //inMemCache = new HashMap<>(mMap.size());

            System.out.println("mMap Size : " + mMap.size());

            for (ArrayMap.Entry<FIleInfo, List<Part>> entry : mMap.entrySet()) {

                int chunkSize = 0;
                File file = new File(entry.getKey().getFileURI().getPath());
                RandomAccessFile randomAccessFile = null;

                try {
                    randomAccessFile = new RandomAccessFile(file, "r");
                    randomAccessFile.seek(0);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int start, end;

                try {
                    Log.e("Length : ", String.valueOf(randomAccessFile.length()));
                    testFileSize = randomAccessFile.length();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                PartInfo mPartInfo = new PartInfo(entry.getValue().size());

                //A loop for making DB

                for (int i = 0; i < entry.getValue().size(); i++) {
                    mPartInfo.addPartWithVal(entry.getValue().get(i).getID(), false);
                }


                //SQLiteDatabase db = dbManager.getInstance().openDatabase();
                DbManager manager = dbManager.getInstance();
                SQLiteDatabase db = manager.openDatabase();
                manager.addFile(entry.getKey(), mPartInfo, db);
                manager.getInstance().closeDatabase();
                Log.e("DB Entry", ": made");


                inMemCache.get(curPosInList).put(entry.getKey(), mPartInfo);
                Log.e("Cache Entry", entry.getKey().getFileName());

                //........................................


                //init Queue Control class
                mQueueControl = new QueueControl(entry.getValue());
                mQueueControl.viableRange();            //Calculate viable ranges
                System.out.println("New Viable--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());

                for (int i = mQueueControl.getstart(); i <= mQueueControl.getend(); i++) {
                    //System.out.println(i);


                    uploadedMap.get(curPosInList).put(entry.getValue().get(i).getID(), (long) 0);


                    /*
                    * TODO moved this part to TaskCALLABLE ...................
                    * */
                    /*
                    start = entry.getValue().get(i).getStart();
                    end = entry.getValue().get(i).getEnd();
                    chunkSize = end - start + 1;
                    System.out.println("ChunkSize:" + chunkSize);

                    byte[] dataArr = new byte[chunkSize];

                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.seek(start);

                            int count = randomAccessFile.read(dataArr);

                            //if (count < 0) continue;
                            System.out.println(dataArr.length);

                            entry.getValue().get(i).setData(dataArr);


                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    */

                    postToExecutor(entry.getKey(), entry.getValue().get(i), randomAccessFile);

                    /*
                    FutureTask<Integer> mFuture = new FutureTask<Integer>(new UploadTaskCallable(getApplicationContext(),
                            entry.getKey(),
                            entry.getValue().get(i),
                            mHandler, this.client));

                    taskList.add(mFuture);
                    executor.submit(mFuture);
                    */

                }
            }



            /*
            while (NumberOfThreads != threadCounter) {
                threadCounter++;


                executor.submit((new UploadTask(
                        new FIleInfo("testFile" + String.valueOf(threadCounter), 10, "text"),
                        0,
                        1023)));
            }*/

            //boolean FLAG_DONE = true;


            /*
            boolean UP_COMPLETE = false;
            TimeLogger mTime = new TimeLogger(System.currentTimeMillis());

            while (!UP_COMPLETE) {
                //Calculate amount downloaded
                //.......................
                int percDONE = 0;
                for (Map.Entry<FIleInfo, PartInfo> entry : inMemCache.entrySet()) {
                    HashMap<Integer, Boolean> info = entry.getValue().getPartInfo();
                    int total = 0;
                    float done = 0;
                    TimeLogger mFileTime = new TimeLogger(System.currentTimeMillis());
                    for (Map.Entry<Integer, Boolean> e : info.entrySet()) {
                        if (e.getValue() == true) done++;
                        total++;
                    }

                    System.out.println("Done: " + done + " Total:" + total);

                    //For single file update here
                    percDONE = Math.round((float) (done / taskList.size()) * 100);
                    publishProgress(percDONE);



                }
                //For multiples files handle here
                if (mTime.getElapsedTime() > 10) {
                    System.out.println("Too much wait");
                    publishProgress(percDONE);
                    break;
                }


            }
            */


            System.out.println("PerTEst for " + curPosInList + " = " + mMap.size());

            int total = 0;
            boolean RESET_RANGES = false;

            while (true) {

                //Refill Queue when empty...

                if (executor.isTerminating()) break;             //break if paused by user
                if (mUploadInfo.get(curPosInList).getState() == UploadInfo.UploadState.PAUSED || mUploadInfo.get(curPosInList).getState() == UploadInfo.UploadState.TOSTART) {
                    if (mUploadInfo.get(curPosInList).isFirstTime())
                        mUploadInfo.get(curPosInList).setPercDone((int) ((total * 100) / testFileSize));
                    else
                        mUploadInfo.get(curPosInList).setPercDone(mUploadInfo.get(curPosInList).getPercDone() + (int) ((total * 100) / testFileSize));
                    System.out.println("Killing Now.");
                    System.out.println("Current Progress : " + mUploadInfo.get(curPosInList).getPercDone() + " %");
                    executor.shutdownNow();
                    kill();
                    break;
                }

                //System.out.println("curPosInList : "+ curPosInList +" param[o].size : "+params[0].size());
                float done = 0;
                //System.out.println("WHILE");
                for (int i = 0; i < taskList.size(); i++) {


                    if (taskList.get(i).isDone()) done++;

                }


                //Kill if a task finishes or add new part
                if (done == taskList.size()) {
                    RESET_RANGES = false;

                } else {

                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                total = 0;
                /*for (Map.Entry<Integer, Long> entry : uploadedMap.entrySet()) {
                    System.out.println(entry.getKey() + " -- " + entry.getValue());
                    total += entry.getValue();
                }*/

                for (int i = 0; i < uploadedMap.get(curPosInList).size(); i++) {

                    //System.out.println(uploadedMap.keyAt(i) + " -- " + uploadedMap.valueAt(i));
                    total += uploadedMap.get(curPosInList).valueAt(i);
                }


                if (mUploadInfo.get(curPosInList).isFirstTime())
                    publishProgress(curPosInList + ":" + String.valueOf((int) ((total * 100) / testFileSize)));
                else
                    publishProgress(curPosInList + ":" + String.valueOf(mUploadInfo.get(curPosInList).getPercDone() + (int) ((total * 100) / testFileSize)));

                //System.out.println("FFF : " + (int) ((total * 100) / testFileSize));

                //System.out.println("Still UPloading " + curPosInList + " Perc-- " + String.valueOf((int) ((total * 100) / testFileSize)));
                if (done == taskList.size() && RESET_RANGES == false) {
                    //System.out.println("Next--" + mQueueControl.getstart() + " -- "+ mQueueControl.getend());

                    if (mQueueControl.getstart() == mQueueControl.getListSize()) {
                        //Done uploading all parts.....
                        System.out.println("Done Uploading All Parts");
                        break;
                    }

                    System.out.println("Test Print = " + mMap.size());

                    mQueueControl.setRanges(mQueueControl.getstart(), mQueueControl.getend());
                    System.out.println("Next--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());
                    mQueueControl.viableRange();
                    System.out.println("New Viable--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());
                    RESET_RANGES = true;
                    repostRangesToExecutor(mQueueControl.getstart(), mQueueControl.getend());
                }

            }

            //publishProgress("Debug");

            total = 0;
            for (int i = 0; i < uploadedMap.get(curPosInList).size(); i++) {
                total += uploadedMap.get(curPosInList).valueAt(i);
            }

            System.out.println("TOTAL = " + total);

            executor.shutdown();

            System.out.println("Waiting for Termination....");

            publishProgress("Termination");

            /*
            while (!executor.isTerminated()) {

            }
            */
            return null;
        }

        private void postToExecutor(FIleInfo mFileInfo, Part mPart, RandomAccessFile randomFileObj) {
            FutureTask<Integer> mFuture = new FutureTask<Integer>(new UploadTaskCallable(getApplicationContext(),
                    mFileInfo,
                    mPart,
                    mHandler,
                    this.client,
                    randomFileObj,
                    curPosInList));

            taskList.add(mFuture);
            executor.submit(mFuture);
        }

        private void repostRangesToExecutor(int startPartRange, int endPartRange) {

            System.out.println("Reposting + size = " + mMap.size());
            if (mMap != null && mMap.size() != 0) {
                System.out.println(mMap.keyAt(0).getFileName());
                System.out.println(mMap.valueAt(0).toString());
            }

            if (mMap.size() != 0) {
                long testFileSize = 0;
                int chunkSize = 0;
                File file = new File(mMap.keyAt(0).getFileURI().getPath());
                RandomAccessFile randomAccessFile = null;

                try {
                    randomAccessFile = new RandomAccessFile(file, "r");
                    randomAccessFile.seek(0);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int start, end;

                try {
                    Log.e("Length : ", String.valueOf(randomAccessFile.length()));
                    testFileSize = randomAccessFile.length();
                } catch (IOException e) {
                    e.printStackTrace();
                }


                for (int i = startPartRange; i <= endPartRange; i++) {
                    //System.out.println(i);

                    uploadedMap.get(curPosInList).put(mMap.valueAt(0).get(i).getID(), (long) 0);

                    start = mMap.valueAt(0).get(i).getStart();
                    end = mMap.valueAt(0).get(i).getEnd();
                    chunkSize = end - start + 1;
                    System.out.println("ChunkSize:" + chunkSize);

                    byte[] dataArr = new byte[chunkSize];

                    if (randomAccessFile != null) {
                        try {
                            randomAccessFile.seek(start);

                            int count = randomAccessFile.read(dataArr);

                            //if (count < 0) continue;
                            System.out.println(dataArr.length);

                            mMap.valueAt(0).get(i).setData(dataArr);


                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    System.out.println("REsposting + " + mMap.valueAt(0).get(i).getStart() + " -- " + mMap.valueAt(0).get(i).getEnd());
                    postToExecutor(mMap.keyAt(0), mMap.valueAt(0).get(i), randomAccessFile);
                }
            } else {
                System.out.println("Noting to repost.");
            }

        }

        @Override
        protected void onProgressUpdate(String... values) {
            //super.onProgressUpdate(values);
            //System.out.println("p:" + values[0]);

            if (values[0].compareTo("Termination") == 0) {
                executor.purge();
                System.gc();
                /*
                * TODO : this is for waiting for termination........
                * */
                /*
                final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait...", true);
                ringProgressDialog.setCancelable(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.gc();
                        while (true) {
                            if (executor != null) {
                                //System.out.println("here");
                                if (executor.isTerminated()) {
                                    ringProgressDialog.dismiss();
                                    break;
                                }
                            } else break;
                        }
                        executor.purge();
                        System.gc();
                    }
                }).start();
                */

            } else if (values[0].contains(":") && isInteger(values[0].substring(values[0].indexOf(":") + 1))) {

                int pos = Integer.parseInt(values[0].substring(0, values[0].indexOf(":")));

                int i = Integer.parseInt(values[0].substring(values[0].indexOf(":") + 1));
                if (i > 100) i = 100;

                //mProgress.setProgress(i);
                //percentText.setText(i + "%");
                if (pos == curPosInList)
                    mProgress.set(pos, i);

                //System.out.println("onProgressUpdate for " + curPosInList + " = " + i + " %");

                Message m = new Message();
                Bundle mBundle = new Bundle();
                mBundle.putString("updateUI", "updateUI");
                m.obj = mBundle;

                mHandler.sendMessageDelayed(m, 2000);

            } else if (values[0].compareTo("Debug") == 0) {
                System.out.println("Sent Ranges--");
                String s = "";
                for (int i = 0; i < mMap.size(); i++) {
                    for (int j = 0; j < mMap.valueAt(i).size(); j++) {
                        s += "start--" + mMap.valueAt(i).get(j).getStart();
                        s += "  ";
                        s += "end--" + mMap.valueAt(i).get(j).getEnd();
                        s += "\n";
                    }
                }

                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
            }
        }

        public boolean isInteger(String s) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return false;
            } catch (NullPointerException e) {
                return false;
            }
            // only got here if we didn't return false
            return true;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            System.out.println("On POST EXEC");
            if (executor.isTerminated()) {
                executor.shutdownNow();
            }
            //executor = null;
            //mBlockingQueue = null;

            Log.e("Time elapsed ", String.valueOf(mTimeLogger.getElapsedTime()));

            System.gc();


            //CHeck DB here : for parts completion

            //SQLiteDatabase db = dbManager.getInstance().openDatabase();

            /*
            DbManager manager = dbManager.getInstance();
            SQLiteDatabase db = manager.openDatabase();
            PartInfo mPart = manager.getPartInfoFromDB("images", db);
            for (Map.Entry<Integer, Boolean> entry : mPart.getPartInfo().entrySet()) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }

            manager.getInstance().closeDatabase();
            */

            /*
            System.out.println("In MEMORY");
            boolean FLAG_DONE = true;
            for (Map.Entry<FIleInfo, PartInfo> entry : inMemCache.get(curPosInList).entrySet()) {
                HashMap<Integer, Boolean> info = entry.getValue().getPartInfo();
                for (Map.Entry<Integer, Boolean> e : info.entrySet()) {
                    System.out.println("Cache: " + e.getKey() + " : " + e.getValue());
                    if (e.getValue() != true)
                        FLAG_DONE = false;
                }

            }
            */


            if (mUploadInfo.get(curPosInList).getState() == UploadInfo.UploadState.FINISHED)
                showSnackBar("Upload Complete.");
            else if (mUploadInfo.get(curPosInList).getState() == UploadInfo.UploadState.PAUSED) {
                mBlockingQueue.clear();
                showSnackBar("PAUSED.");
                mUploadInfo.get(curPosInList).setState(UploadInfo.UploadState.TOSTART);
            } else if (mUploadInfo.get(curPosInList).getState() == UploadInfo.UploadState.STARTED) {

                mUploadInfo.get(curPosInList).setState(UploadInfo.UploadState.FINISHED);

                ArrayList<FIleInfo> fileList = new ArrayList<>();
                fileList.add(inMemCache.get(curPosInList).keyAt(0));

                sanityCheckerFunc(fileList, curPosInList);

                Message m = new Message();
                Bundle mBundle = new Bundle();
                mBundle.putString("finished", "yes");
                m.obj = mBundle;
                mHandler.sendMessageDelayed(m, 10);


                showSnackBar("Upload Complete");

            }

            System.out.println("Final State  --> " + mUploadInfo.get(curPosInList).getState());
        }
    }


    public synchronized void sanityCheckerFunc(final ArrayList<FIleInfo> fileList, final int curPos) {


        final ProgressDialog ringProgressDialog = ProgressDialog.show(MultiFIleActivity.this, "", "Please Wait...", true);
        ringProgressDialog.setCancelable(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    try {
                        Thread.sleep(7000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    //Thread.sleep(3500);
                    /*
                    if (mList != null) mList.clear();
                    mList = new ArrayMap<FIleInfo, List<Part>>();
                    */

                    System.out.println("LIST SIZE: " + fileList.size());

                    System.out.println("Sending Request - " + "http://103.233.25.47:5252/upload" +
                            "/" + fileList.get(0).getServerFileID());

                    for (int i = 0; i < fileList.size(); i++) {

                        Runnable mRunnable = new SanityCheckerThreadOnly(fileList.get(i),
                                "http://103.233.25.47:5252/upload" +
                                        "/" + fileList.get(i).getServerFileID(), mHandler, curPos
                        );

                        Thread mThread = new Thread(mRunnable);
                        mThread.start();
                        System.out.println("Waiting for Sanity Checker...");
                        mThread.join();

                        /*
                        mList.put(fileList.get(i), new SanityCheck(mHandler).execute(
                                "http://103.233.25.47:5252/upload" +
                                        "/" + fileList.get(i).getServerFileID()).get());

                        */
                        System.out.println("GOT- " + (i + 1));

                    }


                    System.out.println("Finally mList == " + mList.size());


                    /*
                    for (ArrayMap.Entry<FIleInfo, List<Part>> entry : mList.entrySet()) {
                        for (int i = 0; i < entry.getValue().size(); i++) {
                            System.out.println("start--" + entry.getValue().get(i).getStart());
                            System.out.println("end--" + entry.getValue().get(i).getEnd());
                        }
                    }*/


                } catch (Exception e) {
                    System.out.println("exception : " + e.toString());
                    e.printStackTrace();
                }

                System.out.println("Sending Message to Handler");
                Message m = new Message();
                Bundle mBundle = new Bundle();
                mBundle.putInt("pos", curPos);
                mBundle.putString("sanitycheckreturn", "yes");
                m.obj = mBundle;
                mHandler.sendMessageDelayed(m, 100);

                ringProgressDialog.dismiss();

            }
        }).start();

    }

}
