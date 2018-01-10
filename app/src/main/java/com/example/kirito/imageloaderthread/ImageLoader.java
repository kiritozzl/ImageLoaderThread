package com.example.kirito.imageloaderthread;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by kirito on 2018.01.09.
 */

public class ImageLoader {
    private static ImageLoader mInstance;

    //引入一个值为1的信号量，防止mPoolThreadHander未初始化完成
    private Semaphore mSemaphore = new Semaphore(1);

    //引入一个值为1的信号量，由于线程池内部也有一个阻塞线程，防止加入任务的速度过快，使LIFO效果不明显
    private Semaphore mPoolSemaphore;

    //轮询的线程
    private Thread mPoolThread;
    private Handler mPoolHandler;

    //运行在UI线程的handler，用于给ImageView设置图片
    private Handler mHandler;

    //线程池
    private ExecutorService mThreadPool;

    //队列的调度方式
    private Type mType = Type.FIFO;

    //任务队列
    private LinkedList<Runnable> mTasks;

    //图片缓存的核心类
    private LruCache<String,Bitmap> mLruCache;

    public enum Type{
        FIFO,LIFO;
    }

    private ImageLoader(int threadCount, Type type){
        init(threadCount,type);
    }

    public static ImageLoader getInstance(){
        if (mInstance == null){
            synchronized (ImageLoader.class){
                if (mInstance == null){
                    mInstance = new ImageLoader(1,Type.LIFO);
                }
            }
        }
        return mInstance;
    }

    private synchronized Runnable getTask(){
        if (mType == Type.FIFO){
            return mTasks.removeFirst();
        }else if (mType == Type.LIFO){
            return mTasks.removeLast();
        }else {
            return null;
        }
    }

    private void init(int threadCount, Type type) {
        mPoolThread = new Thread(){
            @Override
            public void run() {
                super.run();
                try {
                    mSemaphore.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Looper.prepare();
                mPoolHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {
                        mThreadPool.execute(getTask());
                        try {
                            mPoolSemaphore.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                mSemaphore.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;
        mLruCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mPoolSemaphore = new Semaphore(threadCount);
        mTasks = new LinkedList<>();
        mType = type == null ? Type.LIFO : type;
    }

    private synchronized void addTasks(Runnable runnable){
        if (mPoolHandler == null){
            try {
                mSemaphore.acquire();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mTasks.add(runnable);
        mPoolHandler.sendEmptyMessage(0x001);
    }

    public void loadImages(final String path, final ImageView iv){
        iv.setTag(path);

        // UI线程
        if (mHandler == null){
            mHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {
                    imgBeanHolder holder = (imgBeanHolder) msg.obj;
                    ImageView imageView = holder.iv;
                    Bitmap bitmap = holder.bitmap;
                    String path = holder.path;
                    if (path.equals(imageView.getTag().toString())){
                        imageView.setImageBitmap(bitmap);
                    }
                }
            };
        }

        Bitmap bp = getBitmapFromLruCache(path);
        if (bp != null){
            imgBeanHolder holder = new imgBeanHolder();
            holder.iv = iv;
            holder.bitmap = bp;
            holder.path = path;

            Message message = Message.obtain();
            message.obj = holder;
            mHandler.sendMessage(message);
        }else {
            addTasks(new Runnable() {
                @Override
                public void run() {
                    ImageSize imageSize = getImageViewWidth(iv);

                    int reqWidth = imageSize.width;
                    int reqHeight = imageSize.height;

                    Bitmap bm = decodeSampledBitmapFromResource(path, reqWidth,
                            reqHeight);
                    addBitmapToLruCache(path,bm);

                    imgBeanHolder holder = new imgBeanHolder();
                    holder.bitmap = getBitmapFromLruCache(path);
                    holder.iv = iv;
                    holder.path = path;

                    Message message = Message.obtain();
                    message.obj = holder;
                    mHandler.sendMessage(message);

                    mPoolSemaphore.release();
                }
            });
        }
    }

    private void addBitmapToLruCache(String path, Bitmap bm) {
        if (getBitmapFromLruCache(path) == null){
            if (bm != null){
                mLruCache.put(path,bm);
            }
        }
    }

    private Bitmap decodeSampledBitmapFromResource(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path,options);

        options.inSampleSize = calculateInSampleSize(options,reqHeight,reqWidth);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path,options);

        return bitmap;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqHeight, int reqWidth) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        if (width > reqWidth && height > reqHeight){
            int widthRatio = Math.round((float) width / reqWidth);
            int heightRatio = Math.round((float) height / reqHeight);

            inSampleSize = Math.max(widthRatio,heightRatio);
        }
        return inSampleSize;
    }

    private ImageSize getImageViewWidth(ImageView imageView){
        ImageSize imageSize = new ImageSize();
        DisplayMetrics metrics = imageView.getContext().getResources().getDisplayMetrics();
        ViewGroup.LayoutParams params = imageView.getLayoutParams();

        int width = params.width == ViewGroup.LayoutParams.WRAP_CONTENT ? 0: imageView.getWidth();
        if (width <= 0){
            width = params.width;
        }
        if (width <= 0){
            width = getImageViewFieldValue(imageView, "mMaxWidth");
        }
        if (width <= 0){
            width = metrics.widthPixels;
        }

        int height = params.height == ViewGroup.LayoutParams.WRAP_CONTENT ? 0: imageView.getHeight();
        if (height <= 0){
            height = params.height;
        }
        if (height <= 0){
            height = getImageViewFieldValue(imageView,"mMaxHeight");
        }
        if (height <= 0){
            height = metrics.widthPixels;
        }

        imageSize.height = height;
        imageSize.width = width;

        return imageSize;
    }

    //反射获得ImageView设置的最大宽度和高度
    private int getImageViewFieldValue(Object object, String fieldName) {
        int values = 0;
        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            int fieldValue = (int) field.get(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE){
                values = fieldValue;
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return values;
    }

    private Bitmap getBitmapFromLruCache(String path){
        return mLruCache.get(path);
    }

    private class imgBeanHolder{
        String path;
        ImageView iv;
        Bitmap bitmap;
    }

    private class ImageSize{
        int width,height;
    }
}
