
package android.sim;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SimListAdapter extends BaseAdapter {

    public final class ViewHolder {

        public ViewGroup color;

        public TextView name;

        public Button viewBtn;
    }

    private LayoutInflater mInflater;

    private Sim[] mData;

    private OnClickListener mListener;

    private int mLayoutId;

    public SimListAdapter(Context context, Sim[] data, OnClickListener listener, int layoutId) {
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.mListener = listener;
        this.mLayoutId = layoutId;
    }

    public int getCount() {

        return mData.length;
    }

    public Object getItem(int position) {

        return mData[position];
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        
        if (convertView == null) {

            holder = new ViewHolder();

            convertView = mInflater.inflate(mLayoutId, null);
            holder.color = (RelativeLayout) convertView.findViewById(com.android.internal.R.id.sim_color);
            holder.name = (TextView) convertView.findViewById(com.android.internal.R.id.sim_name);
            holder.viewBtn = (Button) convertView.findViewById(com.android.internal.R.id.btn);
            convertView.setTag(holder);

        } else {

            holder = (ViewHolder) convertView.getTag();
        }
        Sim sim = (Sim) getItem(position);
        if (sim == null) {
            return convertView;
        }
        if (sim.getPhoneId() < 0) {
            holder.color.setVisibility(View.GONE);
        } else {
            holder.color.setVisibility(View.VISIBLE);
            holder.color.setBackgroundResource(SimManager.COLORS_IMAGES[sim.getColorIndex()]);
        }
        holder.name.setText(mData[position].getName());
        if (holder.viewBtn != null && mListener != null)
            holder.viewBtn.setOnClickListener(mListener);

        return convertView;
    }

}
