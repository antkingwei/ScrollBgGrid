package com.example.newgrid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class ApiClient {
    
    public static Bitmap getBitmap(String url,int width)throws MalformedURLException,
    IOException{
        Bitmap bmp = null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        InputStream stream = new URL(url).openStream();
        
        byte[] bytes = getBytes(stream);
        
        opts.inJustDecodeBounds = true;
        //计算图片的原大小
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length,opts);
        
        opts.inSampleSize = computeInitialSampleSize(opts,-1,width);
        
        opts.inJustDecodeBounds = false;
        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        return bmp;
    }
   
    private static int computeInitialSampleSize(BitmapFactory.Options options,int minSideLength,int maxNumOfPixels){
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (width > maxNumOfPixels) {
            // 计算出实际宽度和目标宽度的比率
            final int widthRatio = Math.round((float) width / (float) maxNumOfPixels);
            inSampleSize = widthRatio;
        }
        return inSampleSize;
        
    }
    private static byte[] getBytes(InputStream is){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        byte[] b = new byte[2048];
        int len =0;
        try{
            while((len = is.read(b, 0, 2048))!=-1){
                baos.write(b, 0, len);
                baos.flush();
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        byte[] bytes = baos.toByteArray();
        
        return bytes;
    }

}
