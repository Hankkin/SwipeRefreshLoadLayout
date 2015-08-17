package com.jiechic.library.swiperefreshloadlayout.sample;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLoadLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import butterknife.Bind;
import butterknife.ButterKnife;


public class MainActivity extends AppCompatActivity {

    @Bind(R.id.recyclerView)
    RecyclerView recyclerView;
    @Bind(R.id.swipeRefreshLoadLayout)
    SwipeRefreshLoadLayout swipeRefreshLoadLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(new Adapter());
        swipeRefreshLoadLayout.setOnRefreshListener(() -> swipeRefreshLoadLayout.postDelayed(() -> swipeRefreshLoadLayout.setRefreshing(false), 2000));
        swipeRefreshLoadLayout.setOnLoadListener(()->swipeRefreshLoadLayout.postDelayed(()->swipeRefreshLoadLayout.setLoading(false), 2000));

    }


    class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder>{

        String[] list=new String[]{"1","2","3","4","5","6","7","8","9","0","1","2","3","4","5","6","7","8","9","0","1","2","3","4","5","6","7","8","9","0","1","2","3","4","5","6","7","8","9","0"};

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(ViewHolder.LAYOUT_ID,parent,false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textView.setText(list[position]);
        }

        @Override
        public int getItemCount() {
            return list.length;
        }

        class ViewHolder extends RecyclerView.ViewHolder{
            @Bind(R.id.textview)
            TextView textView;
            public static final int LAYOUT_ID = R.layout.simple_text;
            public ViewHolder(View itemView) {
                super(itemView);
                ButterKnife.bind(this,itemView);
            }
        }
    }
}
