package com.example.newgrid;

import com.example.newgrid.model.ItemBean;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BitmapManager {
    
    private static LruCache<String,Bitmap> cache;
    
    private static ExecutorService pool;
    
    private static Map<ImageView,String> imageViews;//弱引用
    
    private Bitmap defaultBmp;
    
    private static BitmapManager bitmapManager;
    
    private ImageLoadCompleteListener mImageLoadCompleteListener;
    
    static{
      //获取应用程序最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory/8;
        //设置图片缓存大小为程序最大可用内存的1/8;
        cache = new LruCache<String,Bitmap>(cacheSize);
        pool = Executors.newFixedThreadPool(5);//固定线程池
        
        imageViews = Collections.synchronizedMap(new WeakHashMap<ImageView,String>());
    }
    
    public static BitmapManager getBitMapInstance(){
        if(bitmapManager ==null)
            bitmapManager = new BitmapManager();
        return bitmapManager;
    }
    private BitmapManager(){
        
    }
    
    private BitmapManager(Bitmap def){
        this.defaultBmp = def;
    }
    /**
     * 设置默认图片
     * @param bmp
     */
    public void setDefaultBmp(Bitmap bmp){
        defaultBmp = bmp;
    }
    
    public void loadBitmap(String url,ImageView imageView,Bitmap defaultBmp){
        loadBitmap(url,imageView,defaultBmp,80);
    }
    
    public void loadBitmap(String url,ImageView imageView,Bitmap defaultBmp,int width){
        imageViews.put(imageView, url);
        Bitmap bitmap = getBitmapFromCache(url);
        
        if(bitmap !=null){
            
            setImageData(imageView,bitmap,width);
        }else{
            //加载SD卡中得图片缓存
            String filename = FileUtils.getFileName(url);
            String filepath = imageView.getContext().getFilesDir()+File.separator+filename;
            File file = new File(filepath);
            if(file.exists()){
                //显示图片
                Bitmap bmp = decodeSampledBitmapFromResource(filepath,width);
                setImageData(imageView,bmp,width);
            }else{
                imageView.setImageBitmap(defaultBmp);
                queueJob(url,imageView,width);
            }
        }
    }
    
    public void loadBitmap(Context context,ItemBean bean,Bitmap defaultBmp,int width,int position){
        Bitmap bitmap = getBitmapFromCache(bean.image_url);
        if(bitmap !=null){
            mImageLoadCompleteListener.onLoadComplete(bitmap, bean,position);
        }else{
          //加载SD卡中得图片缓存
            String filename = FileUtils.getFileName(bean.image_url);
            String filepath = context.getFilesDir()+File.separator+filename;
            File file = new File(filepath);
            if(file.exists()){
                //显示图片
                Bitmap bmp = decodeSampledBitmapFromResource(filepath,width);
                mImageLoadCompleteListener.onLoadComplete(bmp, bean,position);
            }else{
                
                queueJob(context,bean,width,position);
            }
        }
        
    }
    
    public void setImageLoadCompleteListener(ImageLoadCompleteListener listener){
        this.mImageLoadCompleteListener = listener;
    }
    
    public interface ImageLoadCompleteListener{
        void onLoadComplete(Bitmap bitmap,ItemBean bean,int position);
    }
    
    private void setImageData(ImageView imageView,Bitmap bitmap,int width){
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,bitmap.getHeight()*width/bitmap.getWidth());
        imageView.setLayoutParams(params);
        imageView.setScaleType(ScaleType.FIT_XY);
        imageView.setPadding(5, 5,5,5);
        imageView.setImageBitmap(bitmap);
    }
    
    public void queueJob(final String url,final ImageView imageView,final int width){
        final Handler handler = new Handler(){
          public void handleMessage(Message msg){
              String tag = imageViews.get(imageView);
              if(tag!=null && tag.equals(url)){
                  if(msg.obj!=null){
                      Bitmap bitmap = (Bitmap)msg.obj;
                      setImageData(imageView,bitmap,width);
                      try{
                          saveImage(imageView.getContext(),FileUtils.getFileName(url),(Bitmap)msg.obj,100);
                      }catch(Exception e){
                          e.printStackTrace();
                      }
                  }
              }
          }
        };
        pool.execute(new Runnable(){

            @Override
            public void run() {
                // TODO Auto-generated method stub
                Message message  = Message.obtain();
                message.obj = downloadBitmap(url,width);
                handler.sendMessage(message);
            }
            
        });
    }
    
    public void queueJob(final Context context,final ItemBean bean,final int width,final int position){
        final Handler handler = new Handler(){
            public void handleMessage(Message msg){
                if(msg.obj!=null){
                    Bitmap bitmap = (Bitmap)msg.obj;
                    mImageLoadCompleteListener.onLoadComplete(bitmap, bean,position);
                    try{
                        saveImage(context,FileUtils.getFileName(bean.image_url),(Bitmap)msg.obj,100);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        };
        pool.execute(new Runnable(){

            @Override
            public void run() {
                // TODO Auto-generated method stub
                Message message = Message.obtain();
                message.obj = downloadBitmap(bean.image_url,width);
                handler.sendMessage(message);
            }
            
        });
    }
    
    private  Bitmap downloadBitmap(String url,int width){
        Bitmap bitmap = null;
        try{
           bitmap= ApiClient.getBitmap(url, width);
        }catch(Exception e){
            e.printStackTrace();
        }
        addBitmapToMemoryCache(url,bitmap);
        return bitmap;
    }
    
    public static void saveImage(Context context,String fileName,Bitmap bitmap,int quality) throws IOException{
        if(bitmap ==null){
            return;
        }
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, quality, stream);
        byte[] bytes = stream.toByteArray();
        fos.write(bytes);
        fos.close();
        
    }
    public static int calculateInSampleSize(BitmapFactory.Options options,
            int reqWidth) {
        // 源图片的宽度
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (width > reqWidth) {
            // 计算出实际宽度和目标宽度的比率
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = widthRatio;
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromResource(String pathName,
            int reqWidth) {
        // 第一次解析将inJustDecodeBounds设置为true，来获取图片大小
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);
        // 调用上面定义的方法计算inSampleSize值
        options.inSampleSize = calculateInSampleSize(options, reqWidth);
        // 使用获取到的inSampleSize值再次解析图片
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(pathName, options);
    }
    
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromCache(key) == null) {
            cache.put(key, bitmap);
        }
    }
    public Bitmap getBitmapFromCache(String url){
        return cache.get(url);
    }

}
