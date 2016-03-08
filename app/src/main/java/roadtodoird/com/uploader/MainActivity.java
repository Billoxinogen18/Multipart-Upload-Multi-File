package roadtodoird.com.uploader;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import com.nononsenseapps.filepicker.FilePickerActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.Inet4Address;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
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
import java.nio.charset.Charset;

import okhttp3.OkHttpClient;
import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.Part;
import roadtodoird.com.uploader.Models.PartInfo;
import roadtodoird.com.uploader.Models.UploadTask;
import roadtodoird.com.uploader.Models.UploadTaskCallable;
import roadtodoird.com.uploader.Services.BlockingThreadPoolExecutor;
import roadtodoird.com.uploader.Services.DbHelper;
import roadtodoird.com.uploader.Services.DbManager;
import roadtodoird.com.uploader.Services.DbUpdateService;
import roadtodoird.com.uploader.Services.InitUploadTask;
import roadtodoird.com.uploader.Services.QueueControl;
import roadtodoird.com.uploader.Services.SanityCheck;
import roadtodoird.com.uploader.Services.SanityCheckerThreadOnly;
import roadtodoird.com.uploader.Services.TimeLogger;
import roadtodoird.com.uploader.Services.UploadInfo;
import roadtodoird.com.uploader.Services.UploadInfo.UploadState;


public class MainActivity extends AppCompatActivity {

    ArrayMap<FIleInfo, List<Part>> mList = new ArrayMap<>();

    private Button uploadButton, filePickerButton;
    private UploadManager mUploadManager;
    private final static int FILE_CODE = 0;

    private ArrayList<Uri> mFileURI = new ArrayList<Uri>();

    private ProgressBar mProgress;

    private UploadInfo mUploadInfo;

    private Handler mHandler;
    private long uploaded = 0;
    private long prev = 0;
    private TextView percentText;
    private long testFileSize = 0;

    private ArrayMap<Integer, Long> uploadedMap = new ArrayMap<>();

