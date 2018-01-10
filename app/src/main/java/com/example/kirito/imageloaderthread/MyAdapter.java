package com.example.kirito.imageloaderthread;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.io.File;
import java.util.List;

/**
 * Created by kirito on 2018.01.09.
 */

public class MyAdapter extends BaseAdapter {
    private Context mContext;
    private List<String> fileNames;
    private String mDirpath;
    private LayoutInflater inflater;
    private ImageLoader loader;

    public MyAdapter(Context mContext, List<String> fileNames, String path) {
        this.mContext = mContext;
        this.fileNames = fileNames;
        this.mDirpath = path;

        inflater = LayoutInflater.from(mContext);
        loader = ImageLoader.getInstance();
    }

    @Override
    public int getCount() {
        return fileNames.size();
    }

    @Override
    public Object getItem(int i) {
        return fileNames.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        viewHolder holder = null;
        if (view == null){
            holder = new viewHolder();
            view = inflater.inflate(R.layout.layout_item,viewGroup,false);
            holder.iv = view.findViewById(R.id.iv_item);

            view.setTag(holder);
        }else {
            holder = (viewHolder) view.getTag();
        }
        holder.iv.setImageResource(R.drawable.ic_launcher_background);
        loader.loadImages(mDirpath + "/" + fileNames.get(i),holder.iv);//图片的路径=文件夹所在路径+图片名
        return view;
    }

    private class viewHolder{
        ImageView iv;
    }
}
