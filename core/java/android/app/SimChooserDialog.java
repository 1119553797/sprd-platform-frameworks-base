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
public class SimChooserDialog extends Dialog implements OnItemClickListener {
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
    }

    public void setListener(OnSimPickedListener listener) {
	mListener=listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	setContentView(com.android.internal.R.layout.list_content);
	setTitle("choose sim card");
	mList = (ListView)findViewById(com.android.internal.R.id.list);
	mTelephonyManager = (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
	int phoneCount=mTelephonyManager.getPhoneCount();
	
	for (int i=0;i<phoneCount;++i) {
	    TelephonyManager tm=(TelephonyManager)getContext().getSystemService(
		PhoneFactory.getServiceName(Context.TELEPHONY_SERVICE,i));
	    if (tm.hasIccCard()) {
		String simName= "SIM "+(i+1)+" "+tm.getNetworkOperatorName();
		mPhoneIds.add(i);
		mPhoneNames.add(simName);
	    } 
	}
	mAdapter=new ArrayAdapter<String>(getContext(),com.android.internal.R.layout.simple_list_item_1,mPhoneNames);
	mList.setAdapter(mAdapter);
	mList.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	this.dismiss();
	if (mListener!=null) {
	    mListener.onSimPicked(mPhoneIds.get(position));
	} 
    }
}