    private boolean firstTime = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initServiceAndSchedule();
        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);

        percentText = (TextView) findViewById(R.id.textView);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                //super.handleMessage(msg);

                /*
                showSnackBar("Resuming upload.");
                uploadedMap.clear();
                fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
                startUpload();
                */

                Bundle mBundle;
                mBundle = (Bundle) msg.obj;

                uploadedMap.put(mBundle.getInt("id"), mBundle.getLong("count"));

                if (mBundle.getLong("done") == 100) {
                    mProgress.setProgress(100);
                    percentText.setText("100" + "%");
                }

                if (mBundle.getString("sanitycheckreturn") != null && mUploadInfo.getState() == UploadState.TOSTART) {
                    if (mList.size() != 0) {
                        showSnackBar("Resuming upload.");
                        uploadedMap.clear();
                        fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
                        startUpload();
                    }
                }
                if (mBundle.getString("finished") != null) {
                    showSnackBar("Upload Complete");
                    fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_action_backup));
                }

                if (mBundle.getString("newRanges") != null) {
                    FIleInfo fIleInfo = (FIleInfo) mBundle.getSerializable("mFileInfo");
                    ArrayList<Part> partList = (ArrayList<Part>) mBundle.getSerializable("mNewPartList");
                    System.out.println("File :-- " + fIleInfo.getFileName());

                    System.out.println("PartList :- " + partList.size());

                    mList.put(fIleInfo, partList);
                }
                /*
                prev = uploaded;
                uploaded += Long.parseLong(msg.obj.toString());

                final int res = (int) (((uploaded - prev) * 100) / testFileSize);

                System.out.println("UP: " + (uploaded - prev));
                System.out.println("TEST: " + testFileSize);
                mProgress.setProgress(res);
                percentText.setText(res + "%");
                */
            }
        };

        mUploadInfo = new UploadInfo(0);


        fab.setAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.fab_slide_in_from_right));

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (firstTime && mUploadManager == null && mUploadInfo.getState() == UploadState.TOSTART) {
                    if (mFileURI.isEmpty()) {
                        //Toast.makeText(getApplicationContext(), "Please select at leat 1 file.", Toast.LENGTH_SHORT).show();
                        showSnackBar("Please select at leat 1 file.");
                        return;
                    }

                    fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
                    mUploadInfo.setState(UploadState.WARMINGUP);
                    startUpload();
                    firstTime = false;

                } else if (mUploadManager != null && mUploadInfo.getState() == UploadState.STARTED) {

                    //mUploadManager.cancel(true);
                    //System.gc();
                    mUploadManager = null;
                    System.gc();
                    showSnackBar("Upload paused");
                    mUploadInfo.setState(UploadState.PAUSED);
                    fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_play_arrow));
                } else if (mUploadManager != null && mUploadInfo.getState() == UploadState.PAUSED) {
                    showSnackBar("Resuming");
                    //change this finally
                    //mProgress.setProgress(0);
                    //percentText.setText("0%");
                    uploadedMap.clear();
                    //*************************
                    fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_av_pause));
                    startUpload();
                } else if (mUploadManager != null && mUploadInfo.getState() == UploadState.WARMINGUP) {
                    showSnackBar("Starting Threads.");
                } else if (mUploadManager == null && mUploadInfo.getState() == UploadState.TOSTART) {


                    //Send fileInfos to sanity checker
                    ArrayList<FIleInfo> fileList = new ArrayList<>();
                    for (Map.Entry<FIleInfo, PartInfo> entry : inMemCache.entrySet()) {
                        fileList.add(entry.getKey());
                    }

                    sanityCheckerFunc(fileList);


                } else if (mUploadInfo.getState() == UploadState.FINISHED) {
                    fab.setImageDrawable(getApplicationContext().getResources().getDrawable(R.drawable.ic_action_backup));
                }

            }
        });

        mProgress = (ProgressBar) findViewById(R.id.progressBar);


        filePickerButton = (Button) findViewById(R.id.selectFile);

        filePickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFilePicker(getApplicationContext());
            }
        });

    }

    private void showSnackBar(String message) {
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coor);
        Snackbar snackbar = Snackbar
                .make(coordinatorLayout, message, Snackbar.LENGTH_LONG).setAction("OK", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                    }
                });

        snackbar.show();
    }

    private void updateList(ArrayMap<FIleInfo, List<Part>> list) {
        mList = list;
    }

    private void startUpload() {

        if (mUploadInfo.getState() == UploadState.WARMINGUP) {
            final InitUploadTask newUploadTask = new InitUploadTask(getApplicationContext(), mFileURI);
            //newUploadTask.execute("http://solulabdev.com/itransfer/dev/api/temp_v2/upload");
            final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait...", true);
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

                    if (mList.size() != 0) {
                        mUploadManager = new UploadManager(5, 5, 100, 30000);
                        mUploadManager.execute(mList);

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

            mUploadInfo.setState(UploadState.STARTED);
        } else if (mUploadInfo.getState() == UploadState.TOSTART) {

            mUploadManager = null;
            System.gc();

            mUploadManager = new UploadManager(5, 5, 100, 30000);
            mUploadManager.execute(mList);

            mUploadInfo.setState(UploadState.STARTED);
        }


    }


    private void startFilePicker(Context context) {

        Intent i = new Intent(context, FilePickerActivity.class);
        // This works if you defined the intent filter
        // Intent i = new Intent(Intent.ACTION_GET_CONTENT);

        // Set these depending on your use case. These are the defaults.
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, true);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);

        // Configure initial directory by specifying a String.
        // You could specify a String like "/storage/emulated/0/", but that can
        // dangerous. Always use Android's API calls to get paths to the SD-card or
        // internal memory.
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

        startActivityForResult(i, FILE_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();

                    for (int i = 0; i < clip.getItemCount(); i++) {
                        Uri uri = clip.getItemAt(i).getUri();
                        mFileURI.add(uri);
                    }

                    if (clip != null) {

                        if (clip.getItemCount() == 1) {
                            showSnackBar("Selected " + clip.getItemCount() + " file.");

                        } else if (clip.getItemCount() <= 5) {

                            showSnackBar("Selected " + clip.getItemCount() + " files.");
                            Intent intent = new Intent(MainActivity.this, MultiFIleActivity.class);
                            intent.putParcelableArrayListExtra("fileUri", mFileURI);
                            startActivity(intent);

                        } else {
                            showSnackBar("Select less than 5 files.");
                        }
                    } else
                        showSnackBar("Please Retry.");

                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra
                            (FilePickerActivity.EXTRA_PATHS);

                    if (paths != null) {
                        for (String path : paths) {
                            Uri uri = Uri.parse(path);
                            mFileURI.add(uri);
                        }
                        showSnackBar("Selected " + paths.size() + " files.");
                    } else
                        showSnackBar("Please Retry.");
                }

            } else {
                showSnackBar("Selected a File.");
                Uri uri = data.getData();
                // Do something with the URI

                //System.out.println(uri.toString());
                mFileURI.add(uri);
            }
        } else if (requestCode == FILE_CODE && resultCode == Activity.RESULT_CANCELED) {

        }


    }


    private static HashMap<FIleInfo, PartInfo> inMemCache;

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


        public UploadManager(Integer QueueSize, Integer corePoolSize, Integer maxPoolSize, long keepAliveTime) {
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


            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);

            mMap.putAll((SimpleArrayMap<? extends FIleInfo, ? extends List<Part>>) params[0]);

            inMemCache = new HashMap<>(mMap.size());

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

                inMemCache.put(entry.getKey(), mPartInfo);

                //........................................


                //init Queue Control class
                mQueueControl = new QueueControl(entry.getValue());
                mQueueControl.viableRange();            //Calculate viable ranges
                System.out.println("New Viable--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());

                for (int i = mQueueControl.getstart(); i <= mQueueControl.getend(); i++) {
                    //System.out.println(i);

                    uploadedMap.put(entry.getValue().get(i).getID(), (long) 0);

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


            int total = 0;
            boolean RESET_RANGES = false;

            while (true) {

                //Refill Queue when empty...

                if (executor.isTerminating()) break;             //break if paused by user
                if (mUploadInfo.getState() == UploadState.PAUSED || mUploadInfo.getState() == UploadState.TOSTART) {
                    if (mUploadInfo.isFirstTime())
                        mUploadInfo.setPercDone((int) ((total * 100) / testFileSize));
                    else
                        mUploadInfo.setPercDone(mUploadInfo.getPercDone() + (int) ((total * 100) / testFileSize));
                    System.out.println("Killing Now.");
                    System.out.println("Current Progress : " + mUploadInfo.getPercDone() + " %");
                    executor.shutdownNow();
                    kill();
                    break;
                }
                float done = 0;
                //System.out.println("WHILE");
                for (int i = 0; i < taskList.size(); i++) {


                    if (taskList.get(i).isDone()) done++;

                }


                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //System.out.println("Done: " + done);
                //System.out.println("Perc:" + (float) (done / taskList.size()) * 100);

                int prog = Math.round((float) (done / taskList.size()) * 100);
                //System.out.println("mProg " + prog);
                //publishProgress(prog);


                //Kill if a task finishes or add new part
                if (done == taskList.size()) RESET_RANGES = false;


                total = 0;
                /*for (Map.Entry<Integer, Long> entry : uploadedMap.entrySet()) {
                    System.out.println(entry.getKey() + " -- " + entry.getValue());
                    total += entry.getValue();
                }*/

                for (int i = 0; i < uploadedMap.size(); i++) {

                    //System.out.println(uploadedMap.keyAt(i) + " -- " + uploadedMap.valueAt(i));
                    total += uploadedMap.valueAt(i);
                }


                if (mUploadInfo.isFirstTime())
                    publishProgress(String.valueOf((int) ((total * 100) / testFileSize)));
                else
                    publishProgress(String.valueOf(mUploadInfo.getPercDone() + (int) ((total * 100) / testFileSize)));

                //System.out.println("FFF : " + (int) ((total * 100) / testFileSize));

                //System.out.println("Perc-- " + String.valueOf((int) ((total * 100) / testFileSize)));
                if (done == taskList.size() && RESET_RANGES == false) {
                    //System.out.println("Next--" + mQueueControl.getstart() + " -- "+ mQueueControl.getend());

                    if (mQueueControl.getstart() == mQueueControl.getListSize()) {
                        //Done uploading all parts.....
                        break;
                    }

                    mQueueControl.setRanges(mQueueControl.getstart(), mQueueControl.getend());
                    System.out.println("Next--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());
                    mQueueControl.viableRange();
                    System.out.println("New Viable--" + mQueueControl.getstart() + " -- " + mQueueControl.getend());
                    RESET_RANGES = true;
                    repostRangesToExecutor(mQueueControl.getstart(), mQueueControl.getend());
                }


            }

            publishProgress("Debug");

            total = 0;
            for (int i = 0; i < uploadedMap.size(); i++) {
                total += uploadedMap.valueAt(i);
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
                    randomFileObj, 0));

            taskList.add(mFuture);
            executor.submit(mFuture);
        }

        private void repostRangesToExecutor(int startPartRange, int endPartRange) {

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

                uploadedMap.put(mMap.valueAt(0).get(i).getID(), (long) 0);

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


                postToExecutor(mMap.keyAt(0), mMap.valueAt(0).get(i), randomAccessFile);
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

            } else if (isInteger(values[0])) {
                int i = Integer.parseInt(values[0]);
                if (i > 100) i = 100;

                mProgress.setProgress(i);
                percentText.setText(i + "%");

                System.out.println("onProgressUpdate -- " + i + " %");

            } else if (values[0].compareTo("Debug") == 0) {

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

            System.out.println("In MEMORY");
            boolean FLAG_DONE = true;
            for (Map.Entry<FIleInfo, PartInfo> entry : inMemCache.entrySet()) {
                HashMap<Integer, Boolean> info = entry.getValue().getPartInfo();
                for (Map.Entry<Integer, Boolean> e : info.entrySet()) {
                    System.out.println("Cache: " + e.getKey() + " : " + e.getValue());
                    if (e.getValue() != true)
                        FLAG_DONE = false;
                }

                //if (FLAG_DONE)
                /*
                new SanityCheck(mHandler).execute(
                        "http://103.233.25.47:5252/upload" +
                                "/" + entry.getKey().getServerFileID());
                */

            }


            if (mUploadInfo.getState() == UploadState.FINISHED)
                showSnackBar("Upload Complete.");
            else if (mUploadInfo.getState() == UploadState.PAUSED) {
                mBlockingQueue.clear();
                showSnackBar("PAUSED.");
                mUploadInfo.setState(UploadState.TOSTART);
            } else if (mUploadInfo.getState() == UploadState.STARTED) {

                mUploadInfo.setState(UploadState.TOSTART);
                //mUploadInfo.setState(UploadState.FINISHED);

                ArrayList<FIleInfo> fileList = new ArrayList<>();
                for (Map.Entry<FIleInfo, PartInfo> entry : inMemCache.entrySet()) {
                    fileList.add(entry.getKey());
                }

                sanityCheckerFunc(fileList);

                /*
                Message m = new Message();
                Bundle mBundle = new Bundle();
                mBundle.putString("finished", "yes");
                m.obj = mBundle;
                mHandler.sendMessageDelayed(m, 10);
                showSnackBar("Upload Complete");
                */
            }

            System.out.println("Final State  --> " + mUploadInfo.getState());

        }
    }

    private void sanityCheckerFunc(final ArrayList<FIleInfo> fileList) {


        final ProgressDialog ringProgressDialog = ProgressDialog.show(MainActivity.this, "", "Please Wait...", true);
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


                    if (mList != null) mList.clear();
                    mList = new ArrayMap<FIleInfo, List<Part>>();


                    System.out.println("LIST SIZE: " + mList.size());

                    for (int i = 0; i < fileList.size(); i++) {
                        /*
                        Callable callable = new SanityCheckerThreadOnly(
                                "http://103.233.25.47:5252/upload" +
                                        "/" + fileList.get(i).getServerFileID(),
                                mHandler
                        );

                        ExecutorService pool = Executors.newFixedThreadPool(1);
                        Future mFuture = pool.submit(callable);


                        mList.put(fileList.get(i), (ArrayList<Part>) mFuture.get());
                        */

                        Runnable mRunnable = new SanityCheckerThreadOnly(fileList.get(i),
                                "http://103.233.25.47:5252/upload" +
                                        "/" + fileList.get(i).getServerFileID(), mHandler, 0
                        );

                        Thread mThread = new Thread(mRunnable);
                        mThread.start();
                        System.out.println("Waiting for Sanity Checker...");
                        mThread.join();

                        System.out.println("GOT- " + (i + 1));

                    }

                    System.out.println("Finally mList == " + mList.size());

                    for (ArrayMap.Entry<FIleInfo, List<Part>> entry : mList.entrySet()) {
                        for (int i = 0; i < entry.getValue().size(); i++) {
                            System.out.println("start--" + entry.getValue().get(i).getStart());
                            System.out.println("end--" + entry.getValue().get(i).getEnd());
                        }
                    }


                } catch (Exception e) {
                    System.out.println("exception : " + e.toString());
                    e.printStackTrace();
                }


                System.out.println("Sending Message to Handler");
                Message m = new Message();
                Bundle mBundle = new Bundle();

                mBundle.putString("sanitycheckreturn", "yes");
                m.obj = mBundle;
                mHandler.sendMessageDelayed(m, 100);

                ringProgressDialog.dismiss();

            }
        }).start();


    }

}
