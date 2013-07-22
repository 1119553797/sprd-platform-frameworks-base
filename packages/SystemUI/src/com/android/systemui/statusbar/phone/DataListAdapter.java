
package com.android.systemui.statusbar.phone;

import com.android.internal.telephony.PhoneFactory;

import android.provider.Settings.System;
import android.sim.Sim;
import android.sim.SimManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.net.ConnectivityManager;
import com.android.systemui.R;

public class DataListAdapter extends BaseAdapter {

    public final class ViewHolder {

        public RelativeLayout colorImage;

        public TextView name;

        public RadioButton viewBtn;
    }

    private LayoutInflater mInflater;
    private Sim[] mData;
    private Context mContext;
    private OnClickListener mListener;
    private int mLayoutId;
    private int mode = -1;
    boolean isCloseData = false;
    Sim simData[];

    public DataListAdapter(Context context, final Sim[] data, OnClickListener listener,
            int layoutId, boolean isCloseData) {
        this.mContext = context;
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.mListener = listener;
        this.mLayoutId = layoutId;
        this.isCloseData = isCloseData;
    }

    public int getCount() {

        return mData.length;
    }

    public Object getItem(int position) {

        return mData[position];
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int values) {
        mode = values;
    }

    public long getItemId(int position) {
        return position;
    }

    private void initSim() {
//20130718 BUG 189145 data connection icon defect START
        ConnectivityManager mConnManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean mDataDefaultNetworkOn = mConnManager.getMobileDataEnabledByPhoneId(TelephonyManager
                .getDefaultDataPhoneId(mContext));
        if(!mDataDefaultNetworkOn){
//20130718 BUG 189145 data connection icon defect END
            isCloseData = true;
        }
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        initSim();
        ViewHolder holder = null;
//20130718 BUG 189145 data connection icon defect START
        Sim sim = (Sim) mData[position];
//20130718 BUG 189145 data connection icon defect END
        int val = -1;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(mLayoutId, null);
            holder.colorImage = (RelativeLayout) convertView
                    .findViewById(com.android.internal.R.id.sim_color);
            holder.name = (TextView) convertView.findViewById(com.android.internal.R.id.sim_name);
            holder.viewBtn = (RadioButton) convertView.findViewById(com.android.internal.R.id.btn);
            if (holder.viewBtn != null)
                holder.viewBtn.setId(position);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        if (sim == null) {
            return convertView;
        }
        boolean isPhoneEnabled = System.getInt(mContext.getContentResolver(),
                PhoneFactory.getSetting(System.SIM_STANDBY, mData[position].getPhoneId()), 1) == 1;
        if (!isPhoneEnabled) {
            holder.name.setTextColor(Color.GRAY);
            if (holder.viewBtn != null) {
                holder.viewBtn.setEnabled(false);
            }
        } else {
            holder.name.setTextColor(Color.BLACK);
            if (holder.viewBtn != null) {
                holder.viewBtn.setEnabled(true);
            }
        }
        if (sim.getPhoneId() == -1) {
            holder.colorImage.setVisibility(View.GONE);
        } else {
            holder.colorImage.setVisibility(View.VISIBLE);
            holder.colorImage.setBackgroundResource(SimManager.COLORS_IMAGES[sim.getColorIndex()]);
        }
        holder.name.setText(mData[position].getName());
        if (holder.viewBtn != null && mListener != null) {
            val = TelephonyManager.getDefaultDataPhoneId(mContext);
            if (isCloseData && sim.getPhoneId() == -1) {
                holder.viewBtn.setChecked(true);
                isCloseData = false;
            } else {
                if (!isCloseData && sim.getPhoneId() == val) {
                    holder.viewBtn.setChecked(true);
                } else {
                    holder.viewBtn.setChecked(false);
                }
            }

            holder.viewBtn.setOnClickListener(mListener);
        }
        return convertView;
    }
}
