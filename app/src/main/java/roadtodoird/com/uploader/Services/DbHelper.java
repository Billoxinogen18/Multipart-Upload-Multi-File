package roadtodoird.com.uploader.Services;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.PartInfo;

/**
 * Created by misterj on 19/1/16.
 */
public class DbHelper extends SQLiteOpenHelper {
    public final static String DB_NAME = "Upload Info";
    public static final int DB_VERSION = 1;
    public final static String TABLE_NAME = "FileInfo";

    public final static String KEY_ID = "id";
    public final static String FILE_NAME = "FileName";
    public final static String PART_INFO = "part";

    public DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        String CREATE_FILE_TABLE = "CREATE TABLE " + TABLE_NAME + "("
                + KEY_ID + " INTEGER PRIMARY KEY," + FILE_NAME + " TEXT,"
                + PART_INFO + " TEXT" + ")";

        db.execSQL(CREATE_FILE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void addFile(FIleInfo mFileInfo, PartInfo mPartInfo) {
        SQLiteDatabase db = this.getWritableDatabase();

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
        db.close(); // Closing database connection
    }

    public PartInfo getPartInfoFromDB(String fileName) {
        SQLiteDatabase db = this.getReadableDatabase();

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


    public String getPartString(String fileName) {

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_NAME, new String[]{KEY_ID,
                        FILE_NAME, PART_INFO}, FILE_NAME + "=?",
                new String[]{fileName}, null, null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();
            return cursor.getString(2);
        }

        return "";
    }

    public int updatePartInfo(FIleInfo mFileInfo, Integer PartID) {
        SQLiteDatabase db = this.getWritableDatabase();

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

    @Override
    public synchronized void close() {
        super.close();
    }
}
