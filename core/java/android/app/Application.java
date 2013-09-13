/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.util.ArrayList;
import android.content.ComponentCallbacks;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.util.Slog;

/**
 * Base class for those who need to maintain global application state. You can
 * provide your own implementation by specifying its name in your
 * AndroidManifest.xml's &lt;application&gt; tag, which will cause that class
 * to be instantiated for you when the process for your application/package is
 * created.
 * 
 * <p class="note">There is normally no need to subclass Application.  In
 * most situation, static singletons can provide the same functionality in a
 * more modular way.  If your singleton needs a global context (for example
 * to register broadcast receivers), the function to retrieve it can be
 * given a {@link android.content.Context} which internally uses
 * {@link android.content.Context#getApplicationContext() Context.getApplicationContext()}
 * when first constructing the singleton.</p>
 */
public class Application extends ContextWrapper implements ComponentCallbacks2 {

    private ArrayList<ComponentCallbacks> mComponentCallbacks =
            new ArrayList<ComponentCallbacks>();
    public Application() {
        super(null);
    }

    /**
     * Called when the application is starting, before any other application
     * objects have been created.  Implementations should be as quick as
     * possible (for example using lazy initialization of state) since the time
     * spent in this function directly impacts the performance of starting the
     * first activity, service, or receiver in a process.
     * If you override this method, be sure to call super.onCreate().
     */
    public void onCreate() {
    }

    /**
     * This method is for use in emulated process environments.  It will
     * never be called on a production Android device, where processes are
     * removed by simply killing them; no user code (including this callback)
     * is executed when doing so.
     */
    public void onTerminate() {
    }
    
    public void onConfigurationChanged(Configuration newConfig) {
    }
    
    public void onLowMemory() {
    }

    public void onTrimMemory(int level) {
        Object[] callbacks = collectComponentCallbacks();
        if (callbacks != null) {
            for (int i=0; i<callbacks.length; i++) {
                Object c = callbacks[i];
                if (c instanceof ComponentCallbacks2) {
                    ((ComponentCallbacks2)c).onTrimMemory(level);
                }
            }
        }
    }	
      public void registerComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            mComponentCallbacks.add(callback);
        }
    }

    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        synchronized (mComponentCallbacks) {
            mComponentCallbacks.remove(callback);
        }
    }
	
    // ------------------ Internal API ------------------
    
    /**
     * @hide
     */
    /* package */ final void attach(Context context) {
        attachBaseContext(context);
    }
    private Object[] collectComponentCallbacks() {
        Object[] callbacks = null;
        synchronized (mComponentCallbacks) {
            if (mComponentCallbacks.size() > 0) {
                callbacks = mComponentCallbacks.toArray();
            }
        }
        return callbacks;
    }	

}
