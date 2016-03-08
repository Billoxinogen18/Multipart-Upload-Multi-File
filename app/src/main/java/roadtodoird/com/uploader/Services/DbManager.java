package roadtodoird.com.uploader.Services;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.PartInfo;

/**
 * Created by misterj on 25/1/16.
 */
public class DbManager {

    private static DbManager instance;
    private static SQLiteOpenHelper mDbHelper;
    private SQLiteDatabase mDatabase;

    private AtomicInteger mOpenCounter = new AtomicInteger();

    public final static String TABLE_NAME = "FileInfo";
    public final static String KEY_ID = "id";
    public final static String FILE_NAME = "FileName";
    public final static String PART_INFO = "part";


    public static synchronized void initInstance(SQLiteOpenHelper helper) {
        if (instance == null) {
            instance = new DbManager();
            mDbHelper = helper;
        }
    }

    public static synchronized DbManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(DbManager.class.getSimpleName() +
                    " is not initialized, call initInstance(..) method first.");
        }
        return instance;
    }

    public synchronized SQLiteDatabase openDatabase() {
        if (mOpenCounter.incrementAndGet() == 1) {
            // Opening new database
            mDatabase = mDbHelper.getWritableDatabase();
        }
        return mDatabase;
    }

    public synchronized void closeDatabase() {
        if (mOpenCounter.decrementAndGet() == 0) {
            // Closing database
            mDatabase.close();

        }

    }

    public void addFile(FIleInfo mFileInfo, PartInfo mPartInfo,SQLiteDatabase db) {


        ContentValues values = new ContentValues();
        values.put(FILE_NAME, mFileInfo.getFileName());
        values.put(PART_INFO, new Gson().toJson(mPartInfo.getPartInfo()));

        /*
        System.out.println(new Gson().toJson(mPartInfo.getPartInfo()));
        System.out.println("");
        for (Map.Entry<Integer, Boolean> entry : mPartInfo.getPartInfo().entrySet()) {
            System.out.println(entry.getKey()+" : "+entry.getValue());
        }
        */
        db.insert(TABLE_NAME, null, values);
    }

    public PartInfo getPartInfoFromDB(String fileName,SQLiteDatabase db) {


        Cursor cursor = db.query(TABLE_NAME, new String[]{KEY_ID,
                        FILE_NAME, PART_INFO}, FILE_NAME + "=?",
                new String[]{fileName}, null, null, null, null);

        if (cursor != null)
            cursor.moveToFirst();


        PartInfo mPart = new PartInfo();
        Type type = new TypeToken<HashMap<Integer, Boolean>>() {
        }.getType();
        HashMap<Integer, Boolean> tempMap = new Gson().fromJson(cursor.getString(2), type);

        mPart.setPartInfo(tempMap);

        return mPart;
    }


    public int updatePartInfo(FIleInfo mFileInfo, Integer PartID,SQLiteDatabase db) {


        //Search for file
        Cursor cursor = db.query(TABLE_NAME, new String[]{KEY_ID,
                        FILE_NAME, PART_INFO}, FILE_NAME + "=?",
                new String[]{mFileInfo.getFileName()}, null, null, null, null);

        if (cursor != null)
            cursor.moveToFirst();


        Type type = new TypeToken<HashMap<Integer, Boolean>>() {
        }.getType();
        HashMap<Integer, Boolean> tempMap = new Gson().fromJson(cursor.getString(2), type);

        tempMap.put(PartID, true);

        //

        ContentValues values = new ContentValues();
        values.put(FILE_NAME, mFileInfo.getFileName());
        values.put(PART_INFO, new Gson().toJson(tempMap));

        return db.update(TABLE_NAME, values, FILE_NAME + " = ?",
                new String[]{mFileInfo.getFileName()});
    }

}
