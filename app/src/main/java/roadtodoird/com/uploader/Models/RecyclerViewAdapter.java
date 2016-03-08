package roadtodoird.com.uploader.Models;

import android.content.Context;
import android.net.Uri;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import roadtodoird.com.uploader.MultiFIleActivity;
import roadtodoird.com.uploader.R;

/**
 * Created by misterj on 23/2/16.
 */
public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> implements View.OnLongClickListener {

    private ArrayList<Uri> mFileUri = new ArrayList<>();
    private OnItemLongClickListener onItemClickListener;
    private OnFabClickListener onFabClickListener;

    private ArrayList<Integer> mProgressValues;

    private Context mContext;

    public RecyclerViewAdapter(Context c, ArrayList<Uri> mList, ArrayList<Integer> mProg) {
        this.mFileUri = mList;
        this.mContext = c;
        this.mProgressValues = mProg;
        System.out.println("Adapter length = " + this.mProgressValues.size());
    }


    public void setOnItemClickListener(OnItemLongClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setOnFabClickListener(OnFabClickListener what) {
        this.onFabClickListener = what;
    }


    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item, parent, false);

        itemView.setOnLongClickListener(this);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final MyViewHolder holder, final int position) {

        final String fileName = getFileNameFromURI(mFileUri.get(position).toString());
        holder.title.setText(fileName);
        holder.itemView.setTag(fileName);
        holder.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabClickListener.onFabClick(holder.itemView, fileName, position);
            }
        });

        //holder.fab.setAnimation(AnimationUtils.loadAnimation(mContext, R.anim.fab_slide_in_from_right));

        holder.progressBar.setProgress(mProgressValues.get(position));
        holder.perc.setText(mProgressValues.get(position) + "%");
    }

    @Override
    public int getItemCount() {
        return this.mFileUri.size();
    }


    private String getFileNameFromURI(String uri) {
        int index = uri.lastIndexOf('/');
        int end = uri.lastIndexOf('.');
        return uri.substring(index + 1, end);
    }


    public interface OnItemLongClickListener {

        void onItemClick(View view, String name);

    }

    public interface OnFabClickListener {

        void onFabClick(View view, String s, int pos);
    }

    @Override
    public boolean onLongClick(View v) {
        onItemClickListener.onItemClick(v, (String) v.getTag());
        return true;
    }


    public class MyViewHolder extends RecyclerView.ViewHolder {

        TextView title, perc;
        ProgressBar progressBar;
        FloatingActionButton fab;


        public MyViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.title);
            perc = (TextView) itemView.findViewById(R.id.perc);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progressBar);
            fab = (FloatingActionButton) itemView.findViewById(R.id.fab);
        }
    }


}
