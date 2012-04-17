/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.app;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.IccCard;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.telephony.TelephonyManager;
import java.util.ArrayList;

/**
 * Search dialog. This is controlled by the 
 * SearchManager and runs in the current foreground process.
 * 
 * @hide
 */
public class SimChooserDialog extends AlertDialog implements OnItemClickListener {
    public interface OnSimPickedListener {
	void onSimPicked(int phoneId) ;
    }
    
    private ListView mList;
    private ArrayAdapter<String> mAdapter;
    private TelephonyManager mTelephonyManager;
    private OnSimPickedListener mListener;
    private ArrayList<Integer> mPhoneIds=new ArrayList<Integer>();
    private ArrayList<String> mPhoneNames=new ArrayList<String>();
    
    public SimChooserDialog(Context context) {
	super(context);
	setTitle(com.android.internal.R.string.sim_chooser_title);
	mList=new ListView(context);
	mTelephonyManager = (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
	int phoneCount=mTelephonyManager.getPhoneCount();
	
	String Unknown = getContext().getResources().getString(com.android.internal.R.string.unknownName);
	
	for (int i=0;i<phoneCount;++i) {
	    TelephonyManager tm=(TelephonyManager)getContext().getSystemService(
		PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i));
	    if (tm.hasIccCard()
		&& tm.getSimState()==TelephonyManager.SIM_STATE_READY) {
		String simName= "SIM "+(i+1)+" "+(tm.getNetworkOperatorName() != null ? tm.getNetworkOperatorName() : Unknown);
		mPhoneIds.add(i);
		mPhoneNames.add(simName);
	    } 
	}

	if (mPhoneIds.isEmpty()) {
	    setMessage(getContext().getResources().getText(com.android.internal.R.string.lockscreen_missing_sim_message_short));
	    if (mListener!=null) {
		mListener.onSimPicked(-1);
	    } 
	} else {
	    mAdapter=new ArrayAdapter<String>(getContext(),com.android.internal.R.layout.simple_list_item_1,mPhoneNames);
	    mList.setAdapter(mAdapter);
	    mList.setOnItemClickListener(this);
	    setView(mList);
	}
    }

    public void setListener(OnSimPickedListener listener) {
	mListener=listener;
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	this.dismiss();
	if (mListener!=null) {
	    mListener.onSimPicked(mPhoneIds.get(position));
	} 
    }
}
