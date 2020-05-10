package com.example.pma.adapter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.pma.MainActivity;
import com.example.pma.R;
import com.example.pma.RouteDetailActivity;
import com.example.pma.RouteDetailFragment;
import com.example.pma.model.Route;

import java.util.List;


public class SimpleRouteRecyclerViewAdapter extends RecyclerView.Adapter<SimpleRouteRecyclerViewAdapter.ViewHolder> {

    private final MainActivity mParentActivity;
    private final List<Route> mValues;
    private final boolean mTwoPane;

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Route route = (Route) view.getTag();
            if (mTwoPane) {
                Bundle arguments = new Bundle();
                arguments.putString(RouteDetailFragment.ARG_ROUTE_ID, route.id);
                RouteDetailFragment fragment = new RouteDetailFragment();
                fragment.setArguments(arguments);
                mParentActivity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.route_detail_container, fragment)
                        .commit();
            } else {
                Context context = view.getContext();
                Intent intent = new Intent(context, RouteDetailActivity.class);
                intent.putExtra(RouteDetailFragment.ARG_ROUTE_ID, route.id);

                context.startActivity(intent);
            }
        }
    };

    public SimpleRouteRecyclerViewAdapter(MainActivity parent, List<Route> routes, boolean twoPane) {
        mValues = routes;
        mParentActivity = parent;
        mTwoPane = twoPane;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.route_list_content, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mIdView.setText(mValues.get(position).id);
        holder.mContentView.setText(mValues.get(position).content);

        holder.itemView.setTag(mValues.get(position));
        holder.itemView.setOnClickListener(mOnClickListener);
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final TextView mIdView;
        final TextView mContentView;

        ViewHolder(View view) {
            super(view);
            mIdView = (TextView) view.findViewById(R.id.id_text);
            mContentView = (TextView) view.findViewById(R.id.name);
        }
    }
}
