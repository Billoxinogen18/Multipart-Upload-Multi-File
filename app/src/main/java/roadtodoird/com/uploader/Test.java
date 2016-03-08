package roadtodoird.com.uploader;

import android.util.Base64;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Created by misterj on 22/1/16.
 */
public class Test {


    public static void main(String args[]) throws IOException {
        RandomAccessFile rafile = new RandomAccessFile("/home/misterj/Desktop/test.png", "r");


        rafile.seek(0);
        int pos = 0;

        byte[] buffer = new byte[8];
        int count = 0;


        rafile.seek(0);
        rafile.read(buffer);
        System.out.println("Data:" + new String(buffer));


        rafile.seek(11);
        rafile.read(buffer);
        System.out.println("Data:" + new String(buffer));
/*
        while ((count = rafile.read(buffer)) >= 0) {
            pos+=count;
            rafile.seek(pos);
            System.out.print(new String(buffer));
            //String s = Base64.encodeToString(buffer,
              //      Base64.NO_WRAP);
            //System.out.print(s);
        }

*/
        System.out.print("POS: " + String.valueOf(pos));


        rafile.close();
    }
}

